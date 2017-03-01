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

package org.gradle.internal.work

import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.progress.BuildOperationExecutor
import org.gradle.test.fixtures.concurrent.ConcurrentSpec


class DefaultAsyncWorkTrackerTest extends ConcurrentSpec {
    AsyncWorkTracker asyncWorkTracker = new DefaultAsyncWorkTracker()

    def "can wait for async work to complete"() {
        when:
        async {
            def operation = Mock(BuildOperationExecutor.Operation)
            5.times { i ->
                start {
                    asyncWorkTracker.registerWork(operation, new AsyncWorkCompletion() {
                        @Override
                        void waitForCompletion() {
                            thread.blockUntil.allStarted
                        }
                    })
                    instant."worker${i}Started"
                }
            }
            5.times { i ->
                thread.blockUntil."worker${i}Started"
            }
            start {
                instant.waitStarted
                asyncWorkTracker.waitForCompletion(operation)
                instant.waitFinished
            }
            thread.blockUntil.waitStarted
            instant.allStarted
        }

        then:
        instant.waitFinished >= instant.allStarted
    }

    def "work in different operations does not affect each other"() {
        def operation1 = Mock(BuildOperationExecutor.Operation)
        def operation2 = Mock(BuildOperationExecutor.Operation)

        when:
        async {
            start {
                asyncWorkTracker.registerWork(operation1, new AsyncWorkCompletion() {
                    @Override
                    void waitForCompletion() {
                        thread.blockUntil.completeWorker1
                    }
                })
                instant.worker1Started
            }
            start {
                asyncWorkTracker.registerWork(operation2, new AsyncWorkCompletion() {
                    @Override
                    void waitForCompletion() {
                        throw new IllegalStateException()
                    }
                })
                instant.worker2Started
            }
            thread.blockUntil.worker1Started
            thread.blockUntil.worker2Started
            start {
                asyncWorkTracker.waitForCompletion(operation1)
                instant.waitFinished
            }
            instant.completeWorker1
        }

        then:
        instant.waitFinished >= instant.completeWorker1
    }

    def "work can be submitted to one operation while another operation is being waited on"() {
        def operation1 = Mock(BuildOperationExecutor.Operation)
        def operation2 = Mock(BuildOperationExecutor.Operation)

        when:
        async {
            start {
                asyncWorkTracker.registerWork(operation1, new AsyncWorkCompletion() {
                    @Override
                    void waitForCompletion() {
                        thread.blockUntil.completeWorker1
                    }
                })
                instant.worker1Started
            }
            thread.blockUntil.worker1Started
            start {
                instant.waitStarted
                asyncWorkTracker.waitForCompletion(operation1)
                instant.waitFinished
            }
            start {
                thread.blockUntil.waitStarted
                asyncWorkTracker.registerWork(operation2, new AsyncWorkCompletion() {
                    @Override
                    void waitForCompletion() {
                        thread.blockUntil.completeWorker1
                    }
                })
                instant.worker2Started
            }
            thread.blockUntil.worker2Started
            instant.completeWorker1
        }

        then:
        instant.waitFinished >= instant.worker2Started
    }

    def "can wait for failing work to complete"() {
        def operation1 = Mock(BuildOperationExecutor.Operation)

        when:
        async {
            start {
                asyncWorkTracker.registerWork(operation1, new AsyncWorkCompletion() {
                    @Override
                    void waitForCompletion() {
                        throw new RuntimeException("BOOM!")
                    }
                })
                instant.worker1Started
            }
            start {
                asyncWorkTracker.registerWork(operation1, new AsyncWorkCompletion() {
                    @Override
                    void waitForCompletion() {
                        thread.blockUntil.completeWorker2
                    }
                })
                instant.worker2Started
            }
            thread.blockUntil.worker1Started
            thread.blockUntil.worker2Started
            start {
                try {
                    asyncWorkTracker.waitForCompletion(operation1)
                } finally {
                    instant.waitFinished
                }
            }
            instant.completeWorker2
            thread.blockUntil.waitFinished
        }

        then:
        def e = thrown(DefaultMultiCauseException)
        e.causes.size() == 1

        and:
        e.causes.get(0).message == "BOOM!"

        and:
        instant.waitFinished >= instant.completeWorker2
    }

    def "can remove async work for an operation"() {
        def operation = Mock(BuildOperationExecutor.Operation)

        given:
        3.times {
            asyncWorkTracker.registerWork(operation, new AsyncWorkCompletion() {
                @Override
                void waitForCompletion() {
                    thread.blockUntil.forever
                }
            })
        }

        when:
        asyncWorkTracker.remove(operation)

        then:
        asyncWorkTracker.waitForCompletion(operation)
    }
}
