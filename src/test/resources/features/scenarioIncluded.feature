@web
Feature: This feature is used to be included as part of includetagAspect testing

  Scenario: Dummy_scenario
    Given My app is running in '${SELENIUM_TEST_SITE}'
    When I browse to '/'
    Then I take a snapshot
    Then I maximize the browser
