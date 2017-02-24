/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.caching.internal.tasks;

import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.tasks.TaskState;
import org.gradle.initialization.BuildCompletionListener;
import org.gradle.internal.statistics.TaskExecutionStatistics;
import org.gradle.internal.statistics.TaskExecutionStatisticsAggregator;
import org.gradle.internal.statistics.TaskExecutionStatisticsReporter;

public class TaskExecutionStatisticsEventAdapter implements BuildCompletionListener, TaskExecutionListener {
    private final TaskExecutionStatisticsAggregator statisticsAggregator;
    private final TaskExecutionStatisticsReporter listener;

    public TaskExecutionStatisticsEventAdapter(TaskExecutionStatisticsReporter listener) {
        this.listener = listener;
        this.statisticsAggregator = new TaskExecutionStatisticsAggregator();
    }

    @Override
    public void completed() {
        final TaskExecutionStatistics stats = new TaskExecutionStatistics(
            statisticsAggregator.getTaskCounts(), statisticsAggregator.getCacheMissCount());
        listener.buildFinished(stats);
    }

    @Override
    public void beforeExecute(Task task) {
        // do nothing
    }

    @Override
    public void afterExecute(Task task, TaskState state) {
        statisticsAggregator.count(state);
    }
}
