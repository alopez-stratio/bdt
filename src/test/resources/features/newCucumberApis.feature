Feature: New Cucumber 7 APIs verification

  Scenario: BeforeAll hook fires before first scenario
    Then '!{BEFORE_ALL_RAN}' is 'yes'

  Scenario: DataTableType cell converter receives !{VAR}-replaced value
    Given I save 'resolved_value' in variable 'DT_TEST_VAR'
    When I load first datatabletype cell into var 'DT_RESULT':
      | !{DT_TEST_VAR} |
    Then '!{DT_RESULT}' is 'resolved_value'
