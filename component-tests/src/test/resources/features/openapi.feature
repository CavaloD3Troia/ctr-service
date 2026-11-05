Feature: OpenAPI description
  ctr-service describes its HTTP API at /v3/api-docs, built from the controllers.

  # AC-12
  Scenario: The OpenAPI document describes the camera registration
    When the OpenAPI document is requested
    Then it describes POST "/api/v1/events/{eventId}/upload-session/cameras"
    And the operation takes the headers "Idempotency-Key, X-Correlation-Id"
    And the operation takes a "RegisterCameraRequest" and answers 201 with a "RegisterCameraResponse"
    And the operation answers these statuses, each with the X-Correlation-Id header
      | status | codes                                    |
      | 201    |                                          |
      | 400    | VALIDATION_FAILED                        |
      | 404    | EVENT_NOT_FOUND                          |
      | 409    | CAMERA_SET_CONFLICT, PART_COUNT_MISMATCH |
      | 422    | IDEMPOTENCY_KEY_REUSED                   |
      | 500    | INTERNAL_ERROR                           |
