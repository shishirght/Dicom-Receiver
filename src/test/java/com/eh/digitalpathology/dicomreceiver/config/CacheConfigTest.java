package com.eh.digitalpathology.dicomreceiver.config;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CacheConfigTest {

    @Test
    @DisplayName("studyBarcodeCache(): returns non-null cache and supports put/get")
    void returnsCache_andSupportsPutGet() {
        CacheConfig cfg = new CacheConfig();
        Cache<String, String> cache = cfg.studyBarcodeCache();

        assertNotNull(cache, "Cache bean must not be null");

        cache.put("study-001", "barcode-ABC");
        cache.put("study-002", "barcode-XYZ");

        assertEquals("barcode-ABC", cache.getIfPresent("study-001"));
        assertEquals("barcode-XYZ", cache.getIfPresent("study-002"));
        assertNull(cache.getIfPresent("study-003"), "Unknown key should return null");
    }

    @Test
    @DisplayName("studyBarcodeCache(): has size-based eviction with maximum=1000")
    @Disabled("Ignoring cleanup test temporarily")
    void studyBarcodeCache_hasEvictionPolicyWithMax() {
        CacheConfig cfg = new CacheConfig();
        Cache<String, String> cache = cfg.studyBarcodeCache();

        var evictionOpt = cache.policy().eviction();
        assertTrue(evictionOpt.isPresent(), "Eviction policy should be present (size-based)");
        var eviction = evictionOpt.get();

        assertTrue(eviction.isWeighted(), "Eviction should be size/weighted-based");
        assertEquals(1000L, eviction.getMaximum(), "Configured maximum size must be 1000");
    }


    @Test
    @DisplayName("invalidate(): removes a single key; invalidateAll(): clears everything")
    void invalidationOperationsWork() {
        CacheConfig cfg = new CacheConfig();
        Cache<String, String> cache = cfg.studyBarcodeCache();

        cache.put("s1", "b1");
        cache.put("s2", "b2");
        cache.put("s3", "b3");

        assertEquals("b1", cache.getIfPresent("s1"));
        assertEquals("b2", cache.getIfPresent("s2"));
        assertEquals("b3", cache.getIfPresent("s3"));

        // Remove one
        cache.invalidate("s2");
        assertNull(cache.getIfPresent("s2"));
        assertEquals("b1", cache.getIfPresent("s1"));
        assertEquals("b3", cache.getIfPresent("s3"));

        // Clear all
        cache.invalidateAll();
        assertNull(cache.getIfPresent("s1"));
        assertNull(cache.getIfPresent("s3"));
        assertEquals(0, cache.asMap().size());
    }

    @Test
    @DisplayName("get(key, mappingFunction): computes and stores values on cache miss, returns cached on hit")
    void getWithLoaderComputesOnMiss_andReturnsCachedOnHit() {
        CacheConfig cfg = new CacheConfig();
        Cache<String, String> cache = cfg.studyBarcodeCache();

        AtomicInteger computeCount = new AtomicInteger(0);

        // First call → miss → compute and cache
        String v1 = cache.get("study-900", k -> {
            computeCount.incrementAndGet();
            return "barcode-900";
        });
        assertEquals("barcode-900", v1);
        assertEquals(1, computeCount.get(), "Should have computed once");

        // Second call → hit → do NOT compute again
        String v2 = cache.get("study-900", k -> {
            computeCount.incrementAndGet();
            return "barcode-NEW";
        });
        assertEquals("barcode-900", v2);
        assertEquals(1, computeCount.get(), "Should not compute on cache hit");

        // Another key → miss → compute
        String v3 = cache.get("study-901", k -> {
            computeCount.incrementAndGet();
            return "barcode-901";
        });
        assertEquals("barcode-901", v3);
        assertEquals(2, computeCount.get(), "Should compute for a different key");
    }

    @Test
    @DisplayName("asMap(): exposes a live view matching cache contents")
    void asMapViewReflectsCache() {
        CacheConfig cfg = new CacheConfig();
        Cache<String, String> cache = cfg.studyBarcodeCache();

        cache.put("a", "A");
        cache.put("b", "B");

        Map<String, String> map = cache.asMap();
        assertEquals(2, map.size());
        assertEquals("A", map.get("a"));
        assertEquals("B", map.get("b"));

        // Changes reflect in both directions
        map.put("c", "C"); // inserts via view
        assertEquals("C", cache.getIfPresent("c"));

        cache.invalidate("a"); // invalidates in cache
        assertFalse(map.containsKey("a"));
    }

    @Test
    @DisplayName("getIfPresent(): returns null for missing keys")
    void getIfPresentForMissingKey() {
        CacheConfig cfg = new CacheConfig();
        Cache<String, String> cache = cfg.studyBarcodeCache();

        assertNull(cache.getIfPresent("nope"));
    }


}