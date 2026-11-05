Feature: OpenAPI description
  ctr-service describes its HTTP API at /v3/api-docs, built from the controllers.

  # AC-12
  Scenario: The OpenAPI document describes the camera registration
    When the OpenAPI document is requested
    Then it describes POST "/api/v1/events/{eventId}/upload-session/cameras"
    And the operation takes the headers "Idempotency-Key, X-Correlation-Id"
    And the operation takes a "RegisterCameraRequest" with the required fields "camera, partCount, contentType" and answers 201 with a "RegisterCameraResponse"
    And the venue format is one of "FUTSAL_5V5, FOOTBALL_7V7, FOOTBALL_11V11"
    And the operation answers these statuses, each with the X-Correlation-Id header
      | status | codes                                    |
      | 201    |                                          |
      | 400    | VALIDATION_FAILED                        |
      | 404    | EVENT_NOT_FOUND                          |
      | 409    | CAMERA_SET_CONFLICT, PART_COUNT_MISMATCH |
      | 422    | IDEMPOTENCY_KEY_REUSED                   |
      | 500    | INTERNAL_ERROR                           |

  # TH-142 AC-11
  Scenario: The OpenAPI document describes the clip confirmation
    When the OpenAPI document is requested
    Then it describes POST "/api/v1/upload-sessions/{sessionId}/cameras/{camera}/parts/{partIndex}/complete"
    And the operation takes the path parameters "sessionId, camera, partIndex"
    And the operation takes the headers "X-Correlation-Id"
    And the operation takes a "CompleteClipRequest" with the required fields "sizeBytes, etag, startMs, durationMs" and answers 200 with a "CompleteClipResponse"
    And the operation answers these statuses, each with the X-Correlation-Id header
      | status | codes                                            |
      | 200    |                                                  |
      | 400    | VALIDATION_FAILED                                |
      | 404    | SESSION_NOT_FOUND                                |
      | 409    | CLIP_ALREADY_COMPLETED                           |
      | 422    | OBJECT_NOT_FOUND, SIZE_MISMATCH, ETAG_MISMATCH   |
      | 500    | INTERNAL_ERROR                                   |
      | 503    | STORAGE_UNAVAILABLE                              |
    And the "Retry-After" response header of 503 is documented
