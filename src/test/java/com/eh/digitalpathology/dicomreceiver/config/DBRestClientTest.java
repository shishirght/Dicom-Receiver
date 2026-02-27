package com.eh.digitalpathology.dicomreceiver.config;

import com.eh.digitalpathology.dicomreceiver.exceptions.DbConnectorExeption;
import com.eh.digitalpathology.dicomreceiver.model.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DBRestClientTest {

    private MockWebServer mockWebServer;
    private DBRestClient dbRestClient;
    private ObjectMapper objectMapper;

    private final ParameterizedTypeReference<ApiResponse<String>> responseType =
            new ParameterizedTypeReference<>() {};

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();
        WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();

        dbRestClient = new DBRestClient(webClient);
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private MockResponse jsonResponse(int code, Object body) throws Exception {
        return new MockResponse()
                .setResponseCode(code)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(toJson(body));
    }

    @Test
    void exchange_WithRequestBody_ShouldSendBodyInRequest() throws Exception {
        Map<String, Object> successBody = Map.of(
                "status", "success",
                "data", "result",
                "errorCode", "",
                "errorMessage", ""
        );
        mockWebServer.enqueue(jsonResponse(200, successBody));

        StepVerifier.create(
                        dbRestClient.exchange(HttpMethod.POST, "/test", "payload", responseType, null))
                .assertNext(response -> assertEquals("success", response.status()))
                .verifyComplete();

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertTrue(request.getBody().readUtf8().contains("payload"));
    }

    @Test
    void exchange_WithNullRequestBody_ShouldSendRequestWithoutBody() throws Exception {
        Map<String, Object> successBody = Map.of(
                "status", "success",
                "data", "result",
                "errorCode", "",
                "errorMessage", ""
        );
        mockWebServer.enqueue(jsonResponse(200, successBody));

        StepVerifier.create(
                        dbRestClient.exchange(HttpMethod.GET, "/test", null, responseType, null))
                .assertNext(response -> assertEquals("success", response.status()))
                .verifyComplete();

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals(0, request.getBodySize()); // no body sent
    }

    @Test
    void exchange_WithHeadersConsumer_ShouldApplyCustomHeader() throws Exception {
        Map<String, Object> successBody = Map.of(
                "status", "success",
                "data", "result",
                "errorCode", "",
                "errorMessage", ""
        );
        mockWebServer.enqueue(jsonResponse(200, successBody));

        StepVerifier.create(
                        dbRestClient.exchange(
                                HttpMethod.POST, "/test", "payload", responseType,
                                headers -> headers.add("X-Custom-Header", "custom-value")))
                .assertNext(response -> assertEquals("success", response.status()))
                .verifyComplete();

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("custom-value", request.getHeader("X-Custom-Header"));
    }

    @Test
    void exchange_WithNullHeadersConsumer_ShouldNotAddCustomHeaders() throws Exception {
        Map<String, Object> successBody = Map.of(
                "status", "success",
                "data", "result",
                "errorCode", "",
                "errorMessage", ""
        );
        mockWebServer.enqueue(jsonResponse(200, successBody));

        StepVerifier.create(
                        dbRestClient.exchange(HttpMethod.GET, "/test", null, responseType, null))
                .assertNext(response -> assertEquals("success", response.status()))
                .verifyComplete();

        RecordedRequest request = mockWebServer.takeRequest();
        assertNull(request.getHeader("X-Custom-Header"));
    }

    @Test
    void exchange_WhenErrorResponseStatusIsFailure_ShouldEmitDbConnectorException() throws Exception {
        Map<String, Object> errorBody = Map.of(
                "status", "failure",
                "data", "",
                "errorCode", "ERR_001",
                "errorMessage", "Something went wrong"
        );
        mockWebServer.enqueue(jsonResponse(500, errorBody));

        StepVerifier.create(
                        dbRestClient.exchange(HttpMethod.POST, "/test", "payload", responseType, null))
                .expectErrorSatisfies(throwable -> {
                    assertInstanceOf(DbConnectorExeption.class, throwable);
                    DbConnectorExeption ex = (DbConnectorExeption) throwable;
                    assertEquals("ERR_001", ex.getErrorCode());
                    assertEquals("Something went wrong", ex.getMessage());
                })
                .verify();
    }

    @Test
    void exchange_WhenErrorResponseStatusIsNotFailure_ShouldCompleteEmpty() throws Exception {
        Map<String, Object> errorBody = Map.of(
                "status", "error",      // not "failure" → flatMap returns Mono.empty()
                "data", "",
                "errorCode", "ERR_002",
                "errorMessage", "Partial error"
        );
        mockWebServer.enqueue(jsonResponse(500, errorBody));

        StepVerifier.create(
                        dbRestClient.exchange(HttpMethod.GET, "/test", null, responseType, null))
                .verifyComplete();
    }

    @Test
    void exchange_WhenErrorStatusIsCaseInsensitiveFailure_ShouldEmitDbConnectorException() throws Exception {
        Map<String, Object> errorBody = Map.of(
                "status", "FAILURE",    // uppercase — equalsIgnoreCase must handle this
                "data", "",
                "errorCode", "ERR_403",
                "errorMessage", "Forbidden"
        );
        mockWebServer.enqueue(jsonResponse(403, errorBody));

        StepVerifier.create(
                        dbRestClient.exchange(HttpMethod.GET, "/secure", null, responseType, null))
                .expectErrorSatisfies(throwable -> {
                    assertInstanceOf(DbConnectorExeption.class, throwable);
                    DbConnectorExeption ex = (DbConnectorExeption) throwable;
                    assertEquals("ERR_403", ex.getErrorCode());
                    assertEquals("Forbidden", ex.getMessage());
                })
                .verify();
    }
}