Feature: New Cucumber 7 APIs verification - second feature

  Scenario: BeforeAll hook fires again for this second feature
    Then '!{BEFORE_ALL_RAN}' is 'yes'
