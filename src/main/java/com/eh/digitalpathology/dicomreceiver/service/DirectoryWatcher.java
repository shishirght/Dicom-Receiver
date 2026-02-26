package com.eh.digitalpathology.dicomreceiver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;


@Component
public class DirectoryWatcher {
    private static final Logger log = LoggerFactory.getLogger( DirectoryWatcher.class.getName( ) );
    private final FileProcessingService fileProcessingService;
    private final ExecutorService executorService;
    private final ScheduledExecutorService stabilizerScheduler;

    private final ConcurrentHashMap< Path, ScheduledFuture< ? > > scheduledChecks = new ConcurrentHashMap<>( );

    private final ConcurrentHashMap< Path, WatchEvent< Path > > latestEvents = new ConcurrentHashMap<>( );

    private final Duration quietPeriod = Duration.ofSeconds( 5);   // time with no events before we start probing
    private final Map< Path, ProbeState > probeStates = new ConcurrentHashMap<>( );
    private final Map< Path, Integer > retryCounts = new ConcurrentHashMap<>( );

    public DirectoryWatcher ( FileProcessingService fileProcessingService, @Qualifier( "directoryWatcherExecutor" ) ExecutorService executorService, @Qualifier( "stabilizerScheduler" ) ScheduledExecutorService stabilizerScheduler ) {
        this.fileProcessingService = fileProcessingService;
        this.executorService = executorService;
        this.stabilizerScheduler = stabilizerScheduler;
    }

    public void directoryLookup ( String fileStore, String intermediateStore ) {
        Path dir = Paths.get( fileStore ).toAbsolutePath( );
        try ( WatchService watchService = FileSystems.getDefault( ).newWatchService( ) ) {
            if ( !Files.isDirectory( dir ) ) {
                throw new IllegalArgumentException( "Not a directory: " + dir );
            }
            dir.register( watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.OVERFLOW );
            log.info( "directoryLookup :: Watching directory: {}", dir );
            for ( ; ; ) {
                WatchKey key = watchService.take( ); // blocks until events available
                for ( WatchEvent< ? > evt : key.pollEvents( ) ) {
                    WatchEvent.Kind< ? > kind = evt.kind( );

                    if ( kind == StandardWatchEventKinds.OVERFLOW ) {
                        log.warn( "directoryLookup :: OVERFLOW detected; rescanning for missed files..." );
                        rescanDirectory( dir, fileStore, intermediateStore );
                        continue;
                    }

                    @SuppressWarnings( "unchecked" ) WatchEvent< Path > event = (WatchEvent< Path >) evt;
                    Path childAbsPath = dir.resolve( event.context( ) ).toAbsolutePath( );

                    if ( kind == StandardWatchEventKinds.ENTRY_CREATE ) {
                        onEvent( childAbsPath, event, fileStore, intermediateStore );
                    }
                }
                boolean valid = key.reset( );
                if ( !valid ) {
                    log.warn( "directoryLookup :: WatchKey invalid for {}; stopping watcher.", dir );
                    break;
                }
            }
        } catch ( InterruptedException e ) {
            log.error( "directoryLookup :: Interrupted", e );
            Thread.currentThread( ).interrupt( );
        } catch ( Exception e ) {
            log.error( "directoryLookup :: Error initializing watcher for {}: {}", dir, e.getMessage() );
        }
    }

    /**
     * Debounce per file and schedule a stability check after quiet period.
     */
    private void onEvent ( Path absoluteFilePath, WatchEvent< Path > event, String fileStore, String intermediateStore ) {
        // Skip directories
        if ( Files.isDirectory( absoluteFilePath ) ) {
            log.debug( "onEvent :: Skip directory {}", absoluteFilePath );
            return;
        }
        // Skip temp/partial files
        String name = absoluteFilePath.getFileName( ).toString( );
        if ( name.endsWith( ".part" ) || name.endsWith( ".filepart" ) || name.endsWith( ".tmp" ) ) {
            log.info( "onEvent :: Ignoring temp/partial {}", absoluteFilePath );
            return;
        }
        // Record latest event (we'll pass this to processFile later)
        latestEvents.put( absoluteFilePath, event );

        // Cancel any previous scheduled check (debounce)
        ScheduledFuture< ? > prev = scheduledChecks.get( absoluteFilePath );
        if ( prev != null ) {
            prev.cancel( false );
        }
        long delayMs = computeDynamicQuietMillis( absoluteFilePath );
        ScheduledFuture< ? > future = stabilizerScheduler.schedule( ( ) -> startStabilityCheck( absoluteFilePath, fileStore, intermediateStore ), delayMs, TimeUnit.MILLISECONDS );
        scheduledChecks.put( absoluteFilePath, future );
    }

    private long computeDynamicQuietMillis ( Path path ) {
        final long baseMs = quietPeriod.toMillis( ); // e.g., configured 2000 ms (2s) or 5000 ms (5s)
        try {
            if ( Files.exists( path ) ) {
                long bytes = Files.size( path );
                final long MB = 1024L * 1024L;
                final long GB = 1024L * MB;

                // Buckets
                if ( bytes <= 10L * MB ) {
                    // Tiny files: minimal quiet
                    return Math.max( 500L, Math.min( baseMs, 2000L ) ); // 0.5–2s
                } else if ( bytes <= 512L * MB ) {
                    // Small files: modest quiet scaled by size
                    // Linearly map 10MB..512MB to ~2s..10s
                    double frac = ( bytes - 10.0 * MB ) / ( 502.0 * MB ); // 0..~1
                    long scaled = (long) ( 2000L + frac * ( 8000L ) ); // 2s..10s
                    return Math.max( baseMs, scaled );
                } else {
                    // Large files: per-GB increment + base, with cap
                    long gb = Math.max( 1L, bytes / GB ); // true GB count (no minimum for tiny files anymore)
                    long perGbMs = 20_000L;             // +20s per GB (tune: 25–30s for SMB/NFS, 10–15s for local)
                    long scaled = Math.max( baseMs, 10_000L ) + ( gb * perGbMs ); // ensure at least 10s base for large
                    long capMs = 240_000L;              // cap at 4 minutes (tune as needed)
                    return Math.min( scaled, capMs );
                }
            }
        } catch ( IOException ignore ) {
            // fall back to base
        }
        return baseMs;
    }


    private void startStabilityCheck ( Path path, String fileStore, String intermediateStore ) {
        try {
            if ( !Files.exists( path ) ) {
                log.warn( "startStabilityCheck :: Path no longer exists: {}", path );
                cleanup( path );
                return;
            }
            if ( !Files.isReadable( path ) ) {
                log.warn( "startStabilityCheck :: Path not readable yet: {}", path );
                reschedule( path, fileStore, intermediateStore );
                return;
            }

            long bytes = Files.size( path );
            final long MB = 1024L * 1024L;
            final long GB = 1024L * MB;

            long probeMs;
            int probesRequired;

            if ( bytes <= 10L * MB ) {
                probeMs = 500L;       // fast cadence for tiny files
                probesRequired = 2;   // need 2 consecutive stable checks
            } else if ( bytes <= 512L * MB ) {
                probeMs = 1500L;      // moderate cadence
                probesRequired = 3;   // 3 consecutive stable checks
            } else {
                long gb = Math.max( 1L, bytes / GB );
                probeMs = Math.min( 10_000L, 3_000L + gb * 2_000L ); // 3s base + 2s/GB, cap 10s
                probesRequired = (int) Math.min( 8, 3 + gb );        // 3 base + 1 per GB, cap 8
            }

            ProbeState state = new ProbeState( );
            state.probeMs = probeMs;
            state.probesRequired = probesRequired;
            probeStates.put( path, state );
            log.debug( "startStabilityCheck :: Init probe for {}: probeMs={}ms probesRequired={}", path, probeMs, probesRequired );
            // Schedule first probe (non-blocking)
            ScheduledFuture< ? > f = stabilizerScheduler.schedule( ( ) -> runProbe( path, fileStore, intermediateStore ), probeMs, TimeUnit.MILLISECONDS );
            scheduledChecks.put( path, f );

        } catch ( IOException ioex ) {
            log.error( "startStabilityCheck :: Error initializing for {}: {}", path, ioex.getMessage() );
            reschedule( path, fileStore, intermediateStore );
        } catch ( Exception ex ) {
            log.error( "startStabilityCheck :: Error for {}: {}", path, ex.getMessage() );
            reschedule( path, fileStore, intermediateStore );
        }
    }


    private void runProbe ( Path path, String fileStore, String intermediateStore ) {
        ProbeState state = probeStates.get( path );
        if ( state == null ) {
            // Initialize if missing (defensive)
            startStabilityCheck( path, fileStore, intermediateStore );
            return;
        }
        try {
            if ( !Files.exists( path ) ) {
                log.warn( "runProbe :: Path no longer exists: {}", path );
                cleanup( path );
                probeStates.remove( path );
                return;
            }
            if ( !Files.isReadable( path ) ) {
                log.warn( "runProbe :: Path not readable yet: {}", path );
                reschedule( path, fileStore, intermediateStore );
                return;
            }

            long size = Files.size( path );
            FileTime mtime = Files.getLastModifiedTime( path );

            if ( size == state.prevSize && Objects.equals( mtime, state.prevMTime ) ) {
                state.stableCount++;
            } else {
                state.stableCount = 0; // reset when change observed
            }
            state.prevSize = size;
            state.prevMTime = mtime;

            if ( state.stableCount >= state.probesRequired ) {
                log.info( "runProbe :: File is stable after {} probes @ {} ms: {}", state.probesRequired, state.probeMs, path );
                cleanup( path );
                probeStates.remove( path );
                // Retrieve last event or synthesize
                WatchEvent< Path > event = latestEvents.get( path );
                if ( event == null ) {
                    event = SyntheticWatchEvent.create( StandardWatchEventKinds.ENTRY_CREATE, path.getFileName( ) );
                }
                WatchEvent< Path > finalEvent = event;
                // Process on separate executor (non-blocking of scheduler)
                executorService.submit( ( ) -> {
                    try {
                        fileProcessingService.processFile( finalEvent, fileStore, intermediateStore );
                    } catch ( Exception ex ) {
                        log.error( "Processing failed for {}: {}", path, ex.getMessage());
                    } finally {
                        latestEvents.remove( path );
                    }
                } );
            } else {
                // Not yet stable; schedule next probe without blocking any thread
                ScheduledFuture< ? > f = stabilizerScheduler.schedule( ( ) -> runProbe( path, fileStore, intermediateStore ), state.probeMs, TimeUnit.MILLISECONDS );
                scheduledChecks.put( path, f );
                log.trace( "runProbe :: {} not stable yet (count={}/{})", path, state.stableCount, state.probesRequired );
            }

        } catch ( IOException ioex ) {
            log.error( "runProbe :: IO error for {}: {}", path, ioex.getMessage() );
            reschedule( path, fileStore, intermediateStore );
        } catch ( Exception ex ) {
            log.error( "runProbe :: Error for {}: {}", path, ex.getMessage() );
            reschedule( path, fileStore, intermediateStore );
        }
    }

    private void reschedule ( Path path, String fileStore, String intermediateStore ) {
        int attempt = retryCounts.merge( path, 1, Integer::sum );
        long baseQuiet = computeDynamicQuietMillis( path );
        // Exponential backoff: 1x, 2x, 4x, 8x (capped)
        long delayMs = baseQuiet * Math.min( 8, 1 << Math.max( 0, attempt - 1 ) );
        ScheduledFuture< ? > future = stabilizerScheduler.schedule( ( ) -> startStabilityCheck( path, fileStore, intermediateStore ), delayMs, TimeUnit.MILLISECONDS );
        scheduledChecks.put( path, future );
    }

    private void cleanup ( Path path ) {
        ScheduledFuture< ? > prev = scheduledChecks.remove( path );
        if ( prev != null ) {
            prev.cancel( false );
        }
        probeStates.remove( path );
        retryCounts.remove( path );
    }

    private void rescanDirectory ( Path dir, String fileStore, String intermediateStore ) {
        try ( DirectoryStream< Path > stream = Files.newDirectoryStream( dir ) ) {
            for ( Path candidate : stream ) {
                // Create a synthetic event so your service can still use event.context()
                WatchEvent< Path > synthetic = SyntheticWatchEvent.create( StandardWatchEventKinds.ENTRY_CREATE, candidate.getFileName( ) );
                onEvent( candidate.toAbsolutePath( ), synthetic, fileStore, intermediateStore );
            }
        } catch ( IOException ioex ) {
            log.error( "rescanDirectory :: Error scanning directory {}, {}", dir, ioex.getMessage() );
        }
    }

    /**
     * Minimal synthetic WatchEvent implementation used for rescan/overflow.
     */
    private static final class SyntheticWatchEvent implements WatchEvent< Path > {
        private final Kind< Path > kind;
        private final Path context;
        private final int count;

        private SyntheticWatchEvent ( Kind< Path > kind, Path context, int count ) {
            this.kind = kind;
            this.context = context;
            this.count = count;
        }

        static SyntheticWatchEvent create ( Kind< ? > kind, Path context ) {
            @SuppressWarnings( "unchecked" ) Kind< Path > k = (Kind< Path >) kind;
            return new SyntheticWatchEvent( k, context, 1 );
        }

        @Override
        public Kind< Path > kind ( ) {
            return kind;
        }

        @Override
        public int count ( ) {
            return count;
        }

        @Override
        public Path context ( ) {
            return context;
        }
    }

    private static final class ProbeState {
        long prevSize = -1L;
        FileTime prevMTime = null;
        int stableCount = 0;
        int probesRequired; // dynamic per size
        long probeMs;       // dynamic per size
    }
}




