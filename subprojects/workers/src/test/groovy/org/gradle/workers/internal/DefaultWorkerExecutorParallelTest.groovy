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

import com.google.common.util.concurrent.ListenableFutureTask
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.Factory
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.StoppableExecutor
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.operations.BuildOperationWorkerRegistry
import org.gradle.internal.progress.BuildOperationExecutor
import org.gradle.internal.work.AsyncWorkTracker
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.UsesNativeServices

import java.util.concurrent.Future

@UsesNativeServices
class DefaultWorkerExecutorParallelTest extends ConcurrentSpec {
    def workerDaemonFactory = Mock(WorkerDaemonFactory)
    def workerExecutorFactory = Mock(ExecutorFactory)
    def buildOperationWorkerRegistry = Mock(BuildOperationWorkerRegistry)
    def buildOperationExecutor = Mock(BuildOperationExecutor)
    def asyncWorkerTracker = Mock(AsyncWorkTracker)
    def fileResolver = Mock(FileResolver)
    def serverImpl = Mock(WorkerDaemonProtocol)
    def stoppableExecutor = Mock(StoppableExecutor)
    ListenableFutureTask task
    DefaultWorkerExecutor workerExecutor

    def setup() {
        _ * fileResolver.resolveLater(_) >> fileFactory()
        _ * fileResolver.resolve(_) >> { files -> files[0] }
        _ * workerExecutorFactory.create(_ as String) >> stoppableExecutor
        workerExecutor = new DefaultWorkerExecutor(workerDaemonFactory, fileResolver, serverImpl.class, workerExecutorFactory, buildOperationWorkerRegistry, buildOperationExecutor, asyncWorkerTracker)
    }

    def "work can be submitted concurrently"() {
        when:
        async {
            5.times {
                start {
                    thread.blockUntil.allStarted
                    workerExecutor.submit(TestRunnable.class) { config ->
                        config.params = []
                    }
                }
            }
            instant.allStarted
        }

        then:
        5 * buildOperationWorkerRegistry.getCurrent()
        5 * stoppableExecutor.execute(_ as ListenableFutureTask)
    }

    def "can wait on multiple results to complete"() {
        given:
        def results = [
            Mock(Future),
            Mock(Future),
            Mock(Future),
            Mock(Future),
            Mock(Future)
        ]

        when:
        workerExecutor.await(results)

        then:
        results.each { result ->
            1 * result.get()
        }
    }

    def "all errors are thrown when waiting on multiple results"() {
        given:
        def succeeds = [
            Mock(Future),
            Mock(Future),
            Mock(Future)
        ]
        def fails = [
            Mock(Future),
            Mock(Future)
        ]

        when:
        workerExecutor.await(fails + succeeds)

        then:
        def e = thrown(DefaultMultiCauseException)
        succeeds.each { result ->
            1 * result.get()
        }
        fails.each { result ->
            1 * result.get() >> { throw new RuntimeException("FAIL!")}
        }

        and:
        e.causes.size() == 2
    }

    Factory fileFactory() {
        return Stub(Factory) {
            create() >> Stub(File)
        }
    }

    public static class TestRunnable implements Runnable {
        @Override
        void run() {
        }
    }
}
