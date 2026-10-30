Feature: Events
  An event is created over HTTP, stored, and announced on ctr.event.lifecycle.v1.

  Scenario: Creating an event stores it and announces it
    When an event " Benfica vs Porto " of sport "football" is created
    Then the response status is 201
    And the response points to the created event
    And the created event is returned with name "Benfica vs Porto" and sport "FOOTBALL"
    And the created event can be fetched
    And an EventCreated message is published for the created event with name "Benfica vs Porto" and sport "FOOTBALL"

  Scenario: The correlation id travels from the request to the message
    When an event "Sporting vs Braga" of sport "futsal" is created with correlation id "component-test-1"
    Then the response header "X-Correlation-Id" is "component-test-1"
    And the message published for the created event has header "correlationId" with value "component-test-1"

  Scenario Outline: An invalid event is rejected
    When an event "<name>" of sport "<sport>" is created
    Then the response status is 400
    And the problem code is "VALIDATION_FAILED"
    And the problem reports "<field>" as invalid

    Examples:
      | name             | sport    | field |
      |                  | football | name  |
      | Benfica vs Porto |          | sport |

  Scenario: An unknown event is not found
    When the event "00000000-0000-0000-0000-000000000000" is requested
    Then the response status is 404
    And the problem code is "EVENT_NOT_FOUND"
