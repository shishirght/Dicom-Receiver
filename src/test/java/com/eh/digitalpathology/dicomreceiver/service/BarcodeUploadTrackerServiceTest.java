package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.GcpConfig;
import com.eh.digitalpathology.dicomreceiver.config.KafkaTopicConfig;
import com.eh.digitalpathology.dicomreceiver.model.PathQa;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BarcodeUploadTrackerServiceTest {

    @Mock
    private ScheduledExecutorService scheduler;
    @Mock
    private ScheduledFuture<?> scheduledFuture;
    @Mock
    private EventNotificationService eventNotificationService;
    @Mock
    private GcpConfig gcpConfig;
    @Mock
    private KafkaTopicConfig kafkaTopicConfig;
    @InjectMocks
    private BarcodeUploadTrackerService service;

    @BeforeEach
    void setUp() {
        service = new BarcodeUploadTrackerService(scheduler, eventNotificationService, kafkaTopicConfig);
    }

    @Test
    void testRecordUpload_ExistingTask_ShouldCancelOldAndReschedule() {

        ScheduledFuture<?> mockExisting = mock(ScheduledFuture.class);
        doReturn(scheduledFuture)
                .when(scheduler)
                .schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        service.recordUpload("BAR001", "STUDY", "SERIES", "DEVICE");
        service.recordUpload("BAR001", "STUDY", "SERIES", "DEVICE");

        verify(mockExisting, never()).cancel(anyBoolean());
        verify(scheduler, atLeast(1))
                .schedule(any(Runnable.class), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void testRecordUpload_ShouldScheduleNewTask() throws Exception {

        doReturn("mockTopic").when(kafkaTopicConfig).getPathqa();

        ObjectMapper mockMapper = mock(ObjectMapper.class);
        doReturn("{\"mock\":\"payload\"}").when(mockMapper).writeValueAsString(any());

        var field = BarcodeUploadTrackerService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(service, mockMapper);

        final Runnable[] capturedRunnable = new Runnable[1];
        doAnswer(invocation -> {
            capturedRunnable[0] = invocation.getArgument(0);
            return scheduledFuture;
        }).when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        service.recordUpload("BAR100", "STUDY100", "SERIES100", "DEVICE100");

        verify(scheduler).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.MINUTES));

        assertNotNull(capturedRunnable[0]);
        capturedRunnable[0].run();
        verify(eventNotificationService, times(1))
                .sendEvent("mockTopic", "BAR100", "{\"mock\":\"payload\"}");
    }

    @Test
    void testSendKafkaNotification_SuccessfulFlow() throws Exception {

        BarcodeUploadTrackerService spyService =
                Mockito.spy(new BarcodeUploadTrackerService(scheduler, eventNotificationService, kafkaTopicConfig));

        doReturn("mockTopic").when(kafkaTopicConfig).getPathqa();
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        doReturn("{\"json\":\"mock\"}").when(mockMapper).writeValueAsString(any(PathQa.class));

        Field field = BarcodeUploadTrackerService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(spyService, mockMapper);

        Method method = BarcodeUploadTrackerService.class
                .getDeclaredMethod("sendKafkaNotification", String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(spyService, "BAR456", "STUDY", "SERIES", "DEVICE");

        verify(eventNotificationService, times(1))
                .sendEvent("mockTopic","BAR456", "{\"json\":\"mock\"}");
    }

}