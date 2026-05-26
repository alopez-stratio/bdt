Feature: Cucumber7 filter.tags property test

  # Sets variable to prove this scenario ran
  @cucumber7tagA
  Scenario: Tag A - sets variable
    Then I run 'echo tagA_ran' locally
    And I save 'tagA_ran' in variable 'cucumber7TagFilterVar'

  # Normal run (no CUCUMBER7_TAG_FILTER_TEST): tagA ran, var is set
  @cucumber7tagB @skipOnEnv(CUCUMBER7_TAG_FILTER_TEST)
  Scenario: Tag B - normal run, tagA ran before this
    Then '!{cucumber7TagFilterVar}' is 'tagA_ran'

  # Filter run (-Dcucumber.filter.tags=@cucumber7tagB -DCUCUMBER7_TAG_FILTER_TEST=true):
  # after fix, tagA scenario was excluded -> var NOT set
  @cucumber7tagB @runOnEnv(CUCUMBER7_TAG_FILTER_TEST)
  Scenario: Tag B - filter test, tagA must NOT have run
    Then the variable 'cucumber7TagFilterVar' is not defined
