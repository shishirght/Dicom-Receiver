package com.eh.digitalpathology.dicomreceiver.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventNotificationServiceTest {
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private EventNotificationService eventNotificationService;


    @Test
    void testSendEvent_Success() {

        String topic = "test-topic";
        String key = "sample-key";
        String value = "sample-value";
        eventNotificationService.sendEvent(topic, key, value);
        verify(kafkaTemplate, times(1)).send(topic, key, value);
        verifyNoMoreInteractions(kafkaTemplate);

    }

    @Test
    void testSendEvent_HandlesException() {
        String topic = "faulty-topic";
        String key = "error-key";
        String value = "invalid-value";
        doThrow(new RuntimeException()).when(kafkaTemplate).send(anyString(), anyString(), anyString());
        eventNotificationService.sendEvent(topic, key, value);
        verify(kafkaTemplate, times(1)).send(topic, key, value);
        verifyNoMoreInteractions(kafkaTemplate);
    }


}