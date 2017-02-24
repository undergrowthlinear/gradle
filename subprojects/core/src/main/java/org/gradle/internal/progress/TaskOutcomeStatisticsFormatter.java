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
package org.gradle.internal.progress;

import com.google.common.base.Joiner;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.tasks.TaskState;
import org.gradle.internal.statistics.TaskExecutionStatistics;
import org.gradle.internal.statistics.TaskExecutionStatisticsAggregator;

import java.util.ArrayList;
import java.util.List;

public class TaskOutcomeStatisticsFormatter {
    private TaskExecutionStatisticsAggregator stats;

    public TaskOutcomeStatisticsFormatter() {
        this.stats = new TaskExecutionStatisticsAggregator();
    }

    public String incrementAndGetProgress(TaskState state) {
        stats.count(state);
        return format(new TaskExecutionStatistics(stats.getTaskCounts(), stats.getCacheMissCount()));
    }

    private String format(TaskExecutionStatistics taskExecutionStatistics) {
        List<String> outcomes = new ArrayList<String>(TaskExecutionOutcome.values().length);
        final int allTasksCount = taskExecutionStatistics.getAllTasksCount();
        for (TaskExecutionOutcome outcome : TaskExecutionOutcome.values()) {
            if (taskExecutionStatistics.getTasksCount(outcome) > 0) {
                String message = outcome.getMessage();
                if (message == null) {
                    message = TaskExecutionOutcome.EXECUTED.toString();
                }
                outcomes.add(message + " " + Math.round(taskExecutionStatistics.getTasksCount(outcome) * 100.0 / allTasksCount) + '%');
            }
        }
        return " [" + Joiner.on(", ").join(outcomes) + "]";
    }
}
