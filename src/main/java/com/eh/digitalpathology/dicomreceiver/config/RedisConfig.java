package com.eh.digitalpathology.dicomreceiver.config;

import io.lettuce.core.resource.ClientResources;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Value( "${spring.redis.host}" )
    private String redisHost;

    @Value( "${spring.redis.port}" )
    private int redisPort;

    @Value( "${spring.redis.password}" )
    private String redisPassword;

    @Bean( name = "customRedisConnectionFactory" )
    @Primary
    @RefreshScope
    public RedisConnectionFactory customRedisConnectionFactory ( ObjectProvider< LettuceClientConfigurationBuilderCustomizer > customizers, ObjectProvider< ClientResources > clientResources ) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration( redisHost, redisPort );
        if ( redisPassword != null && !redisPassword.isEmpty( ) ) {
            config.setPassword( RedisPassword.of( redisPassword ) );
        }
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder( );
        // If you have a shared ClientResources bean elsewhere, wire it in
        clientResources.ifAvailable( builder::clientResources );
        // Apply all available customizers in order (includes your RedisLettuceTuning)
        customizers.orderedStream( ).forEach( c -> c.customize( builder ) );
        LettuceClientConfiguration clientConfig = builder.build( );
        return new LettuceConnectionFactory( config, clientConfig );

    }

    @Bean
    public RedisTemplate< String, Object > redisTemplate ( @Qualifier( "customRedisConnectionFactory" ) RedisConnectionFactory factory ) {
        RedisTemplate< String, Object > template = new RedisTemplate<>( );
        template.setConnectionFactory( factory );
        var stringSerializer = new StringRedisSerializer( );
        var jsonSerializer = new GenericJackson2JsonRedisSerializer( );
        template.setKeySerializer( stringSerializer );
        template.setValueSerializer( jsonSerializer );
        template.setHashKeySerializer( stringSerializer );
        template.setHashValueSerializer( jsonSerializer );
        template.afterPropertiesSet( );
        return template;
    }
}

