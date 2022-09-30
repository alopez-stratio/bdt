@rest
Feature: OpenSearch steps test

  Scenario: Connect to OpenSearch
    Given I connect to OpenSearch cluster at host '${ES_NODE:-127.0.0.1}' using native port '${ES_PORT:-9200}'
    Given I connect to 'OpenSearch' cluster at '${ES_NODE:-127.0.0.1}'

  Scenario: Obtain clustername in OpenSearch
    Given I obtain opensearch cluster name in '${ES_NODE:-127.0.0.1}:${ES_PORT:-9200}' and save it in variable 'clusternameES'

  Scenario: Create new index in OpenSearch
    Given I create an opensearch index named 'indexes' removing existing index if exist
    Then An opensearch index named 'indexes' exists

  Scenario: Connect to OpenSearch with params
    Given I drop an opensearch index named 'indexes'
    Given An opensearch index named 'indexes' does not exist

  Scenario: Drop OpenSearch indexes
    Given I drop every existing opensearch index

  Scenario: Connect to OpenSearch with clustername obtained
    Given I connect to OpenSearch cluster at host '${ES_NODE:-127.0.0.1}' using native port '${ES_PORT:-9200}' using cluster name '!{clusternameES}'