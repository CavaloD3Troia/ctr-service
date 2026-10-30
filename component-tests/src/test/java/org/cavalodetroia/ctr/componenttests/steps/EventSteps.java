package org.cavalodetroia.ctr.componenttests.steps;

import com.google.protobuf.InvalidProtocolBufferException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.cavalodetroia.contracts.ctr.v1.EventLifecycle;
import org.cavalodetroia.ctr.componenttests.support.ApiClient;
import org.cavalodetroia.ctr.componenttests.support.KafkaTestClient;
import org.cavalodetroia.ctr.componenttests.support.ScenarioContext;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.cavalodetroia.ctr.componenttests.support.KafkaTestClient.EVENT_LIFECYCLE_TOPIC;

public class EventSteps {

    private static final String EVENTS = "/api/v1/events";

    private final ApiClient api;
    private final KafkaTestClient kafka;
    private final ScenarioContext scenario;

    public EventSteps(final ApiClient api, final KafkaTestClient kafka, final ScenarioContext scenario) {
        this.api = api;
        this.kafka = kafka;
        this.scenario = scenario;
    }

    @Given("an event {string} of sport {string} exists")
    public void anEventExists(final String name, final String sport) {
        anEventIsCreated(name, sport);
        assertThat(api.lastResponse().status()).as(api.lastResponse().body()).isEqualTo(201);
    }

    @When("an event {string} of sport {string} is created")
    public void anEventIsCreated(final String name, final String sport) {
        create(name, sport, Map.of());
    }

    @When("an event {string} of sport {string} is created with correlation id {string}")
    public void anEventIsCreatedWithCorrelationId(final String name, final String sport, final String correlationId) {
        create(name, sport, Map.of("X-Correlation-Id", correlationId));
    }

    @When("the event {string} is requested")
    public void theEventIsRequested(final String eventId) {
        api.get(EVENTS + "/" + eventId);
    }

    @Then("the response points to the created event")
    public void theResponsePointsToTheCreatedEvent() {
        assertThat(api.lastResponse().header(HttpHeaders.LOCATION)).endsWith(EVENTS + "/" + scenario.eventId());
    }

    @Then("the created event is returned with name {string} and sport {string}")
    public void theCreatedEventIsReturned(final String name, final String sport) {
        var response = api.lastResponse();
        assertThat(response.<String>json("$.id")).isEqualTo(scenario.eventId().toString());
        assertThat(response.<String>json("$.name")).isEqualTo(name);
        assertThat(response.<String>json("$.sport")).isEqualTo(sport);
        assertThat(response.<String>json("$.createdAt")).isNotBlank();
    }

    @Then("the created event can be fetched")
    public void theCreatedEventCanBeFetched() {
        var created = api.lastResponse();
        var fetched = api.get(EVENTS + "/" + scenario.eventId());
        assertThat(fetched.status()).isEqualTo(200);
        assertThat(fetched.<Object>json("$")).isEqualTo(created.json("$"));
    }

    @Then("an EventCreated message is published for the created event with name {string} and sport {string}")
    public void anEventCreatedMessageIsPublished(final String name, final String sport)
            throws InvalidProtocolBufferException {
        var record = kafka.awaitMessage(EVENT_LIFECYCLE_TOPIC, scenario.eventId().toString());
        var message = EventLifecycle.parseFrom(record.value());

        assertThat(message.getEventId()).isEqualTo(scenario.eventId().toString());
        assertThat(message.hasOccurredAt()).isTrue();
        assertThat(message.getChangeCase()).isEqualTo(EventLifecycle.ChangeCase.EVENT_CREATED);
        assertThat(message.getEventCreated().getName()).isEqualTo(name);
        assertThat(message.getEventCreated().getSport()).isEqualTo(sport);
    }

    @Then("the message published for the created event has header {string} with value {string}")
    public void theMessageHasHeader(final String name, final String value) {
        var record = kafka.awaitMessage(EVENT_LIFECYCLE_TOPIC, scenario.eventId().toString());
        var header = record.headers().lastHeader(name);

        assertThat(header).as("header '%s'", name).isNotNull();
        assertThat(new String(header.value(), UTF_8)).isEqualTo(value);
    }

    private void create(final String name, final String sport, final Map<String, String> headers) {
        var json = JsonMapper.shared().writeValueAsString(Map.of("name", name, "sport", sport));
        var response = api.post(EVENTS, json, headers);
        if (response.status() == 201) {
            scenario.eventId(UUID.fromString(response.json("$.id")));
        }
    }
}
