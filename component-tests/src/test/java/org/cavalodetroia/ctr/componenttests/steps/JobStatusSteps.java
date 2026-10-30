package org.cavalodetroia.ctr.componenttests.steps;

import com.google.protobuf.Timestamp;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.cavalodetroia.contracts.pipeline.v1.JobState;
import org.cavalodetroia.contracts.pipeline.v1.JobStatusChanged;
import org.cavalodetroia.ctr.componenttests.support.ApiClient;
import org.cavalodetroia.ctr.componenttests.support.KafkaTestClient;
import org.cavalodetroia.ctr.componenttests.support.ScenarioContext;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.cavalodetroia.ctr.componenttests.support.KafkaTestClient.JOB_STATUS_TOPIC;

public class JobStatusSteps {

    private final ApiClient api;
    private final KafkaTestClient kafka;
    private final ScenarioContext scenario;

    public JobStatusSteps(final ApiClient api, final KafkaTestClient kafka, final ScenarioContext scenario) {
        this.api = api;
        this.kafka = kafka;
        this.scenario = scenario;
    }

    @When("the pipeline reports the analysis of that event as {string}")
    public void thePipelineReports(final String state) {
        var now = Instant.now();
        var message = JobStatusChanged.newBuilder()
                .setEventId(eventId())
                .setState(JobState.valueOf("JOB_STATE_" + state))
                .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()))
                .build();
        kafka.send(JOB_STATUS_TOPIC, eventId(), message.toByteArray());
    }

    @When("the pipeline sends a message for that event that is not protobuf")
    public void thePipelineSendsAnUnreadableMessage() {
        kafka.send(JOB_STATUS_TOPIC, eventId(), new byte[]{(byte) 0xff, 0x01, 0x02});
    }

    @When("the analysis of that event is requested")
    public void theAnalysisIsRequested() {
        api.get(analysisPath());
    }

    @Then("the analysis of that event is eventually {string}")
    public void theAnalysisIsEventually(final String state) {
        // Scenario-scoped beans only resolve on the scenario's own thread
        await().pollInSameThread().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var response = api.get(analysisPath());
            assertThat(response.status()).as(response.body()).isEqualTo(200);
            assertThat(response.<String>json("$.eventId")).isEqualTo(eventId());
            assertThat(response.<String>json("$.state")).isEqualTo(state);
        });
    }

    private String eventId() {
        return scenario.eventId().toString();
    }

    private String analysisPath() {
        return "/api/v1/events/" + eventId() + "/analysis";
    }
}
