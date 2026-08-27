@web
Feature: Selenium steps against a locally served page

  Background: Point the browser at the site this suite serves
    Given My app is running in '${SELENIUM_TEST_SITE}'
    When I browse to '/'

  # Moved here from the orphaned selenium.feature, which was the only thing pulling
  # scenarioIncluded.feature in. It covers @include against a scenario that drives a browser.
  @include(feature:scenarioIncluded.feature,scenario:Dummy_scenario)
  Scenario: A web scenario can be pulled in by the include tag
    Given I wait '1' seconds

  Scenario: The page loads and its text can be checked
    Then '1' element exists with 'id:main-title'
    And the element on index '0' has 'BDT test site' as text
    And this text exists 'Everything the Selenium steps need'
    And we are in page '/'

  Scenario: Elements can be located every way the step supports
    Then '1' element exists with 'id:username'
    And '1' element exists with 'name:username'
    And '1' element exists with 'xpath://*[@id="username"]'
    And '1' element exists with 'css:#username'
    And '3' elements exist with 'class:field'

  Scenario: Waiting for something that only appears later
    When '1' element exists with 'id:reveal-btn'
    And I click on the element on index '0'
    Then in less than '10' seconds, checking each '1' seconds, '1' elements exists with 'id:slow-result'

  Scenario: Waiting for an element to become available
    Then I wait for element 'id:main-title' to be available for '10' seconds

  Scenario: Typing into a text input and reading it back
    When '1' element exists with 'id:username'
    And I type 'stratio' on the element on index '0'
    Then the element on index '0' has 'value' as 'stratio'

  Scenario: Typing by selector rather than by index
    When I type on element '#username' the following text 'from a selector'
    And '1' element exists with 'id:username'
    Then the element on index '0' has 'value' as 'from a selector'

  # "has '<attr>' as '<value>'" fails on an empty attribute before it ever compares, so an emptied
  # input is proved by typing into it: had the clear not worked, the old content would still be
  # there in front of the new text and the full-match regexp would not hold.
  Scenario: Clearing an input
    When '1' element exists with 'id:prefilled'
    And I clear the content on text input at index '0'
    And I type 'after clearing' on the element on index '0'
    Then the element on index '0' has 'value' as 'after clearing'

  Scenario: Clearing an input with the keyboard
    When '1' element exists with 'id:prefilled'
    And I clear the content on text input using keyboard at index '0'
    And I type 'cleared by keyboard' on the element on index '0'
    Then the element on index '0' has 'value' as 'cleared by keyboard'

  Scenario: Deleting part of the text and replacing it
    When '1' element exists with 'id:prefilled'
    And I delete the text 'delete me please' on the element on index '0' and replace it for 'brand new'
    Then the element on index '0' has 'value' as 'brand new'

  Scenario: Sending a key to an element
    When '1' element exists with 'id:username'
    And I type 'abc' on the element on index '0'
    And I send 'BACK_SPACE' on the element on index '0'
    Then the element on index '0' has 'value' as 'ab'

  Scenario: Typing only if the element is there
    When I type 'conditional' if the element exists with 'id:username'
    And I type 'ignored' if the element exists with 'id:not-in-this-page'
    And '1' element exists with 'id:username'
    Then the element on index '0' has 'value' as 'conditional'

  Scenario: Clicking and double clicking
    When '1' element exists with 'id:click-counter'
    And I click on the element on index '0'
    Then '1' element exists with 'id:click-echo'
    And the element on index '0' has 'clicked' as text
    When '1' element exists with 'id:click-counter'
    And I double click on the element on index '0'
    Then the element on index '0' has 'double clicked' as text

  Scenario: Moving the pointer onto an element
    When '1' element exists with 'id:hover-target'
    And I move to element on index '0'
    Then '1' element exists with 'id:hover-echo'
    And the element on index '0' has 'hovered' as text

  Scenario: Selecting and de-selecting options
    When '1' element exists with 'id:role'
    And I select 'admin' on the element on index '0'
    Then the element on index '0' has 'value' as 'admin'
    When '1' element exists with 'id:tags'
    And I de-select every item on the element on index '0'
    Then '1' element exists with 'id:tags'

  Scenario: Whether an element is displayed, enabled or selected
    Then '1' element exists with 'id:hidden-box'
    And the element on index '0' IS NOT displayed
    Then '1' element exists with 'id:disabled-field'
    And the element on index '0' IS NOT enabled
    Then '1' element exists with 'id:username'
    And the element on index '0' IS displayed
    And the element on index '0' IS enabled
    Then '1' element exists with 'id:remember'
    And the element on index '0' IS NOT selected
    When I click on the element on index '0'
    Then '1' element exists with 'id:remember'
    And the element on index '0' IS selected

  Scenario: Saving the content and the attributes of an element
    When '1' element exists with 'id:main-title'
    And I save content of element in index '0' in environment variable 'titleText'
    And I save attribute 'data-role' of element in index '0' in environment variable 'titleRole'
    Then I run 'echo !{titleText} | grep -q "BDT test site"' locally with exit status '0'
    And I run 'echo !{titleRole} | grep -q heading' locally with exit status '0'

  Scenario: Retrieving a value by selector
    Given I retrieve value from element '#prefilled' and save the value in environment variable 'prefilledValue'
    Then I run 'echo !{prefilledValue} | grep -q "delete me please"' locally with exit status '0'

  Scenario: Switching into an iframe and back out
    # switching by index takes the iframe from the elements the previous search found
    When '1' element exists with 'id:frame-one'
    And I switch to the iframe on index '0'
    Then '1' element exists with 'id:frame-content'
    And the element on index '0' has 'inside the iframe' as text
    When I switch to a parent frame
    Then '1' element exists with 'id:main-title'
    When I switch to iframe with 'id:frame-one'
    Then '1' element exists with 'id:frame-content'
    When I switch to the main frame container
    Then '1' element exists with 'id:main-title'

  Scenario: Opening a second window and moving between them
    When '1' element exists with 'id:second-link'
    And I click on the element on index '0'
    Then a new window is opened
    When I change active window
    Then '1' element exists with 'id:second-content'
    When I change active window
    Then '1' element exists with 'id:main-title'

  Scenario: Dragging an element around
    When I move element with 'id:draggable', '80' pixels horizontally and '30' pixels vertically
    Then '1' element exists with 'id:draggable'

  Scenario: Cookies set by the site can be saved and inspected
    Then I save selenium cookies in context
    And I have '2' selenium cookies saved
    And The cookie 'bdtSession' exists in the saved cookies
    When I save selenium cookie 'bdtFlavour' in variable 'flavour'
    Then I run 'echo !{flavour} | grep -q vanilla' locally with exit status '0'

  Scenario: The browser window can be maximized and a snapshot taken
    When I maximize the browser
    Then I take a snapshot
    And '1' element exists with 'id:main-title'
