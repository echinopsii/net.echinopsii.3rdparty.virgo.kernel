/*******************************************************************************
 * Copyright (c) 2008, 2011 VMware Inc. and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   VMware Inc. - initial contribution (PlanInstallArtifactTreeFactory)
 *   EclipseSource - Bug 358442 Change InstallArtifact graph from a tree to a DAG
 *******************************************************************************/

package org.eclipse.virgo.kernel.install.artifact.internal;

import static org.eclipse.virgo.kernel.install.artifact.ArtifactIdentityDeterminer.PAR_TYPE;
import static org.eclipse.virgo.kernel.install.artifact.ArtifactIdentityDeterminer.PLAN_TYPE;

import java.io.InputStream;
import java.util.Map;

import org.eclipse.virgo.kernel.artifact.plan.PlanDescriptor;
import org.eclipse.virgo.kernel.artifact.plan.PlanReader;
import org.eclipse.virgo.kernel.deployer.core.DeploymentException;
import org.eclipse.virgo.kernel.install.artifact.ArtifactIdentity;
import org.eclipse.virgo.kernel.install.artifact.ArtifactIdentityDeterminer;
import org.eclipse.virgo.kernel.install.artifact.ArtifactStorage;
import org.eclipse.virgo.kernel.install.artifact.InstallArtifact;
import org.eclipse.virgo.kernel.install.artifact.InstallArtifactGraphFactory;
import org.eclipse.virgo.kernel.install.artifact.ScopeServiceRepository;
import org.eclipse.virgo.kernel.install.artifact.internal.bundle.BundleInstallArtifactGraphFactory;
import org.eclipse.virgo.kernel.serviceability.NonNull;
import org.eclipse.virgo.kernel.shim.scope.ScopeFactory;
import org.eclipse.virgo.medic.eventlog.EventLogger;
import org.eclipse.virgo.util.common.DirectedAcyclicGraph;
import org.eclipse.virgo.util.common.GraphNode;
import org.eclipse.virgo.util.io.IOUtils;
import org.osgi.framework.BundleContext;

/**
 * {@link PlanInstallArtifactGraphFactory} is an {@link InstallArtifactGraphFactory} for plan {@link InstallArtifact
 * InstallArtifacts}.
 * <p />
 * 
 * <strong>Concurrent Semantics</strong><br />
 * 
 * This class is thread safe.
 * 
 */
final class PlanInstallArtifactGraphFactory extends AbstractArtifactGraphFactory {

    private final BundleContext bundleContext;

    private final ScopeServiceRepository scopeServiceRepository;

    private final ScopeFactory scopeFactory;

    private final EventLogger eventLogger;

    private final InstallArtifactRefreshHandler refreshHandler;

    private final ParPlanInstallArtifactFactory parFactory;

    public PlanInstallArtifactGraphFactory(@NonNull BundleContext bundleContext, @NonNull ScopeServiceRepository scopeServiceRepository,
        @NonNull ScopeFactory scopeFactory, @NonNull EventLogger eventLogger,
        @NonNull BundleInstallArtifactGraphFactory bundleInstallArtifactGraphFactory, @NonNull InstallArtifactRefreshHandler refreshHandler,
        @NonNull ConfigInstallArtifactGraphFactory configInstallArtifactGraphFactory, @NonNull ArtifactStorageFactory artifactStorageFactory,
        @NonNull ArtifactIdentityDeterminer artifactIdentityDeterminer, @NonNull DirectedAcyclicGraph<InstallArtifact> dag) {
    		super(dag);
        this.bundleContext = bundleContext;
        this.scopeServiceRepository = scopeServiceRepository;
        this.scopeFactory = scopeFactory;
        this.eventLogger = eventLogger;
        this.refreshHandler = refreshHandler;

        this.parFactory = new ParPlanInstallArtifactFactory(eventLogger, bundleContext, bundleInstallArtifactGraphFactory, scopeServiceRepository,
            scopeFactory, refreshHandler, configInstallArtifactGraphFactory, artifactStorageFactory, artifactIdentityDeterminer, this);
    }

    /**
     * {@inheritDoc}
     */
    public GraphNode<InstallArtifact> constructInstallArtifactGraph(ArtifactIdentity identity, ArtifactStorage artifactStorage,
        Map<String, String> deploymentProperties, String repositoryName) throws DeploymentException {
        String type = identity.getType();
        if (PLAN_TYPE.equalsIgnoreCase(type)) {
            return createPlanGraph(identity, artifactStorage, getPlanDescriptor(artifactStorage), repositoryName);
        } else if (PAR_TYPE.equalsIgnoreCase(type)) {
            return createParGraph(identity, artifactStorage, repositoryName);
        } else {
            return null;
        }
    }

    private GraphNode<InstallArtifact> createParGraph(ArtifactIdentity artifactIdentity, ArtifactStorage artifactStorage, String repositoryName)
        throws DeploymentException {

        ParPlanInstallArtifact parArtifact = this.parFactory.createParPlanInstallArtifact(artifactIdentity, artifactStorage, repositoryName);
        GraphNode<InstallArtifact> graph = constructInstallGraph(parArtifact);
        parArtifact.setGraph(graph);
        return graph;
    }

    /**
     * @throws DeploymentException
     */
    private PlanDescriptor getPlanDescriptor(ArtifactStorage artifactStorage) throws DeploymentException {
        InputStream in = null;
        try {
            in = artifactStorage.getArtifactFS().getEntry("").getInputStream();
            PlanDescriptor planDescriptor = new PlanReader().read(in);
            return planDescriptor;
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    private GraphNode<InstallArtifact> createPlanGraph(ArtifactIdentity artifactIdentity, ArtifactStorage artifactStorage, PlanDescriptor planDescriptor,
        String repositoryName) throws DeploymentException {

        StandardPlanInstallArtifact planInstallArtifact;

        planInstallArtifact = new StandardPlanInstallArtifact(artifactIdentity, planDescriptor.getAtomic(), planDescriptor.getScoped(),
            artifactStorage, new StandardArtifactStateMonitor(this.bundleContext), this.scopeServiceRepository, this.scopeFactory, this.eventLogger,
            this.refreshHandler, repositoryName, planDescriptor.getArtifactSpecifications());

        GraphNode<InstallArtifact> graph = constructInstallGraph(planInstallArtifact);
        planInstallArtifact.setGraph(graph);
        return graph;
    }

}
