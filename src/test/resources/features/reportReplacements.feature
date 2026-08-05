Feature: Report replacements

  Scenario: Scenario name resolved to ${REPORT_CLUSTER:-romeo}
    Given I save '${REPORT_CLUSTER:-romeo}' in variable 'reportCluster'
    Then '!{reportCluster}' is 'romeo'
