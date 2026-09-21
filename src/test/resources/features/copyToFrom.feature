Feature: Copy To/From tests
  Scenario: Copy to file to file same name
    Given I open a ssh connection to '${SSH}' with user 'root' and password 'stratio'
    And I run 'mkdir /tmp/copytofrom' in the ssh connection with exit status '0'
    When I outbound copy 'src/test/resources/schemas/copytofrom/prueba1.sh' through a ssh connection to '/tmp/copytofrom/prueba1.sh'
    Then I run 'cat /tmp/copytofrom/prueba1.sh' in the ssh connection with exit status '0'
    And the command output contains 'content1'

  Scenario: Copy to file to file renaming
    Given I open a ssh connection to '${SSH}' with user 'root' and password 'stratio'
    When I outbound copy 'src/test/resources/schemas/copytofrom/prueba1.sh' through a ssh connection to '/tmp/copytofrom/test1.sh'
    Then I run 'cat /tmp/copytofrom/test1.sh' in the ssh connection with exit status '0'
    And the command output contains 'content1'

  Scenario: Copy file to a bare directory keeping its name
    Given I open a ssh connection to '${SSH}' with user 'root' and password 'stratio'
    And I run 'rm -Rf /tmp/copytofrom' locally with exit status '0'
    When I outbound copy 'src/test/resources/schemas/copytofrom/prueba1.sh' through a ssh connection to '/tmp/copytofrom'
    Then I run 'cat /tmp/copytofrom/prueba1.sh' in the ssh connection with exit status '0'
    And the command output contains 'content1'

  Scenario: Copy file to a bare directory that exists locally too
    Given I open a ssh connection to '${SSH}' with user 'root' and password 'stratio'
    When I outbound copy 'src/test/resources/schemas/copytofrom/prueba1.sh' through a ssh connection to '/tmp'
    Then I run 'cat /tmp/prueba1.sh' in the ssh connection with exit status '0'
    And the command output contains 'content1'
    And I run 'rm /tmp/prueba1.sh' in the ssh connection with exit status '0'

  Scenario: Copy to dir to dir
    Given I open a ssh connection to '${SSH}' with user 'root' and password 'stratio'
    When I outbound copy 'src/test/resources/schemas/copytofrom' through a ssh connection to '/tmp/copytofrom'
    Then I run 'cat /tmp/copytofrom/prueba1.sh' in the ssh connection with exit status '0'
    And the command output contains 'content1'
    And I run 'cat /tmp/copytofrom/prueba2.sh' in the ssh connection with exit status '0'
    And the command output contains 'content2'

  Scenario: Copy from file to file same name, not specifying same name
    Given I open a ssh connection to '${SSH}' with user 'root' and password 'stratio'
    And I run 'mkdir /tmp/copytofrom' locally with exit status '0'
    When I inbound copy '/tmp/copytofrom/prueba1.sh' through a ssh connection to '/tmp/copytofrom'
    Then I run 'cat /tmp/copytofrom/prueba1.sh' locally with exit status '0'
    And the command output contains 'content1'
    And I run 'rm /tmp/copytofrom/prueba1.sh' locally with exit status '0'

  Scenario: Copy from file to file same name, specifying same name
    Given I open a ssh connection to '${SSH}' with user 'root' and password 'stratio'
    When I inbound copy '/tmp/copytofrom/prueba2.sh' through a ssh connection to '/tmp/copytofrom/prueba2.sh'
    Then I run 'cat /tmp/copytofrom/prueba2.sh' locally with exit status '0'
    And the command output contains 'content2'
    And I run 'rm /tmp/copytofrom/prueba2.sh' locally with exit status '0'

  Scenario: Copy from file to file renaming
    Given I open a ssh connection to '${SSH}' with user 'root' and password 'stratio'
    When I inbound copy '/tmp/copytofrom/prueba1.sh' through a ssh connection to '/tmp/copytofrom/test1.sh'
    Then I run 'cat /tmp/copytofrom/test1.sh' locally with exit status '0'
    And the command output contains 'content1'
    And I run 'rm /tmp/copytofrom/test1.sh' locally with exit status '0'

  Scenario: Clean up
    Given I open a ssh connection to '${SSH}' with user 'root' and password 'stratio'
    Then I run 'rm -Rf /tmp/copytofrom' locally with exit status '0'
    And I run 'rm -Rf /tmp/copytofrom' in the ssh connection with exit status '0'