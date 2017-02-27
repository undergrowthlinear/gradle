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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Pair;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DeferredVisitArtifactSet implements ResolvedArtifactSet {
    private final ResolvedArtifactSet delegate;
    private final BuildOperationProcessor buildOperationProcessor;

    private DeferredVisitArtifactSet(ResolvedArtifactSet delegate, BuildOperationProcessor buildOperationProcessor) {
        this.delegate = delegate;
        this.buildOperationProcessor = buildOperationProcessor;
    }

    public static ResolvedArtifactSet of(ResolvedArtifactSet delegate, BuildOperationProcessor buildOperationProcessor) {
        if (delegate instanceof CompositeArtifactSet) {
            return new DeferredVisitArtifactSet(delegate, buildOperationProcessor);
        }
        return delegate;
    }

    @Override
    public Set<ResolvedArtifact> getArtifacts() {
        return delegate.getArtifacts();
    }

    @Override
    public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
        delegate.collectBuildDependencies(dest);
    }

    @Override
    public void visit(final ArtifactVisitor visitor) {
        final Set<ResolvedArtifact> prepared = Sets.newLinkedHashSet();
        final Set<ResolvedArtifact> failed = Sets.newHashSet();
        final List<Pair<AttributeContainer, ResolvedArtifact>> visitedArtifacts = Lists.newArrayList();

        ArtifactVisitor deferredArtifactVisitor = new ArtifactVisitor() {
            @Override
            public void prepareArtifact(ResolvedArtifact artifact) {
                prepared.add(artifact);
            }

            @Override
            public void visitArtifact(AttributeContainer variant, ResolvedArtifact artifact) {
                visitedArtifacts.add(Pair.of(variant, artifact));
            }

            @Override
            public boolean includeFiles() {
                return visitor.includeFiles();
            }

            @Override
            public void visitFile(ComponentArtifactIdentifier artifactIdentifier, AttributeContainer variant, File file) {
                visitor.visitFile(artifactIdentifier, variant, file);
            }

            @Override
            public void visitFailure(Throwable failure) {
                visitor.visitFailure(failure);
            }
        };

        // Delegate to the visitor, collecting prepare and visit calls
        delegate.visit(deferredArtifactVisitor);

        // Execute all 'prepare' calls in parallel
        buildOperationProcessor.run(new Action<BuildOperationQueue<RunnableBuildOperation>>() {
            @Override
            public void execute(BuildOperationQueue<RunnableBuildOperation> buildOperationQueue) {
                for (final ResolvedArtifact resolvedArtifact : prepared) {
                    buildOperationQueue.add(new RunnableBuildOperation() {
                        @Override
                        public void run() {
                            try {
                                visitor.prepareArtifact(resolvedArtifact);
                            } catch (Throwable t) {
                                failed.add(resolvedArtifact);
                                visitor.visitFailure(t);
                            }
                        }

                        @Override
                        public String getDescription() {
                            return "Prepare artifact: " + resolvedArtifact;
                        }
                    });
                }
            }
        });

        // Now call visit for each artifact in order.
        for (Pair<AttributeContainer, ResolvedArtifact> visitedArtifact : visitedArtifacts) {
            ResolvedArtifact artifact = visitedArtifact.getRight();
            if (!failed.contains(artifact)) {
                visitor.visitArtifact(visitedArtifact.getLeft(), artifact);
            }
        }
    }
}
