@publishing
Feature: Hand a completed part over to extraction
  When a confirmation completes a part, ctr-service publishes it for extraction once the database has the part:
  ClipPairUploaded on clip-pair-uploaded.v1 for a LEFT and RIGHT session, ClipReady on clip-ready.v1 for a SINGLE one,
  keyed <session_id>:<part_index>, with the platform's standard headers. A publish that fails never fails the
  confirmation: the part stays unpublished, and the same confirmation sent again publishes it. (TH-152)

  # AC-1, AC-9: pair flow
  Scenario: The second clip of a LEFT and RIGHT part publishes ClipPairUploaded with the overlap
    Given a session with the cameras "LEFT, RIGHT", 40 parts and a FOOTBALL_7V7 venue of 60 by 40 m
    And the LEFT camera has uploaded clip 3
    And the RIGHT camera has uploaded clip 3
    And the LEFT camera confirmed clip 3 starting at 900000 ms for 300000 ms
    When the RIGHT camera confirms clip 3 starting at 898000 ms for 300000 ms
    Then the confirmation is answered with 200
    And the answer says part 3 is published, 1 of 40 parts published
    And part 3 is published in the database
    And one ClipPairUploaded for part 3 of 40 is on clip-pair-uploaded.v1, starting at 900000 ms for 298000 ms
    And its LEFT and RIGHT clips are the uploaded ones, of type video/mp4
    And its venue is football FOOTBALL_7V7 of 60 by 40 m
    And nothing about the session is on clip-ready.v1

  # AC-2, AC-9: single flow
  Scenario: The clip of a SINGLE part publishes ClipReady with the clip's own window
    Given a session with the cameras "SINGLE", 2 parts and a FUTSAL_5V5 venue of 38 by 19 m
    And the SINGLE camera has uploaded clip 0
    When the SINGLE camera confirms clip 0 starting at 0 ms for 280000 ms
    Then the confirmation is answered with 200
    And the answer says part 0 is published, 1 of 2 parts published
    And part 0 is published in the database
    And one ClipReady for part 0 of 2 is on clip-ready.v1, starting at 0 ms for 280000 ms
    And its SINGLE clip is the uploaded one, of type video/mp4
    And its venue is futsal FUTSAL_5V5 of 38 by 19 m
    And nothing about the session is on clip-pair-uploaded.v1

  # AC-3, AC-9: headers and key
  Scenario: The message is keyed by session and part and carries the standard headers
    Given a session with the cameras "SINGLE" and 1 part
    And the SINGLE camera has uploaded clip 0
    When the SINGLE camera confirms clip 0 starting at 0 ms for 280000 ms
    Then one ClipReady for part 0 of 1 is on clip-ready.v1, starting at 0 ms for 280000 ms
    And its key is the session id, a colon and the part index 0
    And its headers are a UUIDv7 message_id, the message_type cavalodetroia.clip.v1.ClipReady, an occurred_at in UTC, the producer ctr-service and the correlation id of the confirmation

  # AC-4, AC-9: repeat without republish
  Scenario: The same confirmation again publishes nothing more
    Given a session with the cameras "LEFT, RIGHT" and 40 parts
    And the LEFT camera has uploaded clip 3
    And the RIGHT camera has uploaded clip 3
    And the LEFT camera confirmed clip 3 starting at 900000 ms for 300000 ms
    And the RIGHT camera confirmed clip 3 starting at 898000 ms for 300000 ms
    And one ClipPairUploaded for part 3 of 40 is on clip-pair-uploaded.v1, starting at 900000 ms for 298000 ms
    When the RIGHT camera confirms clip 3 starting at 898000 ms for 300000 ms
    Then the confirmation is answered with 200
    And the answer is the same as the first confirmation's
    And no other message about part 3 is published within 3 seconds

  # AC-4, AC-6, AC-9, NFR-2: publish retry after a broker outage
  Scenario: A broker that does not answer leaves the part unpublished, and the same confirmation publishes it later
    Given a session with the cameras "LEFT, RIGHT" and 40 parts
    And the LEFT camera has uploaded clip 3
    And the RIGHT camera has uploaded clip 3
    And the LEFT camera confirmed clip 3 starting at 900000 ms for 300000 ms
    And Kafka stops answering
    When the RIGHT camera confirms clip 3 starting at 898000 ms for 300000 ms
    Then the confirmation is answered with 200
    And the answer came within 6 seconds and says part 3 is COMPLETE but not published
    And part 3 is COMPLETE in the database but not published
    And an error says part 3 could not be published, carrying the session id and the part index
    When Kafka answers again
    And the RIGHT camera confirms clip 3 starting at 898000 ms for 300000 ms
    Then the confirmation is answered with 200
    And the answer says part 3 is published, 1 of 40 parts published
    And part 3 is published in the database
    And the repeated confirmation published one ClipPairUploaded for part 3 on clip-pair-uploaded.v1

  # AC-5, AC-9: NO_OVERLAP
  Scenario: A part whose clips do not overlap publishes nothing
    Given a session with the cameras "LEFT, RIGHT" and 40 parts
    And the LEFT camera has uploaded clip 5
    And the RIGHT camera has uploaded clip 5
    And the LEFT camera confirmed clip 5 starting at 0 ms for 100000 ms
    When the RIGHT camera confirms clip 5 starting at 400000 ms for 100000 ms
    Then the confirmation is answered with 200
    And the answer says the part is NO_OVERLAP and the session is OPEN
    And part 5 is NO_OVERLAP in the database, starting at 400000 ms for -300000 ms
    And no message about part 5 is published within 3 seconds

  # AC-7, AC-9: metric recorded
  Scenario: Every published message is measured in bytes under its topic, over the actuator and JMX
    Given the messages measured on clip-ready.v1 so far
    And a session with the cameras "SINGLE" and 1 part
    And the SINGLE camera has uploaded clip 0
    When the SINGLE camera confirms clip 0 starting at 0 ms for 280000 ms
    Then one ClipReady for part 0 of 1 is on clip-ready.v1, starting at 0 ms for 280000 ms
    And the actuator measured one more message on clip-ready.v1, of the key, value and header bytes it carries
    And JMX measured one more message on clip-ready.v1

  # AC-7, AC-8, AC-9: topics created locally
  Scenario: Only the local profile creates the topics, with their retry and dead-letter topics, and serves Jolokia
    Given the running app declares no topic and serves no Jolokia
    And there is no .retry or .dlq topic for clip-pair-uploaded.v1 or clip-ready.v1
    When another instance of the app starts with the local profile added
    Then the topics clip-pair-uploaded.v1, clip-ready.v1 and their .retry and .dlq exist
    And that instance serves Jolokia for Hawtio
