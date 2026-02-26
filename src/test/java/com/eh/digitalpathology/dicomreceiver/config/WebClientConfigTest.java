package com.eh.digitalpathology.dicomreceiver.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WebClientConfigTest {

    /** Helper to set private field 'baseUrl' on WebClientConfig so we don't need a Spring context. */
    private static void setBaseUrl(WebClientConfig cfg, String baseUrl) throws Exception {
        Field f = WebClientConfig.class.getDeclaredField("baseUrl");
        f.setAccessible(true);
        f.set(cfg, baseUrl);
    }

    @Test
    @DisplayName("webClient(): uses baseUrl and sets default Content-Type: application/json")
    void testWebClientBaseUrlAndDefaultHeader() throws Exception {
        WebClientConfig cfg = new WebClientConfig();
        setBaseUrl(cfg, "https://db.service.local");

        WebClient client = cfg.webClient();

        // Capture the actual request produced by this WebClient without hitting the network.
        AtomicReference<URI> capturedUri = new AtomicReference<>();
        AtomicReference<String> capturedContentType = new AtomicReference<>();

        WebClient intercepted = client.mutate()
                .exchangeFunction(request -> {
                    capturedUri.set(request.url());
                    capturedContentType.set(request.headers().getFirst(HttpHeaders.CONTENT_TYPE));
                    // Return a minimal OK response
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .build();

        // Perform a simple GET relative to the baseUrl
        intercepted.get()
                .uri("/health")
                .retrieve()
                .toBodilessEntity()
                .block();

        // Verify the effective URL = baseUrl + relative path
        assertEquals(URI.create("https://db.service.local/health"), capturedUri.get());

        // Verify default header from config is set
        assertEquals(MediaType.APPLICATION_JSON_VALUE, capturedContentType.get());
    }
}