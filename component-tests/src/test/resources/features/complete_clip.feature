Feature: Confirm an uploaded clip
  POST /api/v1/upload-sessions/{sessionId}/cameras/{camera}/parts/{partIndex}/complete. A camera uploads a clip
  straight to the bucket, then confirms it with what S3 answered (size, etag) and where the clip sits in the
  recording. ctr-service checks the object with a HEAD, without downloading it, and only then records the clip. A
  part is complete when every camera has uploaded its clip of it, with the overlap of the clips as its window; the
  session is completed when every clip of every camera is uploaded. (TH-142)

  # AC-1, AC-9: object present
  Scenario: The left camera confirms a clip that is in the bucket
    Given a session with the cameras "LEFT, RIGHT" and 40 parts
    And the LEFT camera has uploaded clip 3
    When the LEFT camera confirms clip 3 starting at 900000 ms for 300000 ms
    Then the confirmation is answered with 200
    And the answer is clip LEFT 3 UPLOADED, its part WAITING_RIGHT and not published, the session OPEN with 0 of 40 parts published
    And clip LEFT 3 is UPLOADED in the database with the uploaded size and etag, start 900000 ms, duration 300000 ms and the upload time
    And part 3 is WAITING in the database
    And every log line of the confirmation carries the session id, the camera LEFT and the part index 3

  # AC-2, AC-9: LEFT then RIGHT, with the overlap computed
  Scenario: The right camera confirms the same part and it completes with the overlap of the two clips
    Given a session with the cameras "LEFT, RIGHT" and 40 parts
    And the LEFT camera has uploaded clip 3
    And the RIGHT camera has uploaded clip 3
    And the LEFT camera confirmed clip 3 starting at 900000 ms for 300000 ms
    When the RIGHT camera confirms clip 3 starting at 898000 ms for 300000 ms
    Then the confirmation is answered with 200
    And the answer says the part is COMPLETE and the session is OPEN
    And part 3 is COMPLETE in the database, starting at 900000 ms for 298000 ms
    And clips 3 of "LEFT, RIGHT" are UPLOADED in the database

  # AC-2, AC-8, AC-9: SINGLE flow
  Scenario: A single camera with one part completes the part and the session in one confirmation
    Given a session with the cameras "SINGLE" and 1 part
    And the SINGLE camera has uploaded clip 0
    When the SINGLE camera confirms clip 0 starting at 0 ms for 280000 ms
    Then the confirmation is answered with 200
    And the answer says the part is COMPLETE and the session is COMPLETED
    And part 0 is COMPLETE in the database, starting at 0 ms for 280000 ms
    And the session is COMPLETED in the database

  # AC-3, AC-9: non-overlapping clips
  Scenario: Two clips that do not overlap mark the part NO_OVERLAP
    Given a session with the cameras "LEFT, RIGHT" and 40 parts
    And the LEFT camera has uploaded clip 5
    And the RIGHT camera has uploaded clip 5
    And the LEFT camera confirmed clip 5 starting at 0 ms for 100000 ms
    When the RIGHT camera confirms clip 5 starting at 400000 ms for 100000 ms
    Then the confirmation is answered with 200
    And the answer says the part is NO_OVERLAP and the session is OPEN
    And part 5 is NO_OVERLAP in the database, starting at 400000 ms for -300000 ms
    And a warning says part 5 has no overlap, carrying the session id and the part index

  # AC-8, AC-9: the last part completes the session
  Scenario: The last clip of the last camera completes the session
    Given a session with the cameras "LEFT, RIGHT" and 2 parts
    And every camera has uploaded every clip
    And the LEFT camera confirmed clip 0 starting at 0 ms for 300000 ms
    And the LEFT camera confirmed clip 1 starting at 300000 ms for 300000 ms
    And the RIGHT camera confirmed clip 0 starting at 0 ms for 300000 ms
    When the RIGHT camera confirms clip 1 starting at 300000 ms for 300000 ms
    Then the confirmation is answered with 200
    And the answer says the part is COMPLETE and the session is COMPLETED
    And the session is COMPLETED in the database

  # AC-4, AC-9: object absent
  Scenario: A clip that is not in the bucket is not recorded
    Given a session with the cameras "LEFT, RIGHT" and 40 parts
    When the LEFT camera confirms clip 3 starting at 900000 ms for 300000 ms
    Then the confirmation is rejected with 422 and the code "OBJECT_NOT_FOUND"
    And nothing changed in the database

  # AC-4, AC-9: wrong size
  Scenario: A clip with another size than the confirmed one is not recorded
    Given a session with the cameras "LEFT, RIGHT" and 40 parts
    And the LEFT camera has uploaded clip 3
    When the LEFT camera confirms clip 3 with one byte more than it uploaded
    Then the confirmation is rejected with 422 and the code "SIZE_MISMATCH"
    And nothing changed in the database

  # AC-4, AC-9: wrong etag
  Scenario: A clip replaced in the bucket after the upload is not recorded
    Given a session with the cameras "LEFT, RIGHT" and 40 parts
    And the LEFT camera has uploaded clip 3
    And clip LEFT 3 is replaced in the bucket by another one of the same size
    When the LEFT camera confirms clip 3 starting at 900000 ms for 300000 ms
    Then the confirmation is rejected with 422 and the code "ETAG_MISMATCH"
    And nothing changed in the database

  # AC-7, AC-9: repeat with the same body. The clip is gone from the bucket, so a HEAD would answer 422: the 200
  # shows the repeat never asked the bucket (NFR-1)
  Scenario: The same confirmation again gets the same answer without asking the bucket or writing
    Given a session with the cameras "LEFT, RIGHT" and 40 parts
    And the LEFT camera has uploaded clip 3
    And the LEFT camera confirmed clip 3 starting at 900000 ms for 300000 ms
    And clip LEFT 3 is deleted from the bucket
    When the LEFT camera confirms clip 3 starting at 900000 ms for 300000 ms
    Then the confirmation is answered with 200
    And the answer is the same as the first confirmation's
    And nothing changed in the database

  # AC-6, AC-9: repeat with another body
  Scenario: Another confirmation for an uploaded clip is a conflict
    Given a session with the cameras "LEFT, RIGHT" and 40 parts
    And the LEFT camera has uploaded clip 3
    And the LEFT camera confirmed clip 3 starting at 900000 ms for 300000 ms
    When the LEFT camera confirms clip 3 starting at 900000 ms for 299000 ms
    Then the confirmation is rejected with 409 and the code "CLIP_ALREADY_COMPLETED"
    And nothing changed in the database

  # AC-5, NFR-2, AC-9: MinIO stopped. A paused container keeps the connection open and never answers, so only the
  # client's timeouts end the request
  Scenario: A storage that does not answer gives a quick 503 and records nothing
    Given a session with the cameras "LEFT, RIGHT" and 40 parts
    And the LEFT camera has uploaded clip 3
    And the storage stops answering
    When the LEFT camera confirms clip 3 starting at 900000 ms for 300000 ms
    Then the confirmation is rejected with 503 and the code "STORAGE_UNAVAILABLE"
    And the answer came within 6 seconds and says to retry after 5 seconds
    And nothing changed in the database

  # AC-6
  Scenario: An unknown session is not found
    Given the session is unknown
    When the LEFT camera confirms clip 3 starting at 900000 ms for 300000 ms
    Then the confirmation is rejected with 404 and the code "SESSION_NOT_FOUND"

  # AC-6: checked against the session before the bucket is asked
  Scenario Outline: <rule> is rejected and names the field
    Given a session with the cameras "<cameras>" and 40 parts
    When the <camera> camera confirms clip <partIndex> starting at 0 ms for 300000 ms
    Then the confirmation is rejected with 400 and the code "VALIDATION_FAILED"
    And the problem names the field "<field>"
    And nothing changed in the database

    Examples:
      | rule                               | cameras     | camera | partIndex | field     |
      | A camera that is not registered    | LEFT        | RIGHT  | 3         | camera    |
      | SINGLE on a LEFT and RIGHT session | LEFT, RIGHT | SINGLE | 3         | camera    |
      | A part index past the last part    | LEFT, RIGHT | LEFT   | 40        | partIndex |
      | A negative part index              | LEFT, RIGHT | LEFT   | -1        | partIndex |

  # AC-6
  Scenario Outline: An invalid body is rejected and names the field: <rule>
    Given a session with the cameras "LEFT, RIGHT" and 40 parts
    When the LEFT camera confirms clip 3 with this body
      """
      <body>
      """
    Then the confirmation is rejected with 400 and the code "VALIDATION_FAILED"
    And the problem names the field "<field>"
    And nothing changed in the database

    Examples:
      | rule                   | field      | body                                                          |
      | sizeBytes not positive | sizeBytes  | {"sizeBytes": 0, "etag": "e", "startMs": 0, "durationMs": 1}  |
      | etag empty             | etag       | {"sizeBytes": 1, "etag": "", "startMs": 0, "durationMs": 1}   |
      | startMs negative       | startMs    | {"sizeBytes": 1, "etag": "e", "startMs": -1, "durationMs": 1} |
      | durationMs missing     | durationMs | {"sizeBytes": 1, "etag": "e", "startMs": 0}                   |

  # AC-2: the session row lock serialises the two confirmations, so the part closes exactly once. A race shows up
  # only now and then, so it runs on many sessions
  Scenario: Both cameras confirming the same part at the same time close it once
    Given 10 sessions with the cameras "LEFT, RIGHT" and 1 part, each with both clips uploaded
    When on each session LEFT and RIGHT confirm clip 0 at the same time
    Then on each session one answer waits for the other camera and the other says COMPLETE
    And on each session both clips are UPLOADED and part 0 is COMPLETE in the database
