package org.cavalodetroia.ctr.componenttests.steps;

import io.cucumber.java.en.Then;
import org.cavalodetroia.ctr.componenttests.support.ApiClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assertions on the last HTTP response, shared by every feature.
 */
public class HttpSteps {

    private final ApiClient api;

    public HttpSteps(final ApiClient api) {
        this.api = api;
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(final int status) {
        var response = api.lastResponse();
        assertThat(response.status()).as(response.body()).isEqualTo(status);
    }

    @Then("the response header {string} is {string}")
    public void theResponseHeaderIs(final String name, final String value) {
        assertThat(api.lastResponse().header(name)).isEqualTo(value);
    }

    @Then("the problem code is {string}")
    public void theProblemCodeIs(final String code) {
        assertThat(api.lastResponse().<String>json("$.code")).isEqualTo(code);
    }

    @Then("the problem reports {string} as invalid")
    public void theProblemReportsAsInvalid(final String field) {
        assertThat(api.lastResponse().<Map<String, Object>>json("$.errors")).containsKey(field);
    }
}
