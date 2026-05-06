# BDT Cucumber 7 Upgrade — Technical Reference

**Branch:** `chore/cucumber-7-upgrade`  
**Ticket:** QATM-55212  
**Cucumber:** 6.11.0 → 7.34.3  
**Date:** 2026-05

---

## Why this upgrade

Cucumber 6.11.0 was the last 6.x release. Cucumber 7 dropped several deprecated APIs
(TypeRegistry system, `strict` mode) and tightened internal constructor signatures.
Goal: keep BDT on a supported Cucumber release and reduce the delta to upstream.

---

## Compile errors (starting state: 3 errors)

| Error | Cause | Fix |
|---|---|---|
| `TypeRegistryConfigurer` not found | Removed in v7 | Replace with `@ParameterType` method |
| `ScanningTypeRegistryConfigurerSupplier` not found | Removed in v7 | Drop from `CucumberRunner`; remove typeRegistry param from `ThreadLocalRunnerSupplier` |
| `CustomOptionsAdapter`/`StandardOptionsAdapter` incomplete | `uuidGenerator()` added to `CucumberOptionsAnnotationParser.CucumberOptions` interface | Implement `uuidGenerator() → null` in both adapters |
| `strict()` `@Override` fails | `strict()` removed from `CucumberOptions` interface | Remove the override from both adapters |
| `addDefaultSummaryPrinterIfAbsent()` not found | Renamed in v7 | → `addDefaultSummaryPrinterIfNotDisabled()` |
| `addDefaultFormatterIfAbsent()` not found | Removed in v7 | Drop the call |
| `ObjectFactoryServiceLoader(RuntimeOptions)` not found | Constructor signature changed | → `ObjectFactoryServiceLoader(Supplier<ClassLoader>, Options)` |

---

## What changed

### 1. TypeRegistry system removed

Cucumber 7 removed `TypeRegistryConfigurer` / `TypeRegistry` entirely. Custom parameter
types must now be registered via `@ParameterType` annotation on glue methods.

**Before (Cucumber 6):**
```java
public class TypeRegistryConfiguration implements TypeRegistryConfigurer {
    @Override public Locale locale() { return ENGLISH; }
    @Override public void configureTypeRegistry(TypeRegistry registry) {
        registry.defineParameterType(new ParameterType<>("isornot", "(IS|IS NOT)", Boolean.class, ...));
    }
}
```

**After (Cucumber 7):**
```java
public class TypeRegistryConfiguration {
    @ParameterType("IS|IS NOT")
    public Boolean isornot(String input) { return "IS".equals(input); }
}
```

`ScanningTypeRegistryConfigurerSupplier` (which scanned the classpath for
`TypeRegistryConfigurer` implementations) was removed along with it. The
`ThreadLocalRunnerSupplier` constructor dropped the typeRegistry parameter:

```java
// Cucumber 6 — 5 params
new ThreadLocalRunnerSupplier(runtimeOptions, bus, backendSupplier, objectFactory, typeRegistry);

// Cucumber 7 — 4 params (no typeRegistry)
new ThreadLocalRunnerSupplier(runtimeOptions, bus, backendSupplier, objectFactory);
```

### 2. strict mode removed

`strict()` was removed from the `CucumberOptionsAnnotationParser.CucumberOptions`
interface. In Cucumber 7, undefined/pending steps always cause the scenario to fail —
there is no "lenient" mode. Both `CustomOptionsAdapter` and `StandardOptionsAdapter`
had `@Override public boolean strict() { return true; }` which now causes a compile
error. Remove the override from both adapters.

### 3. ObjectFactoryServiceLoader constructor changed

```java
// Cucumber 6 — 1 param
new ObjectFactoryServiceLoader(runtimeOptions);

// Cucumber 7 — 2 params
new ObjectFactoryServiceLoader(() -> classLoader, runtimeOptions);
```

The second `Options` parameter is `io.cucumber.core.backend.Options`. `RuntimeOptions`
implements it.

### 4. CucumberOptions interface: uuidGenerator() added

Cucumber 7 added UUID generator support for event bus IDs. The
`CucumberOptionsAnnotationParser.CucumberOptions` interface gained an abstract method:

```java
Class<? extends UuidGenerator> uuidGenerator();
```

Both adapters must implement it. Return `null` to use the default (`UUID::randomUUID`
from `CucumberRunner`).

### 5. RuntimeOptionsBuilder API changes

| Cucumber 6 | Cucumber 7 |
|---|---|
| `addDefaultSummaryPrinterIfAbsent()` | `addDefaultSummaryPrinterIfNotDisabled()` |
| `addDefaultFormatterIfAbsent()` | Removed — default formatter handled differently |

### 6. JsonFormatter — ConcurrentEventListener + message-based output

In Cucumber 6, `JSONFormatter` implemented `EventListener`. In Cucumber 7 it was
rewritten as `JsonFormatter` (note: lowercase 'f') implementing `ConcurrentEventListener`
and using a completely different output model (`MessagesToJsonWriter`).

**Three changes required in `CucumberRunner`:**

#### 6a. Plugin registration method

`setSerialEventBusOnEventListenerPlugins(bus)` only registers `EventListener` plugins. It
silently skips all `ConcurrentEventListener` plugins — including `JsonFormatter` and
`DefaultSummaryPrinter` (both `ConcurrentEventListener` in v7). Result: formatter never
receives events, JSON files remain 0 bytes.

Fix: replace with `setEventBusOnEventListenerPlugins(bus)`, which handles both types.

```java
// Cucumber 6
plugins.setSerialEventBusOnEventListenerPlugins(bus);

// Cucumber 7
plugins.setEventBusOnEventListenerPlugins(bus);
```

#### 6b. Feature parse events (Source / GherkinDocument / Pickle envelopes)

In Cucumber 7, `JsonFormatter` stores data via a `Repository` that maps pickle IDs to
their definitions. The repository is populated by `Envelope` events carrying `Source`,
`GherkinDocument`, and `Pickle` messages — which in the standard Cucumber runtime are
emitted by `CucumberExecutionContext.runFeature()` via `feature.getParseEvents()`.

Our custom `CucumberRunner` only sent the old-style `TestSourceRead` event. Without the
`Pickle` envelope, the repository throws `No Pickle for testCaseStarted <id>` when the
first test case runs.

Fix: send `feature.getParseEvents()` on the bus immediately after `TestSourceRead`:

```java
for (Feature feature : features) {
    bus.send(new TestSourceRead(bus.getInstant(), feature.getUri(), feature.getSource()));
    bus.sendAll(feature.getParseEvents());   // Source + GherkinDocument + Pickle envelopes
}
```

#### 6c. Flushing the writer (TestRunFinished messages envelope)

`MessagesToJsonWriter.write(Envelope)` buffers everything in a `Query` object — it does
**not** write to disk incrementally. The actual JSON serialization happens only in
`MessagesToJsonWriter.close()`, which is triggered when `JsonFormatter.write()` receives
an `Envelope` whose `getTestRunFinished().isPresent()` returns true.

This `Envelope` must wrap `io.cucumber.messages.types.TestRunFinished` — NOT the old
`io.cucumber.plugin.event.TestRunFinished` that `CucumberRunner.finish()` was already
sending. Without this envelope, JSON output files are created but always 0 bytes.

Fix: send the messages-style envelope at the end of `finish()`:

```java
public void finish() {
    if (currentFeatureUri != null) {
        runnerSupplier.get().runAfterAllHooks();
    }
    // Old-style event (still needed for EventListener plugins / hooks)
    bus.send(new TestRunFinished(bus.getInstant(), new Result(Status.PASSED, Duration.ZERO, null)));
    // Messages-style envelope — triggers MessagesToJsonWriter.close() → flushes JSON to disk
    io.cucumber.messages.types.TestRunFinished msgFinished =
            new io.cucumber.messages.types.TestRunFinished(
                    null, true,
                    new Timestamp(bus.getInstant().getEpochSecond(),
                                  (long) bus.getInstant().getNano()),
                    null, null);
    bus.send(Envelope.of(msgFinished));
}
```

**Root cause summary:** `MessagesToJsonWriter` is a query-based accumulator — it collects
all `Envelope` events into a `Query` object and builds the JSON report from it on
`close()`. If `close()` is never called (missing messages-style `TestRunFinished`
envelope), the output file remains 0 bytes even though all tests passed.

---

## Weave points — status in Cucumber 7

All AspectJ weave points from Cucumber 6 continue to work in Cucumber 7 without
changes. No aspect files required modification.

| Aspect | Weave point | Status in v7 |
|---|---|---|
| `RunOnTagAspect` / `IgnoreTagAspect` / `MandatoryAspect` | `io.cucumber.core.runner.Runner.runPickle(..)` | ✅ Unchanged |
| `ReplacementAspect` | `Runner.runPickle`, `Runner.matchStepToStepDefinition`, `JavaStepDefinition.execute(Object[])`, `PickleStepTestStep.run`, `UndefinedPickleStepDefinitionMatch.runStep` | ✅ All present and unchanged |
| `AssertJAspect` | `org.assertj.core.internal.Failures.failure` | ✅ Unchanged |
| `SeleniumAspect` | Selenium method calls | ✅ Unchanged |
| `BrowsersDataProviderAspect` | `BrowsersDataProvider.available*` | ✅ Unchanged |

These weave points remain **high-risk** on any future Cucumber upgrade. The reflection
calls in `ReplacementAspect.aroundUndefinedStepRun` (`matchStepToStepDefinition` and
`runStep` via `getDeclaredMethod`) are especially fragile — verify them first on any
version bump.

---

## IT results

**0 compile errors** after fixes. **0 new IT failures** introduced.

| State | Tests | Failures |
|---|---|---|
| Starting (Cucumber 7 bump, before fixes) | — | 3 compile errors |
| After fixes | 306 | 0 |

---

## Fork classes — no changes required

All fork runner classes compiled without modification beyond the fixes above:

| Class | Status |
|---|---|
| `CucumberRunner` | Modified (see above) |
| `RuntimeOptionsFactoryCustom` | Modified (see above) |
| `TypeRegistryConfiguration` | Rewritten (see above) |
| `FeaturePathFeatureSupplier` | No changes |
| `TestCaseResultListener` | No changes |
| `TestSourcesModelUtil` | No changes |
| `BaseGTest` / `PickleEventWrapper` / `CucumberFeatureWrapper` | No changes |

---

## Dependency changes

```xml
<!-- Before -->
<cucumber-java.version>6.11.0</cucumber-java.version>

<!-- After -->
<cucumber-java.version>7.34.3</cucumber-java.version>
```

No transitive dependency changes required.

---

## How to verify

```bash
# Unit tests (no infra)
mvn clean test -DskipITs

# Full IT suite (requires Docker services — see CLAUDE.md)
mvn clean verify -DskipUTs -Dmaven.javadoc.skip [... vars ...]
```

Expected: **306 run, 0 failures**.
