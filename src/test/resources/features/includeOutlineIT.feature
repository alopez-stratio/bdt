@rest
Feature: @include QATM-55235 end-to-end IT

  Scenario: [001] Base setup
    Given I save 'setup_done' in variable 'baseSetupVar'

  Scenario Outline: [002] Parametric check after base setup
    Given I save '<value>' in variable 'resolvedValue'
    Then '!{resolvedValue}' is '<expected>'
    Examples:
      | value       | expected    |
      | alpha_value | alpha_value |
      | beta_value  | beta_value  |

  @include(feature:includeOutlineIT.feature,scenario:[001] Base setup)
  Scenario: [003] Include of base setup

  Scenario Outline: [004] Follow-up parametric check
    Given I save '<item>' in variable 'resolvedItem'
    Then '!{resolvedItem}' is '<itemExpected>'
    Examples:
      | item      | itemExpected |
      | check_one | check_one    |
