# BDT — Claude Context

## What this is

**bdt** (`com.stratio.qa:bdt`) is a **published Maven library** — a Cucumber/TestNG test framework used as a base dependency across Stratio's acceptance test projects. This repo does **not** contain product tests; it contains the framework that other test projects consume.

## Architecture

### 3 layers

1. **Aspects (AOP)** — 8 AspectJ aspects that intercept Cucumber internals via load-time weaving. These are the core innovation.
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

## The 8 Aspects

| Aspect | Weave point | Purpose |
|---|---|---|
| `RunOnTagAspect` | `Runner.runPickle` (`@Before`) | `@runOnEnv` / `@skipOnEnv` with `&&`, `\|\|`, `=`, `>`, `<`, semver |
| `IgnoreTagAspect` | `Runner.runPickle` (`@Around`) | `@ignore @manual\|unimplemented\|toocomplex\|tillfixed(JIRA-XXX)` — calls Jira REST API for `tillfixed` |
| `LoopIncludeTagAspect` | `FeatureParser.read` (`@Around`) | Pre-processes feature text before Gherkin parses: `@loop`, `@multiloop`, `@include`, `@background` |
| `ReplacementAspect` | `Runner.runPickle` + step execution | Variable interpolation (`${}`/`!{}`/`@{}`) + `@error`/`@errorMessage` tags |
| `MandatoryAspect` | `Runner.runPickle` (`@Around`) | `@mandatory @vars(X,Y)` — aborts if env vars missing |
| `AssertJAspect` | `Failures.failure` | Downgrades AssertJ errors to WARN during polling loops |
| `SeleniumAspect` | Selenium method calls | Auto-captures HTML + screenshot on any Selenium failure |
| `BrowsersDataProviderAspect` | `BrowsersDataProvider.available*` | `FORCE_BROWSER` system property short-circuits browser matrix |

## Known architectural risks

### Forked Cucumber runtime
`com.stratio.qa.cucumber.*` is a fork of Cucumber 4.4.0 internals. Aspects weave into private internal methods (`Runner.runPickle`, `FeatureParser.read`). **Any Cucumber version upgrade is high-risk** — these weave points may not exist or may have changed signatures.

### CommonG god-object
`CommonG.java` (~3000 lines) holds all shared state. Tightly coupled, hard to unit-test in isolation. Resist adding more responsibilities to it.

### Static state in HookGSpec
`prevScenarioFailed` and `importantScenarioHasFail` are `static` fields — breaks under parallel scenario execution.

### Jira HTTP call in IgnoreTagAspect
`@tillfixed(JIRA-XXX)` makes a synchronous HTTP call to Jira REST API during test execution. If Jira is unreachable, the aspect logs an error and falls back to skipping the scenario.

## Build

```bash
# Run all integration tests
mvn verify

# Run specific test class
mvn verify -Dit.test=com.stratio.qa.ATests.LoopTagAspectIT

# Run by group
mvn verify -Dgroups=hol

# Pass env vars to tests
mvn verify -DVAR=value -Dit.test=...
```

Load-time weaving is injected via `maven-failsafe-plugin` argLine: `-javaagent:.../aspectjweaver-1.9.9.1.jar`.

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
