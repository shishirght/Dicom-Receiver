package com.eh.digitalpathology.dicomreceiver.config;
import com.eh.digitalpathology.dicomreceiver.exceptions.DbConnectorExeption;
import com.eh.digitalpathology.dicomreceiver.model.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

@Component
public class DBRestClient {

    private final WebClient webClient;

    @Autowired
    public DBRestClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public <T> Mono<ApiResponse<T>> exchange(HttpMethod method, String uri, Object requestBody, ParameterizedTypeReference<ApiResponse<T>> responseType, Consumer<HttpHeaders> headersConsumer) {
        WebClient.RequestBodySpec requestSpec = webClient.method(method).uri(uri);

        WebClient.RequestHeadersSpec<?> headersSpec;
        if (requestBody != null) {
            headersSpec = requestSpec.body(BodyInserters.fromValue(requestBody));
        } else {
            headersSpec = requestSpec;
        }
        if (headersConsumer != null) {
            headersSpec.headers(headersConsumer);
        }

        return headersSpec.retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                clientResponse.bodyToMono(new ParameterizedTypeReference<ApiResponse<T>>() {})
                    .flatMap(errorResponse -> {
                        if ("failure".equalsIgnoreCase(errorResponse.status())) {
                            return Mono.error(new DbConnectorExeption(errorResponse.errorCode(), errorResponse.errorMessage()));
                        }
                        return Mono.empty();
                })).bodyToMono(responseType);
    }
}
