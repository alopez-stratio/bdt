Feature: Set a variable to the value of the first row whose condition holds

  Scenario Outline: Lower bounds alone, from the highest one down, pick <expected> for version <version>
    Given I save '<version>' in variable 'SW_VERSION'
    And I set variable 'MODE' to the first value whose condition holds:
      | SW_VERSION>=0.3.0 | C |
      | SW_VERSION>=0.1.0 | B |
      | otherwise         | A |
    Then I check the following environment variable values:
      | MODE | is | <expected> |

    Examples:
      | version  | expected |
      | 0.0.5    | A        |
      | 0.1.0    | B        |
      | 0.2.9    | B        |
      | 0.3.0    | C        |
      | 1.0.0-M1 | C        |

  Scenario Outline: Explicit ranges pick <expected> for version <version>
    Given I save '<version>' in variable 'SW_VERSION'
    And I set variable 'MODE' to the first value whose condition holds:
      | SW_VERSION<0.1.0                      | A |
      | SW_VERSION>=0.1.0&&SW_VERSION<0.3.0   | B |
      | SW_VERSION>=0.3.0                     | C |
    Then I check the following environment variable values:
      | MODE | is | <expected> |

    Examples:
      | version | expected |
      | 0.0.9   | A        |
      | 0.2.0   | B        |
      | 0.3.0   | C        |

  Scenario Outline: The '||' operator escaped inside the table picks <expected> for version <version>
    Given I save '<version>' in variable 'SW_VERSION'
    And I set variable 'MODE' to the first value whose condition holds:
      | SW_VERSION<0.1.0\|\|SW_VERSION>=0.5.0 | edge   |
      | otherwise                             | middle |
    Then I check the following environment variable values:
      | MODE | is | <expected> |

    Examples:
      | version | expected |
      | 0.0.1   | edge     |
      | 0.3.0   | middle   |
      | 0.7.0   | edge     |

  Scenario Outline: Repeating the value in two rows does the same as '||', picking <expected> for version <version>
    Given I save '<version>' in variable 'SW_VERSION'
    And I set variable 'MODE' to the first value whose condition holds:
      | SW_VERSION<0.1.0  | edge   |
      | SW_VERSION>=0.5.0 | edge   |
      | otherwise         | middle |
    Then I check the following environment variable values:
      | MODE | is | <expected> |

    Examples:
      | version | expected |
      | 0.0.1   | edge     |
      | 0.3.0   | middle   |
      | 0.7.0   | edge     |

  Scenario: A condition on an undefined property falls through to otherwise
    Given I set variable 'MODE' to the first value whose condition holds:
      | SW_UNDEFINED_VERSION>=0.3.0 | C |
      | SW_UNDEFINED_VERSION>=0.1.0 | B |
      | otherwise                   | A |
    Then I check the following environment variable values:
      | MODE | is | A |

  Scenario: The values are replaced like any other step argument
    Given I save '0.5.0' in variable 'SW_VERSION'
    And I save 'release' in variable 'SW_PREFIX'
    And I set variable 'MODE' to the first value whose condition holds:
      | SW_VERSION>=0.3.0 | !{SW_PREFIX}-new |
      | otherwise         | !{SW_PREFIX}-old |
    Then I check the following environment variable values:
      | MODE | is | release-new |
