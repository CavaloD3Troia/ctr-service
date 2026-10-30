package org.cavalodetroia.ctr.componenttests.support;

import io.cucumber.spring.ScenarioScope;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Talks to the running app over real HTTP and keeps the last response for the {@code Then} steps.
 * Never throws on a 4xx or 5xx: asserting on those is the point.
 */
@ScenarioScope
public class ApiClient {

    private final RestClient restClient;
    private ApiResponse lastResponse;

    public ApiClient(@LocalServerPort final int port) {
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    public ApiResponse get(final String path) {
        return remember(restClient.get().uri(path));
    }

    public ApiResponse post(final String path, final String json, final Map<String, String> headers) {
        return remember(restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                .body(json));
    }

    public ApiResponse lastResponse() {
        if (lastResponse == null) {
            throw new IllegalStateException("No request was sent in this scenario");
        }
        return lastResponse;
    }

    private ApiResponse remember(final RestClient.RequestHeadersSpec<?> request) {
        lastResponse = request.exchangeForRequiredValue((ignored, response) -> new ApiResponse(
                response.getStatusCode().value(),
                HttpHeaders.copyOf(response.getHeaders()),
                new String(response.getBody().readAllBytes(), UTF_8)));
        return lastResponse;
    }
}
