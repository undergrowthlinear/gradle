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

package org.gradle.performance.regression.nativeplatform

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import spock.lang.Unroll

class NativeBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll('Project #type native build')
    def "build"() {
        given:
        runner.testId = "native build ${type}"
        runner.testProject = "${type}Native"
        runner.tasksToRun = ["clean", "assemble"]
        runner.gradleOpts = ["-Xms$maxMemory", "-Xmx$maxMemory"]
        runner.runs = iterations
        runner.warmUpRuns = iterations
        runner.targetVersions = ["3.5-20170221000043+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        type     | maxMemory    | iterations
        "small"  | '256m'       | 40
        "medium" | '256m'       | null
        "big"    | '1g'         | null
        "multi"  | '256m'       | null
    }

    def "Many projects native build"() {
        given:
        runner.testId = "native build many projects"
        runner.testProject = "manyProjectsNative"
        runner.tasksToRun = ["clean", "assemble"]
        runner.targetVersions = ["3.5-20170221000043+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
