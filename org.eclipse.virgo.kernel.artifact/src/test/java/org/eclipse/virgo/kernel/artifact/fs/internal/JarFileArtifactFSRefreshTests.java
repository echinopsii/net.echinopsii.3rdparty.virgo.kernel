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

package org.eclipse.virgo.kernel.artifact.fs.internal;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import org.eclipse.virgo.kernel.artifact.fs.ArtifactFS;
import org.eclipse.virgo.kernel.artifact.fs.ArtifactFSEntry;
import org.eclipse.virgo.util.io.PathReference;
import org.junit.Before;
import org.junit.Test;

/**
 * Emulate the file manipulations when refreshing a bundle and test that JarFileArtifactFS does not perform
 * inappropriate caching.
 */
public class JarFileArtifactFSRefreshTests {

    private PathReference testModule;

    @Before
    public void setUp() throws Exception {
        PathReference pr = new PathReference("./target/redeploy-refresh");
        pr.delete(true);
        pr.createDirectory();
        this.testModule = pr.newChild("simple.module.jar");
    }

    @Test
    public void testRefresh() throws Exception {
        PathReference simpleModule = new PathReference("src/test/resources/refresh/simple.module.jar");
        simpleModule.copy(this.testModule);

        ArtifactFS artifactFS = new JarFileArtifactFS(new PathReference("./target/redeploy-refresh/simple.module.jar").toFile());

        checkBsn(artifactFS, "simple.module");

        this.testModule.delete();
        new PathReference("src/test/resources/refresh/simple2.module.jar").copy(this.testModule);
        
        JarFileArtifactFS fs = new JarFileArtifactFS(new PathReference("./target/redeploy-refresh/simple.module.jar").toFile());

        checkBsn(fs, "simple2.module");
    }

    public void checkBsn(ArtifactFS artifactFS, String bsn) throws IOException {
        ArtifactFSEntry entry = artifactFS.getEntry("META-INF/MANIFEST.MF");
        InputStream inputStream = entry.getInputStream();
        String manifest = new Scanner(inputStream).useDelimiter("\\A").next();
        assertTrue(manifest.contains(bsn));
        inputStream.close();
    }

}
