Feature: Events
  An event created over HTTP is stored, published on ctr.event.created.v1,
  and consumed back by the service, which logs the payload.

  Scenario: Creating an event
    When an event "Benfica vs Porto" is created
    Then the response status is 201
    And the event is stored in the database
    And an EventCreated message is published
    And the service consumes the message and logs its payload

  Scenario: An event needs a name
    When an event "" is created
    Then the response status is 400
