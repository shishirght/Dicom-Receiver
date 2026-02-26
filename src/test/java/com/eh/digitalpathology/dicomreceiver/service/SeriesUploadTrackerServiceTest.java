package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.GcpConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeriesUploadTrackerServiceTest {

    @Mock
    private ScheduledExecutorService scheduler;

    @Mock
    private GcpConfig gcpConfig;

    @Mock
    private SeriesNotifierService seriesNotifierService;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    @InjectMocks
    private SeriesUploadTrackerService seriesUploadTrackerService;

    private static final String STUDY_INSTANCE_UID = "1.2.840.113619.2.55.3.123456789";
    private static final String SERIES_INSTANCE_UID = "1.2.840.113619.2.55.3.987654321";
    private static final String DICOM_WEB_URL = "https://healthcare.googleapis.com/v1";
    private static final String RESEARCH_STORE_URL = "projects/test-project/locations/us-central1/datasets/test-dataset/dicomStores/research-store";
    private static final int QUIET_PERIOD_MINUTES = 3;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(gcpConfig.getDicomWebUrl()).thenReturn(DICOM_WEB_URL);
        lenient().when(gcpConfig.getResearchStoreUrl()).thenReturn(RESEARCH_STORE_URL);
    }

    @Test
    void testRecordUpload_firstUpload_schedulesTask() {
        // Arrange
        when(scheduler.schedule(any(Runnable.class), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> scheduledFuture);

        // Act
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);

        // Assert
        verify(scheduler, times(1)).schedule(any(Runnable.class), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES));
        verify(scheduledFuture, never()).cancel(anyBoolean());
    }

    @Test
    void testRecordUpload_multipleUploads_cancelsExistingTask() {
        // Arrange
        when(scheduler.schedule(any(Runnable.class), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> scheduledFuture);
        when(scheduledFuture.isDone()).thenReturn(false);

        // Act - First upload
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);
        
        // Act - Second upload (should cancel first task)
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);

        // Assert
        verify(scheduler, times(2)).schedule(any(Runnable.class), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES));
        verify(scheduledFuture, times(1)).cancel(false);
        verify(scheduledFuture, times(1)).isDone();
    }

    @Test
    void testRecordUpload_existingTaskIsDone_doesNotCancel() {
        // Arrange
        when(scheduler.schedule(any(Runnable.class), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> scheduledFuture);
        when(scheduledFuture.isDone()).thenReturn(true);

        // Act - First upload
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);
        
        // Act - Second upload (existing task is done, should not cancel)
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);

        // Assert
        verify(scheduler, times(2)).schedule(any(Runnable.class), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES));
        verify(scheduledFuture, never()).cancel(anyBoolean());
        verify(scheduledFuture, times(1)).isDone();
    }

    @Test
    void testRecordUpload_scheduledTaskExecution_notifiesVisiopharm() {
        // Arrange
        when(scheduler.schedule(runnableCaptor.capture(), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> scheduledFuture);

        // Act
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);

        // Get the captured Runnable and execute it
        Runnable capturedRunnable = runnableCaptor.getValue();
        capturedRunnable.run();

        // Assert
        String expectedSeriesUrl = String.format("%s/%s/dicomWeb/studies/%s/series/%s",
                DICOM_WEB_URL, RESEARCH_STORE_URL, STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);
        
        verify(seriesNotifierService, times(1)).notifyVisiopharm(expectedSeriesUrl);
        verify(gcpConfig, times(1)).getDicomWebUrl();
        verify(gcpConfig, times(1)).getResearchStoreUrl();
    }

    @Test
    void testRecordUpload_scheduledTaskExecution_constructsCorrectUrl() {
        // Arrange
        when(scheduler.schedule(runnableCaptor.capture(), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> scheduledFuture);

        // Act
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);

        // Get the captured Runnable and execute it
        Runnable capturedRunnable = runnableCaptor.getValue();
        capturedRunnable.run();

        // Assert - Verify the URL format
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(seriesNotifierService).notifyVisiopharm(urlCaptor.capture());

        String capturedUrl = urlCaptor.getValue();
        assertTrue(capturedUrl.contains(DICOM_WEB_URL));
        assertTrue(capturedUrl.contains(RESEARCH_STORE_URL));
        assertTrue(capturedUrl.contains(STUDY_INSTANCE_UID));
        assertTrue(capturedUrl.contains(SERIES_INSTANCE_UID));
        assertTrue(capturedUrl.contains("/dicomWeb/studies/"));
        assertTrue(capturedUrl.contains("/series/"));
    }

    @Test
    void testRecordUpload_differentSeriesInstances_createsMultipleTasks() {
        // Arrange
        String seriesInstanceUID1 = "series-001";
        String seriesInstanceUID2 = "series-002";
        
        when(scheduler.schedule(any(Runnable.class), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> scheduledFuture);

        // Act
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, seriesInstanceUID1);
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, seriesInstanceUID2);

        // Assert
        verify(scheduler, times(2)).schedule(any(Runnable.class), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES));
        verify(scheduledFuture, never()).cancel(anyBoolean());
    }

    @Test
    void testRecordUpload_rapidSuccessiveUploads_cancelsIntermediateTasks() {
        // Arrange
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> future1 = mock(ScheduledFuture.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> future2 = mock(ScheduledFuture.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> future3 = mock(ScheduledFuture.class);
        
        when(scheduler.schedule(any(Runnable.class), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> future1)
                .thenAnswer(invocation -> future2)
                .thenAnswer(invocation -> future3);
        
        when(future1.isDone()).thenReturn(false);
        when(future2.isDone()).thenReturn(false);

        // Act - Three rapid uploads
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);

        // Assert
        verify(scheduler, times(3)).schedule(any(Runnable.class), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES));
        verify(future1, times(1)).cancel(false);
        verify(future2, times(1)).cancel(false);
        verify(future3, never()).cancel(anyBoolean());
    }

    @Test
    void testRecordUpload_withDifferentStudyInstanceUIDs_schedulesCorrectly() {
        // Arrange
        String studyInstanceUID1 = "study-001";
        String studyInstanceUID2 = "study-002";
        
        when(scheduler.schedule(runnableCaptor.capture(), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> scheduledFuture);

        // Act
        seriesUploadTrackerService.recordUpload(studyInstanceUID1, SERIES_INSTANCE_UID);

        // Execute the captured task
        runnableCaptor.getValue().run();

        // Assert
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(seriesNotifierService).notifyVisiopharm(urlCaptor.capture());
        assertTrue(urlCaptor.getValue().contains(studyInstanceUID1));
        assertFalse(urlCaptor.getValue().contains(studyInstanceUID2));
    }

    @Test
    void testRecordUpload_schedulerDelayAndTimeUnit_areCorrect() {
        // Arrange
        ArgumentCaptor<Long> delayCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> timeUnitCaptor = ArgumentCaptor.forClass(TimeUnit.class);
        
        when(scheduler.schedule(any(Runnable.class), delayCaptor.capture(), timeUnitCaptor.capture()))
                .thenAnswer(invocation -> scheduledFuture);

        // Act
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);

        // Assert
        assertEquals(QUIET_PERIOD_MINUTES, delayCaptor.getValue());
        assertEquals(TimeUnit.MINUTES, timeUnitCaptor.getValue());
    }

    @Test
    void testRecordUpload_withNullStudyInstanceUID_stillSchedulesTask() {
        // Arrange
        when(scheduler.schedule(runnableCaptor.capture(), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> scheduledFuture);

        // Act
        seriesUploadTrackerService.recordUpload(null, SERIES_INSTANCE_UID);
        runnableCaptor.getValue().run();

        // Assert
        verify(scheduler, times(1)).schedule(any(Runnable.class), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES));
        verify(seriesNotifierService, times(1)).notifyVisiopharm(anyString());
    }

    @Test
    void testRecordUpload_withSpecialCharactersInUIDs_handlesCorrectly() {
        // Arrange
        String studyWithSpecialChars = "1.2.840.113619.2.55.3.12345-67890";
        String seriesWithSpecialChars = "1.2.840.113619.2.55.3.98765-43210";
        
        when(scheduler.schedule(runnableCaptor.capture(), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> scheduledFuture);

        // Act
        seriesUploadTrackerService.recordUpload(studyWithSpecialChars, seriesWithSpecialChars);
        runnableCaptor.getValue().run();

        // Assert
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(seriesNotifierService).notifyVisiopharm(urlCaptor.capture());
        
        String capturedUrl = urlCaptor.getValue();
        assertTrue(capturedUrl.contains(studyWithSpecialChars));
        assertTrue(capturedUrl.contains(seriesWithSpecialChars));
    }

    @Test
    void testRecordUpload_multipleSeriesForSameStudy_maintainsSeparateTasks() {
        // Arrange
        String series1 = "series-001";
        String series2 = "series-002";
        String series3 = "series-003";
        
        when(scheduler.schedule(any(Runnable.class), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> scheduledFuture);

        // Act
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, series1);
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, series2);
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, series3);

        // Assert
        verify(scheduler, times(3)).schedule(any(Runnable.class), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES));
        // Since these are different series, no cancellation should occur
        verify(scheduledFuture, never()).cancel(anyBoolean());
    }

    @Test
    void testRecordUpload_whenNotifierServiceThrowsException_exceptionPropagates() {
        // Arrange
        when(scheduler.schedule(runnableCaptor.capture(), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> scheduledFuture);
        
        doThrow(new RuntimeException("Notification failed"))
                .when(seriesNotifierService).notifyVisiopharm(anyString());

        // Act
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);
        
        // Execute the captured task and expect exception
        Runnable capturedRunnable = runnableCaptor.getValue();
        
        // Assert
        assertThrows(RuntimeException.class, capturedRunnable::run);
        verify(seriesNotifierService, times(1)).notifyVisiopharm(anyString());
    }

    @Test
    void testRecordUpload_urlFormat_matchesExpectedPattern() {
        // Arrange
        when(scheduler.schedule(runnableCaptor.capture(), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> scheduledFuture);

        // Act
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);
        runnableCaptor.getValue().run();

        // Assert
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(seriesNotifierService).notifyVisiopharm(urlCaptor.capture());

        String expectedUrl = String.format("%s/%s/dicomWeb/studies/%s/series/%s",
                DICOM_WEB_URL, RESEARCH_STORE_URL, STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);
        
        assertEquals(expectedUrl, urlCaptor.getValue());
    }

    @Test
    void testRecordUpload_cancelWithFalseParameter_usesCorrectCancelMode() {
        // Arrange
        ArgumentCaptor<Boolean> cancelCaptor = ArgumentCaptor.forClass(Boolean.class);
        
        when(scheduler.schedule(any(Runnable.class), eq((long) QUIET_PERIOD_MINUTES), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> scheduledFuture);
        when(scheduledFuture.isDone()).thenReturn(false);

        // Act
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);
        seriesUploadTrackerService.recordUpload(STUDY_INSTANCE_UID, SERIES_INSTANCE_UID);

        // Assert
        verify(scheduledFuture).cancel(cancelCaptor.capture());
        assertFalse(cancelCaptor.getValue(), "Cancel should be called with false (mayInterruptIfRunning)");
    }
}
