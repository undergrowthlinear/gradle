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

package org.gradle.internal.progress

import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.tasks.TaskState
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.tasks.TaskExecutionOutcome.EXECUTED
import static org.gradle.api.internal.tasks.TaskExecutionOutcome.FROM_CACHE
import static org.gradle.api.internal.tasks.TaskExecutionOutcome.NO_SOURCE
import static org.gradle.api.internal.tasks.TaskExecutionOutcome.SKIPPED
import static org.gradle.api.internal.tasks.TaskExecutionOutcome.UP_TO_DATE

@Subject(TaskOutcomeStatisticsFormatter)
class TaskOutcomeStatisticsFormatterTest extends Specification {
    TaskOutcomeStatisticsFormatter formatter

    def setup() {
        formatter = new TaskOutcomeStatisticsFormatter()
    }

    def "formats outcomes as percentage of total"() {
        expect:
        formatter.incrementAndGetProgress(taskState(UP_TO_DATE)) == " [UP-TO-DATE 100%]"
    }

    def "formats executed task as EXECUTED"() {
        expect:
        formatter.incrementAndGetProgress(taskState(EXECUTED)) == " [EXECUTED 100%]"
    }

    def "formats multiple outcome types"() {
        expect:
        formatter.incrementAndGetProgress(taskState(SKIPPED)) == " [SKIPPED 100%]"
        formatter.incrementAndGetProgress(taskState(UP_TO_DATE)) == " [UP-TO-DATE 50%, SKIPPED 50%]"
        formatter.incrementAndGetProgress(taskState(FROM_CACHE)) == " [FROM-CACHE 33%, UP-TO-DATE 33%, SKIPPED 33%]"
        formatter.incrementAndGetProgress(taskState(NO_SOURCE)) == " [FROM-CACHE 25%, UP-TO-DATE 25%, SKIPPED 25%, NO-SOURCE 25%]"
        formatter.incrementAndGetProgress(taskState(EXECUTED)) == " [FROM-CACHE 20%, UP-TO-DATE 20%, SKIPPED 20%, NO-SOURCE 20%, EXECUTED 20%]"
        formatter.incrementAndGetProgress(taskState(UP_TO_DATE)) == " [FROM-CACHE 17%, UP-TO-DATE 33%, SKIPPED 17%, NO-SOURCE 17%, EXECUTED 17%]"
    }

    private TaskState taskState(TaskExecutionOutcome taskExecutionOutcome) {
        def state = new TaskStateInternal('')
        state.outcome = taskExecutionOutcome
        state
    }
}
