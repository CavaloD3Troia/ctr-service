Feature: OpenAPI description
  ctr-service describes its HTTP API at /v3/api-docs, built from the controllers.

  # AC-12
  Scenario: The OpenAPI document describes the camera registration
    When the OpenAPI document is requested
    Then it describes POST "/api/v1/events/{eventId}/upload-session/cameras"
    And the operation takes the headers "Idempotency-Key, X-Correlation-Id"
    And the operation takes a "RegisterCameraRequest" and answers 201 with a "RegisterCameraResponse"
    And the venue format is one of "FUTSAL_5V5, FOOTBALL_7V7, FOOTBALL_11V11"
    And the operation answers these statuses, each with the X-Correlation-Id header
      | status | codes                                    |
      | 201    |                                          |
      | 400    | VALIDATION_FAILED                        |
      | 404    | EVENT_NOT_FOUND                          |
      | 409    | CAMERA_SET_CONFLICT, PART_COUNT_MISMATCH |
      | 422    | IDEMPOTENCY_KEY_REUSED                   |
      | 500    | INTERNAL_ERROR                           |

  # TH-143 AC-6: a part always has its window, null or not; a clip leaves out what it doesn't know yet
  Scenario: The OpenAPI document describes reading an upload session
    When the OpenAPI document is requested
    Then it describes GET "/api/v1/upload-sessions/{sessionId}"
    And the operation takes the headers "X-Correlation-Id"
    And the operation answers 200 with a "UploadSessionResponse"
    And the operation answers these statuses, each with the X-Correlation-Id header
      | status | codes             |
      | 200    |                   |
      | 400    | VALIDATION_FAILED |
      | 404    | SESSION_NOT_FOUND |
      | 500    | INTERNAL_ERROR    |
    And "PartResponse" requires exactly "partIndex, status, published, startMs, durationMs, clips"
    And "PartResponse" may have null "startMs, durationMs"
    And "ClipResponse" requires exactly "camera, status"
    And the "status" of "PartResponse" is one of "WAITING_LEFT, WAITING_RIGHT, WAITING, COMPLETE, NO_OVERLAP"
