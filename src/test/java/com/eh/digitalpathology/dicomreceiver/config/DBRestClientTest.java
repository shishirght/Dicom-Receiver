package com.eh.digitalpathology.dicomreceiver.config;

import com.eh.digitalpathology.dicomreceiver.exceptions.DbConnectorExeption;
import com.eh.digitalpathology.dicomreceiver.model.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DBRestClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private DBRestClient dbRestClient;

    private final ParameterizedTypeReference<ApiResponse<String>> responseType =
            new ParameterizedTypeReference<>() {};

    @BeforeEach
    void setUp() {
        dbRestClient = new DBRestClient(webClient);
        when(webClient.method(any(HttpMethod.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    }

    @Test
    void exchange_withRequestBody_returnsResponse() {
        ApiResponse<String> apiResponse = new ApiResponse<>("success", "result", null, null);

        when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(apiResponse));

        ApiResponse<String> result = dbRestClient.exchange(HttpMethod.POST, "/test", "payload", responseType, null).block();

        assertEquals("success", result.status());
    }

    @Test
    void exchange_withNullBody_returnsResponse() {
        ApiResponse<String> apiResponse = new ApiResponse<>("success", "result", null, null);

        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(apiResponse));

        ApiResponse<String> result = dbRestClient.exchange(HttpMethod.GET, "/test", null, responseType, null).block();

        assertEquals("success", result.status());
    }

    @Test
    void exchange_withHeadersConsumer_appliesHeaders() {
        ApiResponse<String> apiResponse = new ApiResponse<>("success", "result", null, null);

        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(apiResponse));

        ApiResponse<String> result = dbRestClient.exchange(HttpMethod.GET, "/test", null, responseType,
                headers -> headers.add("X-Custom-Header", "custom-value")).block();

        assertEquals("success", result.status());
        verify(requestBodySpec).headers(any());
    }

    @Test
    void exchange_withNullHeadersConsumer_doesNotCallHeaders() {
        ApiResponse<String> apiResponse = new ApiResponse<>("success", "result", null, null);

        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(apiResponse));

        dbRestClient.exchange(HttpMethod.GET, "/test", null, responseType, null).block();

        verify(requestBodySpec, never()).headers(any());
    }

    @Test
    void exchange_whenErrorStatusIsFailure_throwsDbConnectorException() {
        when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(new DbConnectorExeption("ERR_001", "Something went wrong")));

        Mono<ApiResponse<String>> mono = dbRestClient.exchange(HttpMethod.POST, "/test", "payload", responseType, null);

        Throwable thrown = assertThrows(Throwable.class, mono::block);
        Throwable cause = Exceptions.unwrap(thrown);
        assertInstanceOf(DbConnectorExeption.class, cause);
        assertEquals("ERR_001", ((DbConnectorExeption) cause).getErrorCode());
        assertEquals("Something went wrong", cause.getMessage());
    }

    @Test
    void exchange_whenErrorStatusIsFailureCaseInsensitive_throwsDbConnectorException() {
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(new DbConnectorExeption("ERR_403", "Forbidden")));

        Mono<ApiResponse<String>> mono = dbRestClient.exchange(HttpMethod.GET, "/secure", null, responseType, null);

        Throwable thrown = assertThrows(Throwable.class, mono::block);
        Throwable cause = Exceptions.unwrap(thrown);
        assertInstanceOf(DbConnectorExeption.class, cause);
        assertEquals("ERR_403", ((DbConnectorExeption) cause).getErrorCode());
        assertEquals("Forbidden", cause.getMessage());
    }

    @Test
    void exchange_whenErrorStatusIsNotFailure_completesEmpty() {
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.empty());

        ApiResponse<String> result = dbRestClient.exchange(HttpMethod.GET, "/test", null, responseType, null).block();

        assertNull(result);
    }
}