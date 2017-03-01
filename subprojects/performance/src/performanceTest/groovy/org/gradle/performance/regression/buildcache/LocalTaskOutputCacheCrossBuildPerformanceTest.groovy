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

package org.gradle.performance.regression.buildcache

import org.gradle.caching.configuration.internal.DefaultBuildCacheConfiguration
import org.gradle.launcher.daemon.configuration.GradleProperties
import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.categories.PerformanceExperiment
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(PerformanceExperiment)
class LocalTaskOutputCacheCrossBuildPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Unroll("Test '#testProject' calling #tasks (daemon) with local cache")
    def "test"() {
        when:
        runner.testId = "locally cached ${tasks.join(' ')} on $testProject"
        runner.testGroup = "task output cache"
        runner.buildSpec {
            projectName(testProject).displayName("always-miss pull-only cache").invocation {
                tasksToRun("clean", *tasks).useDaemon().args(
                    "-D${GradleProperties.TASK_OUTPUT_CACHE_PROPERTY}=true",
                    "-D${GradleProperties.BUILD_CACHE_PROPERTY}=true",
                    "-D${DefaultBuildCacheConfiguration.BUILD_CACHE_CAN_PUSH}=false")
            }
        }
        runner.buildSpec {
            projectName(testProject).displayName("push-only cache").invocation {
                tasksToRun("clean", *tasks).useDaemon().args(
                    "-D${GradleProperties.TASK_OUTPUT_CACHE_PROPERTY}=true",
                    "-D${GradleProperties.BUILD_CACHE_PROPERTY}=true",
                    "-D${DefaultBuildCacheConfiguration.BUILD_CACHE_CAN_PULL}=false")
            }
        }
        runner.buildSpec {
            projectName(testProject).displayName("fully cached").invocation {
                tasksToRun("clean", *tasks).useDaemon().args(
                    "-D${GradleProperties.TASK_OUTPUT_CACHE_PROPERTY}=true",
                    "-D${GradleProperties.BUILD_CACHE_PROPERTY}=true")
            }
        }
        runner.baseline {
            projectName(testProject).displayName("fully up-to-date").invocation {
                tasksToRun(tasks).useDaemon()
            }
        }
        runner.baseline {
            projectName(testProject).displayName("non-cached").invocation {
                tasksToRun("clean", *tasks).useDaemon()
            }
        }

        then:
        runner.run()

        where:
        testProject                  | tasks
        "largeMonolithicProjectJava" | ["assemble"]
        "largeMultiProjectJava"      | ["assemble"]
    }

}
