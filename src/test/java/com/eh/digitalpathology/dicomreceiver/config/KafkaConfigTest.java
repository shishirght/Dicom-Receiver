package com.eh.digitalpathology.dicomreceiver.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KafkaConfigTest {

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("producerFactory(): config map has bootstrap servers, serializers, acks, idempotence")
    void producerFactory_hasExpectedProperties() throws Exception {
        KafkaConfig config = new KafkaConfig();
        setField(config, "bootstrapServers", "localhost:9092");
        setField(config, "acksConfig", "all");
        setField(config, "enableIdempotenceConfig", "true");

        ProducerFactory<String, String> pf = config.producerFactory();
        assertNotNull(pf);
        assertTrue(pf instanceof DefaultKafkaProducerFactory);

        Map<String, Object> props = ((DefaultKafkaProducerFactory<String, String>) pf).getConfigurationProperties();
        assertEquals("localhost:9092", props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals(StringSerializer.class, props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
        assertEquals(StringSerializer.class, props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals("all", props.get(ProducerConfig.ACKS_CONFIG));
        assertEquals("true", props.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
    }


    @Test
    @DisplayName("customKafkaTemplate(): is created from the configured producerFactory")
    void customKafkaTemplate_constructs() throws Exception {
        KafkaConfig config = new KafkaConfig();
        setField(config, "bootstrapServers", "broker:9092");
        setField(config, "acksConfig", "1");
        setField(config, "enableIdempotenceConfig", "false");

        KafkaTemplate<String, String> tpl = config.customKafkaTemplate();
        assertNotNull(tpl);
    }

    @Test
    @DisplayName("kafkaListenerContainerFactory(): sets MANUAL_IMMEDIATE ack mode")
    void kafkaListenerContainerFactory_setsAckMode() throws Exception {
        KafkaConfig config = new KafkaConfig();
        setField(config, "bootstrapServers", "broker:9092");
        setField(config, "acksConfig", "all");
        setField(config, "enableIdempotenceConfig", "true");

        ConcurrentKafkaListenerContainerFactory<String, String> factory = config.kafkaListenerContainerFactory();
        assertNotNull(factory);
        assertEquals(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE,
                factory.getContainerProperties().getAckMode()
        );
    }


}
