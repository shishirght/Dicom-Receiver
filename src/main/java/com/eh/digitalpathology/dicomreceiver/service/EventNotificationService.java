package com.eh.digitalpathology.dicomreceiver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventNotificationService {
    private static final Logger log = LoggerFactory.getLogger(EventNotificationService.class.getName());

    private final KafkaTemplate<String, String> kafkaTemplate;

    public EventNotificationService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEvent(String topic, String key, String value) {
        try {
            kafkaTemplate.send(topic, key, value);
            log.info("sendEvent :: Message sent to topic {}: Value: {}", topic, value);
            log.info("sendEvent :: Message sent to topic {}: Key: {}", topic, key);

        } catch (Exception e) {
            log.error("sendEvent :: Exception occurred while sending message: {}", e.getMessage());
        }
    }
}
