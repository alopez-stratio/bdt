@rest
Feature: Check /etc/hosts has been restored and lock file removed by general cleanup

  Scenario: Check remote machine is back to initial state
    # Check /etc/hosts file is restored
    Given I open a ssh connection to '${SSH}' with user 'root' and password 'stratio'
    When I run 'cat /etc/hosts' in the ssh connection
    Then the command output does not contain '3.3.3.3   bdt3.stratio.com'
    # Check backup and lock file have been removed
    When I run 'ls -al /etc | grep hosts' in the ssh connection
    Then the command output does not contain 'hosts.bdt'
    And the command output does not contain 'hosts.lock.'

  Scenario: Set /etc/hosts variable
    Given I save '/etc/hosts' in variable 'etchosts'
    Given I save '/etc' in variable 'hostsfolderpath'
    Given I run 'if ldconfig -p | grep libnss_homehosts; then echo yes; else echo no; fi | tail -1' locally and save the value in environment variable 'homeHostsInstalled'

  @runOnEnv(homeHostsInstalled=yes)
  Scenario: Set /etc/hosts variable
    Given I save '~/.hosts' in variable 'etchosts'
    Given I save '~' in variable 'hostsfolderpath'
    Given I run 'if [ ! -f ~/.hosts ]; then echo "127.0.0.1  localhost" >> ~/.hosts; fi' locally

  Scenario: Check local system is back to initial state
    # Check /etc/hosts file is restored
    When I run 'cat !{etchosts}' locally
    Then the command output does not contain '3.3.3.3   bdt3.stratio.com'
    # Check backup and lock file have been removed
    When I run 'ls -al !{hostsfolderpath} | grep hosts' locally
    Then the command output does not contain 'hosts.bdt'
    And the command output does not contain 'hosts.lock.'