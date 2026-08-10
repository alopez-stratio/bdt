# BDT — Claude Context

## What this is

**bdt** (`com.stratio.qa:bdt`) is a **published Maven library** — a Cucumber/TestNG test framework used as a base dependency across Stratio's acceptance test projects. This repo does **not** contain product tests; it contains the framework that other test projects consume.

## Architecture

### 3 layers

1. **Aspects (AOP)** — 7 AspectJ aspects that intercept Cucumber internals via load-time weaving. These are the core innovation.
2. **Specs** — 26 Cucumber step-definition classes covering: REST, SSH, Selenium, Kubernetes, Cassandra/Mongo/ES/OpenSearch, Kafka, HDFS, Vault, LDAP, Zookeeper, PostgreSQL/Oracle/DB2/SQLServer/SAP HANA, Cypress, AWS S3, GCS, Azure, Marathon, Mesos, DC/OS, Keos.
3. **CommonG** — central god-object (~3000 lines) holding all shared clients (HTTP, WebDriver, SSH, DB, K8s…). Injected via Picocontainer into every spec class constructor.

### Variable interpolation (ReplacementAspect — transparent to steps)

Step code never sees unresolved tokens. Before each step executes, `ReplacementAspect` replaces:

| Syntax | Source | Modifiers |
|---|---|---|
| `${VAR}` | Java system property (`-DVAR=value`) | `.toLower`, `.toUpper`, `:-default` |
| `!{VAR}` | `ThreadProperty` (thread-local store, set by steps at runtime) | `.toLower`, `.toUpper` |
| `@{JSON.path/to/file.json}` | JSON file content (relative to `src/test/resources`) | — |
| `@{FILE.path/to/file}` | Raw file content | — |
| `@{IP.eth0}` | Local IP of named network interface | — |

Empty default: `${VAR:-''}` resolves to empty string if VAR not defined.

### ThreadProperty

`com.stratio.qa.utils.ThreadProperty` — `ThreadLocal<Properties>` store. Steps write with `ThreadProperty.set("key", "value")`, read with `ThreadProperty.get("key")`. This is how `!{VAR}` works and how state flows between steps within a scenario.

### Platform duality

Many specs (`GosecSpec`, `CCTSpec`, `DcosSpec`, `KeosSpec`, `K8SSpec`) branch between:
- **DC/OS** — Marathon/Mesos-based; `@dcos` tag triggers setup in `HookGSpec`
- **Keos** — Kubernetes-based; `@keos` tag triggers K8s client init in `HookGSpec`

When suggesting changes to these specs, ask the user which platform they target, or cover both paths.

## The 7 Aspects

| Aspect | Weave point | Purpose |
|---|---|---|
| `RunOnTagAspect` | `Runner.runPickle` (`@Before`) | `@runOnEnv` / `@skipOnEnv` with `&&`, `\|\|`, `=`, `>`, `<`, semver |
| `IgnoreTagAspect` | `Runner.runPickle` (`@Around`) | `@ignore @manual\|unimplemented\|toocomplex\|tillfixed(JIRA-XXX)` — calls Jira REST API for `tillfixed` |
| `ReplacementAspect` | `Runner.runPickle` + step execution | Variable interpolation (`${}`/`!{}`/`@{}`) + `@error`/`@errorMessage` tags |
| `MandatoryAspect` | `Runner.runPickle` (`@Around`) | `@mandatory @vars(X,Y)` — aborts if env vars missing |
| `AssertJAspect` | `Failures.failure` | Downgrades AssertJ errors to WARN during polling loops |
| `SeleniumAspect` | Selenium method calls | Auto-captures HTML + screenshot on any Selenium failure |
| `BrowsersDataProviderAspect` | `BrowsersDataProvider.available*` | `FORCE_BROWSER` system property short-circuits browser matrix |

`LoopIncludeTagAspect` is **no longer an aspect** (Phase 2). It is now a plain Java utility called directly from `FeatureParser.read()` to preprocess `@include` and `@background` blocks before Gherkin parsing. `@loop` / `@progloop` / `@multiloop` are handled post-Gherkin by `LoopConverter` in `CucumberRunner`.

## Known architectural risks

### Forked Cucumber runtime
`com.stratio.qa.cucumber.*` is a fork of Cucumber 4.4.0 internals. Aspects weave into private internal methods (`Runner.runPickle`; `FeatureParser.read` is no longer a weave point after Phase 2). **Any Cucumber version upgrade is high-risk** — these weave points may not exist or may have changed signatures.

### CommonG god-object
`CommonG.java` (~3000 lines) holds all shared state. Tightly coupled, hard to unit-test in isolation. Resist adding more responsibilities to it.

### Static state in HookGSpec
`prevScenarioFailed` and `importantScenarioHasFail` are `static` fields — breaks under parallel scenario execution.

### Jira HTTP call in IgnoreTagAspect
`@tillfixed(JIRA-XXX)` makes a synchronous HTTP call to Jira REST API during test execution. If Jira is unreachable, the aspect logs an error and falls back to skipping the scenario.

## Build

```bash
# Unit tests only (fast, no infra needed)
mvn clean test -DskipITs

# Integration tests only (requires Docker services — see below)
mvn clean verify -DskipUTs -Dmaven.javadoc.skip

# Run specific IT class — always include ALL vars (some may be unused but prevents failures)
# See "Running integration tests locally" for full IP setup and variable reference.
# Example (requires Docker services running and IPs exported as shown below):
mvn clean verify -DskipUTs -Dmaven.javadoc.skip -Dit.test=com.stratio.qa.ATests.LoopTagAspectIT \
  -DFORCE_BROWSER=${CHROME_IP} \
  -DMONGO_HOST=${MONGO_IP} -DCASSANDRA_HOST=${CASS_IP} \
  -DES_NODE=${ELASTIC_IP} -DES_CLUSTER=elasticsearch \
  -DZOOKEEPER_HOSTS=${ZK_IP}:2181 -DSECURIZED_ZOOKEEPER=false \
  -DWAIT=1 -DAGENT_LIST=1,2 -DPROGLOOP=2 -DINCLUDE=1 \
  -DKAFKA_HOSTS=${KAFKA_IP}:9092 -DSSH=${UBUNTU_IP} -DSLEEPTEST=1 \
  -DLDAP_USER=admin -DLDAP_BASE=dc=stratio,dc=com -DLDAP_PASSWORD=stratio \
  -DLDAP_SSL=false -DLDAP_PORT=389 -DLDAP_URL=${LDAP_IP} \
  -DVAULT_URL=${VAULT_IP} -DVAULT_PROTOCOL=http:// -DVAULT_HOST=${VAULT_IP} -DVAULT_TOKEN=stratio \
  -DlogLevel=DEBUG

# Run by group
mvn clean verify -Dgroups=hol
```

Load-time weaving is injected via `maven-failsafe-plugin` argLine: `-javaagent:.../aspectjweaver-1.9.9.1.jar`.

## Running integration tests locally

ITs require 9 Docker services. Create a shared network first:

```bash
docker network create bdt-it
```

Start services (Elastic first — needs 600s warmup):

```bash
docker run -d --name bdt-elastic --network bdt-it -e "discovery.type=single-node" -e "opendistro_security.disabled=true" -m 2g amazon/opendistro-for-elasticsearch:1.13.2
docker run -d --name bdt-zookeeper --network bdt-it zookeeper:3.5.7
docker run -d --name bdt-mongodb --network bdt-it qa.int.stratio.com/stratio/mongo:3.0.4
docker run -d --name bdt-cassandra --network bdt-it -e MAX_HEAP=256M qa.int.stratio.com/stratio/cassandra-lucene-index:3.0.7.3
docker run -d --name bdt-ldap --network bdt-it -e LDAP_SCHEMA=rfc2307 -e "LDAP_HOSTNAME=bdt-ldap" -e "HOSTNAME=bdt-ldap.cd" -e "LDAP_ORGANISATION=Stratio" -e "LDAP_DOMAIN=stratio.com" -e "LDAP_ADMIN_PASSWORD=stratio" qa.int.stratio.com/stratio/ldap-docker:0.2.0
docker run -d --name bdt-chrome --network bdt-it --shm-size=2g -m 2g --dns 8.8.8.8 selenium/standalone-chrome-debug:3.141.59
docker run -d --name bdt-ubuntu --network bdt-it qa.int.stratio.com/stratio/ubuntu-base:16.04 /usr/sbin/sshd -D -e
docker run -d --name bdt-vault --network bdt-it -e VAULT_DEV_ROOT_TOKEN_ID=stratio -e "VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200" vault:0.6.2
```

Wait ~30s for ZK, then start Kafka (needs ZK running, and needs its own IP for ADVERTISED_HOST_NAME):

```bash
# Start Kafka once to get its assigned IP, then restart with correct advertised config
docker run -d --name bdt-kafka --network bdt-it confluentinc/cp-kafka:5.3.1
KAFKA_IP=$(docker inspect bdt-kafka --format '{{ (index .NetworkSettings.Networks "bdt-it").IPAddress }}')
docker rm -f bdt-kafka

docker run -d --name bdt-kafka --network bdt-it -m 1g \
  -e "KAFKA_ADVERTISED_HOST_NAME=${KAFKA_IP}" \
  -e "KAFKA_ZOOKEEPER_CONNECT=bdt-zookeeper:2181" \
  -e "KAFKA_DELETE_TOPIC_ENABLE=true" \
  -e "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://${KAFKA_IP}:9092" \
  -e "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT" \
  -e "KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092" \
  -e "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1" \
  -e "KAFKA_BROKER_ID=1" \
  confluentinc/cp-kafka:5.3.1
```

Wait for warmup (Elastic 600s, Kafka 300s from start). Then get IPs and run:

```bash
ELASTIC_IP=$(docker inspect bdt-elastic --format '{{ (index .NetworkSettings.Networks "bdt-it").IPAddress }}')
ZK_IP=$(docker inspect bdt-zookeeper --format '{{ (index .NetworkSettings.Networks "bdt-it").IPAddress }}')
MONGO_IP=$(docker inspect bdt-mongodb --format '{{ (index .NetworkSettings.Networks "bdt-it").IPAddress }}')
CASS_IP=$(docker inspect bdt-cassandra --format '{{ (index .NetworkSettings.Networks "bdt-it").IPAddress }}')
KAFKA_IP=$(docker inspect bdt-kafka --format '{{ (index .NetworkSettings.Networks "bdt-it").IPAddress }}')
LDAP_IP=$(docker inspect bdt-ldap --format '{{ (index .NetworkSettings.Networks "bdt-it").IPAddress }}')
CHROME_IP=$(docker inspect bdt-chrome --format '{{ (index .NetworkSettings.Networks "bdt-it").IPAddress }}')
UBUNTU_IP=$(docker inspect bdt-ubuntu --format '{{ (index .NetworkSettings.Networks "bdt-it").IPAddress }}')
VAULT_IP=$(docker inspect bdt-vault --format '{{ (index .NetworkSettings.Networks "bdt-it").IPAddress }}')

mvn clean verify -DskipUTs -Dmaven.javadoc.skip \
  -DFORCE_BROWSER=${CHROME_IP} \
  -DMONGO_HOST=${MONGO_IP} \
  -DCASSANDRA_HOST=${CASS_IP} \
  -DES_NODE=${ELASTIC_IP} \
  -DES_CLUSTER=elasticsearch \
  -DZOOKEEPER_HOSTS=${ZK_IP}:2181 \
  -DSECURIZED_ZOOKEEPER=false \
  -DWAIT=1 -DAGENT_LIST=1,2 -DPROGLOOP=2 \
  -DKAFKA_HOSTS=${KAFKA_IP}:9092 \
  -DSSH=${UBUNTU_IP} \
  -DSLEEPTEST=1 \
  -DLDAP_USER=admin -DLDAP_BASE=dc=stratio,dc=com -DLDAP_PASSWORD=stratio \
  -DLDAP_SSL=false -DLDAP_PORT=389 \
  -DVAULT_URL=${VAULT_IP} -DVAULT_PROTOCOL=http:// -DVAULT_HOST=${VAULT_IP} -DVAULT_TOKEN=stratio \
  -DLDAP_URL=${LDAP_IP} \
  -DINCLUDE=1 -DlogLevel=DEBUG
```

**Notes:**
- `stratio/*` images are hosted at `qa.int.stratio.com` — use the full path as shown above
- Kafka `ADVERTISED_HOST_NAME` must be Kafka's own container IP (use `bdt-kafka` inspect after start if IP changes)
- `FORCE_BROWSER` = Chrome container hostname/IP. Jenkins derives grid URL as `http://<FORCE_BROWSER>:4444/wd/hub`. NEVER add `-DSELENIUM_GRID` — it breaks `HookGSpec.seleniumSetup` split logic.
- Always use `mvn clean verify` (not just `verify`) to avoid stale class files from previous runs
- All tests should pass with 0 failures on a clean run
- **Kafka timeout errors after machine reboot**: `KafkaSecStepsIT` may fail with `TimeoutException: Topic not present in metadata` if Kafka+ZK were just restarted. The broker needs ~300s to elect leaders after a cold start. Fix: recreate ZK and Kafka containers (ZK first, wait 30s, then Kafka with the correct ZK IP), then wait 5min before running the ITs. `docker start` after reboot is NOT enough — Kafka stores the old ZK IP in its config; `docker rm -f bdt-kafka bdt-zookeeper` and recreate from scratch.
- **MongoDB lock file after machine reboot**: `MongoStepsIT`/`MongoToolsIT` fail with `MongoTimeoutException` if `bdt-mongodb` was not cleanly stopped before reboot — Mongo leaves a `mongod.lock` file that prevents restart. Fix: `docker rm -f bdt-mongodb` and recreate with the original `docker run` command. `docker start` will fail silently.

## Kubernetes tests (no cluster required)

`KubernetesClient` and `K8SSpec` are covered against an **in-JVM fabric8 mock API server**
(`io.fabric8:kubernetes-server-mock`, test scope). No cluster, no Docker, no network — which matters because CI
itself runs on Kubernetes and cannot start a nested one.

- **Base class**: `src/test/java/com/stratio/qa/clients/k8s/K8sMockServerTestBase.java`. It starts the mock server,
  writes a throwaway kubeconfig pointing at it, and calls the real `KubernetesClient.connect(path)`. The production
  singleton exposes no setter, so the kubeconfig **is** the injection point.
- **Fixtures**: `K8sFixtures` builds the seeded Kubernetes objects shared by all the test classes.
- **Two dispatcher modes**, chosen with `crudMode()`:
  - `true` (default) — CRUD only: an in-memory store where create/get/list/update/delete behave like a real API server.
  - `false` — mixed: `server.expect()` scripts win, everything else falls back to the same CRUD store. Needed for the
    subresources CRUD does not implement (`/log`, scale, rolling restart, `exec` websocket) and for error responses.
- **Gherkin layer**: `K8SMockClusterIT` + `src/test/resources/features/k8sMockCluster.feature` boot the same mock and
  publish its kubeconfig as `-DK8S_MOCK_KUBECONFIG`, so the feature connects with
  `I connect to Kubernetes cluster using config file located at '${K8S_MOCK_KUBECONFIG}'`. Do **not** tag it `@keos`,
  which would trigger the real workspace download in `HookGSpec.keosSetup`.

Run them with no infrastructure at all:

```bash
mvn clean test -DskipITs -Dtest='KubernetesClient*Test,K8SSpec*Test'
```

```bash
mvn clean verify -DskipUTs -Dmaven.javadoc.skip -Dit.test=com.stratio.qa.ATests.K8SMockClusterIT
```

Port forwarding *does* work against the mock: fabric8 binds the local socket eagerly and only opens the tunnel
when something connects, so seeding the service plus a matching pod is enough to set a forward up and tear it
down. That is what makes the Vault steps testable — they forward `vault:8200` before their try block. The port
is hardcoded in production code, so `K8SSpecVaultTest` skips itself when 8200 is already taken.

Coverage sits at ~76% for `KubernetesClient` and ~90% for `K8SSpec`. What is left is not reachable from a mock:

- **Keos workspace bootstrap** (~263 lines, 90% of what remains in `KubernetesClient`) — `getK8sConfigFromWorkspace`
  and its private helpers download the workspace over SSH/CCT and parse `keos.yaml`.
- **File transfer** — `copyFileToPod`/`copyFileFromPod` ride the exec subresource upload, which the mock does not
  implement. Only the failure paths are covered.
- **`execCommand` exit codes** — the mock cannot emit the channel-3 exit status that drives `onExit`.
- **`readSecretVault`'s body** — it delegates to `hasSecretVault`, which needs a real HTTPS Vault answering 200
  behind the forward. The `vault` container in `ITSERVICES` does not help: the step never reads `VAULT_URL`, it
  hardcodes `https://127.0.0.1:8200` behind a Kubernetes port forward.

Do not chase these with a mock. If they ever need coverage, it has to be a real cluster.

## Key files

| File | Size | Notes |
|---|---|---|
| `src/main/java/com/stratio/qa/specs/CommonG.java` | ~3000 lines | Central shared state, all clients |
| `src/main/java/com/stratio/qa/specs/GosecSpec.java` | ~3100 lines | Security/auth — DCOS and Keos paths |
| `src/main/java/com/stratio/qa/specs/CCTSpec.java` | ~3000 lines | Control-plane ops |
| `src/main/java/com/stratio/qa/specs/K8SSpec.java` | ~1300 lines | Kubernetes operations |
| `src/main/java/com/stratio/qa/specs/DatabaseSpec.java` | ~2000 lines | All BBDD engines |
| `src/main/java/com/stratio/qa/specs/HookGSpec.java` | — | Cucumber `@Before`/`@After` lifecycle, `@important` fast-fail logic |
| `src/main/java/com/stratio/qa/aspects/` | — | All 8 aspects |
| `src/main/java/com/stratio/qa/cucumber/` | — | Forked Cucumber 4.4.0 internals — handle with care |

## Test structure

`src/test/` tests the framework itself:
- `ATests/` — thin TestNG runners, each points Cucumber at one `.feature` file
- `resources/features/` — ~50 feature files covering all framework behaviors
- `resources/META-INF/aop.xml` — registers all 8 aspects for load-time weaving in test runs

## Lifecycle tags (HookGSpec)

| Tag | Effect |
|---|---|
| `@rest` | Sets up standard REST client (5s connect / 60s request / 120s read) |
| `@restWithLongerTimeout` | Sets up long-timeout REST client (30s connect / 600s request+read) |
| `@dcos` | Initializes DC/OS/Marathon/Mesos clients |
| `@keos` | Initializes Kubernetes client |
| `@important` | Aborts remaining scenarios in feature on failure |
| `@notimportant` | Overrides `-Dquietasdefault=false` per scenario |
| `@continueOnPreviousFeatureFailure` | Feature continues even if previous feature had `@important` failure |
| `@always` | Scenario runs even if previous `@important` failed |

## SonarQube

Dashboard: `https://sonar.int.stratio.com/dashboard?id=bdt`

The dashboard loads via JavaScript so WebFetch cannot read it. Use the REST API instead:

```bash
SONAR_TOKEN=$(cat ~/.config/sonar/token)
curl -s -u "${SONAR_TOKEN}:" "https://sonar.int.stratio.com/api/measures/component?component=bdt&metricKeys=coverage,bugs,vulnerabilities,code_smells,duplicated_lines_density,ncloc,alert_status,reliability_rating,security_rating,sqale_rating"
```

**Token setup** (user-specific, never committed):
```bash
mkdir -p ~/.config/sonar
echo "<token>" > ~/.config/sonar/token
chmod 600 ~/.config/sonar/token
```

If the user doesn't have a token yet: SonarQube → top-right avatar → My Account → Security → Generate Token → type **User Token**.

**JaCoCo / coverage:** coverage reports land at `target/site/jacoco/jacoco.xml` (UTs) and `target/site/jacoco-it/jacoco.xml` (ITs). Sonar picks them up automatically during `doStaticAnalysis` in the Jenkins pipeline. Coverage was 0% until PR #1167 fixed the missing JaCoCo plugin executions.

## qa-unified-tests skills

Skills from `qa-unified-tests` (platform ops, Cucumber results reader, etc.) can be loaded into this project automatically.

**How it works:** `.claude/scripts/load-qa-skills.sh` runs at every session start. It:
1. Checks `../qa-unified-tests` (convention: both repos at the same parent directory)
2. Falls back to `.claude/qa-path.local` (gitignored, user-specific)

If neither resolves, the hook outputs `QA_SKILLS_NOT_CONFIGURED`. **When you see that message, ask the user if they want to load the qa-unified-tests skills and where the repo is on disk.** Save the absolute path (one line, no quotes) to `.claude/qa-path.local`. Skills will be available from the next session onward.

**Manual setup:**
```bash
echo "/absolute/path/to/qa-unified-tests" > .claude/qa-path.local
```

**Permissions:** `.claude/settings.local.json` (gitignored) can be used to add skill-specific permissions. See `.claude/skills/<skill>/SKILL.md` for what each skill needs, or copy from `<qa-path>/.claude/settings.local.json.example`.

### After every IT run

After any IT run completes — whether a background task notification arrives or a synchronous run finishes — **always**:

1. Use the `cucumber-results-reader` skill (`--mode summary`) to parse results and report the summary.
2. If there are any failures, follow up immediately with `--mode failures` to analyze them: show the failing scenario, the exact step that failed, the error message, and your diagnosis of the root cause.
