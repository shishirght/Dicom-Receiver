package com.eh.digitalpathology.dicomreceiver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisClientTest {

    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;
    private RedisClient redisClient;

    @BeforeEach
    void setup() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        redisClient = new RedisClient(redisTemplate);
    }

    @Test
    void testIsFileProcessed_WhenKeyExists_ShouldReturnTrue() {
        when(redisTemplate.hasKey("file1")).thenReturn(true);

        assertTrue(redisClient.isFileProcessed("file1"));
        verify(redisTemplate).hasKey("file1");
    }

    @Test
    void testIsFileProcessed_WhenKeyDoesNotExist_ShouldReturnFalse() {
        when(redisTemplate.hasKey("file2")).thenReturn(false);

        assertFalse(redisClient.isFileProcessed("file2"));
        verify(redisTemplate).hasKey("file2");
    }

    @Test
    void testIsFileProcessed_WhenHasKeyReturnsNull_ShouldReturnFalse() {
        when(redisTemplate.hasKey("fileNull")).thenReturn(null);

        assertFalse(redisClient.isFileProcessed("fileNull"));
    }

    @Test
    void testIsFileProcessed_WhenRedisThrowsException_ShouldReturnFalse() {
        when(redisTemplate.hasKey("fileEx")).thenThrow(new RuntimeException("Redis down"));

        assertFalse(redisClient.isFileProcessed("fileEx"));
    }

    @Test
    void testMarkFileAsProcessed_shouldStoreKeyWithTwelveHourTTL() {
        String fileKey = "file3";
        Duration expectedDuration = Duration.ofHours(12);

        redisClient.markFileAsProcessed(fileKey);

        verify(valueOperations).set(fileKey, "processed", expectedDuration);
    }

    @Test
    void testTryLockFile_WhenLockAcquired_ShouldReturnTrue() {
        String fileKey = "file1";
        Duration lockDuration = Duration.ofMinutes(10);

        when(valueOperations.setIfAbsent(fileKey + ":lock", "LOCKED", lockDuration)).thenReturn(true);

        assertTrue(redisClient.tryLockFile(fileKey, lockDuration));
        verify(valueOperations).setIfAbsent(fileKey + ":lock", "LOCKED", lockDuration);
    }

    @Test
    void testTryLockFile_WhenLockAlreadyHeld_ShouldReturnFalse() {
        String fileKey = "file2";
        Duration lockDuration = Duration.ofMinutes(10);

        when(valueOperations.setIfAbsent(fileKey + ":lock", "LOCKED", lockDuration)).thenReturn(false);

        assertFalse(redisClient.tryLockFile(fileKey, lockDuration));
    }

    @Test
    void testTryLockFile_WhenSetIfAbsentReturnsNull_ShouldReturnFalse() {
        String fileKey = "fileNull";
        Duration lockDuration = Duration.ofMinutes(10);

        when(valueOperations.setIfAbsent(fileKey + ":lock", "LOCKED", lockDuration)).thenReturn(null);

        assertFalse(redisClient.tryLockFile(fileKey, lockDuration));
    }

    @Test
    void testTryLockFile_WhenRedisThrowsException_ShouldReturnFalse() {
        String fileKey = "fileEx";
        Duration lockDuration = Duration.ofMinutes(10);

        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        assertFalse(redisClient.tryLockFile(fileKey, lockDuration));
    }

    @Test
    void testReleaseFileLock_shouldDeleteLockKey() {
        String fileKey = "file3";

        redisClient.releaseFileLock(fileKey);

        verify(redisTemplate).delete(fileKey + ":lock");
    }

    @Test
    void testReleaseFileLock_WhenRedisThrowsException_ShouldNotPropagate() {
        String fileKey = "fileEx";

        doThrow(new RuntimeException("Redis down")).when(redisTemplate).delete(fileKey + ":lock");

        assertDoesNotThrow(() -> redisClient.releaseFileLock(fileKey));
    }
}