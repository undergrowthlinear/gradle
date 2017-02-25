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

import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.tasks.TaskState;
import org.gradle.internal.statistics.TaskExecutionStatistics;
import org.gradle.internal.statistics.TaskExecutionStatisticsAggregator;

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
        final int allTasksCount = taskExecutionStatistics.getAllTasksCount();
        int tasksAvoided = 0;
        int tasksExecuted = 0;
        for (TaskExecutionOutcome outcome : TaskExecutionOutcome.values()) {
            switch (outcome) {
                case EXECUTED: tasksExecuted += taskExecutionStatistics.getTasksCount(outcome); break;
                default: tasksAvoided += taskExecutionStatistics.getTasksCount(outcome);
            }
        }
        return " [" + formatPercentage(tasksAvoided, allTasksCount) + " AVOIDED, " + formatPercentage(tasksExecuted, allTasksCount) + " EXECUTED]";
    }

    private String formatPercentage(int num, int total) {
        return String.valueOf(Math.round(num * 100.0 / total)) + '%';
    }
}
