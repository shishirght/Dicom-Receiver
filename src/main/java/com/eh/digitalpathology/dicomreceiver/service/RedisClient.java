package com.eh.digitalpathology.dicomreceiver.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisClient {

    private final RedisTemplate< String, Object > redisTemplate;

    public RedisClient ( RedisTemplate< String, Object > redisTemplate ) {
        this.redisTemplate = redisTemplate;
    }


    public boolean isFileProcessed ( String fileKey ) {
        try {
            return Boolean.TRUE.equals( redisTemplate.hasKey( fileKey ) );
        }catch ( Exception ex ){
            return false;
        }
    }

    public void markFileAsProcessed ( String fileKey ) {
        redisTemplate.opsForValue( ).set( fileKey, "processed", java.time.Duration.ofHours( 12 ) );
    }

    /**
     * Try to acquire a lock for a file. Returns true if lock is acquired, false otherwise.
     */
    public boolean tryLockFile ( String fileKey, Duration lockDuration ) {
        try {
            Boolean success = redisTemplate.opsForValue( ).setIfAbsent( fileKey + ":lock", "LOCKED", lockDuration );
            return Boolean.TRUE.equals( success );
        }catch ( Exception ex ){
            return false;
        }
    }

    /**
     * Release the lock manually if needed.
     */
    public void releaseFileLock ( String fileKey ) {
        try {
            redisTemplate.delete( fileKey + ":lock" );
        }catch ( Exception ignored ){
            // Ignored
        }
    }

}


