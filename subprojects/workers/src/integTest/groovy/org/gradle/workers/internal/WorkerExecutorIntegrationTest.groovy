/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.workers.internal

import groovy.transform.NotYetImplemented
import org.gradle.api.JavaVersion
import org.gradle.api.specs.Spec
import org.gradle.execution.taskgraph.DefaultTaskExecutionPlan
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JvmInstallation
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil
import org.junit.Assume
import org.junit.Rule
import spock.lang.Unroll

import static org.hamcrest.CoreMatchers.*

class WorkerExecutorIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    private final String fooPath = TextUtil.normaliseFileSeparators(file('foo').absolutePath)

    @Rule
    public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    @Unroll
    def "can create and use an #execModel worker runnable defined in buildSrc"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInWorker(type: DaemonTask) {
                fork = $doFork
            }
        """

        when:
        succeeds("runInWorker")

        then:
        assertRunnableExecuted("runInWorker")

        where:
        doFork | execModel
        true   | 'daemon'
        false  | 'in-process'
    }

    @Unroll
    def "can create and use an #execModel worker runnable defined in build script"() {
        withRunnableClassInBuildScript()

        buildFile << """
            task runInWorker(type: DaemonTask) {
                fork = $doFork
            }
        """

        when:
        succeeds("runInWorker")

        then:
        assertRunnableExecuted("runInWorker")

        where:
        doFork | execModel
        true   | 'daemon'
        false  | 'in-process'
    }

    @Unroll
    def "can create and use an #execModel worker runnable defined in an external jar"() {
        def runnableJarName = "runnable.jar"
        withRunnableClassInExternalJar(file(runnableJarName))

        buildFile << """
            buildscript {
                dependencies {
                    classpath files("$runnableJarName")
                }
            }

            task runInWorker(type: DaemonTask) {
                fork = $doFork
            }
        """

        when:
        succeeds("runInWorker")

        then:
        assertRunnableExecuted("runInWorker")

        where:
        doFork | execModel
        true   | 'daemon'
        false  | 'in-process'
    }

    def "re-uses an existing idle worker daemon"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: DaemonTask)

            task reuseDaemon(type: DaemonTask) {
                dependsOn runInDaemon
            }
        """

        when:
        succeeds("reuseDaemon")

        then:
        assertSameDaemonWasUsed("runInDaemon", "reuseDaemon")
    }

    def "starts a new worker daemon when existing worker daemons are incompatible"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: DaemonTask)

            task startNewDaemon(type: DaemonTask) {
                dependsOn runInDaemon

                // Force a new daemon to be used
                additionalForkOptions = {
                    it.systemProperty("foo", "bar")
                }
            }
        """

        when:
        succeeds("startNewDaemon")

        then:
        assertDifferentDaemonsWereUsed("runInDaemon", "startNewDaemon")
    }

    def "starts a new worker daemon when there are no idle compatible worker daemons available"() {
        blockingServer.start()
        blockingServer.expectConcurrentExecution("runInDaemon", "startNewDaemon")

        withRunnableClassInBuildSrc()
        withBlockingRunnableClassInBuildSrc("http://localhost:${blockingServer.port}")

        buildFile << """
            task runInDaemon(type: DaemonTask) {
                runnableClass = BlockingRunnable.class
            }

            task startNewDaemon(type: DaemonTask) {
                runnableClass = BlockingRunnable.class
            }

            task runAllDaemons {
                dependsOn runInDaemon, startNewDaemon
            }
        """

        when:
        args("--parallel", "-D${DefaultTaskExecutionPlan.INTRA_PROJECT_TOGGLE}=true")
        succeeds("runAllDaemons")

        then:
        assertDifferentDaemonsWereUsed("runInDaemon", "startNewDaemon")
    }

    def "re-uses an existing compatible worker daemon when a different runnable is executed"() {
        withRunnableClassInBuildSrc()
        withAlternateRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: DaemonTask)

            task reuseDaemon(type: DaemonTask) {
                runnableClass = AlternateRunnable.class
                dependsOn runInDaemon
            }
        """

        when:
        succeeds("reuseDaemon")

        then:
        assertSameDaemonWasUsed("runInDaemon", "reuseDaemon")
    }

    @Unroll
    def "throws if #execModel worker used from a thread with no current build operation"() {
        given:
        withRunnableClassInBuildSrc()

        and:
        buildFile << """
            class DaemonTaskUsingCustomThreads extends DaemonTask {
                @TaskAction
                void executeTask() {
                    def thrown = null
                    def customThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                workerExecutor.submit(runnableClass) { config ->
                                    config.fork = $doFork
                                    config.forkOptions(additionalForkOptions)
                                    config.classpath(additionalClasspath)
                                    config.params = [ list.collect { it as String }, new File(outputFileDirPath), foo ]
                                }.get()
                            } catch(Exception ex) {
                                thrown = ex
                            }
                        }
                    })
                    customThread.start()
                    customThread.join()
                    if(thrown) {
                        throw thrown
                    }
                }
            }

            task runInDaemon(type: DaemonTaskUsingCustomThreads)
        """.stripIndent()

        when:
        fails 'runInDaemon'

        then:
        failure.assertHasCause 'No build operation associated with the current thread'

        where:
        doFork | execModel
        true   | 'daemon'
        false  | 'in-process'
    }

    @Requires(TestPrecondition.JDK_ORACLE)
    def "interesting worker daemon fork options are honored"() {
        Assume.assumeThat(Jvm.current().jre, notNullValue())
        withRunnableClassInBuildSrc()
        outputFileDir.createDir()

        buildFile << """
            import org.gradle.internal.jvm.Jvm

            $optionVerifyingRunnable

            task runInDaemon(type: DaemonTask) {
                runnableClass = OptionVerifyingRunnable.class
                additionalForkOptions = { options ->
                    options.with {
                        minHeapSize = "128m"
                        maxHeapSize = "128m"
                        systemProperty("foo", "bar")
                        jvmArgs("-Dbar=baz")
                        bootstrapClasspath = fileTree(new File(Jvm.current().jre.homeDir, "lib")).include("*.jar")
                        bootstrapClasspath(new File('${fooPath}'))
                        defaultCharacterEncoding = "UTF-8"
                        enableAssertions = true
                        workingDir = file('${outputFileDirPath}')
                        environment "foo", "bar"
                    }
                }
            }
        """

        when:
        succeeds("runInDaemon")

        then:
        assertRunnableExecuted("runInDaemon")
    }

    @NotYetImplemented
    def "worker daemons honor different executable specified in fork options"() {
        def differentJvm = findAnotherJvm()
        Assume.assumeNotNull(differentJvm)
        def differentJavaExecutablePath = TextUtil.normaliseFileSeparators(differentJvm.getExecutable("java").absolutePath)

        withRunnableClassInBuildSrc()

        buildFile << """
            ${getExecutableVerifyingRunnable(differentJvm.javaHome)}

            task runInDaemon(type: DaemonTask) {
                runnableClass = ExecutableVerifyingRunnable.class
                additionalForkOptions = { options ->
                    options.executable = new File('${differentJavaExecutablePath}')
                }
            }
        """

        when:
        succeeds("runInDaemon")

        then:
        assertRunnableExecuted("runInDaemon")
    }

    @Unroll
    def "can set a custom display name for #execModel work items"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            import org.gradle.internal.progress.BuildOperationService
            import org.gradle.internal.progress.BuildOperationListener
            import org.gradle.internal.progress.BuildOperationInternal
            import org.gradle.internal.progress.OperationStartEvent
            import org.gradle.internal.progress.OperationResult
            
            ext.operationExecuted = false
            task runInDaemon(type: DaemonTask) {
                fork = $doFork
                displayName = "Test Work"
                def operationListener = new BuildOperationListener() {

                    void started(BuildOperationInternal buildOperation, OperationStartEvent startEvent) {
                        if (buildOperation.displayName == "Test Work") {
                            operationExecuted = true
                        }
                    }

                    void finished(BuildOperationInternal buildOperation, OperationResult finishEvent) {
                    }
                }
                doFirst {
                    services.get(BuildOperationService).addListener(operationListener)
                }
                doLast {
                    assert operationExecuted : "Did not receive a build operation event for an operation with displayName 'Test Work'"
                    services.get(BuildOperationService).removeListener(operationListener)
                }
            }
        """.stripIndent()

        when:
        succeeds("runInDaemon")

        then:
        assertRunnableExecuted("runInDaemon")

        where:
        doFork | execModel
        true   | 'daemon'
        false  | 'in-process'
    }

    String getBlockingRunnableThatCreatesFiles(String url) {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.net.URL;

            public class BlockingRunnable extends TestRunnable {
                public BlockingRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                }

                public void run() {
                    super.run();
                    try {
                        new URL("$url/" + outputDir.getName()).openConnection().getHeaderField("RESPONSE");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """
    }

    String getAlternateRunnable() {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.net.URL;

            public class AlternateRunnable extends TestRunnable {
                public AlternateRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                }
            }
        """
    }

    String getOptionVerifyingRunnable() {
        return """
            import java.io.File;
            import java.util.regex.Pattern;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.lang.management.ManagementFactory;
            import java.lang.management.RuntimeMXBean;

            public class OptionVerifyingRunnable extends TestRunnable {
                public OptionVerifyingRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                }

                public void run() {
                    RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
                    List<String> arguments = runtimeMxBean.getInputArguments();
                    assert arguments.contains("-Dfoo=bar");
                    assert arguments.contains("-Dbar=baz");
                    assert arguments.contains("-Xmx128m");
                    assert arguments.contains("-Xms128m");
                    assert arguments.contains("-Dfile.encoding=UTF-8");
                    assert arguments.contains("-ea");

                    assert runtimeMxBean.getBootClassPath().replaceAll(Pattern.quote(File.separator),'/').endsWith("${fooPath}");

                    assert new File(System.getProperty("user.dir")).equals(new File('${outputFileDirPath}'));

                    //NotYetImplemented
                    //assert System.getenv("foo").equals("bar")

                    super.run();
                }
            }
        """
    }

    String getExecutableVerifyingRunnable(File differentJvmHome) {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.net.URL;

            public class ExecutableVerifyingRunnable extends TestRunnable {
                public ExecutableVerifyingRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                }

                public void run() {
                    assert new File(System.getProperty("java.home")).equals(new File('${differentJvmHome.absolutePath}'));

                    super.run();
                }
            }
        """
    }

    void withBlockingRunnableClassInBuildSrc(String url) {
        file("buildSrc/src/main/java/org/gradle/test/BlockingRunnable.java") << """
            package org.gradle.test;

            ${getBlockingRunnableThatCreatesFiles(url)}
        """

        addImportToBuildScript("org.gradle.test.BlockingRunnable")
    }

    void withAlternateRunnableClassInBuildSrc() {
        file("buildSrc/src/main/java/org/gradle/test/AlternateRunnable.java") << """
            package org.gradle.test;

            $alternateRunnable
        """

        addImportToBuildScript("org.gradle.test.AlternateRunnable")
    }

    Jvm findAnotherJvm() {
        def current = Jvm.current()
        AvailableJavaHomes.getAvailableJdk(new Spec<JvmInstallation>() {
            @Override
            boolean isSatisfiedBy(JvmInstallation jvm) {
                return jvm.javaHome != current.javaHome && jvm.javaVersion >= JavaVersion.VERSION_1_7
            }
        })
    }
}
