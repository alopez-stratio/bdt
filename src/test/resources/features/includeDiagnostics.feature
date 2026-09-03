Feature: Fixture for the @include resolution diagnostics

  # This feature is only read by IncludeTagAspectTest through featureStepConverter().
  # It deliberately contains the three shapes a reference can hit: a unique name, a name
  # declared twice, and a name that is only there inside a comment.

  Scenario: [001] Unique scenario
    Given I save 'unique_done' in variable 'uniqueVar'

  Scenario: [002] Duplicated scenario
    Given I save 'first_body' in variable 'duplicatedVar'

  Scenario: [003] Another unique scenario
    Given I save 'another_done' in variable 'anotherVar'

  Scenario: [002] Duplicated scenario
    Given I save 'second_body' in variable 'duplicatedVar'

#  Scenario: [004] Commented out scenario
#    Given I save 'commented_body' in variable 'commentedVar'

  Scenario: [005] Last scenario
    Given I save 'last_done' in variable 'lastVar'
