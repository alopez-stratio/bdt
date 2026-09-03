Feature: Feature used in testing the loop tags on a Scenario Outline

  # Cucumber expands the Examples first and LoopConverter expands each resulting pickle
  # afterwards, so the loop runs once per example row. Every scenario here writes to a
  # file and a following scenario asserts how many lines were written and in which order.

  Scenario: Set the lists used by the loops
    Given I save 'aaa,bbb,ccc' in variable 'OUTLINE_LIST'
    And I save '0,1' in variable 'OUTLINE_LIST_A'
    And I save 'x,y' in variable 'OUTLINE_LIST_B'
    And I save '2' in variable 'OUTLINE_TIMES'

  Scenario: wipe the loop outline file
    Given I run 'rm -f testLoopOutline.txt' locally

  @loop(OUTLINE_LIST,item)
  Scenario Outline: loop <group> with <item>
    Given I run 'echo <group>-<item> >> testLoopOutline.txt' locally
    Examples:
      | group |
      | g1    |
      | g2    |

  Scenario: verify loop on outline size
    Given I run 'wc -l < testLoopOutline.txt' locally
    Then the command output contains '6'

  Scenario: verify loop on outline content and order
    Given I run 'cat testLoopOutline.txt | tr "\n" " "' locally
    Then the command output contains 'g1-aaa g1-bbb g1-ccc g2-aaa g2-bbb g2-ccc'
    Given I run 'rm -f testLoopOutline.txt' locally

  Scenario: wipe the loop index file
    Given I run 'rm -f testLoopOutlineId.txt' locally

  @loop(OUTLINE_LIST,item)
  Scenario Outline: loop index <group> with <item>
    Given I run 'echo <group>-<item.id> >> testLoopOutlineId.txt' locally
    Examples:
      | group |
      | g1    |
      | g2    |

  Scenario: verify the loop index restarts on every example row
    Given I run 'cat testLoopOutlineId.txt | tr "\n" " "' locally
    Then the command output contains 'g1-0 g1-1 g1-2 g2-0 g2-1 g2-2'
    Given I run 'rm -f testLoopOutlineId.txt' locally

  @loop(OUTLINE_LIST,item)
  Scenario Outline: loop replacement in a datatable of an outline, <group> with <item>
    Given I create file 'testLoopOutline<group><item>.json' based on 'schemas/simple1.json' as 'json' with:
      | $.a | REPLACE | <group>-<item> | string |
    Given I run 'cat target/test-classes/testLoopOutline<group><item>.json' locally
    Then the command output contains '{"a":"<group>-<item>"}'
    And I run 'rm -f target/test-classes/testLoopOutline<group><item>.json' locally
    Examples:
      | group |
      | g1    |
      | g2    |

  Scenario: wipe the progloop outline file
    Given I run 'rm -f testProgLoopOutline.txt' locally

  @progloop(OUTLINE_TIMES,iteration)
  Scenario Outline: progloop <group> iteration <iteration>
    Given I run 'echo <group>-<iteration> >> testProgLoopOutline.txt' locally
    Examples:
      | group |
      | g1    |
      | g2    |

  Scenario: verify progloop on outline size
    Given I run 'wc -l < testProgLoopOutline.txt' locally
    Then the command output contains '4'

  Scenario: verify progloop on outline content and order
    Given I run 'cat testProgLoopOutline.txt | tr "\n" " "' locally
    Then the command output contains 'g1-1 g1-2 g2-1 g2-2'
    Given I run 'rm -f testProgLoopOutline.txt' locally

  Scenario: wipe the multiloop outline file
    Given I run 'rm -f testMultiLoopOutline.txt' locally

  @multiloop(OUTLINE_LIST_A=>first,OUTLINE_LIST_B=>second)
  Scenario Outline: multiloop <group> with <first> and <second>
    Given I run 'echo <group>-<first><second> >> testMultiLoopOutline.txt' locally
    Examples:
      | group |
      | g1    |
      | g2    |

  Scenario: verify multiloop on outline size
    Given I run 'wc -l < testMultiLoopOutline.txt' locally
    Then the command output contains '8'

  Scenario: verify multiloop on outline content and order
    Given I run 'cat testMultiLoopOutline.txt | tr "\n" " "' locally
    Then the command output contains 'g1-0x g1-0y g1-1x g1-1y g2-0x g2-0y g2-1x g2-1y'
    Given I run 'rm -f testMultiLoopOutline.txt' locally

  @runOnEnv(OUTLINE_LIST)
  @loop(OUTLINE_LIST,item)
  Scenario Outline: runOnEnv and loop on the same outline, <group> with <item>
    Given I run 'echo <group>-<item>' locally
    Then the command output contains '<group>-<item>'
    Examples:
      | group |
      | g1    |
      | g2    |

  @skipOnEnv(OUTLINE_LIST)
  @loop(OUTLINE_LIST,item)
  Scenario Outline: skipOnEnv wins over the loop, <group> with <item> (it should not be executed)
    Given I run 'echo <group>-<item>' locally with exit status '2'
    Examples:
      | group |
      | g1    |
      | g2    |
