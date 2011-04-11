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

package org.eclipse.virgo.kernel.shell.internal.commands;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.osgi.framework.Version;

import org.eclipse.virgo.kernel.model.management.ManageableArtifact;
import org.eclipse.virgo.kernel.model.management.RuntimeArtifactModelObjectNameCreator;
import org.eclipse.virgo.kernel.osgi.region.Region;
import org.eclipse.virgo.kernel.osgi.region.RegionDigraph;
import org.eclipse.virgo.kernel.shell.Command;
import org.eclipse.virgo.kernel.shell.internal.formatting.InstallArtifactCommandFormatter;
import org.eclipse.virgo.kernel.shell.internal.util.ArtifactRetriever;

/**
 * An abstract class that handles the methods that are delegated to an install artifact.
 * <p />
 * 
 * <strong>Concurrent Semantics</strong><br />
 * Thread-safe
 * 
 */
abstract class AbstractInstallArtifactBasedCommands<T extends ManageableArtifact> {

    private static final String NO_ARTIFACT_FOR_NAME_AND_VERSION = "No %s with name '%s' and version '%s' was found";

    private final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

    private final String type;

    private final RuntimeArtifactModelObjectNameCreator objectNameCreator;

    private final InstallArtifactCommandFormatter<T> formatter;

    private final ArtifactRetriever<T> artifactRetriever;

    private final Region kernelRegion;

    public AbstractInstallArtifactBasedCommands(String type, RuntimeArtifactModelObjectNameCreator objectNameCreator,
        InstallArtifactCommandFormatter<T> formatter, Class<T> artifactType, RegionDigraph regionDigraph) {
        this.type = type;
        this.objectNameCreator = objectNameCreator;
        this.formatter = formatter;
        this.artifactRetriever = new ArtifactRetriever<T>(type, objectNameCreator, artifactType);
        this.kernelRegion = regionDigraph == null ? null : regionDigraph.getRegion(0L);
    }

    @Command("list")
    public List<String> list() {
        Set<ObjectName> objectNames = this.server.queryNames(this.objectNameCreator.createArtifactsOfTypeQuery(this.type), null);
        List<T> artifacts = new ArrayList<T>(objectNames.size());
        for (ObjectName objectName : objectNames) {
            try {
                artifacts.add(this.artifactRetriever.getArtifact(objectName));
            } catch (InstanceNotFoundException e) {
                // Swallow to allow other to proceed
            }
        }

        return this.formatter.formatList(artifacts);
    }

    @Command("examine")
    public List<String> examine(String name, String versionString) {
        Version version = convertToVersion(versionString);
        try {
            return this.formatter.formatExamine(this.artifactRetriever.getArtifact(name, version));
        } catch (IllegalArgumentException iae) {
            return Arrays.asList(iae.getMessage());
        } catch (InstanceNotFoundException e) {
            if (this.kernelRegion != null) {
                try {
                    return this.formatter.formatExamine(this.artifactRetriever.getArtifact(name, version, this.kernelRegion));
                } catch (InstanceNotFoundException _) {
                    return getDoesNotExistMessage(this.type, name, versionString);
                }
            } else {
                return getDoesNotExistMessage(this.type, name, versionString);
            }
        }
    }

    protected List<String> getDoesNotExistMessage(String type, String name, String version) {
        return Arrays.asList(String.format(NO_ARTIFACT_FOR_NAME_AND_VERSION, type, name, version));
    }

    @Command("start")
    public List<String> start(String name, String version) {
        try {
            this.artifactRetriever.getArtifact(name, convertToVersion(version)).start();
            return Arrays.asList(String.format("%s %s:%s started successfully", this.type, name, version));
        } catch (IllegalArgumentException iae) {
            return Arrays.asList(iae.getMessage());
        } catch (InstanceNotFoundException e) {
            return getDoesNotExistMessage(this.type, name, version);
        } catch (Exception e) {
            return Arrays.asList(String.format("%s %s:%s start failed", this.type, name, version), "", "", formatException(e));
        }
    }

    @Command("stop")
    public List<String> stop(String name, String version) {
        try {
            this.artifactRetriever.getArtifact(name, convertToVersion(version)).stop();
            return Arrays.asList(String.format("%s %s:%s stopped successfully", this.type, name, version));
        } catch (IllegalArgumentException iae) {
            return Arrays.asList(iae.getMessage());
        } catch (InstanceNotFoundException e) {
            return getDoesNotExistMessage(this.type, name, version);
        } catch (Exception e) {
            return Arrays.asList(String.format("%s %s:%s stop failed", this.type, name, version), "", "", formatException(e));
        }
    }

    @Command("refresh")
    public List<String> refresh(String name, String version) {
        try {
            if (this.artifactRetriever.getArtifact(name, convertToVersion(version)).refresh()) {
                return Arrays.asList(String.format("%s %s:%s refreshed successfully", this.type, name, version));
            } else {
                return Arrays.asList(String.format("%s %s:%s not refreshed, no changes made", this.type, name, version));
            }
        } catch (IllegalArgumentException iae) {
            return Arrays.asList(iae.getMessage());
        } catch (InstanceNotFoundException e) {
            return getDoesNotExistMessage(this.type, name, version);
        } catch (Exception e) {
            return Arrays.asList(String.format("%s %s:%s refresh failed", this.type, name, version), "", "", formatException(e));
        }
    }

    @Command("uninstall")
    public List<String> uninstall(String name, String version) {
        try {
            this.artifactRetriever.getArtifact(name, convertToVersion(version)).uninstall();
            return Arrays.asList(String.format("%s %s%s uninstalled successfully", this.type, name, version));
        } catch (IllegalArgumentException iae) {
            return Arrays.asList(iae.getMessage());
        } catch (InstanceNotFoundException e) {
            return getDoesNotExistMessage(this.type, name, version);
        } catch (Exception e) {
            return Arrays.asList(String.format("%s %s:%s uninstall failed", this.type, name, version), "", "", formatException(e));
        }
    }

    protected final ArtifactRetriever<T> getArtifactRetriever() {
        return this.artifactRetriever;
    }

    private String formatException(Exception e) {
        StringWriter formattedException = new StringWriter();
        PrintWriter writer = new PrintWriter(formattedException);
        e.printStackTrace(writer);

        return formattedException.toString();
    }

    static Version convertToVersion(String versionString) {
        try {
            return new Version(versionString);
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException(String.format("'%s' is not a valid version", versionString));
        }
    }
}
