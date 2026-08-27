Feature: Kafka steps test

  # Every read assertion consumes with a hardcoded group.id ("functionalAT") and auto-commit on,
  # so the offsets are committed when the consumer closes and a second read of the same topic
  # comes back empty. That is why each check below gets a topic of its own.

  Scenario: Open Kafka connection
    Given I open connection to kafka with url '${KAFKA_HOSTS}'
    And I wait '60' seconds, checking every '5' seconds for kafka server availability

  Scenario: Create topic
    When I create topic '${TOPIC:-ittopic}1'
    Then topic '${TOPIC:-ittopic}1' exists
    And number of partitions in topic '${TOPIC:-ittopic}1' is '1'

  Scenario: A topic can be asked for exactly one partition
    # The topic is always created with one partition and then widened, so asking for 1 used to
    # come back as "InvalidPartitionsException: Topic already has 1 partitions".
    When I create topic '${TOPIC:-ittopic}single' with '1' partitions
    Then topic '${TOPIC:-ittopic}single' exists
    And number of partitions in topic '${TOPIC:-ittopic}single' is '1'

  Scenario: A topic can be created with several partitions
    When I create topic '${TOPIC:-ittopic}multi' with '3' partitions
    Then topic '${TOPIC:-ittopic}multi' exists
    And number of partitions in topic '${TOPIC:-ittopic}multi' is '3'

  Scenario: Send message to topic
    When I send message '${MESSAGE:-itmessage}1' to topic '${TOPIC:-ittopic}1'
    And I send message '${MESSAGE:-itmessage}2' to topic '${TOPIC:-ittopic}1'
    And I wait '5' seconds
    Then topic '${TOPIC:-ittopic}1' contains '2' messages with values:
      | ${MESSAGE:-itmessage}1 |
      | ${MESSAGE:-itmessage}2 |

  Scenario: A single message can be looked up by value
    Given I create topic '${TOPIC:-ittopic}one'
    When I send message '${MESSAGE:-itmessage}single' to topic '${TOPIC:-ittopic}one'
    And I wait '5' seconds
    Then topic '${TOPIC:-ittopic}one' contains message '${MESSAGE:-itmessage}single'

  Scenario: The number of messages in a topic can be checked on its own
    Given I create topic '${TOPIC:-ittopic}count'
    When I send message '${MESSAGE:-itmessage}a' to topic '${TOPIC:-ittopic}count'
    And I send message '${MESSAGE:-itmessage}b' to topic '${TOPIC:-ittopic}count'
    And I send message '${MESSAGE:-itmessage}c' to topic '${TOPIC:-ittopic}count'
    And I wait '5' seconds
    Then topic '${TOPIC:-ittopic}count' contains '3' messages

  Scenario: A message can be sent to a chosen partition and read back from it
    Given I create topic '${TOPIC:-ittopic}p1' with '3' partitions
    When I send message '${MESSAGE:-itmessage}part' to topic '${TOPIC:-ittopic}p1' in partition '2'
    And I wait '5' seconds
    Then topic '${TOPIC:-ittopic}p1' contains message '${MESSAGE:-itmessage}part' in partition '2'

  Scenario: Messages can be counted within one partition
    Given I create topic '${TOPIC:-ittopic}p2' with '3' partitions
    When I send message '${MESSAGE:-itmessage}p0' to topic '${TOPIC:-ittopic}p2' in partition '0'
    And I send message '${MESSAGE:-itmessage}p1' to topic '${TOPIC:-ittopic}p2' in partition '1'
    And I send message '${MESSAGE:-itmessage}p1bis' to topic '${TOPIC:-ittopic}p2' in partition '1'
    And I wait '5' seconds
    Then topic '${TOPIC:-ittopic}p2' contains '2' messages in partition '1'

  Scenario: The contents of one partition can be checked value by value
    Given I create topic '${TOPIC:-ittopic}p3' with '3' partitions
    When I send message '${MESSAGE:-itmessage}v1' to topic '${TOPIC:-ittopic}p3' in partition '1'
    And I send message '${MESSAGE:-itmessage}v2' to topic '${TOPIC:-ittopic}p3' in partition '1'
    And I wait '5' seconds
    Then topic '${TOPIC:-ittopic}p3' contains '2' messages in partition '1' with values:
      | ${MESSAGE:-itmessage}v1 |
      | ${MESSAGE:-itmessage}v2 |

  Scenario: Transactional messages are committed and read back
    # Needs KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 on the broker; the default of 3
    # cannot be met by the single broker in ITSERVICES and initTransactions() times out.
    Given I create topic '${TOPIC:-ittopic}tx'
    When I send transactional messages to topic '${TOPIC:-ittopic}tx'
    And I wait '5' seconds
    Then topic '${TOPIC:-ittopic}tx' contains transactional messages

  Scenario: A topic that was never created is reported as missing
    Then topic '${TOPIC:-ittopic}neverthere' does not exist

  @important
  Scenario: Delete topics and close connection
    When I delete topic '${TOPIC:-ittopic}1'
    And I delete topic '${TOPIC:-ittopic}single'
    And I delete topic '${TOPIC:-ittopic}multi'
    And I delete topic '${TOPIC:-ittopic}one'
    And I delete topic '${TOPIC:-ittopic}count'
    And I delete topic '${TOPIC:-ittopic}p1'
    And I delete topic '${TOPIC:-ittopic}p2'
    And I delete topic '${TOPIC:-ittopic}p3'
    And I delete topic '${TOPIC:-ittopic}tx'
    Then topic '${TOPIC:-ittopic}1' does not exist
    And topic '${TOPIC:-ittopic}tx' does not exist
    And I close Kafka connection
