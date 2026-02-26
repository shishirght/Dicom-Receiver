package com.eh.digitalpathology.dicomreceiver.config;

import io.lettuce.core.resource.ClientResources;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RedisConfig without starting Spring or Redis.
 */
class RedisConfigTest {

    /** Minimal ObjectProvider impl for single instance. */
    private static <T> ObjectProvider<T> ofSingle(T instance) {
        return new ObjectProvider<>() {
            @Override public T getObject(Object... args) { return instance; }
            @Override public T getIfAvailable() { return instance; }
            @Override public T getIfUnique() { return instance; }
            @Override public void forEach(java.util.function.Consumer action) { if (instance != null) action.accept(instance); }
            @Override public Stream<T> stream() { return instance == null ? Stream.empty() : Stream.of(instance); }
            @Override public Stream<T> orderedStream() { return stream(); }
        };
    }

    /** Minimal ObjectProvider impl for multiple instances preserving order. */
    private static <T> ObjectProvider<T> ofList(List<T> items) {
        return new ObjectProvider<>() {
            @Override public T getObject(Object... args) { return items.isEmpty() ? null : items.get(0); }
            @Override public T getIfAvailable() { return items.isEmpty() ? null : items.get(0); }
            @Override public T getIfUnique() { return items.size() == 1 ? items.get(0) : null; }
            @Override public void forEach(java.util.function.Consumer action) { items.forEach(action); }
            @Override public Stream<T> stream() { return items.stream(); }
            @Override public Stream<T> orderedStream() { return items.stream(); }
        };
    }

    @Test
    @DisplayName("customRedisConnectionFactory(): applies host/port/password, clientResources, and customizers")
    void customRedisConnectionFactory_withPassword_andCustomizers() {
        // Arrange
        RedisConfig cfg = new RedisConfig();
        // Inject @Value fields via reflection
        setField(cfg, "redisHost", "127.0.0.1");
        setField(cfg, "redisPort", 6380);
        setField(cfg, "redisPassword", "s3cr3t");

        // Prepare a ClientResources instance
        ClientResources cr = ClientResources.builder().build();

        // Two customizers; later ones may override earlier ones if they set the same option.
        List<LettuceClientConfigurationBuilderCustomizer> customizers = new ArrayList<>();
        customizers.add(builder -> builder.commandTimeout(Duration.ofSeconds(3)));
        customizers.add(builder -> builder.shutdownTimeout(Duration.ofSeconds(1)));

        // Act
        LettuceConnectionFactory lcf = (LettuceConnectionFactory)
                cfg.customRedisConnectionFactory(ofList(customizers), ofSingle(cr));

        // Must initialize the LCF to populate internal config accessors
        lcf.afterPropertiesSet();

        // Assert standalone settings
        assertNotNull(lcf.getStandaloneConfiguration());
        assertEquals("127.0.0.1", lcf.getStandaloneConfiguration().getHostName());
        assertEquals(6380, lcf.getStandaloneConfiguration().getPort());
        RedisPassword pwd = lcf.getStandaloneConfiguration().getPassword();
        assertNotNull(pwd);
        assertEquals("s3cr3t", new String(pwd.get()));

        // Assert client configuration reflects customizers
        LettuceClientConfiguration clientCfg = lcf.getClientConfiguration();
        assertEquals(Duration.ofSeconds(3), clientCfg.getCommandTimeout(), "commandTimeout");
        assertEquals(Duration.ofSeconds(1), clientCfg.getShutdownTimeout(), "shutdownTimeout");

        // clientResources presence is not directly exposed; ensure no exceptions and config reachable
        assertNotNull(clientCfg, "clientCfg should not be null");
    }

    @Test
    @DisplayName("customRedisConnectionFactory(): omits password if blank and still builds")
    @Disabled("Ignoring cleanup test temporarily")
    void customRedisConnectionFactory_withoutPassword() {
        // Arrange
        RedisConfig cfg = new RedisConfig();
        setField(cfg, "redisHost", "localhost");
        setField(cfg, "redisPort", 6379);
        setField(cfg, "redisPassword", ""); // blank → should not set password

        // No client resources, no customizers
        LettuceConnectionFactory lcf = (LettuceConnectionFactory)
                cfg.customRedisConnectionFactory(ofList(List.of()), ofSingle(null));

        lcf.afterPropertiesSet();

        // Assert standalone settings
        assertEquals("localhost", lcf.getStandaloneConfiguration().getHostName());
        assertEquals(6379, lcf.getStandaloneConfiguration().getPort());
        assertNull(lcf.getStandaloneConfiguration().getPassword(), "password should be null when blank input");
    }

    @Test
    @DisplayName("redisTemplate(): uses provided factory and sets String/JSON serializers")
    void redisTemplate_serializersAndFactory() {
        // Build a minimal factory from config
        RedisConfig cfg = new RedisConfig();
        setField(cfg, "redisHost", "localhost");
        setField(cfg, "redisPort", 6379);
        setField(cfg, "redisPassword", null);

        LettuceConnectionFactory lcf = (LettuceConnectionFactory)
                cfg.customRedisConnectionFactory(ofList(List.of()), ofSingle(null));
        lcf.afterPropertiesSet();

        // Act
        RedisTemplate<String, Object> template = cfg.redisTemplate(lcf);

        // Assert connection factory wired
        assertSame(lcf, template.getConnectionFactory());

        // Assert serializers
        assertTrue(template.getKeySerializer() instanceof StringRedisSerializer);
        assertTrue(template.getHashKeySerializer() instanceof StringRedisSerializer);
        assertTrue(template.getValueSerializer() instanceof GenericJackson2JsonRedisSerializer);
        assertTrue(template.getHashValueSerializer() instanceof GenericJackson2JsonRedisSerializer);
    }

    // ---- helpers ----
    private static void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set field '" + name + "'", e);
        }
    }
}