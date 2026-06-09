@rest
Feature: @include regression QATM-55235

  Scenario: [001] Pre-include plain scenario
    Given I save 'setup_done' in variable 'setupDoneVar'

  Scenario Outline: [002] Outline immediately after plain scenario
    Given I save '<testParam>' in variable 'testParamResolved'
    Then '!{testParamResolved}' is '<expected>'
    Examples:
      | testParam        | expected         |
      | value_alpha      | value_alpha      |
      | value_beta       | value_beta       |

  @include(feature:includeOutlineTest.feature,scenario:[001] Pre-include plain scenario)
  Scenario: [003] Include of [001]

  Scenario Outline: [004] Sketch after include scenario
    Given I save '<otherParam>' in variable 'otherParamResolved'
    Then '!{otherParamResolved}' is '<otherExpected>'
    Examples:
      | otherParam  | otherExpected |
      | check_one   | check_one     |

  Scenario: [005] Setup for outline context verification
    Given I save 'outline_done' in variable 'outlineContextVar'
