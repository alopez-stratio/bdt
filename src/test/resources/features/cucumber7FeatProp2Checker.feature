Feature: Cucumber7 features property - checker

  # Normal run (no CUCUMBER7_FEAT_PROP_TEST): setter feature also ran, var is set
  @skipOnEnv(CUCUMBER7_FEAT_PROP_TEST)
  Scenario: Normal run - setter ran, var is set
    Then '!{cucumber7FeatPropVar}' is 'setter_ran'

  # Filter run (-Dcucumber.features=checker.feature -DCUCUMBER7_FEAT_PROP_TEST=true):
  # after fix, setter was excluded -> var NOT set
  @runOnEnv(CUCUMBER7_FEAT_PROP_TEST)
  Scenario: Filter test - only checker ran, var must NOT be set
    Then the variable 'cucumber7FeatPropVar' is not defined
