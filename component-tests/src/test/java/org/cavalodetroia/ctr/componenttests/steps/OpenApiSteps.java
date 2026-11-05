package org.cavalodetroia.ctr.componenttests.steps;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the OpenAPI document the app serves and checks what it says about an operation.
 */
public class OpenApiSteps {

    private final RestClient http;

    private DocumentContext document;
    private String operation;

    public OpenApiSteps(@LocalServerPort final int port) {
        this.http = RestClient.create("http://localhost:" + port);
    }

    @When("the OpenAPI document is requested")
    public void theOpenApiDocumentIsRequested() {
        document = JsonPath.parse(http.get().uri("/v3/api-docs").retrieve().body(String.class));
    }

    @Then("it describes POST {string}")
    public void itDescribesPost(final String path) {
        operation = "$.paths['" + path + "'].post";
        final Map<String, Object> post = document.read(operation);
        assertThat(post).containsKeys("summary", "requestBody", "responses");
    }

    @Then("the operation takes the headers {string}")
    public void theOperationTakesTheHeaders(final String headers) {
        final List<String> documented = document.read(operation + ".parameters[?(@.in == 'header')].name");
        assertThat(documented).containsExactlyInAnyOrderElementsOf(names(headers));
    }

    @Then("the operation takes the path parameters {string}")
    public void theOperationTakesThePathParameters(final String parameters) {
        final List<String> documented = document.read(operation + ".parameters[?(@.in == 'path')].name");
        assertThat(documented).containsExactlyInAnyOrderElementsOf(names(parameters));
    }

    @Then("the operation takes a {string} with the required fields {string} and answers {int} with a {string}")
    public void theOperationTakesAndAnswers(final String request, final String required, final int status,
                                            final String response) {
        assertThat(document.read(operation + ".requestBody.content['application/json'].schema['$ref']", String.class))
                .endsWith("/" + request);
        assertThat(document.read(operation + ".responses['" + status + "'].content['application/json'].schema['$ref']",
                String.class))
                .endsWith("/" + response);
        assertThat(document.read("$.components.schemas." + request + ".required", List.class))
                .containsExactlyInAnyOrderElementsOf(names(required));
    }

    @Then("the {string} response header of {int} is documented")
    public void theResponseHeaderIsDocumented(final String header, final int status) {
        final Map<String, Object> headers = document.read(operation + ".responses['" + status + "'].headers");
        assertThat(headers).containsKey(header);
    }

    // The format is a string in VenueRequest, checked by the domain on the first camera only; the document still
    // lists the formats
    @Then("the venue format is one of {string}")
    public void theVenueFormatIsOneOf(final String formats) {
        final List<String> documented = document.read("$.components.schemas.VenueRequest.properties.format.enum");
        assertThat(documented).containsExactlyElementsOf(names(formats));
    }

    @Then("the operation answers these statuses, each with the X-Correlation-Id header")
    public void theOperationAnswersTheseStatuses(final List<Map<String, String>> statuses) {
        final Map<String, Object> responses = document.read(operation + ".responses");
        assertThat(responses).containsOnlyKeys(statuses.stream().map(row -> row.get("status")).toList());
        for (final Map<String, String> row : statuses) {
            final String response = operation + ".responses['" + row.get("status") + "']";
            final Map<String, Object> headers = document.read(response + ".headers");
            assertThat(headers).as("headers of %s", row.get("status")).containsKey("X-Correlation-Id");
            final String description = document.read(response + ".description");
            if (row.get("codes") != null) {
                assertThat(description).as("description of %s", row.get("status")).contains(names(row.get("codes")));
            }
        }
    }

    private static List<String> names(final String commaSeparated) {
        return Arrays.stream(commaSeparated.split(",\\s*")).toList();
    }
}
