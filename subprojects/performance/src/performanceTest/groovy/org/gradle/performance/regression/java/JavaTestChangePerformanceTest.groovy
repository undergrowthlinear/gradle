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

package org.gradle.performance.regression.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.mutator.ApplyNonAbiChangeToJavaSourceFileMutator
import spock.lang.Unroll

import static JavaTestProject.LARGE_MONOLITHIC_JAVA_PROJECT
import static JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static JavaTestProject.MEDIUM_JAVA_MULTI_PROJECT_WITH_TEST_NG

class JavaTestChangePerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll
    def "test change on #testProject"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs
        runner.tasksToRun = ['test']
        runner.addBuildExperimentListener(new ApplyNonAbiChangeToJavaSourceFileMutator(fileToChange))
        runner.targetVersions = ["3.5-20170221000043+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                            | warmUpRuns | runs | fileToChange
        LARGE_JAVA_MULTI_PROJECT               | 2          | 6    | "project450/src/main/java/org/gradle/test/performance450_1/Production450_1.java"
        MEDIUM_JAVA_MULTI_PROJECT_WITH_TEST_NG | 2          | 6    | "project50/src/main/java/org/gradle/test/performance50_1/Production50_1.java"
        LARGE_MONOLITHIC_JAVA_PROJECT          | 2          | 6    | "src/main/java/org/gradle/test/performancenull_450/Productionnull_44901.java"

        //monolithicJavaTestNGProject" - testNG requires more test workers, which take too long to start up
    }
}
