@rest
Feature: SSE client step

  Scenario: SSE GET request reads all events
    Given I send requests to 'localhost:${SSE_MOCK_PORT}'
    When I send a SSE 'GET' request to '/stream'
    Then the service response status must be '200'
    And I save service response header 'content-type' value in environment variable 'content_type'
    Then '!{content_type}' contains 'text/event-stream'
    Given I parse the SSE response events and save them in environment variable 'sse_events'
    Then 'sse_events' matches the following cases:
      | $.events[0].event_value.token | equal | Hello |
      | $.events[0].event_type        | equal | token |
      | $.events[1].event_value.end   | equal | true  |
      | $.events[1].event_type        | equal | end   |
      | $.events                      | size  | 2     |

  Scenario: SSE POST request reads all events with JSON body
    Given I send requests to 'localhost:${SSE_MOCK_PORT}'
    When I send a SSE 'POST' request to '/stream' based on 'sse/invoke.json' as 'json' with:
      | $.input | REPLACE | Hello! | string |
    Then the service response status must be '200'
    Given I parse the SSE response events and save them in environment variable 'sse_events'
    Then 'sse_events' matches the following cases:
      | $.events[0].event_value.token | equal | Hello |
      | $.events[0].event_type        | equal | token |
      | $.events[1].event_value.end   | equal | true  |
      | $.events[1].event_type        | equal | end   |
      | $.events                      | size  | 2     |
