@web
Feature: Selenium run test

#  Scenario: Dummy scenario
#    Given My app is running in 'www.google.com:80'
#    When I browse to '/'
#    When '1' elements exists with 'xpath://input[@name="q"]'
#    And I click on the element on index '0'
#    Then I type 'stratio' on the element on index '0'
#    Then I send 'ENTER' on the element on index '0'
#    And I wait '1' seconds

  @include(feature:scenarioIncluded.feature,scenario:Dummy_scenario)
  Scenario: Testing include
    Given I wait '1' seconds

  Scenario: Dummy scenario with HTTPS
    Given My app is running in 'qa.stratio.com'
    When I securely browse to '/'

  Scenario: Checking element steps
    Given My app is running in 'builder.int.stratio.com'
    When I browse to '/'
    Then in less than '20' seconds, checking each '2' seconds, '1' elements exists with 'id:side-panel'
    When '1' elements exists with 'xpath://*[@id="page-header"]/div[3]/a/b'
    And I click on the element on index '0'
    Then I wait for element 'id:loginIntroDefault' to be available for '20' seconds
    When '1' elements exists with 'id:loginIntroDefault'
    Then '1' elements exists with 'id:j_username'
    And I run 'echo "soy un texto"' locally with exit status '0' and save the value in environment variable 'testvar'
    When I type on element '#j_username' the following text '!{testvar}'
    When I wait '2' seconds
    And I clear the content on text input at index '0'
    When I wait '2' seconds
    When '1' elements exists with 'id:remember_me'

  Scenario: Draggable
    Given My app is running in 'marcojakob.github.io:443'
    When I securely browse to '/dart-dnd/basic'
    When I move element with 'xpath:/html/body/div/img[1]', '300' pixels horizontally and '50' pixels vertically
    When I wait '2' seconds