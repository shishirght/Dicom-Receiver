package com.eh.digitalpathology.dicomreceiver.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KafkaTopicConfigTest {

    @Test
    @DisplayName("Should set and get all Kafka topic configuration properties")
    void testKafkaTopicConfigProperties() {
        KafkaTopicConfig config = new KafkaTopicConfig();

        config.setReceiver("receiver-topic");
        config.setStgcmt("stgcmt-topic");
        config.setEmail("email-topic");
        config.setPathqa("pathqa-topic");

        assertEquals("receiver-topic", config.getReceiver());
        assertEquals("stgcmt-topic", config.getStgcmt());
        assertEquals("email-topic", config.getEmail());
        assertEquals("pathqa-topic", config.getPathqa());
    }

    @Test
    @DisplayName("Default values should be null before assignment")
    void testDefaultNullValues() {
        KafkaTopicConfig config = new KafkaTopicConfig();

        assertNull(config.getReceiver());
        assertNull(config.getStgcmt());
        assertNull(config.getEmail());
        assertNull(config.getPathqa());
    }

    @Test
    @DisplayName("Allow empty strings (no trimming/coercion happens at this layer)")
    void testEmptyStringsAreAccepted() {
        KafkaTopicConfig config = new KafkaTopicConfig();

        config.setReceiver("");
        config.setStgcmt("");
        config.setEmail("");
        config.setPathqa("");

        assertEquals("", config.getReceiver());
        assertEquals("", config.getStgcmt());
        assertEquals("", config.getEmail());
        assertEquals("", config.getPathqa());
    }

    @Test
    @DisplayName("Annotations are present to catch accidental removal")
    void testAnnotationPresence() {
        assertTrue(
            KafkaTopicConfig.class.isAnnotationPresent(org.springframework.boot.context.properties.ConfigurationProperties.class),
            "@ConfigurationProperties should be present"
        );
        assertTrue(
            KafkaTopicConfig.class.isAnnotationPresent(org.springframework.cloud.context.config.annotation.RefreshScope.class),
            "@RefreshScope should be present"
        );
        assertTrue(
            KafkaTopicConfig.class.isAnnotationPresent(org.springframework.context.annotation.Configuration.class),
            "@Configuration should be present"
        );
    }
}