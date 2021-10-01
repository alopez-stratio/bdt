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

package com.stratio.qa.cucumber.testng;

import com.stratio.qa.cucumber.converter.LoopConverter;
import com.stratio.qa.cucumber.runtime.FeaturePathFeatureSupplier;
import com.stratio.qa.cucumber.runtime.RuntimeOptionsFactoryCustom;
import com.stratio.qa.cucumber.runtime.RuntimeOptions;
import com.stratio.qa.cucumber.runtime.model.CucumberFeature;
import com.stratio.qa.cucumber.runtime.model.FeatureLoader;
import cucumber.api.event.ConcurrentEventListener;
import cucumber.api.event.TestRunFinished;
import cucumber.api.event.TestRunStarted;
import cucumber.runner.Runner;
import cucumber.runner.TimeServiceEventBus;
import cucumber.runner.EventBus;
import cucumber.runner.TimeService;
import cucumber.runtime.BackendModuleBackendSupplier;
import cucumber.runtime.ClassFinder;
import cucumber.runtime.CucumberException;
import cucumber.runtime.filter.Filters;
import cucumber.runtime.formatter.Plugins;
import cucumber.runtime.formatter.PluginFactory;
import cucumber.runner.ThreadLocalRunnerSupplier;
import cucumber.runtime.io.MultiLoader;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.io.ResourceLoaderClassFinder;
import gherkin.events.PickleEvent;
import org.reflections.Reflections;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CucumberRunner {

    private final EventBus bus;

    private final Filters filters;

    private final FeaturePathFeatureSupplier featureSupplier;

    private final ThreadLocalRunnerSupplier runnerSupplier;

    private final RuntimeOptions runtimeOptions;

    private final Plugins plugins;

    /**
     * Bootstrap the cucumber runtime
     *
     * @param clazz Which has the cucumber.api.CucumberOptions and org.testng.annotations.Test annotations
     */
    public CucumberRunner(Class clazz) throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, URISyntaxException {
        ClassLoader classLoader = clazz.getClassLoader();
        ResourceLoader resourceLoader = new MultiLoader(classLoader);

        RuntimeOptionsFactoryCustom runtimeOptionsFactory = new RuntimeOptionsFactoryCustom(clazz);
        runtimeOptions = runtimeOptionsFactory.create();

        String testSuffix = System.getProperty("TESTSUFFIX");
        String targetExecutionsPath = "target/executions/";
        if (testSuffix != null) {
            targetExecutionsPath = targetExecutionsPath + testSuffix + "/";
        }
        new File(targetExecutionsPath).mkdirs();
        CucumberReporter reporterTestNG = new CucumberReporter(targetExecutionsPath, clazz.getCanonicalName());

        addGlue();

        ClassFinder classFinder = new ResourceLoaderClassFinder(resourceLoader, classLoader);
        BackendModuleBackendSupplier backendSupplier = new BackendModuleBackendSupplier(resourceLoader, classFinder, runtimeOptions);
        bus = new TimeServiceEventBus(TimeService.SYSTEM);

        plugins = new Plugins(classLoader, new PluginFactory(), runtimeOptions);
        plugins.addPlugin(reporterTestNG);

        Set<Class<? extends ConcurrentEventListener>> implementers = new Reflections("com.stratio.qa.utils").getSubTypesOf(ConcurrentEventListener.class);
        for (Class<? extends ConcurrentEventListener> implementerClazz : implementers) {
            Constructor<?> ctor = implementerClazz.getConstructor();
            ctor.setAccessible(true);
            Object newPlugin = ctor.newInstance();
            plugins.addPlugin((ConcurrentEventListener) newPlugin);
        }

        FeatureLoader featureLoader = new FeatureLoader(resourceLoader);
        filters = new Filters(runtimeOptions);
        this.runnerSupplier = new ThreadLocalRunnerSupplier(runtimeOptions, bus, backendSupplier);
        featureSupplier = new FeaturePathFeatureSupplier(featureLoader, runtimeOptions);
    }

    private void addGlue() throws URISyntaxException {
        List<URI> uniqueGlue = new ArrayList<>();
        uniqueGlue.add(new URI("classpath:com/stratio/cct/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/qa/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/sparta/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/gosecsso/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/gosecmanagement/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/dcos/crossdata/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/cct/configuration/api/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/crossdata/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/streaming/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/ingestion/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/datavis/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/connectors/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/admin/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/explorer/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/manager/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/viewer/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/decision/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/paas/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/cassandra/lucene/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/analytic/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/exhibitor/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/intelligence/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/postgresbd/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/postgresql/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/zookeeper/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/universe/testsAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/paas/dgDatadictionaryAT/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/paas/dgtests/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/elastic/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/kafka/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/hdfs/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/kibana/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/cassandra/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/schema_registry/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/rest_proxy/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/spark/tests/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/schema/discovery/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/pgbouncer/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/ignite/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/qa/cucumber/converter"));
        uniqueGlue.add(new URI("classpath:com/stratio/logs_solution/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/marathonlbsec/specs"));
        uniqueGlue.add(new URI("classpath:com/stratio/grafana/specs"));

        runtimeOptions.getGlue().clear();
        runtimeOptions.getGlue().addAll(uniqueGlue);
    }

    public void runScenario(PickleEvent pickle) throws Throwable {
        //Possibly invoked in a multi-threaded context
        Throwable e = null;
        Runner runner = runnerSupplier.get();
        List<PickleEvent> pickleEventList = getPickleEventListFromTags(pickle);
        for (PickleEvent pickleEvent : pickleEventList) {
            try {
                TestCaseResultListener testCaseResultListener = new TestCaseResultListener(runner.getBus(), runtimeOptions.isStrict());
                runner.runPickle(pickleEvent);
                testCaseResultListener.finishExecutionUnit();

                if (!testCaseResultListener.isPassed()) {
                    throw testCaseResultListener.getError();
                }
            } catch (Throwable e1) {
                e = e1;
            }
        }
        if (e != null) {
            throw e;
        }
    }

    public void finish() {
        bus.send(new TestRunFinished(bus.getTime(), bus.getTimeMillis()));
    }

    /**
     * @return returns the cucumber scenarios as a two dimensional array of {@link PickleEventWrapper}
     * scenarios combined with their {@link CucumberFeatureWrapper} feature.
     */
    public Object[][] provideScenarios() {
        try {
            List<Object[]> scenarios = new ArrayList<>();
            List<CucumberFeature> features = getFeatures();
            for (CucumberFeature feature : features) {
                for (PickleEvent pickle : feature.getPickles()) {
                    if (filters.matchesFilters(pickle)) {
                        scenarios.add(new Object[]{new PickleEventWrapperImpl(pickle), new CucumberFeatureWrapperImpl(feature)});
                    }
                }
            }
            return scenarios.toArray(new Object[][]{});
        } catch (CucumberException e) {
            return new Object[][]{new Object[]{new CucumberExceptionWrapper(e), null}};
        }
    }

    List<CucumberFeature> getFeatures() {
        plugins.setSerialEventBusOnEventListenerPlugins(bus);

        List<CucumberFeature> features = featureSupplier.get();
        bus.send(new TestRunStarted(bus.getTime(), bus.getTimeMillis()));
        for (CucumberFeature feature : features) {
            feature.sendTestSourceRead(bus);
        }
        return features;
    }

    private List<PickleEvent> getPickleEventListFromTags(PickleEvent pe) {
        List<PickleEvent> pickleEventList = new ArrayList<>();
        //pickleEventList.add(pe);
        pickleEventList = LoopConverter.transformPickleEventWithLoopTags(pe);
        return pickleEventList;
    }
}
