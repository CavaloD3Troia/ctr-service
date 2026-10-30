Feature: Analysis job status
  The pipeline reports how the analysis of an event is going on pipeline.job.status.v1,
  and ctr-service serves the latest status it has seen.

  Background:
    Given an event "Benfica vs Porto" of sport "football" exists

  Scenario: No status has been reported yet
    When the analysis of that event is requested
    Then the response status is 404
    And the problem code is "JOB_STATUS_NOT_FOUND"

  Scenario: The latest reported status is served
    When the pipeline reports the analysis of that event as "PROCESSING"
    And the pipeline reports the analysis of that event as "READY"
    Then the analysis of that event is eventually "READY"

  Scenario: A message that cannot be read does not block the ones behind it
    When the pipeline sends a message for that event that is not protobuf
    And the pipeline reports the analysis of that event as "PROCESSING"
    Then the analysis of that event is eventually "PROCESSING"
