Feature: Cameras registering at the same time
  The cameras of a match register on their own, so two of them can reach ctr-service at once. Whatever the timing,
  an event gets one upload session, each camera is in it once, and the parts are created once, by the camera that
  opened it. A race shows up only now and then, so each scenario runs on many events.

  # AC-1, AC-2, AC-3: both first, one opens the session (upload_session_one_open_per_event) and the other joins it
  Scenario: LEFT and RIGHT register at the same time
    Given 20 events exist
    When on each event the LEFT and RIGHT cameras register with 40 parts at the same time
    Then every registration is answered 201, with the same session for both cameras of an event
    And every event has one upload session, with the cameras "LEFT, RIGHT"
    And every session has 40 waiting parts, created once, and 40 pending clips for each camera
    And every session is at version 1

  # AC-1, AC-2, AC-3: the second arrives while the first is still being written, or just after
  Scenario: LEFT registers first and RIGHT right after
    Given 20 events exist
    When on each event the LEFT camera registers with 40 parts and the RIGHT camera up to 40 ms later, without waiting for the answer
    Then every registration is answered 201, with the same session for both cameras of an event
    And every event has one upload session, with the cameras "LEFT, RIGHT"
    And every session has 40 waiting parts, created once, and 40 pending clips for each camera
    And every session is at version 1

  # AC-2, AC-5: both read the session with LEFT only; the version lets one join, the other reads again and is a 409
  Scenario: Two RIGHT cameras join at the same time
    Given 20 events exist
    When on each event the LEFT camera registers with 40 parts, then two RIGHT cameras at the same time
    Then on each event one RIGHT camera is answered 201 and the other 409 "CAMERA_SET_CONFLICT"
    And every event has one upload session, with the cameras "LEFT, RIGHT"
    And every session has 40 waiting parts, created once, and 40 pending clips for each camera
    And every session is at version 1
