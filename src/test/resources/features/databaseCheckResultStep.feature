Feature: DatabaseSpec - I check that result is

  # Verifies that comparetable() correctly flattens multi-column DataTables
  # after the Cucumber 7 migration (asList(String.class) broke for tables wider than 1 column).
  # queryresponse0..N are set manually to simulate a prior SQL query result.

  Scenario: Single-column table comparison succeeds
    Given I save 'header' in variable 'queryresponse0'
    And I save 'row1' in variable 'queryresponse1'
    And I save 'row2' in variable 'queryresponse2'
    Then I check that result is:
      | header |
      | row1   |
      | row2   |

  Scenario: Multi-column table comparison succeeds
    Given I save 'id' in variable 'queryresponse0'
    And I save 'validated' in variable 'queryresponse1'
    And I save 'version' in variable 'queryresponse2'
    And I save '1' in variable 'queryresponse3'
    And I save 'true' in variable 'queryresponse4'
    And I save '0.12.0-abc' in variable 'queryresponse5'
    And I save '2' in variable 'queryresponse6'
    And I save 'false' in variable 'queryresponse7'
    And I save '0.22.0-xyz' in variable 'queryresponse8'
    Then I check that result is:
      | id | validated | version    |
      | 1  | true      | 0.12.0-abc |
      | 2  | false     | 0.22.0-xyz |
