[![Coverage Status](https://coveralls.io/repos/github/Stratio/bdt/badge.svg?branch=master)](https://coveralls.io/github/Stratio/bdt?branch=master)
[![Maven central](https://maven-badges.herokuapp.com/maven-central/com.stratio.qa/bdt/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.stratio.qa/bdt)

Stratio bdt
===========

Stratio Acceptance Test library — a published Maven library (`com.stratio.qa:bdt`) used as the base framework for acceptance tests across Stratio projects.

 * Cucumber for test definition
 * TestNG for execution
 * AsyncHTTP Client
 * Selenium
 * JSONPath
 * AssertJ

[Javadoc always live](http://stratiodocs.s3.amazonaws.com/bdt/index.html)

## AVAILABLE STEP DEFINITIONS

The library provides step definitions for the following domains:

| Spec | Domain |
|---|---|
| `CommonG` / `MiscSpec` | Variables, file creation, JWT tokens, timestamps, JSON manipulation |
| `RestSpec` | HTTP REST requests, response assertions, polling |
| `CommandExecutionSpec` | SSH connections, local/remote command execution, SCP |
| `SeleniumSpec` | Browser automation, element interaction, assertions |
| `DatabaseSpec` | Cassandra, MongoDB, Elasticsearch, OpenSearch, PostgreSQL, Oracle, DB2, SQL Server, SAP HANA, CrossData |
| `K8SSpec` | Kubernetes — pods, deployments, configmaps, secrets, exec, manifests |
| `GosecSpec` | Security — users, groups, tenants, profiles, policies (DC/OS and Keos) |
| `CCTSpec` | Stratio control plane — deploy, scale, teardown, configuration |
| `DcosSpec` | DC/OS platform, Marathon, schema validation |
| `KeosSpec` | Keos platform, playbooks, universe management |
| `VaultSpec` | Vault secrets — certificates, keytabs, credentials |
| `KafkaSecSpec` | Kafka — topics, produce, consume |
| `HDFSSecSpec` | HDFS — read, write, list |
| `LdapSpec` | LDAP — users, groups, membership |
| `ZookeeperSpec` | Zookeeper — zNode operations |
| `AWSSpec` | AWS S3 — buckets, objects |
| `GCSSpec` | Google Cloud Storage — buckets, blobs |
| `AzureSpec` | Azure Data Lake / Blob Storage |
| `CypressSpec` | Cypress JS test execution |
| `ETCHOSTSManagementSpec` | `/etc/hosts` management (local and remote) |



## EXECUTION

These tests are suposed to be executed as follows:

` mvn verify [-D\<ENV_VAR>=\<VALUE>] [-Dit.test=\<TEST_TO_EXECUTE>|-Dgroups=\<GROUP_TO_EXECUTE>] `

Examples:

_**single class execution**_

` mvn verify -DSECS=AGENT_LIST=1,2 -Dit.test=com.stratio.qa.ATests.LoopTagAspectIT `

_**group execution**_

` mvn verify -DSECS=5 -Dgroups=hol `

### REST Client Configuration

BDT provides configuration options for REST client timeouts through system properties. There are two sets of configurations:

#### Standard REST Client (`@rest`, `@dcos`, `@keos`)
- `BDT_CLIENT_CONNECT_TIMEOUT` - Connection timeout in milliseconds (default: 5000ms / 5s)
- `BDT_CLIENT_REQUEST_TIMEOUT` - Request timeout in milliseconds (default: 60000ms / 60s)
- `BDT_CLIENT_READ_TIMEOUT` - Read timeout in milliseconds (default: 120000ms / 120s)

#### Long Timeout REST Client (`@restWithLongerTimeout`)
For long-running requests (5+ minutes), use the `@restWithLongerTimeout` tag with extended timeouts and keep-alive enabled:

- `BDT_CLIENT_CONNECT_TIMEOUT_LONG` - Connection timeout in milliseconds (default: 30000ms / 30s)
- `BDT_CLIENT_REQUEST_TIMEOUT_LONG` - Request timeout in milliseconds (default: 600000ms / 600s / 10min)
- `BDT_CLIENT_READ_TIMEOUT_LONG` - Read timeout in milliseconds (default: 600000ms / 600s / 10min)
- `BDT_CLIENT_POOLED_IDLE_TIMEOUT_LONG` - Pooled connection idle timeout in milliseconds (default: 900000ms / 900s / 15min)
- `BDT_CLIENT_CONNECTION_TTL_LONG` - Connection time-to-live in milliseconds (default: 900000ms / 900s / 15min)

**Note:** The long timeout client has keep-alive enabled and automatic retries disabled to prevent duplicate requests. This configuration helps avoid `RemotelyClosedException` errors on long-running requests.

_Example:_

```
mvn verify -DBDT_CLIENT_REQUEST_TIMEOUT_LONG=900000 -DBDT_CLIENT_POOLED_IDLE_TIMEOUT_LONG=1200000
```

## ASPECTS

BDT extends Cucumber with 8 AspectJ aspects that operate transparently at runtime.

- **ReplacementAspect** (variable interpolation)

Before each step executes, all placeholder tokens in step text, DataTable cells, and DocStrings are resolved. Step definition code never sees unresolved tokens.

| Syntax | Source |
|---|---|
| `${VAR}` | Java system property (`-DVAR=value` on the Maven command line) |
| `!{VAR}` | Thread-local variable set by a previous step at runtime |
| `@{JSON.path/to/file.json}` | Content of a JSON file (relative to `src/test/resources`) |
| `@{FILE.path/to/file}` | Raw content of any file |
| `@{IP.eth0}` | Local IP address of the named network interface |

Modifiers for `${VAR}` and `!{VAR}`:
- `${VAR.toLower}` — lowercase result
- `${VAR.toUpper}` — uppercase result
- `${VAR:-defaultValue}` — use `defaultValue` if VAR is not defined
- `${VAR:-''}` — use empty string if VAR is not defined

_Examples:_

```
Given I send a 'GET' request to 'http://${HOST}/api'
When I run '[ "!{result}" = "ok" ]' locally
Then I create file 'config.json' based on '@{JSON.schemas/template.json}' as 'json' with:
  | $.host | REPLACE | ${HOST} | string |
```

---

- **RunOnTagAspect**:

` @runOnEnv(METRIC_HOST) `

` @skipOnEnv(METRIC_HOST) `

An AspectJ aspect handles Cucumber tags so that the annotated scenario is skipped if the condition is not met (no trace of execution shows up in reports).

_Example @runOnEnv:_

```
    @runOnEnv(SECS)
    Scenario: Dummy scenario
      And I wait '${SECS}' seconds
```

This scenario will ONLY be executed if environment variable SECS is defined.


_Example @skipOnEnv:_

```
    @skipOnEnv(SECS_2)
    Scenario: Dummy scenario
      And I wait '${SECS}' seconds
```

This scenario will be omitted if environment variable SECS_2 is defined.

**Advanced operators** inside a single tag:

| Operator | Meaning |
|---|---|
| `@runOnEnv(A&&B)` | Run only if BOTH A and B are defined |
| `@runOnEnv(A\|\|B)` | Run if EITHER A or B is defined |
| `@runOnEnv(A=value)` | Run only if A equals `value` |
| `@runOnEnv(A>value)` | Run only if A is greater than `value` (numeric or semver) |
| `@runOnEnv(A<value)` | Run only if A is less than `value` (numeric or semver) |

Multiple tags on separate lines act as AND for `@skipOnEnv` and as independent checks for `@runOnEnv`.

- **IgnoreTagAspect**

An AspectJ aspect that allows skipping a scenario or feature. A tag must be used before the scenario or feature, with an optional reason.

```
 @ignore @manual
 @ignore @unimplemented
 @ignore @toocomplex
 @ignore @tillfixed(DCS-XXX)
```

`@tillfixed(JIRA-XXX)` queries the Jira REST API at execution time: if the ticket is in a "done" state the scenario runs; otherwise it is skipped. Requires `-Dusernamejira=<user> -Dpasswordjira=<pass>` system properties.

- **MandatoryAspect**

Prevents scenario execution if required system properties are not defined.

` @mandatory @vars(VAR1,VAR2,...) `

If any listed variable is absent, execution aborts with an error log (no exception thrown). Useful to enforce required `-D` parameters before a test suite runs.

_Example:_

```
  @mandatory @vars(KEOS_REALM,KEOS_CCT_HOST)
  Scenario: Deploy service
    ...
```

---

- **Error Tag** (part of ReplacementAspect)

Forces deliberate failure of a scenario, useful for testing cleanup flows or marking known broken states.

```
@error
@errorMessage(Custom failure message here)
Scenario: This scenario will always fail
  ...
```

---

- **SeleniumAspect**

On any Selenium `WebDriverException` or Selenium assertion failure, automatically captures:
- Full page HTML source
- Frame HTML source
- Screenshot

Evidence files land in `target/executions/`. No configuration required.

---

- **BrowsersDataProviderAspect**

Set `FORCE_BROWSER` system property to restrict the browser matrix to a single browser, skipping full cross-browser enumeration.

```
mvn verify -DFORCE_BROWSER=chrome
```

---

- **IncludeTagAspect**

An AspectJ aspect that includes a scenario before the tagged one. It also manages parameter passing.

` @include(feature:<feature>,scenario:<scenario>)`

` @include(feature:<feature>,scenario:<scenario>,params:<params>)`


_Examples:_

Simple example
```
    @include(feature:sample.feature,scenario:Not so dummy scenario)
    Scenario: Dummy scenario
          And I wait '${SECS}' seconds
```
Predefined param
```
    @include(feature:sample.feature,scenario:Not so dummy scenario,params:[param1:1])
    Scenario: Dummy scenario
          And I wait '${SECS}' seconds
```
Environment variable(maven command)
```
    @include(feature:sample.feature,scenario:Not so dummy scenario,params:[param1:${test}])
    Scenario: Dummy scenario
          And I wait '${SECS}' seconds
```

Saved variable param
```
    @include(feature:sample.feature,scenario:Not so dummy scenario,params:[param1:!{test}])
    Scenario: Dummy scenario
          And I wait '${SECS}' seconds
```

Included Scenario
```
     Scenario: Included scenario
          And I wait '<param1>' seconds
```

- **LoopTagAspect**

An AspectJ aspect that allows looping over scenarios. It provides two functionalities:

***@loop***

Using this tag before an scenario will convert this scenario into an scenario outline, changing parameter defined "NAME" for every element in the environment variable list received.

` @loop(LIST_PARAM,NAME)`

Being LIST_PARAM: `-DLIST_PARAM=elem1,elem2,elem3`

_Examples:_

```
  @loop(AGENT_LIST,VAR_NAME)
  Scenario: write <VAR_NAME> a file the final result of the scenario.
    Given I run 'echo <VAR_NAME> >> testOutput.txt' locally
```

```
  @loop(AGENT_LIST,VAR_NAME)
  Scenario: With scenarios outlines and datatables
    Given I create file 'testSOATtag<VAR_NAME.id>.json' based on 'schemas/simple<VAR_NAME>.json' as 'json' with:
      | $.a | REPLACE | @{JSON.schemas/empty.json}     | object   |
    Given I save '@{JSON.testSOATtag<VAR_NAME.id>.json}' in variable 'VAR'
    Then I run '[ "!{VAR}" = "{"a":{}}" ]' locally
```

`-DAGENT_LIST=1,2`

More examples can be found in [Loop feature](src/test/resources/features/loopTag.feature)

***@multiloop***

Using this tag before an scenario will convert this scenario into an scenario outline, changing parameter defined names for every element in the environment variable lists received. It creates every possibility between provided lists.

` @multiloop(LIST_PARAM1=>NAME1,LIST_PARAM2=>NAME2,LIST_PARAM3=>NAME3)`

Being LIST_PARAMX: `-DLIST_PARAMX=elem1,elem2,elem3`

_Examples:_

```
  @multiloop(AGENT_LIST=>VAR_NAME)
  Scenario: write <VAR_NAME> a file the final result of the scenario.
      Given I run 'echo <VAR_NAME> >> testOutput.txt' locally
```

Given `-DAGENT_LIST=1,2`, it will lead to an scenario outline with 2 different examples (one for each value in AGENT_LIST)
File will contain these 2 lines:
```
1
2
```

_Notice that this case `@multiloop(AGENT_LIST=>VAR_NAME)` is equivalent to `@loop(AGENT_LIST,VAR_NAME)`_

```
  @multiloop(SERVER_LIST=>SERVER_NAME,CLIENT_LIST=>CLIENT_NAME)
  Scenario: write <SERVER_NAME> and <CLIENT_NAME> into a file.
    Given I run 'echo "<SERVER_NAME>,<CLIENT_NAME>" >> testMultiloopOutput.txt' locally
```

Given `-DSERVER_LIST=server1,server2 -DCLIENT_LIST=client1,client2,client3`, it will lead to an scenario outline with 6 different examples (one for each possible tuple of values, mixing SERVER_LIST and CLIENT_LIST)

File will contain these 6 lines:
```
server1,client1
server2,client1
server1,client2
server2,client2
server1,client3
server2,client3
```

More examples can be found in [Multiloop feature](src/test/resources/features/multiloopTag.feature)

- **Background Tag**

An AspectJ aspect included in loopTagAspect that allows conditional backgrounds. Its used inside the Background label as can be seen in the examples:

```
@background(VAR)        // Beginning of conditional block of steps
   Given X
   When  Y
   Then  Z
@/background            // End of block
  ```

Being VAR: `-DVAR=value`

_Examples:_

```
  Background:
    Given I run '[ "SHOULD_RUN" = "SHOULD_RUN" ]' locally
  @background(WAIT_NO)
    Given I run '[ "SHOULD_RUN" = "FAIL_RUN" ]' locally
    Given I run '[ "SHOULD_RUN" = "FAIL_RUN" ]' locally
    Given I run '[ "SHOULD_RUN" = "FAIL_RUN" ]' locally
  @/background
    Given I run '[ "SHOULD_RUN" = "SHOULD_RUN" ]' locally
```

If the test above its executed *WITH* `-DWAIT_NO=value` then the background will be:


```
   Background:
     Given I run '[ "SHOULD_RUN" = "SHOULD_RUN" ]' locally
     Given I run '[ "SHOULD_RUN" = "FAIL_RUN" ]' locally
     Given I run '[ "SHOULD_RUN" = "FAIL_RUN" ]' locally
     Given I run '[ "SHOULD_RUN" = "FAIL_RUN" ]' locally
     Given I run '[ "SHOULD_RUN" = "SHOULD_RUN" ]' locally
```

On the other hand, if it is executed *WITHOUT* the environment variable, the background will be:

```
   Background:
     Given I run '[ "SHOULD_RUN" = "SHOULD_RUN" ]' locally
     Given I run '[ "SHOULD_RUN" = "SHOULD_RUN" ]' locally
```


In conclusion, if environment variable is defined the code below the tag would be included as part of the background, if not, it will be omitted.

More examples can be found in [Background feature](src/test/resources/features/backgroundTag1.feature)

- **Important Tag**

Stops execution of remaining scenarios in a feature after the tagged scenario fails. Examples:

```
Feature: Example Quit after tag

 Scenario: Keeps executing next scenario
     Given something
     When fail

  @important
  Scenario: important and fails
    Given something
    When fail
    Then something
  ```

In addition, this tag can be used the other way. By defining the property ```-Dquietasdefault=false``` every scenario turns "important" so a ```@notimportant``` tag can be used to prevent fast failure like this:

```
Feature: Example Quit after tag

 Scenario: Keeps executing next scenario
     Given something
     When fail

  @notimportant
  Scenario: important and fails
    Given something
    When fail
    Then something
  ```

Finally, the `@important` tag will make the rest of the Features of the Test Group to be skipped, but this behavior 
can be overwritten using the tag `@continueOnPreviousFeatureFailure` in the Feature(s) that you want to keep being executed.
Example:

```
Feature: Failing feature with important tag

  @important
  Scenario: important and fails
    Given I fail


Feature: Skipped feature

  Scenario: Skipped
    Then this feature will be skipped because the previous failed


@continueOnPreviousFeatureFailure
Feature: Feature that will continue

  Scenario: Will continue
    Then this feature will be executed
```
