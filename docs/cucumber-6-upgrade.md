# BDT Cucumber 6 Upgrade — Technical Reference

**Branch:** `chore/cucumber-6-upgrade`  
**Ticket:** QATM-55212  
**Cucumber:** 4.4.0 → 6.11.0  
**Date:** 2026-05

---

## Why this upgrade

Cucumber 4.4.0 is EOL. The BDT framework shipped a fork of Cucumber 4 internals
(`com.stratio.qa.cucumber.*`) to support custom features (loop tags, variable
replacement, feature ordering). The fork created a maintenance burden and blocked
dependency upgrades in downstream projects.

Goal: eliminate as much of the fork as possible, upgrade to Cucumber 6.11.0 (the
last 6.x release before the major API overhaul in 7+), and keep all existing
behaviour intact.

---

## What was deleted from the fork

These classes existed solely to patch Cucumber 4 internals and were deleted
in the upgrade:

| Deleted class | Replacement |
|---|---|
| `cucumber.runner.Glue` | `io.cucumber.core.runtime.BackendServiceLoader` (internal) |
| `cucumber.runner.Match` | Not needed |
| `cucumber.runner.HookComparator` | Not needed |
| `cucumber.runner.AmbiguousStepDefinitionsException` | `io.cucumber.core.runner` equivalent |
| `cucumber.runner.PickleStepDefinitionMatch` | `io.cucumber.core.runner` equivalent |
| `cucumber.runtime.RuntimeOptions` | `CucumberOptionsAnnotationParser` + `StratioOptionsProvider` |
| `cucumber.runtime.FeatureSupplier` | `FeaturePathFeatureSupplier` (rewritten) |
| `cucumber.runtime.model.FeatureBuilder` | Deleted |
| `cucumber.runtime.model.FeatureLoader` | Deleted |
| `cucumber.runtime.model.FeatureParser` | Deleted (preprocessing moved inline) |

---

## Key architectural changes

### 1. RuntimeOptionsFactoryCustom

Rewrote to use Cucumber 6's `CucumberOptionsAnnotationParser` +
`StratioOptionsProvider`. A `cucumber.api.CucumberOptions` shim annotation was
added so all 41 IT runner classes needed zero changes (they still use the old
import). The shim's `tags()` returns `String` (not `String[]` as in v4).

### 2. FeaturePathFeatureSupplier

Replaced the deleted `FeatureLoader`/`FeatureParser`/`FeatureBuilder` stack.
Uses Cucumber 6's `ResourceScanner<Feature>` with a preprocessing lambda that
runs `LoopIncludeTagAspect` (`@include`, `@background` expansion) before
Gherkin parsing. Preserves `FEATURES_EXECUTION_ORDER` and `DUPLICATED_FEATURES`
system properties.

### 3. LoopConverter

Rewritten from scratch. Processes `@loop`, `@progloop`, `@multiloop` tags after
Gherkin parsing by transforming `Pickle` objects:

- `transformPickleEventWithLoopTags(Pickle)` → `List<Pickle>`
- Wraps steps/args via `WrappedPickle` / `WrappedStep` / `WrappedDataTableArgument` / `WrappedDocStringArgument`
- **Bug fixed**: `@multiloop` row-major iteration order was wrong — outer loop
  must be the first column (commit `d459b835`)
- **Bug fixed**: feature annotation order and duplicates were not preserved after
  loop expansion (commit `bf14b9ba`)

### 4. CucumberRunner

Rewrote to use Cucumber 6 APIs:
`Plugins`, `Filters`, `ThreadLocalRunnerSupplier`, `Reflections` for
`ConcurrentEventListener`. `runScenario` uses `LoopConverter` +
`TestCaseResultListener`. `strict=false` (method removed in v6).

### 5. TestCaseResultListener

Rewrote as `EventPublisher`-based lambda handler. `finishExecutionUnit` removes
the handler to avoid memory leaks.

### 6. Import migration

All `cucumber.api.*` → `io.cucumber.java.*`, all `gherkin.*` →
`io.cucumber.core.gherkin.*` across main and test sources.

---

## ReplacementAspect — Cucumber 6 specific fixes

This aspect does variable interpolation (`${ENV}`, `!{VAR}`, `@{JSON/FILE/IP}`).
Cucumber 6 changed the execution model: **all steps are matched before any
execute** (match-all-then-run-all). In Cucumber 4, match and execute were
interleaved per step.

### Problem: !{VAR} set by step N unavailable when step N+1 is matched

In Cucumber 4, step N+1 was matched immediately before execution, so
`ThreadProperty` values written by step N were already available at match time.

In Cucumber 6, ALL steps are matched first. `!{VAR}` is only available at
execute time.

**Fix** (commit `b4b19eee`): `aroundReplacementMatch` defers `!{VAR}` replacement.
New pointcut `aroundReplacementExecute` on `JavaStepDefinition.execute(Object[])`
resolves `!{VAR}`, `@{*}`, and `${ENV}` in String args at execute time.

### Problem: !{VAR} with newline content breaks shell pipes

`ThreadProperty` values may be multi-line (e.g. `strings <keytab> | grep -vE`
output). When injected into a shell command at execute time, the shell treats
newlines as command separators, so the pipe only applies to the last line.

In Cucumber 4, `ReplacementAspect` normalized newlines to `\n` literals before
re-matching (line 202 of the Cucumber 4 version).

**Fix** (commit `d2a940c7`): `aroundReplacementExecute` applies
`replaceAll("[\\r\\n]+", "\\\\n")` after resolving String args.

### Problem: @{JSON/FILE} in DataTable resolved at match time before file exists

`@{JSON.somefile.json}` in a DataTable cell was resolved at match time via
`replaceCodePlaceholders`. If `somefile.json` is created by a prior step in the
same scenario, `CommonG.retrieveData` returns `"ERR! File not found: ..."` (does
not throw). This string is stored as the cell value. At execute time the cell no
longer contains `@{`, so it passes through unreplaced. The step receives
`"ERR! File not found: ..."` and fails with a JSON parse error.

In Cucumber 4, DataTable cells were resolved inside `executeStep` (execute-time
inherently).

**Fix** (commit `d2a940c7`): removed `replaceCodePlaceholders` from the DataTable
and DocString branches of `aroundReplacementMatch`. `aroundReplacementExecute`
already handles `@{*}` in DataTable cells at execute time.

### Problem: !{VAR} in step text unresolvable at match time → UndefinedStep

If a step text contains `!{VAR}` that can only be resolved after prior steps run,
Cucumber 6 classifies it as `UndefinedPickleStepDefinitionMatch` during the
match-all phase (because the pattern doesn't match the unresolved literal).

**Fix** (commit `d3ece034`): new pointcut `aroundUndefinedStepRun` on
`UndefinedPickleStepDefinitionMatch.runStep`. At execute time, resolves `!{VAR}`
and re-invokes `matchStepToStepDefinition` via reflection. If a match is found,
runs the step normally.

### Problem: duplicate glue registration

Cucumber 6 loads step definitions via `ServiceLoader` + classpath scan. In some
configurations both paths fired, registering each step definition twice →
`AmbiguousStepDefinition` errors on every step.

**Fix** (commit `6d52f1c3`): guard in `CucumberRunner` prevents double
registration.

---

## IT failure analysis (starting state: 82 failures)

Snapshot commit `323f1e9f` captured the state before fixes with 82 IT failures,
categorized into 4 root causes:

| Root cause | Approx. failures | Fix commit |
|---|---|---|
| Duplicate step definition registration | ~47 | `6d52f1c3` |
| `!{VAR}` not resolved at execute time | ~20 | `b4b19eee`, `f3bcf2e9`, `d3ece034` |
| `@multiloop` row-major iteration order | 4 | `d459b835` |
| Feature annotation order / duplicates | 2 | `bf14b9ba` |
| `@{JSON/FILE}` DataTable match-time resolution | 1 | `d2a940c7` |
| `!{VAR}` newlines in shell commands | 2 | `d2a940c7` |

**Final state: 0 failures** across all IT classes (Kafka excluded — see below).

---

## Known pre-existing issue: KafkaSecStepsIT after cold start

`KafkaSecStepsIT` fails with `TimeoutException: Topic not present in metadata`
if `bdt-kafka` + `bdt-zookeeper` containers are restarted without recreation.
Cause: Kafka stores the old ZK IP at startup; `docker start` after reboot uses
stale config.

Fix: recreate both containers (see CLAUDE.md `Running integration tests locally`
notes). Requires ~300s warmup after recreation. This is **not** a regression from
the Cucumber 6 upgrade — the failure reproduces identically on master.

---

## Remaining fork (not deleted in this upgrade)

These classes remain in `com.stratio.qa.cucumber.*` and still intercept Cucumber
internals:

| Class | Why kept |
|---|---|
| `CucumberRunner` | Orchestrates `LoopConverter` + custom runner lifecycle |
| `FeaturePathFeatureSupplier` | `@include`/`@background` preprocessing + feature ordering |
| `RuntimeOptionsFactoryCustom` | `@CucumberOptionsCustom` + shim support |
| `TestCaseResultListener` | Per-scenario result reporting to TestNG |
| `TestSourcesModelUtil` | Step text logging (replaced text display) |
| `BaseGTest` / `PickleEventWrapper` / `CucumberFeatureWrapper` | TestNG integration |

The aspects (`ReplacementAspect`, `RunOnTagAspect`, etc.) weave into Cucumber 6
internal methods. Weave points to watch on future upgrades:

| Aspect | Weave point | Risk on upgrade |
|---|---|---|
| `ReplacementAspect` | `Runner.runPickle`, `Runner.matchStepToStepDefinition`, `JavaStepDefinition.execute`, `PickleStepTestStep.run`, `UndefinedPickleStepDefinitionMatch.runStep` | HIGH — internal methods |
| `RunOnTagAspect` / `IgnoreTagAspect` / `MandatoryAspect` | `Runner.runPickle` | MEDIUM |
| `AssertJAspect` | `Failures.failure` | LOW |
| `SeleniumAspect` | Selenium method calls | LOW |

---

## Dependency changes

```xml
<!-- Before -->
<cucumber.version>4.4.0</cucumber.version>

<!-- After -->
<cucumber.version>6.11.0</cucumber.version>
```

Direct `gherkin` dependency removed (pulled transitively by Cucumber 6).

---

## How to verify

```bash
# Unit tests (no infra)
mvn clean test -DskipITs

# Full IT suite (requires Docker services — see CLAUDE.md)
mvn clean verify -DskipUTs -Dmaven.javadoc.skip [... vars ...]
```

Expected: **0 failures**.
