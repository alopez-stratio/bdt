/*
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// STRATIO-FORK: Stratio-written TestNG runner (no direct upstream equivalent in Cucumber 6).
// Changes vs standard Cucumber TestNG runner: (1) injects 44 Stratio spec glue paths;
//         (2) applies LoopConverter.transformPickleEventWithLoopTags() for @loop/@progloop/@multiloop;
//         (3) integrates RuntimeOptionsFactoryCustom for extraGlue and conditional feature support;
//         (4) uses custom FeaturePathFeatureSupplier for @include/@background preprocessing and
//             PRESERVE_ORDER / ALLOW_DUPLICATED system properties.

package com.stratio.qa.cucumber.testng;

import com.stratio.qa.cucumber.converter.LoopConverter;
import com.stratio.qa.cucumber.runtime.FeaturePathFeatureSupplier;
import com.stratio.qa.cucumber.runtime.RuntimeOptionsFactoryCustom;
import io.cucumber.core.eventbus.EventBus;
import io.cucumber.core.feature.Options;
import io.cucumber.core.filter.Filters;
import io.cucumber.core.gherkin.Feature;
import io.cucumber.core.gherkin.Pickle;
import io.cucumber.core.gherkin.Step;
import io.cucumber.core.gherkin.StepType;
import io.cucumber.core.options.CucumberProperties;
import io.cucumber.core.options.CucumberPropertiesParser;
import io.cucumber.core.options.RuntimeOptions;
import io.cucumber.core.options.RuntimeOptionsBuilder;
import io.cucumber.core.plugin.PluginFactory;
import io.cucumber.core.plugin.Plugins;
import io.cucumber.core.runner.Runner;
import io.cucumber.core.runtime.BackendServiceLoader;
import io.cucumber.core.runtime.ObjectFactoryServiceLoader;
import io.cucumber.core.runtime.ThreadLocalObjectFactorySupplier;
import io.cucumber.core.runtime.ThreadLocalRunnerSupplier;
import io.cucumber.core.runtime.TimeServiceEventBus;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Timestamp;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import io.cucumber.plugin.event.TestSourceRead;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cucumber.core.feature.FeaturePath;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class CucumberRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CucumberRunner.class);

    private final String runnerClassName;

    private final RuntimeOptions runtimeOptions;

    private final TimeServiceEventBus bus;

    private final Plugins plugins;

    private final Filters filters;

    private final ThreadLocalRunnerSupplier runnerSupplier;

    private final ClassLoader classLoader;

    private final List<URI> originalFeaturePaths;

    private final Map<String, io.cucumber.messages.types.Pickle> originalPickleMessages = new HashMap<>();

    private URI currentFeatureUri = null;

    public CucumberRunner(Class<?> clazz) throws Exception {
        this.runnerClassName = clazz.getName();
        this.classLoader = clazz.getClassLoader();
        this.originalFeaturePaths = extractAnnotationFeaturePaths(clazz);

        RuntimeOptionsBuilder annotationBuilder = new RuntimeOptionsFactoryCustom(clazz).buildOptions();
        addStratioGlue(annotationBuilder, clazz);
        RuntimeOptions annotationOptions = annotationBuilder
                .addDefaultSummaryPrinterIfNotDisabled()
                .build();
        // Apply Cucumber 7 system properties on top (cucumber.features, cucumber.filter.tags, etc.)
        this.runtimeOptions = new CucumberPropertiesParser()
                .parse(CucumberProperties.create())
                .build(annotationOptions);

        this.bus = new TimeServiceEventBus(Clock.systemUTC(), UUID::randomUUID);
        this.plugins = new Plugins(new PluginFactory(), runtimeOptions);

        Set<Class<? extends ConcurrentEventListener>> listenerTypes =
                new Reflections("com.stratio.qa.utils").getSubTypesOf(ConcurrentEventListener.class);
        for (Class<? extends ConcurrentEventListener> type : listenerTypes) {
            try {
                Constructor<?> ctor = type.getConstructor();
                ctor.setAccessible(true);
                plugins.addPlugin((ConcurrentEventListener) ctor.newInstance());
            } catch (Exception ignored) {
            }
        }

        this.filters = new Filters(runtimeOptions);

        ObjectFactoryServiceLoader objectFactoryLoader = new ObjectFactoryServiceLoader(() -> classLoader, runtimeOptions);
        ThreadLocalObjectFactorySupplier objectFactory = new ThreadLocalObjectFactorySupplier(objectFactoryLoader);
        BackendServiceLoader backendSupplier = new BackendServiceLoader(() -> classLoader, objectFactory);

        this.runnerSupplier = new ThreadLocalRunnerSupplier(runtimeOptions, bus, backendSupplier, objectFactory);
    }

    public Object[][] provideScenarios() {
        try {
            plugins.setEventBusOnEventListenerPlugins(bus);

            List<URI> pathsForSupplier;
            if (!originalFeaturePaths.isEmpty()) {
                pathsForSupplier = originalFeaturePaths;
            } else if (CucumberProperties.fromSystemProperties().containsKey("cucumber.features")) {
                // Parse directly from the system property — runtimeOptions.getFeaturePaths()
                // calls .sorted() internally and would destroy the user-specified order.
                pathsForSupplier = Arrays.stream(System.getProperty("cucumber.features").split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(FeaturePath::parse)
                        .collect(Collectors.toList());
            } else {
                pathsForSupplier = null;
            }
            FeaturePathFeatureSupplier featureSupplier = new FeaturePathFeatureSupplier(
                () -> classLoader, runtimeOptions, UUID::randomUUID, pathsForSupplier);

            List<Feature> features = featureSupplier.get();
            bus.send(new TestRunStarted(bus.getInstant()));
            for (Feature feature : features) {
                bus.send(new TestSourceRead(bus.getInstant(), feature.getUri(), feature.getSource()));
                Iterable<?> parseEvents = feature.getParseEvents();
                bus.sendAll(parseEvents);
                for (Object event : parseEvents) {
                    if (event instanceof Envelope) {
                        ((Envelope) event).getPickle().ifPresent(
                            p -> originalPickleMessages.put(p.getId(), p));
                    }
                }
            }

            List<Object[]> scenarios = new ArrayList<>();
            for (Feature feature : features) {
                for (Pickle pickle : feature.getPickles()) {
                    if (filters.test(pickle)) {
                        scenarios.add(new Object[]{
                            new PickleEventWrapperImpl(pickle),
                            new CucumberFeatureWrapperImpl(feature)
                        });
                    }
                }
            }
            return scenarios.toArray(new Object[0][0]);
        } catch (RuntimeException e) {
            // Not just CucumberException: Gherkin parse errors are FeatureParserException, which
            // extends RuntimeException directly. Anything that escapes a TestNG data provider is
            // reported as a skip, so a malformed feature would leave the build green.
            LOGGER.error("Could not load the features of {}", runnerClassName, e);
            return new Object[][]{{new CucumberExceptionWrapper(e), null}};
        }
    }

    public void runScenario(Pickle pickle) throws Throwable {
        Runner runner = runnerSupplier.get();
        URI pickleUri = pickle.getUri();
        if (!pickleUri.equals(currentFeatureUri)) {
            if (currentFeatureUri != null) {
                runner.runAfterAllHooks();
            }
            runner.runBeforeAllHooks();
            currentFeatureUri = pickleUri;
        }
        List<Pickle> expanded = LoopConverter.transformPickleEventWithLoopTags(pickle);

        Throwable lastError = null;
        for (Pickle p : expanded) {
            if (!p.getId().equals(pickle.getId())) {
                sendPickleEnvelope(p);
            }
            try {
                TestCaseResultListener listener = new TestCaseResultListener(runner.getBus(), false);
                runner.runPickle(p);
                listener.finishExecutionUnit();
                if (!listener.isPassed()) {
                    Throwable error = listener.getError();
                    if (error != null) {
                        throw error;
                    }
                }
            } catch (Throwable t) {
                lastError = t;
            } finally {
                sendReplacedPickleEnvelope(p);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }

    /**
     * Re-emits the Pickle message with the scenario name and the step texts that
     * ReplacementAspect resolved while the scenario ran.
     * <p>
     * The aspect wraps the runtime pickle, which fixes logs and TestCase.getName(),
     * but message-based formatters (json, html) take both the scenario name and the
     * step names from the Pickle message emitted when the feature was parsed.
     * <p>
     * Step texts must be re-emitted as the text the match was computed against —
     * ${ENV} resolved, !{VAR}/@{CODE} untouched. The match arguments carry offsets
     * into that text, and renderers use them to index the step name: if the two come
     * from different strings the offsets fall outside the name. net.masterthought
     * cucumber-reporting then does chars[offset + val.length() - 1] with no bounds
     * check, throws inside a Velocity #set and prints the raw template reference
     * "$_noescape_stepName" instead of the step (QATM-55258).
     * <p>
     * io.cucumber.query.Repository keys pickles by id, so re-sending the same id
     * overwrites the entry, and the report is only rendered once TestRunFinished
     * arrives.
     *
     * @param pickle pickle that has just been run
     */
    private void sendReplacedPickleEnvelope(Pickle pickle) {
        io.cucumber.messages.types.Pickle msgPickle = originalPickleMessages.get(pickle.getId());
        if (msgPickle == null) {
            return;
        }
        TestSourcesModel model = TestSourcesModelUtil.INSTANCE.getTestSourcesModel();

        String replacedName = model.getReplacedScenarioName(pickle.getId());
        String newName = replacedName != null ? replacedName : msgPickle.getName();

        boolean stepChanged = false;
        List<io.cucumber.messages.types.PickleStep> newSteps = new ArrayList<>();
        for (io.cucumber.messages.types.PickleStep msgStep : msgPickle.getSteps()) {
            String replacedText = model.getReplacedStepText(msgStep.getId());
            if (replacedText == null || replacedText.equals(msgStep.getText())) {
                newSteps.add(msgStep);
            } else {
                newSteps.add(new io.cucumber.messages.types.PickleStep(
                        msgStep.getArgument().orElse(null),
                        msgStep.getAstNodeIds(),
                        msgStep.getId(),
                        msgStep.getType().orElse(null),
                        replacedText));
                stepChanged = true;
            }
        }

        if (!stepChanged && newName.equals(msgPickle.getName())) {
            return;
        }
        bus.send(Envelope.of(new io.cucumber.messages.types.Pickle(
                msgPickle.getId(),
                msgPickle.getUri(),
                newName,
                msgPickle.getLanguage(),
                newSteps,
                msgPickle.getTags(),
                msgPickle.getAstNodeIds())));
    }

    private void sendPickleEnvelope(Pickle pickle) {
        String originalPickleId = LoopConverter.getDelegatePickleId(pickle);
        io.cucumber.messages.types.Pickle originalMsg = originalPickleMessages.get(originalPickleId);

        Map<String, List<String>> originalStepAstIds = new HashMap<>();
        List<String> pickleAstNodeIds = Collections.emptyList();
        if (originalMsg != null) {
            pickleAstNodeIds = originalMsg.getAstNodeIds();
            for (io.cucumber.messages.types.PickleStep ps : originalMsg.getSteps()) {
                originalStepAstIds.put(ps.getId(), ps.getAstNodeIds());
            }
        }

        List<io.cucumber.messages.types.PickleStep> msgSteps = new ArrayList<>();
        for (Step step : pickle.getSteps()) {
            io.cucumber.messages.types.PickleStepType type;
            switch (step.getType()) {
                case GIVEN:
                    type = io.cucumber.messages.types.PickleStepType.CONTEXT;
                    break;
                case WHEN:
                    type = io.cucumber.messages.types.PickleStepType.ACTION;
                    break;
                case THEN:
                    type = io.cucumber.messages.types.PickleStepType.OUTCOME;
                    break;
                default:
                    type = io.cucumber.messages.types.PickleStepType.UNKNOWN;
                    break;
            }
            String origStepId = LoopConverter.getDelegateStepId(step);
            List<String> astNodeIds = originalStepAstIds.getOrDefault(
                    origStepId, Collections.emptyList());
            msgSteps.add(new io.cucumber.messages.types.PickleStep(
                    null, astNodeIds, step.getId(), type, step.getText()));
        }
        List<io.cucumber.messages.types.PickleTag> msgTags = new ArrayList<>();
        for (String tag : pickle.getTags()) {
            msgTags.add(new io.cucumber.messages.types.PickleTag(tag, ""));
        }
        io.cucumber.messages.types.Pickle msgPickle = new io.cucumber.messages.types.Pickle(
                pickle.getId(),
                pickle.getUri().toString(),
                pickle.getName(),
                pickle.getLanguage(),
                msgSteps,
                msgTags,
                pickleAstNodeIds);
        // Cache it under its own id so sendReplacedScenarioNameEnvelope can rebuild
        // from it: @loop-expanded pickles get a fresh id that is not in the parse events.
        originalPickleMessages.put(msgPickle.getId(), msgPickle);
        bus.send(Envelope.of(msgPickle));
    }

    public void finish() {
        if (currentFeatureUri != null) {
            runnerSupplier.get().runAfterAllHooks();
        }
        bus.send(new TestRunFinished(bus.getInstant(), new Result(Status.PASSED, Duration.ZERO, null)));
        // Flush Cucumber 7 message-based formatters (e.g. JsonFormatter uses MessagesToJsonWriter
        // which buffers all events and only writes to disk when it receives this Envelope).
        io.cucumber.messages.types.TestRunFinished msgFinished =
                new io.cucumber.messages.types.TestRunFinished(
                        null, true,
                        new Timestamp(bus.getInstant().getEpochSecond(),
                                      (long) bus.getInstant().getNano()),
                        null, null);
        bus.send(Envelope.of(msgFinished));
    }

    private static void addStratioGlue(RuntimeOptionsBuilder builder, Class<?> runnerClass) throws Exception {
        // CucumberOptionsAnnotationParser auto-adds the runner class's package as default glue
        // when glue() returns an empty array. Compute that path so we can skip it below and
        // avoid DuplicateStepDefinitionException from scanning the same package twice.
        Package pkg = runnerClass.getPackage();
        String runnerPkgPath = pkg != null ? pkg.getName().replace('.', '/') : "";

        List<URI> glue = new ArrayList<>();
        glue.add(new URI("classpath:com/stratio/cct/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/qa/specs"));
        glue.add(new URI("classpath:com/stratio/sparta/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/gosecsso/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/gosecmanagement/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/dcos/crossdata/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/cct/configuration/api/specs"));
        glue.add(new URI("classpath:com/stratio/crossdata/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/streaming/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/ingestion/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/datavis/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/connectors/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/admin/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/explorer/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/manager/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/viewer/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/decision/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/paas/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/cassandra/lucene/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/analytic/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/exhibitor/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/intelligence/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/postgresbd/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/postgresql/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/zookeeper/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/universe/testsAT/specs"));
        glue.add(new URI("classpath:com/stratio/paas/dgDatadictionaryAT/specs"));
        glue.add(new URI("classpath:com/stratio/paas/dgtests/specs"));
        glue.add(new URI("classpath:com/stratio/elastic/specs"));
        glue.add(new URI("classpath:com/stratio/kafka/specs"));
        glue.add(new URI("classpath:com/stratio/hdfs/specs"));
        glue.add(new URI("classpath:com/stratio/kibana/specs"));
        glue.add(new URI("classpath:com/stratio/cassandra/specs"));
        glue.add(new URI("classpath:com/stratio/schema_registry/specs"));
        glue.add(new URI("classpath:com/stratio/rest_proxy/specs"));
        glue.add(new URI("classpath:com/stratio/spark/tests/specs"));
        glue.add(new URI("classpath:com/stratio/schema/discovery/specs"));
        glue.add(new URI("classpath:com/stratio/pgbouncer/specs"));
        glue.add(new URI("classpath:com/stratio/ignite/specs"));
        glue.add(new URI("classpath:com/stratio/qa/cucumber/converter"));
        glue.add(new URI("classpath:com/stratio/logs_solution/specs"));
        glue.add(new URI("classpath:com/stratio/marathonlbsec/specs"));
        glue.add(new URI("classpath:com/stratio/grafana/specs"));
        for (URI g : glue) {
            // Skip if this URI corresponds to the runner class's package: the annotation parser
            // already added it as the default glue (classpath:/<pkg>). Adding it again causes
            // DuplicateStepDefinitionException in Cucumber 6.
            String ssp = g.getSchemeSpecificPart().replaceFirst("^/", "");
            if (!runnerPkgPath.isEmpty() && ssp.equals(runnerPkgPath)) {
                continue;
            }
            builder.addGlue(g);
        }
    }

    private static List<URI> extractAnnotationFeaturePaths(Class<?> clazz) {
        // When cucumber.features is set, defer to CucumberPropertiesParser in runtimeOptions
        if (CucumberProperties.fromSystemProperties().containsKey("cucumber.features")) {
            return Collections.emptyList();
        }
        cucumber.api.CucumberOptions opts = clazz.getAnnotation(cucumber.api.CucumberOptions.class);
        if (opts != null && opts.features().length > 0) {
            return Arrays.stream(opts.features())
                    .map(FeaturePath::parse)
                    .collect(Collectors.toList());
        }
        com.stratio.qa.cucumber.api.CucumberOptionsCustom custom = clazz.getAnnotation(com.stratio.qa.cucumber.api.CucumberOptionsCustom.class);
        if (custom != null && custom.environments().length > 0) {
            String[] features = new RuntimeOptionsFactoryCustom(clazz).getEnvironmentFeatures(custom.environments());
            return Arrays.stream(features)
                    .map(FeaturePath::parse)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
