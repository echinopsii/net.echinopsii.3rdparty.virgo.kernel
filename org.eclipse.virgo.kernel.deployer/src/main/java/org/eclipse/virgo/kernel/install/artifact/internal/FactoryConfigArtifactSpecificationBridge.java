/*
 * This file is part of the Eclipse Virgo project.
 *
 * Copyright (c) 2011 Chariot Solutions, LLC
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    dsklyut - initial contribution
 */

package org.eclipse.virgo.kernel.install.artifact.internal;

import java.util.Set;

import org.eclipse.virgo.kernel.artifact.ArtifactSpecification;
import org.eclipse.virgo.kernel.install.artifact.ArtifactIdentityDeterminer;
import org.eclipse.virgo.kernel.serviceability.NonNull;
import org.eclipse.virgo.repository.ArtifactDescriptor;
import org.eclipse.virgo.repository.Attribute;
import org.eclipse.virgo.repository.RepositoryAwareArtifactDescriptor;
import org.eclipse.virgo.repository.builder.ArtifactDescriptorBuilder;
import org.eclipse.virgo.repository.builder.AttributeBuilder;
import org.eclipse.virgo.util.io.PathReference;
import org.osgi.framework.Version;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * Implementation of {@link ArtifactSpecificationBridge} that deals with Artifacts of type
 * {@link ArtifactIdentityDeterminer#FACTORY_CONFIGURATION_TYPE}
 * <p />
 * As factory configuration is a pseudo artifact that cannot be looked up individually in Repository, but acts as a
 * container for individual instances of configurations and manages their lifecycle.
 * 
 * <strong>Concurrent Semantics</strong><br />
 * Thread Safe
 */
final class FactoryConfigArtifactSpecificationBridge implements ArtifactSpecificationBridge {

    private static final class DelegatingRepositoryAwareArtifactDescriptor implements RepositoryAwareArtifactDescriptor {

        private final ArtifactDescriptor delegate;

        private final String repositoryName;

        public DelegatingRepositoryAwareArtifactDescriptor(ArtifactDescriptor delegate, String repositoryName) {
            this.delegate = delegate;
            this.repositoryName = repositoryName;
        }

        public String getRepositoryName() {
            return this.repositoryName;
        }

        public Set<Attribute> getAttribute(String name) {
            return this.delegate.getAttribute(name);
        }

        public Set<Attribute> getAttributes() {
            return this.delegate.getAttributes();
        }

        public String getFilename() {
            return this.delegate.getFilename();
        }

        public String getName() {
            return this.delegate.getName();
        }

        public String getType() {
            return this.delegate.getType();
        }

        public java.net.URI getUri() {
            if (this.delegate.getUri() == null) {
                PathReference pr = PathReference.concat(this.delegate.getType(), this.delegate.getName(), this.delegate.getVersion().toString());
                return pr.toFile().toURI();
            }
            return this.delegate.getUri();
        }

        public Version getVersion() {
            return this.delegate.getVersion();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (this.getClass().equals(obj.getClass())) {
                return this.delegate.equals(((DelegatingRepositoryAwareArtifactDescriptor) obj).delegate);
            } else if (this.delegate.getClass().isAssignableFrom(obj.getClass())) {
                return this.delegate.equals(obj);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.delegate.hashCode();
        }

        @Override
        public String toString() {
            return this.delegate.toString();
        }
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public RepositoryAwareArtifactDescriptor generateArtifactDescriptor(@NonNull ArtifactSpecification artifactSpecification) {
        if (!ArtifactIdentityDeterminer.FACTORY_CONFIGURATION_TYPE.equals(artifactSpecification.getType())) {
            return null;
        }

        ArtifactDescriptorBuilder builder = new ArtifactDescriptorBuilder()//
        .setName(artifactSpecification.getName())//
        .setType(ArtifactIdentityDeterminer.FACTORY_CONFIGURATION_TYPE)//
        .setVersion(artifactSpecification.getVersionRange().toParseString())//
        .addAttribute(new AttributeBuilder().setName(ConfigurationAdmin.SERVICE_FACTORYPID).setValue(artifactSpecification.getName()).build());

        return new DelegatingRepositoryAwareArtifactDescriptor(builder.build(), null);
    }
}
