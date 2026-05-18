Feature: Preserve Order Multi 1 (annotated second, alphabetically first)

  Scenario: Multi1 checks variable set by previous feature
    Then '!{multiOrderVar}' is 'from_multi2'

  Scenario: Multi1 second scenario
    Then I run 'echo 'preserveOrderMulti1 - scenario 2'' locally
