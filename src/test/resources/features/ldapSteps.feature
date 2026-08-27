Feature: LDAP steps test

  # LDAP_USER_DN, LDAP_GROUP_DN and LDAP_ADMIN_GROUP are normally published by the DC/OS bootstrap
  # step ("I obtain 'LDAP_USER_DN' from descriptor and save it in environment variable"). Standing
  # alone against the test container nothing sets them, so every DN the steps build would start
  # with the literal "null".
  #
  # They point at dc=stratio,dc=com rather than at ou=People / ou=Groups on purpose. The demo tree
  # the image ships is loaded by a script that runs ldapadd right after starting slapd and does not
  # retry if the server is not accepting connections yet:
  #
  #     LDAP schema is: rfc2307
  #     ldap_sasl_bind(SIMPLE): Can't contact LDAP server (-1)
  #
  # CI loses that race often enough that nothing here can depend on the demo data. The root entry
  # comes from the image's own bootstrap LDIF and is always there, so this feature seeds everything
  # it needs underneath it and asserts only on what it created.
  Background: Point the steps at the directory layout and connect
    Given I save 'dc=stratio,dc=com' in variable 'LDAP_USER_DN'
    And I save 'dc=stratio,dc=com' in variable 'LDAP_GROUP_DN'
    And I save 'bdtitadmin' in variable 'LDAP_ADMIN_GROUP'
    # The container serves plain RFC 2307: groups are posixGroup and membership lives in memberUid.
    # Writing the RFC 2307bis shape here gets "invalid structural object class chain
    # (groupOfNames/posixGroup)" back from the server.
    And I save 'rfc2307' in variable 'LDAP_SCHEMA'
    And I connect to LDAP

  Scenario: Anything left behind by a previous run is removed
    Given I delete LDAP user 'bdtituser1'
    And I delete LDAP user 'bdtituser2'
    And I delete LDAP user 'bdtituser3'
    And I delete LDAP group 'bdtitgroup1'
    And I delete LDAP group 'bdtitgroup2'
    And I delete LDAP group 'bdtitadmin'
    And I delete LDAP group 'stratio'
    Then I check that LDAP user 'bdtituser1' does not exist
    And I check that LDAP user 'bdtituser2' does not exist
    And I check that LDAP user 'bdtituser3' does not exist
    And I check that LDAP group 'bdtitgroup1' does not exist
    And I check that LDAP group 'bdtitadmin' does not exist

  Scenario: The groups the rest of the feature needs are created
    # "stratio" is the group a user with no group given falls back to, hardcoded in createLDAPUser.
    When I create LDAP group 'stratio'
    And I create LDAP group 'bdtitadmin'
    And I create LDAP group 'bdtitgroup1'
    And I create LDAP group 'bdtitgroup2'
    Then I check that LDAP group 'bdtitgroup1' exists
    And I check that LDAP group 'bdtitgroup2' exists
    And I check that LDAP group 'bdtitadmin' exists

  Scenario: The CA trust store can be given explicitly
    # LDAP_SSL is false against the test container, so the certificate is never read -- what this
    # covers is that the step accepts an explicit path instead of falling back to the default one.
    Given I connect to LDAP using the CA trust 'target/test-classes/ca_test.crt'
    When I search in LDAP using the filter 'cn=bdtitgroup1' and the baseDn 'dc=stratio,dc=com'
    Then the LDAP entry contains the attribute 'cn' with the value 'bdtitgroup1'

  Scenario: A user created without a group lands in the default one
    When I create LDAP user 'bdtituser1' with password '1234'
    Then I check that LDAP user 'bdtituser1' exists
    And I check that LDAP user 'bdtituser1' belongs to LDAP group 'stratio'

  Scenario: A user can be created straight into a named group
    When I create LDAP user 'bdtituser2' with password '1234' and assign it to LDAP group 'admin'
    And I create LDAP user 'bdtituser3' with password '1234' and assign it to LDAP group 'bdtitgroup1'
    Then I check that LDAP user 'bdtituser2' belongs to LDAP group 'bdtitadmin'
    And I check that LDAP user 'bdtituser3' belongs to LDAP group 'bdtitgroup1'
    And I check that LDAP user 'bdtituser3' does not belong to LDAP group 'bdtitgroup2'

  Scenario: Search for a user and get some of its attributes
    When I search in LDAP using the filter 'uid=bdtituser2' and the baseDn 'dc=stratio,dc=com'
    Then the LDAP entry contains the attribute 'uid' with the value 'bdtituser2'
    And the LDAP entry contains the attribute 'cn' with the value 'bdtituser2'
    And the LDAP entry contains the attribute 'homeDirectory' with the value '/home/bdtituser2'

  Scenario: Test if multiple scenarios can be run sequentially
    When I search in LDAP using the filter 'uid=bdtituser2' and the baseDn 'dc=stratio,dc=com'
    Then the LDAP entry contains the attribute 'uid' with the value 'bdtituser2'

  Scenario: Test if an attribute which has more than one value is correctly found
    # A new group is created with a placeholder member, and bdtituser3 joined it above.
    When I search in LDAP using the filter 'cn=bdtitgroup1' and the baseDn 'dc=stratio,dc=com'
    Then the LDAP entry contains the attribute 'memberUid' with the value 'uid=fake,dc=stratio,dc=com'
    And the LDAP entry contains the attribute 'memberUid' with the value 'uid=bdtituser3,dc=stratio,dc=com'

  Scenario: A user can be assigned to another group and taken out of it again
    Given I check that LDAP user 'bdtituser1' does not belong to LDAP group 'bdtitgroup2'
    When I assign LDAP user 'bdtituser1' to LDAP group 'bdtitgroup2'
    Then I check that LDAP user 'bdtituser1' belongs to LDAP group 'bdtitgroup2'
    When I unassign LDAP user 'bdtituser1' from LDAP group 'bdtitgroup2'
    Then I check that LDAP user 'bdtituser1' does not belong to LDAP group 'bdtitgroup2'

  Scenario: A user can be taken out of every group at once
    Given I assign LDAP user 'bdtituser1' to LDAP group 'bdtitgroup1'
    And I assign LDAP user 'bdtituser1' to LDAP group 'bdtitgroup2'
    When I unassign LDAP user 'bdtituser1' from all LDAP groups
    Then I check that LDAP user 'bdtituser1' does not belong to LDAP group 'bdtitgroup1'
    And I check that LDAP user 'bdtituser1' does not belong to LDAP group 'bdtitgroup2'
    And I check that LDAP user 'bdtituser1' does not belong to LDAP group 'stratio'

  Scenario: The groups a user belongs to are exposed as a variable
    When I get all LDAP groups where LDAP user 'bdtituser3' belongs and save it in environment variable 'groupsList'
    Then I run 'echo !{groupsList} | grep -q bdtitgroup1' locally with exit status '0'

  Scenario: Test change user password
    Then I change the password of LDAP user 'bdtituser2' to '4321'

  Scenario: Test user existence
    Then I check that LDAP user 'bdtituser2' exists
    And I check that LDAP user 'blabla' does not exist

  Scenario: Test group existence
    Then I check that LDAP group 'bdtitgroup1' exists
    And I check that LDAP group 'bleble' does not exist

  Scenario: Deleting a user removes it from the directory and from its groups
    Given I check that LDAP user 'bdtituser3' belongs to LDAP group 'bdtitgroup1'
    When I delete LDAP user 'bdtituser3'
    Then I check that LDAP user 'bdtituser3' does not exist
    And I check that LDAP user 'bdtituser3' does not belong to LDAP group 'bdtitgroup1'

  Scenario: Deleting something that is not there is not an error
    # The JNDI provider maps this to Context.destroySubcontext, which is specified to do nothing
    # when the name is unbound. The cleanup steps depend on it.
    Given I check that LDAP user 'ghost' does not exist
    When I delete LDAP user 'ghost'
    And I delete LDAP group 'bdtitghost'
    Then I check that LDAP user 'ghost' does not exist

  Scenario: Clean everything left
    Then I delete LDAP user 'bdtituser1'
    And I delete LDAP user 'bdtituser2'
    And I delete LDAP group 'bdtitgroup1'
    And I delete LDAP group 'bdtitgroup2'
    And I delete LDAP group 'bdtitadmin'
    And I delete LDAP group 'stratio'
    And I check that LDAP user 'bdtituser1' does not exist
    And I check that LDAP user 'bdtituser2' does not exist
    And I check that LDAP group 'bdtitgroup1' does not exist
    And I check that LDAP group 'bdtitadmin' does not exist
