Feature: Preserve Order Multi 2 (annotated first, alphabetically second)

  Scenario: Multi2 first scenario
    Then I run 'echo 'preserveOrderMulti2 - scenario 1'' locally

  Scenario: Multi2 sets variable for next feature
    Then I run 'echo 'preserveOrderMulti2 - scenario 2'' locally
    And I save 'from_multi2' in variable 'multiOrderVar'
