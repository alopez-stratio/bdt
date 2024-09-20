@rest
Feature: Replacement thread property

  Scenario: Store var
    Given I save the following variables:
      | test              | aA |
      | test.dot          | bB |
      | test.dot.dot      | cC |
      | test.dot.final.   | dD |
      | test-hyphen       | eE |
      | test_lower_hyphen | fF |
      | test/slash        | gG |
      | test space        | hH |
      | testUPPER         | iI |

  Scenario Outline: Read var - <var> - got: !{<var>} - expected: <expected_result>
    Then '!{<var>}' is '<expected_result>'

    Examples:
      | var               | expected_result |
      | test              | aA              |
      | test.dot          | bB              |
      | test.dot.dot      | cC              |
      | test.dot.final.   | dD              |
      | test-hyphen       | eE              |
      | test_lower_hyphen | fF              |
      | test/slash        | gG              |
      | test space        | hH              |
      | testUPPER         | iI              |
    Examples:
      | var                       | expected_result |
      | test.toUpper              | AA              |
      | test.dot.toUpper          | BB              |
      | test.dot.dot.toUpper      | CC              |
      | test.dot.final..toUpper   | DD              |
      | test-hyphen.toUpper       | EE              |
      | test_lower_hyphen.toUpper | FF              |
      | test/slash.toUpper        | GG              |
      | test space.toUpper        | HH              |
      | testUPPER.toUpper         | II              |
    Examples:
      | var                       | expected_result |
      | test.toLower              | aa              |
      | test.dot.toLower          | bb              |
      | test.dot.dot.toLower      | cc              |
      | test.dot.final..toLower   | dd              |
      | test-hyphen.toLower       | ee              |
      | test_lower_hyphen.toLower | ff              |
      | test/slash.toLower        | gg              |
      | test space.toLower        | hh              |
      | testUPPER.toLower         | ii              |
