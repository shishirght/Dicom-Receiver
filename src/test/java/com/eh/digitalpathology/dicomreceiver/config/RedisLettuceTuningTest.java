package com.eh.digitalpathology.dicomreceiver.config;

import io.lettuce.core.ClientOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RedisLettuceTuningTest {

    @Test
    @DisplayName("lettuceCustomizer(): sets 3s command timeout and REJECT_COMMANDS on disconnect with autoReconnect")
    void testLettuceCustomizer() {
        RedisLettuceTuning tuning = new RedisLettuceTuning();

        LettuceClientConfigurationBuilderCustomizer customizer = tuning.lettuceCustomizer(); // bean under test
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                LettuceClientConfiguration.builder();

        // Apply customization
        customizer.customize(builder);
        LettuceClientConfiguration cfg = builder.build();

        // Assert timeout
        assertEquals(Duration.ofSeconds(3), cfg.getCommandTimeout());

        // Assert client options configured
        assertTrue(cfg.getClientOptions().isPresent(), "ClientOptions must be present");
        ClientOptions opts = (ClientOptions) cfg.getClientOptions().get();
        assertTrue(opts.isAutoReconnect(), "autoReconnect should be true");
        assertEquals(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS, opts.getDisconnectedBehavior());
    }
}