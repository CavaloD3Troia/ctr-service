Feature: Read an upload session
  GET /api/v1/upload-sessions/{sessionId}. The app shows where the upload stands: the session, its venue, each
  camera with how many clips it has uploaded, and every part with one clip per registered camera. The rows are
  written straight into Postgres, as the upload flow leaves them.

  # AC-1, AC-2, AC-3, AC-5, NFR-1
  Scenario: A fresh session with one camera
    Given an open upload session with the cameras "SINGLE" and 3 parts
    When the upload session is read
    Then the read answers 200
    And the read gives the session on its event, OPEN, with 3 parts and 0 published
    And the read gives the venue FOOTBALL_11V11 measuring 105 by 68 metres
    And the read lists the cameras
      | camera | partCount | partsUploaded |
      | SINGLE | 3         | 0             |
    And the read lists the parts
      | partIndex | status  | published | startMs | durationMs |
      | 0         | WAITING | false     |         |            |
      | 1         | WAITING | false     |         |            |
      | 2         | WAITING | false     |         |            |
    And every part has only a pending clip of the camera SINGLE
    And the read ran at most 4 SQL statements, all of them SELECTs

  # AC-1, AC-2, AC-3, AC-5, NFR-1: part 3's clips don't overlap, so its stored window is not given
  Scenario: Two cameras with uploaded, pending, complete and published parts
    Given an open upload session with the cameras "LEFT, RIGHT" and 4 parts
    And these clips are uploaded
      | camera | partIndex | sizeBytes  | startMs | durationMs | uploadedAt           |
      | LEFT   | 0         | 1073741824 | 0       | 300000     | 2026-11-04T17:52:11Z |
      | RIGHT  | 0         | 1052000000 | 2000    | 300000     | 2026-11-04T17:53:40Z |
      | LEFT   | 1         | 1073741824 | 300000  | 300000     | 2026-11-04T17:57:11Z |
      | RIGHT  | 1         | 1052000000 | 300000  | 300000     | 2026-11-04T17:58:40Z |
      | LEFT   | 2         | 1073741824 | 600000  | 300000     | 2026-11-04T18:02:11Z |
      | LEFT   | 3         | 1073741824 | 900000  | 300000     | 2026-11-04T18:07:02Z |
      | RIGHT  | 3         | 1052000000 | 1202000 | 300000     | 2026-11-04T18:08:30Z |
    And these parts are done
      | partIndex | status     | startMs | durationMs | publishedAt          |
      | 0         | COMPLETE   | 2000    | 298000     | 2026-11-04T18:00:00Z |
      | 1         | COMPLETE   | 300000  | 300000     |                      |
      | 3         | NO_OVERLAP | 1202000 | -2000      |                      |
    When the upload session is read
    Then the read answers 200
    And the read gives the session on its event, OPEN, with 4 parts and 1 published
    And the read lists the cameras
      | camera | partCount | partsUploaded |
      | LEFT   | 4         | 4             |
      | RIGHT  | 4         | 3             |
    And the read lists the parts
      | partIndex | status        | published | startMs | durationMs |
      | 0         | COMPLETE      | true      | 2000    | 298000     |
      | 1         | COMPLETE      | false     | 300000  | 300000     |
      | 2         | WAITING_RIGHT | false     |         |            |
      | 3         | NO_OVERLAP    | false     |         |            |
    And the read gives part 2 the clips
      | camera | status   | sizeBytes  | startMs | durationMs | uploadedAt           |
      | LEFT   | UPLOADED | 1073741824 | 600000  | 300000     | 2026-11-04T18:02:11Z |
      | RIGHT  | PENDING  |            |         |            |                      |
    And the read gives part 3 the clips
      | camera | status   | sizeBytes  | startMs | durationMs | uploadedAt           |
      | LEFT   | UPLOADED | 1073741824 | 900000  | 300000     | 2026-11-04T18:07:02Z |
      | RIGHT  | UPLOADED | 1052000000 | 1202000 | 300000     | 2026-11-04T18:08:30Z |
    And the read ran at most 4 SQL statements, all of them SELECTs

  # AC-4, AC-5
  Scenario: An unknown session is not found
    Given the upload session is unknown
    When the upload session is read
    Then the read answers 404
    And the read is a problem with the code "SESSION_NOT_FOUND"

  # NFR-1: the statements don't grow with the parts
  Scenario: A session of 1000 parts is read with the same few statements
    Given an open upload session with the cameras "LEFT, RIGHT" and 1000 parts
    And every clip of the LEFT camera is uploaded
    When the upload session is read
    Then the read answers 200
    And the read lists 1000 parts by index, each waiting for RIGHT with the clips of "LEFT, RIGHT"
    And the read ran at most 4 SQL statements, all of them SELECTs
    And the read took less than 2 seconds
