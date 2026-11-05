package org.cavalodetroia.ctr.componenttests.steps;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.assertj.core.api.InstanceOfAssertFactories;
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

    @Then("it describes GET {string}")
    public void itDescribesGet(final String path) {
        operation = "$.paths['" + path + "'].get";
        final Map<String, Object> get = document.read(operation);
        assertThat(get).containsKeys("summary", "description", "responses");
    }

    @Then("the operation answers 200 with a {string}")
    public void theOperationAnswers200With(final String response) {
        assertThat(document.read(operation + ".responses['200'].content['application/json'].schema['$ref']",
                String.class))
                .endsWith("/" + response);
    }

    @Then("{string} requires exactly {string}")
    public void theSchemaRequiresExactly(final String schema, final String fields) {
        final List<String> required = document.read("$.components.schemas." + schema + ".required");
        assertThat(required).containsExactlyInAnyOrderElementsOf(names(fields));
    }

    // OpenAPI 3.1 says null with a "null" type, 3.0 with nullable
    @Then("{string} may have null {string}")
    public void theSchemaMayHaveNull(final String schema, final String fields) {
        for (final String field : names(fields)) {
            final Map<String, Object> property = document.read("$.components.schemas." + schema + ".properties."
                    + field);
            assertThat(property).as("%s.%s", schema, field).satisfiesAnyOf(
                    nullable -> assertThat(nullable).containsEntry("nullable", true),
                    typed -> assertThat(typed.get("type")).asInstanceOf(InstanceOfAssertFactories.LIST)
                            .contains("null"));
        }
    }

    @Then("the {string} of {string} is one of {string}")
    public void thePropertyIsOneOf(final String field, final String schema, final String values) {
        final List<String> documented = document.read("$.components.schemas." + schema + ".properties." + field
                + ".enum");
        assertThat(documented).containsExactlyElementsOf(names(values));
    }

    @Then("the operation takes the headers {string}")
    public void theOperationTakesTheHeaders(final String headers) {
        final List<String> documented = document.read(operation + ".parameters[?(@.in == 'header')].name");
        assertThat(documented).containsExactlyInAnyOrderElementsOf(names(headers));
    }

    @Then("the operation takes a {string} and answers 201 with a {string}")
    public void theOperationTakesAndAnswers(final String request, final String response) {
        assertThat(document.read(operation + ".requestBody.content['application/json'].schema['$ref']", String.class))
                .endsWith("/" + request);
        assertThat(document.read(operation + ".responses['201'].content['application/json'].schema['$ref']",
                String.class))
                .endsWith("/" + response);
        assertThat(document.read("$.components.schemas." + request + ".required", List.class))
                .contains("camera", "partCount", "contentType");
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
