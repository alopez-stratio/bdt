Feature: Feature used in testing multiloop tag aspect

  Scenario: wipe test file 1.
    Given I run 'rm -f testMultiloopOutput1.txt' locally

  @multiloop(AGENT_LIST=>AGENT1_NAME)
  Scenario: write <AGENT1_NAME> a file 1 with the final result of the scenario.
    Given I run 'echo "<AGENT1_NAME>" >> testMultiloopOutput1.txt' locally

  Scenario: verify file 1 content size.
    Given I run 'wc -l testMultiloopOutput1.txt' locally
    Then the command output contains '2'

  Scenario: verify file 1 content.
    Given I run 'cat testMultiloopOutput1.txt | sed -E ':a;N;$!ba;s/\r{0,1}\n/\\n/g'' locally
    Then the command output contains '1\n2'

  Scenario: wipe test file 2.
    Given I run 'rm -f testMultiloopOutput2.txt' locally

  @multiloop(AGENT_LIST=>AGENT1_NAME,AGENT_LIST=>AGENT2_NAME)
  Scenario: write <AGENT1_NAME>,<AGENT2_NAME> a file 2 with the final result of the scenario.
    Given I run 'echo "<AGENT1_NAME>,<AGENT2_NAME>" >> testMultiloopOutput2.txt' locally

  Scenario: verify file 2 content size.
    Given I run 'wc -l testMultiloopOutput2.txt' locally
    Then the command output contains '4'

  Scenario: verify file 2 content.
    Given I run 'cat testMultiloopOutput2.txt | sed -E ':a;N;$!ba;s/\r{0,1}\n/\\n/g'' locally
    Then the command output contains '1,1\n1,2\n2,1\n2,2'

  Scenario: wipe test file 3.
      Given I run 'rm -f testMultiloopOutput3.txt' locally

  @multiloop(AGENT_LIST=>AGENT1_NAME,AGENT_LIST=>AGENT2_NAME,AGENT_LIST=>AGENT3_NAME)
  Scenario: write <AGENT1_NAME>,<AGENT2_NAME>,<AGENT3_NAME> a file 3 with the final result of the scenario.
    Given I run 'echo "<AGENT1_NAME>,<AGENT2_NAME>,<AGENT3_NAME>" >> testMultiloopOutput3.txt' locally

  Scenario: verify file 3 content size.
    Given I run 'wc -l testMultiloopOutput3.txt' locally
    Then the command output contains '8'

  Scenario: verify file 3 content.
    Given I run 'cat testMultiloopOutput3.txt | sed -E ':a;N;$!ba;s/\r{0,1}\n/\\n/g'' locally
    Then the command output contains '1,1,1\n1,1,2\n1,2,1\n1,2,2\n2,1,1\n2,1,2\n2,2,1\n2,2,2'

#  @multiloop(AGENT1_LIST=>AGENT1_NAME,AGENT2_LIST=>AGENT2_NAME)
#  Scenario: This is an omitted scenario so it contains a failing assert
#    Given I run '[ "SHOULDNT_RUN" = "FAIL OTHERWISE" ]' locally

  @skipOnEnv(AGENT_LIST)
  Scenario: This scenario should be omitted.
    Given I run '[ "!{VAR_NOT_DEFINED}" = "{"a":{}}" ]' locally

  @runOnEnv(AGENT_LIST)
  Scenario: This scenario should be executed.
    Given I run '[ "SHOULD_RUN" = "SHOULD_RUN" ]' locally

  @runOnEnv(AGENT_LIST)
  @multiloop(AGENT_LIST=>AGENT1_NAME,AGENT_LIST=>AGENT2_NAME)
  Scenario: With scenarios outlines and datatables
    Given I create file 'testSOATtag<AGENT2_NAME>B.json' based on 'schemas/simple<AGENT1_NAME>.json' as 'json' with:
      | $.a | REPLACE | @{JSON.schemas/empty.json}     | object   |
    Given I save '@{JSON.testSOATtag<AGENT2_NAME>B.json}' in variable 'VAR'
    Then I run '[ "!{VAR}" = "{"a":{}}" ]' locally

  @runOnEnv(NO_VAR)
  @multiloop(NO_VAR=>VAR_NAME)
  Scenario: With scenarios outlines and datatables
    Given I create file 'testSOATtag<VAR_NAME.id>B.json' based on 'schemas/simple<VAR_NAME>.json' as 'json' with:
      | $.a | REPLACE | @{JSON.schemas/empty.json}     | object   |
    Given I save '@{JSON.testSOATtag<VAR_NAME.id>B.json}' in variable 'VAR'
    Then I run '[ "!{VAR}" = "{"a":{}}" ]' locally

  @web
  @multiloop(AGENT_LIST=>VAR1_NAME,AGENT_LIST=>VAR2_NAME)
  Scenario: Locate web element with xpath
    Given My app is running in 'www.google.com:80'
    When I browse to '/'
    And I wait '5' seconds
    When '1' elements exists with 'xpath://div[@id="SIvCob"]/a[<VAR1_NAME>]'
    When '1' elements exists with 'xpath://div[@id="SIvCob"]/a[<VAR2_NAME>]'

  Scenario: Save local variable
    Given I save '0,1' in variable 'LOCAL_VAR_LIST'
    Given I save 'a,b,c' in variable 'LOCAL_VAR2_LIST'

  Scenario: wipe testMultiloopOutputLocal
    Given I run 'rm -f testMultiloopOutputLocal.txt' locally

  @multiloop(LOCAL_VAR_LIST=>AGENT1_NAME,LOCAL_VAR2_LIST=>AGENT2_NAME)
  Scenario: write <AGENT1_NAME>,<AGENT2_NAME> a file 2 with the final result of the scenario.
    Given I run 'echo "<AGENT1_NAME>,<AGENT2_NAME>" >> testMultiloopOutputLocal.txt' locally

  Scenario: verify file testMultiloopOutputLocal content size.
    Given I run 'wc -l testMultiloopOutputLocal.txt' locally
    Then the command output contains '6'

  Scenario: verify file testMultiloopOutputLocal content
    Given I run 'cat testMultiloopOutputLocal.txt | sed -E ':a;N;$!ba;s/\r{0,1}\n/\\n/g'' locally
    Then the command output contains '0,a\n0,b\n0,c\n1,a\n1,b\n1,c'

  Scenario: Clean
    Given I run 'rm -f testMultiloopOutput*' locally