package com.eh.digitalpathology.dicomreceiver.config;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedisLettuceTuning {

    @Bean
    LettuceClientConfigurationBuilderCustomizer lettuceCustomizer ( ) {
        return builder -> builder.commandTimeout( Duration.ofSeconds( 3 ) ).clientOptions( ClientOptions.builder( ).autoReconnect( true ).disconnectedBehavior( ClientOptions.DisconnectedBehavior.REJECT_COMMANDS ).build( ) );
    }
}
