# Deliberately invalid Gherkin: the second row has three cells where the others have two.
# Kept outside src/test/resources/features so that no runner scanning that directory picks it up.
Feature: Feature that cannot be parsed

  Scenario: Table with an inconsistent row
    Given I save 'value' in variable 'VAR'
      | key       | value |
      | A<1 || B>2 | X     |
      | C         | Y     |
