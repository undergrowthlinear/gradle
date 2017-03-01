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

class NativeBuildDependentsPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("Native build dependents - #testProject")
    def "native build dependents"() {
        given:
        runner.testId = "native build dependents $testProject"
        runner.testProject = testProject
        runner.tasksToRun = [ "$subprojectPath:$taskName" ]
        runner.args += ["--parallel", "--max-workers=4"]
        runner.targetVersions = ["3.5-20170221000043+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject            | subprojectPath  | taskName
        'nativeDependents'     | ':libA0'        | 'buildDependentsLibA0'
        // TODO Enable once runnable on CI (google test & target platform)
        // 'largeNativeBuild'     | ':project432'   | 'buildDependentsExternalComponent111'
        // TODO Re-evaluate this scenario: memory consumption stress case, gradleOpts = ['-Xms4g', '-Xmx4g']
        // The generated dependency graph is rather complex and deep, unrealistic?
        // 'nativeDependentsDeep' | ':libA0'       | 'buildDependentsLibA0'
    }

    @Unroll("Native report dependents - #testProject")
    def "native report dependents"() {
        given:
        runner.testId = "native report dependents $testProject"
        runner.testProject = testProject
        runner.tasksToRun = [ "$subprojectPath:dependentComponents" ]
        runner.args += ["--parallel", "--max-workers=4"]
        runner.targetVersions = ["3.5-20170221000043+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject            | subprojectPath
        'nativeDependents'     | ':libA0'
        // TODO Enable once runnable on CI (google test & target platform)
        // 'largeNativeBuild'     | ':project432'
        // TODO Re-evaluate this scenario: memory consumption stress case, gradleOpts = ['-Xms4g', '-Xmx4g']
        // The generated dependency graph is rather complex and deep, unrealistic?
        // 'nativeDependentsDeep' | 'libA0'
    }
}
