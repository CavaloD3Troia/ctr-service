Feature: Register a camera on an event's upload session
  POST /api/v1/events/{eventId}/upload-session/cameras. The first camera of an event opens its upload session and
  sets the venue; a second camera joins it. A session ends with the cameras SINGLE, or LEFT and RIGHT, all with the
  same number of parts. Each camera is told where to upload: its prefix in the bucket, then {partIndex}.mp4.

  Background:
    Given an event exists

  # AC-1, AC-2, AC-3
  Scenario: The left camera opens the session and the right camera joins it
    When the LEFT camera registers with 40 parts on a FOOTBALL_11V11 venue measuring 105 by 68 metres
    Then the response status is 201
    And the response gives the LEFT camera its prefix and the clip key "{partIndex}.mp4"
    And the response lists the cameras "LEFT", each with 40 parts and none uploaded
    When the RIGHT camera registers with 40 parts and no venue
    Then the response status is 201
    And the response has the session id of the first registration
    And the response gives the RIGHT camera its prefix and the clip key "{partIndex}.mp4"
    And the response lists the cameras "LEFT, RIGHT", each with 40 parts and none uploaded
    And the session has the cameras "LEFT, RIGHT" in the database
    And the database has 40 pending clips for each of the cameras "LEFT, RIGHT", keyed by camera and part index
    And the database has 40 waiting parts for the session

  # AC-1, AC-3, AC-4
  Scenario: A single camera opens the session alone
    When the SINGLE camera registers with 3 parts on a FUTSAL_5V5 venue measuring 38.5 by 19 metres
    Then the response status is 201
    And the response gives the SINGLE camera its prefix and the clip key "{partIndex}.mp4"
    And the response lists the cameras "SINGLE", each with 3 parts and none uploaded
    And the session has the cameras "SINGLE" in the database
    And the database has 3 pending clips for each of the cameras "SINGLE", keyed by camera and part index
    And the database has 3 waiting parts for the session
    And the session venue in the database is FUTSAL_5V5 measuring 38.5 by 19 metres

  # AC-4
  Scenario: A venue without measures gets the standard dimensions of its format
    When the LEFT camera registers with 40 parts on a FOOTBALL_11V11 venue without measures
    Then the response status is 201
    And the session venue in the database is FOOTBALL_11V11 measuring 105 by 68 metres

  # AC-4: only the first camera's venue counts, so a later one is not checked beyond its measures going together
  Scenario Outline: The venue of a later camera is ignored: <venue>
    Given the LEFT camera registers with 40 parts on a FOOTBALL_11V11 venue measuring 100 by 64 metres
    When the RIGHT camera registers with 40 parts and the venue '<venue>'
    Then the response status is 201
    And the session venue in the database is FOOTBALL_11V11 measuring 100 by 64 metres
    And a warning says the venue was ignored

    Examples:
      | venue                                            |
      | {"format": "FUTSAL_5V5"}                         |
      | {}                                               |
      | {"format": "HOCKEY"}                             |
      | {"lengthM": 100, "widthM": 60}                   |

  # AC-5
  Scenario: A single camera cannot join a session with a left camera
    Given the LEFT camera registers with 40 parts on a FOOTBALL_11V11 venue without measures
    When the SINGLE camera registers with 40 parts and no venue
    Then the response status is 409
    And the response is a problem with the code "CAMERA_SET_CONFLICT"
    And the session has the cameras "LEFT" in the database

  # AC-5
  Scenario: A camera cannot register twice with different Idempotency-Keys
    Given the LEFT camera registers with 40 parts on a FOOTBALL_11V11 venue without measures
    When the LEFT camera registers with 40 parts and no venue
    Then the response status is 409
    And the response is a problem with the code "CAMERA_SET_CONFLICT"
    And the session has the cameras "LEFT" in the database

  # AC-5
  Scenario: The second camera must bring the same number of parts
    Given the LEFT camera registers with 40 parts on a FOOTBALL_11V11 venue without measures
    When the RIGHT camera registers with 39 parts and no venue
    Then the response status is 409
    And the response is a problem with the code "PART_COUNT_MISMATCH"
    And the session has the cameras "LEFT" in the database

  # AC-7
  Scenario: The same request with the same Idempotency-Key is answered once
    When the LEFT camera registers with 40 parts on a FOOTBALL_11V11 venue without measures
    And the same request is sent again
    Then the response status is 201
    And the response is the same as the first one
    And the session has the cameras "LEFT" in the database
    And the database has 40 pending clips for each of the cameras "LEFT", keyed by camera and part index
    And the database has 40 waiting parts for the session
    And one response is stored for the Idempotency-Key

  # AC-6
  Scenario: An unknown event is not found
    Given the event is unknown
    When the LEFT camera registers with 40 parts on a FOOTBALL_11V11 venue without measures
    Then the response status is 404
    And the response is a problem with the code "EVENT_NOT_FOUND"
    And the event has no upload session

  # AC-6
  Scenario: The first camera must send the venue
    When the LEFT camera registers with 40 parts and no venue
    Then the response status is 400
    And the response is a problem with the code "VALIDATION_FAILED"
    And the problem lists the invalid field "venue"
    And the event has no upload session

  # AC-6
  Scenario Outline: The first camera must send a valid venue format: <venue>
    When the LEFT camera registers with 40 parts and the venue '<venue>'
    Then the response status is 400
    And the response is a problem with the code "VALIDATION_FAILED"
    And the problem lists the invalid field "venue.format"
    And the event has no upload session

    Examples:
      | venue                |
      | {"format": "HOCKEY"} |
      | {}                   |

  # AC-6: the body is checked before the event, so an unknown event still gets the 400
  Scenario Outline: An invalid body is rejected and names the field: <rule>
    Given the event is unknown
    When a camera registers with this body
      """
      <body>
      """
    Then the response status is 400
    And the response is a problem with the code "VALIDATION_FAILED"
    And the problem lists the invalid field "<field>"

    Examples:
      | rule                       | field        | body                                                                                                             |
      | camera not in the set      | camera       | {"camera": "FRONT", "partCount": 40, "contentType": "video/mp4"}                                                 |
      | partCount below 1          | partCount    | {"camera": "LEFT", "partCount": 0, "contentType": "video/mp4"}                                                   |
      | partCount above 1000       | partCount    | {"camera": "LEFT", "partCount": 1001, "contentType": "video/mp4"}                                                |
      | contentType not video      | contentType  | {"camera": "LEFT", "partCount": 40, "contentType": "application/octet-stream"}                                   |
      | venue length without width | venue.widthM | {"camera": "LEFT", "partCount": 40, "contentType": "video/mp4", "venue": {"format": "FUTSAL_5V5", "lengthM": 40}} |

  # AC-8, AC-9
  Scenario: The response and the log lines carry the caller's correlation id
    Given the requests carry the correlation id "gui-7f3c:retry.1"
    When the LEFT camera registers with 40 parts on a FOOTBALL_11V11 venue without measures
    Then the response status is 201
    And the response carries the correlation id "gui-7f3c:retry.1"
    And every log line of the request carries that correlation id and the camera LEFT
    And the log line of the registration carries the session id

  # AC-8, AC-9
  Scenario: A correlation id is generated when the caller sends none
    When the LEFT camera registers with 40 parts on a FOOTBALL_11V11 venue without measures
    Then the response status is 201
    And the response carries a generated correlation id starting with "CTR-"
    And every log line of the request carries that correlation id and the camera LEFT
    And the log line of the registration carries the session id
