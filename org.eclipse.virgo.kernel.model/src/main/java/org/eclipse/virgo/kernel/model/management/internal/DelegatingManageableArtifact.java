/*******************************************************************************
 * Copyright (c) 2008, 2010 VMware Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   VMware Inc. - initial contribution
 *******************************************************************************/

package org.eclipse.virgo.kernel.model.management.internal;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.management.ObjectName;

import org.eclipse.virgo.kernel.model.Artifact;
import org.eclipse.virgo.kernel.model.management.ManageableArtifact;
import org.eclipse.virgo.kernel.model.management.RuntimeArtifactModelObjectNameCreator;
import org.eclipse.virgo.kernel.serviceability.NonNull;


/**
 * Implementation of {@link ManageableArtifact} that delegates to an {@link Artifact} for all methods and translates
 * types that are JMX-unfriendly to types that are JMX-friendly
 * <p />
 * 
 * <strong>Concurrent Semantics</strong><br />
 * 
 * Threadsafe
 * 
 * @see Artifact
 */
class DelegatingManageableArtifact implements ManageableArtifact {

    private static final String USER_REGION_NAME = "org.eclipse.virgo.region.user";

    private static final String GLOBAL_REGION_NAME = "global";
    
    private final RuntimeArtifactModelObjectNameCreator artifactObjectNameCreator;

    private final Artifact artifact;

    private final boolean newModel;

    public DelegatingManageableArtifact(@NonNull RuntimeArtifactModelObjectNameCreator artifactObjectNameCreator, @NonNull Artifact artifact, boolean newModel) {
        this.artifactObjectNameCreator = artifactObjectNameCreator;
        this.artifact = artifact;
        this.newModel = newModel;
    }

    /**
     * {@inheritDoc}
     */
    public final ObjectName[] getDependents() {
        return convertToObjectNames(this.artifact.getDependents());
    }

    /**
     * {@inheritDoc}
     */
    public final String getName() {
        return this.artifact.getName();
    }

    /**
     * {@inheritDoc}
     */
    public final String getState() {
        return this.artifact.getState().toString();
    }

    /**
     * {@inheritDoc}
     */
    public final String getType() {
        return this.artifact.getType();
    }

    /**
     * {@inheritDoc}
     */
    public final String getVersion() {
        return this.artifact.getVersion().toString();
    }

    /**
     * {@inheritDoc}
     */
    public String getRegion() {
        return this.artifact.getRegion().getName();
    }

    /**
     * {@inheritDoc}
     */
    public final boolean refresh() {
        return this.artifact.refresh();
    }

    /**
     * {@inheritDoc}
     */
    public final void start() {
        this.artifact.start();
    }

    /**
     * {@inheritDoc}
     */
    public final void stop() {
        this.artifact.stop();
    }

    /**
     * {@inheritDoc}
     */
    public final void uninstall() {
        this.artifact.uninstall();
    }

    /**
     * {@inheritDoc}
     */
    public final Map<String, String> getProperties() {
        return this.artifact.getProperties();
    }

    /**
     * Convert a collection of {@link Artifact}s to a collection of {@link ObjectName}s
     * 
     * @param artifacts The {@link Artifact}s to convert
     * @return The {@link ObjectName}s converted to
     */
    private final ObjectName[] convertToObjectNames(Set<Artifact> artifacts) {
        Set<ObjectName> objectNames = new HashSet<ObjectName>(artifacts.size());
        String regionName;
        for (Artifact artifact : artifacts) {
            regionName = artifact.getRegion().getName();
            if(newModel || (!USER_REGION_NAME.equals(regionName) && !GLOBAL_REGION_NAME.equals(regionName)) ){
                objectNames.add(artifactObjectNameCreator.createArtifactModel(artifact));
            } else {
                objectNames.add(artifactObjectNameCreator.createModel(artifact));
            }
        }
        return objectNames.toArray(new ObjectName[objectNames.size()]);
    }
}
