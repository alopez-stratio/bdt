Feature: Zookeeper steps test

  Scenario: Connection to Zk
    Given I connect to Zookeeper at '${ZOOKEEPER_HOSTS}'

  Scenario: zookeeper node does exist
    Then the zNode '/zookeeper' exists

  Scenario: create node and check existance
    Then I create the zNode '/testephemeral' which IS ephemeral
    Then the zNode '/testephemeral' exists
    Then I remove the zNode '/testephemeral'

  Scenario: create node with content, check existance and remove it
    Then I create the zNode '/testephemeraldata' with content 'mydata' which IS ephemeral
    Then the zNode '/testephemeraldata' exists and contains 'mydata'
    Then I remove the zNode '/testephemeraldata'

  Scenario: check zookeeper nodes no longer exist
    Then the zNode '/testephemeral' does not exist
    Then the zNode '/testephemeraldata' does not exist

  Scenario: create zookeeper non ephemeral node and check existance
    Then I create the zNode '/testnonephemeral' which IS NOT ephemeral
    Then the zNode '/testnonephemeral' exists

  Scenario: create zookeeper node and check existance
    Then I create the zNode '/testnonephemeraldata' with content 'mydata' which IS ephemeral
    Then the zNode '/testnonephemeraldata' exists and contains 'mydata'

  Scenario: clean up non-ephemeral nodes
    Then I remove the zNode '/testnonephemeral'
    Then the zNode '/testnonephemeral' does not exist
    Then I disconnect from Zookeeper
