Feature: Schema
  Liquibase applies db/changelog/db.changelog-master.yaml when the service starts against an empty Postgres, and
  running the changelog again applies nothing. The upload tables keep the state of the upload flow, and an event has
  at most one open upload session at a time.

  Scenario: The upload schema is applied on a clean database
    Then these changesets are applied, in order
      | ctr:0001-create-event         |
      | ctr:0002-create-upload-tables |
    And these tables exist
      | upload_session |
      | upload_camera  |
      | upload_clip    |
      | upload_part    |
    And these tables have these primary keys
      | upload_session | id                             |
      | upload_camera  | session_id, camera             |
      | upload_clip    | session_id, camera, part_index |
      | upload_part    | session_id, part_index         |
    And these tables have these foreign keys
      | upload_session | event_id   | event          |
      | upload_camera  | session_id | upload_session |
      | upload_clip    | session_id | upload_session |
      | upload_part    | session_id | upload_session |
    And the index "upload_session_one_open_per_event" is
      """
      CREATE UNIQUE INDEX upload_session_one_open_per_event ON public.upload_session USING btree (event_id) WHERE (status = 'OPEN'::text)
      """

  Scenario: Running the changelog again applies nothing
    When the changelog runs again
    Then no changeset is applied again

  Scenario: An event has at most one open upload session
    Given an event with an open upload session
    When another open upload session is created for the event
    Then it is rejected by the index "upload_session_one_open_per_event"
    When the open upload session is completed
    And another open upload session is created for the event
    Then it is stored next to the completed one
