package com.eh.digitalpathology.dicomreceiver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisClientTest  {
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
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

        boolean result = redisClient.isFileProcessed("file1");

        assertTrue(result);
        verify(redisTemplate).hasKey("file1");
    }

    @Test
    void testIsFileProcessed_WhenKeyDoesNotExist_ShouldReturnFalse() {
        when(redisTemplate.hasKey("file2")).thenReturn(false);

        boolean result = redisClient.isFileProcessed("file2");

        assertFalse(result);
        verify(redisTemplate).hasKey("file2");
    }

    @Test
    void testMarkFileAsProcessed_shouldStoreKey() {
        String fileKey = "file3";
        Duration expectedDuration = Duration.ofHours(12);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        redisClient.markFileAsProcessed(fileKey);

        verify(redisTemplate).opsForValue();
        verify(valueOperations).set(fileKey, "processed", expectedDuration);

    }
    @Test
    void tryLockFile_shouldReturnTrue_whenLockIsAcquired() {
        String fileKey = "file1";
        Duration lockDuration = Duration.ofMinutes(10);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(fileKey + ":lock", "LOCKED", lockDuration)).thenReturn(true);

        boolean result = redisClient.tryLockFile(fileKey, lockDuration);

        assertTrue(result);
        verify(redisTemplate).opsForValue();
        verify(valueOperations).setIfAbsent(fileKey + ":lock", "LOCKED", lockDuration);
    }

    @Test
    void releaseFileLock_shouldDeleteLockKey() {
        String fileKey = "file3";
        redisClient.releaseFileLock(fileKey);

        verify(redisTemplate).delete(fileKey + ":lock");
    }

}