/*******************************************************************************
 * Copyright (c) 2008, 2010 VMware Inc. and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   VMware Inc. - initial contribution (StandardInstalArtifactTreeInclosureTests.java)
 *   EclipseSource - Bug 358442 Change InstallArtifact graph from a tree to a DAG
 *******************************************************************************/

package org.eclipse.virgo.kernel.install.artifact.internal;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.virgo.kernel.artifact.ArtifactSpecification;
import org.eclipse.virgo.kernel.artifact.fs.StandardArtifactFSFactory;
import org.eclipse.virgo.kernel.core.BundleStarter;
import org.eclipse.virgo.kernel.deployer.core.DeploymentException;
import org.eclipse.virgo.kernel.deployer.core.DeploymentOptions;
import org.eclipse.virgo.kernel.install.artifact.BundleInstallArtifact;
import org.eclipse.virgo.kernel.install.artifact.InstallArtifact;
import org.eclipse.virgo.kernel.install.artifact.InstallArtifactGraphFactory;
import org.eclipse.virgo.kernel.install.artifact.InstallArtifactGraphInclosure;
import org.eclipse.virgo.kernel.install.artifact.internal.bundle.BundleInstallArtifactGraphFactory;
import org.eclipse.virgo.kernel.install.environment.InstallEnvironmentFactory;
import org.eclipse.virgo.kernel.install.pipeline.Pipeline;
import org.eclipse.virgo.kernel.osgi.framework.OsgiFramework;
import org.eclipse.virgo.kernel.osgi.framework.PackageAdminUtil;
import org.eclipse.virgo.kernel.shim.serviceability.TracingService;
import org.eclipse.virgo.medic.test.eventlog.MockEventLogger;
import org.eclipse.virgo.repository.ArtifactBridge;
import org.eclipse.virgo.repository.Repository;
import org.eclipse.virgo.repository.RepositoryAwareArtifactDescriptor;
import org.eclipse.virgo.teststubs.osgi.framework.StubBundleContext;
import org.eclipse.virgo.util.common.GraphNode;
import org.eclipse.virgo.util.common.ThreadSafeDirectedAcyclicGraph;
import org.eclipse.virgo.util.io.PathReference;
import org.eclipse.virgo.util.osgi.manifest.VersionRange;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Version;

/**
 */
public class StandardInstallArtifactGraphInclosureTests {

	private static final String TEST_BUNDLE_REPOSITORY_NAME = "testBundleRepositoryName";

	private final InstallEnvironmentFactory installEnvironmentFactory = createMock(InstallEnvironmentFactory.class);

	private final Pipeline refreshPipeline = createMock(Pipeline.class);

	private InstallArtifactGraphInclosure installArtifactFactory;

	private URI bundleURI;

	private OsgiFramework osgiFramework;

	private BundleStarter bundleStarter;

	private TracingService tracingService;

	private PackageAdminUtil packageAdminUtil;

	private Repository repository;

	private RepositoryAwareArtifactDescriptor artifactDescriptor;

	private final ArtifactStorageFactory artifactStorageFactory = new StandardArtifactStorageFactory(
			new PathReference("target/work/deployer.staging"),
			new StandardArtifactFSFactory(), new MockEventLogger());

	private Set<ArtifactBridge> testArtifactBridges = new HashSet<ArtifactBridge>();

	private ThreadSafeDirectedAcyclicGraph<InstallArtifact> dag = new ThreadSafeDirectedAcyclicGraph<InstallArtifact>();

	@Before
	public void setUp() {
		this.bundleURI = new File("src/test/resources/artifacts/simple.jar")
				.toURI();
		this.osgiFramework = createMock(OsgiFramework.class);
		this.bundleStarter = createMock(BundleStarter.class);
		this.tracingService = createMock(TracingService.class);
		this.packageAdminUtil = createMock(PackageAdminUtil.class);
		this.repository = createMock(Repository.class);
		this.artifactDescriptor = createMock(RepositoryAwareArtifactDescriptor.class);
		this.testArtifactBridges.add(new StubArtifactBridge("bundle", ".jar"));
	}

	private void replayMocks() {
		replay(this.osgiFramework, this.bundleStarter, this.tracingService,
				this.packageAdminUtil, this.repository, this.artifactDescriptor);
	}

	private void verifyMocks() {
		verify(this.osgiFramework, this.bundleStarter, this.tracingService,
				this.packageAdminUtil, this.repository, this.artifactDescriptor);
	}

	private void resetMocks() {
		reset(this.osgiFramework, this.bundleStarter, this.tracingService,
				this.packageAdminUtil, this.repository, this.artifactDescriptor);
	}

	@Test
	public void testBundle() throws DeploymentException, IOException {
		StubBundleContext bundleContext = new StubBundleContext();
		StubBundleContext userRegionBundleContext = new StubBundleContext();
		expect(this.osgiFramework.getBundleContext()).andReturn(bundleContext)
				.anyTimes();
		expect(
				this.repository.get(isA(String.class), isA(String.class),
						isA(VersionRange.class))).andReturn(
				this.artifactDescriptor);
		expect(this.artifactDescriptor.getUri()).andReturn(this.bundleURI);
		expect(this.artifactDescriptor.getVersion()).andReturn(
				new Version(1, 2, 3));
		expect(this.artifactDescriptor.getRepositoryName()).andReturn(
				TEST_BUNDLE_REPOSITORY_NAME);

		replayMocks();

		StandardArtifactIdentityDeterminer artifactIdentityDeterminer = new StandardArtifactIdentityDeterminer(
				testArtifactBridges);

		StandardInstallArtifactRefreshHandler refreshHelper = new StandardInstallArtifactRefreshHandler(
				installEnvironmentFactory, refreshPipeline);

		bundleContext.registerService(InstallArtifactGraphFactory.class
				.getName(), new BundleInstallArtifactGraphFactory(
				this.osgiFramework, bundleContext, refreshHelper,
				this.bundleStarter, this.tracingService, this.packageAdminUtil,
				userRegionBundleContext, new MockEventLogger(), null, dag),
				null);

		this.installArtifactFactory = new StandardInstallArtifactGraphInclosure(
				this.artifactStorageFactory, bundleContext, this.repository,
				new MockEventLogger(), artifactIdentityDeterminer);

		ArtifactSpecification specification = new ArtifactSpecification(
				"bundle", "a", new VersionRange("2.0.0"));
		InstallArtifact installArtifact = this.installArtifactFactory
				.createInstallGraph(specification).getValue();
		assertNotNull(installArtifact);
		assertEquals(TEST_BUNDLE_REPOSITORY_NAME,
				installArtifact.getRepositoryName());
		assertTrue(installArtifact instanceof BundleInstallArtifact);
		BundleInstallArtifact bundleInstallArtifact = (BundleInstallArtifact) installArtifact;
		assertEquals("a", bundleInstallArtifact.getBundleManifest()
				.getBundleSymbolicName().getSymbolicName());

		verifyMocks();
		resetMocks();
	}

	@Test
	public void testBundleImplicitTypeAndVersion() throws DeploymentException,
			IOException {
		StubBundleContext bundleContext = new StubBundleContext();
		StubBundleContext userRegionBundleContext = new StubBundleContext();
		expect(this.osgiFramework.getBundleContext()).andReturn(bundleContext)
				.anyTimes();

		replayMocks();

		StandardArtifactIdentityDeterminer artifactIdentityDeterminer = new StandardArtifactIdentityDeterminer(
				testArtifactBridges);

		StandardInstallArtifactRefreshHandler refreshHelper = new StandardInstallArtifactRefreshHandler(
				installEnvironmentFactory, refreshPipeline);

		bundleContext.registerService(InstallArtifactGraphFactory.class
				.getName(), new BundleInstallArtifactGraphFactory(
				this.osgiFramework, bundleContext, refreshHelper,
				this.bundleStarter, this.tracingService, this.packageAdminUtil,
				userRegionBundleContext, new MockEventLogger(), null, dag),
				null);

		this.installArtifactFactory = new StandardInstallArtifactGraphInclosure(
				this.artifactStorageFactory, bundleContext, this.repository,
				new MockEventLogger(), artifactIdentityDeterminer);

		GraphNode<InstallArtifact> installArtifactGraph = this.installArtifactFactory
				.createInstallGraph(new File(this.bundleURI));
		checkBundleImplicitTypeAndVersion(installArtifactGraph.getValue());

		verifyMocks();
		resetMocks();
	}

	@Test
	public void testNoBSNBundleImplicitTypeAndVersion()
			throws DeploymentException {
		StubBundleContext bundleContext = new StubBundleContext();
		StubBundleContext userRegionBundleContext = new StubBundleContext();
		expect(this.osgiFramework.getBundleContext()).andReturn(bundleContext)
				.anyTimes();

		replayMocks();

		StandardArtifactIdentityDeterminer artifactIdentityDeterminer = new StandardArtifactIdentityDeterminer(
				testArtifactBridges);

		StandardInstallArtifactRefreshHandler refreshHelper = new StandardInstallArtifactRefreshHandler(
				installEnvironmentFactory, refreshPipeline);

		bundleContext.registerService(InstallArtifactGraphFactory.class
				.getName(), new BundleInstallArtifactGraphFactory(
				this.osgiFramework, bundleContext, refreshHelper,
				this.bundleStarter, this.tracingService, this.packageAdminUtil,
				userRegionBundleContext, new MockEventLogger(), null, dag),
				null);

		this.installArtifactFactory = new StandardInstallArtifactGraphInclosure(
				this.artifactStorageFactory, bundleContext, this.repository,
				new MockEventLogger(), artifactIdentityDeterminer);

		GraphNode<InstallArtifact> installArtifactGraph = this.installArtifactFactory
				.createInstallGraph(new File(
						"src/test/resources/artifacts/nobsn.jar"));
		InstallArtifact installArtifact = installArtifactGraph.getValue();
		assertNotNull(installArtifact);
		assertTrue(installArtifact instanceof BundleInstallArtifact);
		assertEquals("nobsn", installArtifact.getName());
		assertEquals(new Version("0"), installArtifact.getVersion());

		verifyMocks();
		resetMocks();
	}

	@Test
	public void testRecoverBundleImplicitTypeAndVersion()
			throws DeploymentException, IOException {
		StubBundleContext bundleContext = new StubBundleContext();
		StubBundleContext userRegionBundleContext = new StubBundleContext();
		expect(this.osgiFramework.getBundleContext()).andReturn(bundleContext)
				.anyTimes();

		replayMocks();

		StandardInstallArtifactRefreshHandler refreshHelper = new StandardInstallArtifactRefreshHandler(
				installEnvironmentFactory, refreshPipeline);

		StandardArtifactIdentityDeterminer artifactIdentityDeterminer = new StandardArtifactIdentityDeterminer(
				testArtifactBridges);

		bundleContext.registerService(InstallArtifactGraphFactory.class
				.getName(), new BundleInstallArtifactGraphFactory(
				this.osgiFramework, bundleContext, refreshHelper,
				this.bundleStarter, this.tracingService, this.packageAdminUtil,
				userRegionBundleContext, new MockEventLogger(), null, dag),
				null);

		this.installArtifactFactory = new StandardInstallArtifactGraphInclosure(
				this.artifactStorageFactory, bundleContext, this.repository,
				new MockEventLogger(), artifactIdentityDeterminer);

		GraphNode<InstallArtifact> installArtifactGraph = this.installArtifactFactory
				.createInstallGraph(new File(this.bundleURI));
		checkBundleImplicitTypeAndVersion(installArtifactGraph.getValue());

		DeploymentOptions deploymentOptions = new DeploymentOptions(
		/* recoverable */true, /* deployerOwned */false, true);
		GraphNode<InstallArtifact> recoveredInstallGraph = this.installArtifactFactory
				.recoverInstallGraph(new File(this.bundleURI),
						deploymentOptions);
		checkBundleImplicitTypeAndVersion(recoveredInstallGraph.getValue());

		verifyMocks();
		resetMocks();
	}

	private void checkBundleImplicitTypeAndVersion(
			InstallArtifact installArtifact) throws IOException {
		assertNotNull(installArtifact);
		assertTrue(installArtifact instanceof BundleInstallArtifact);
		BundleInstallArtifact bundleInstallArtifact = (BundleInstallArtifact) installArtifact;
		assertEquals("simple", bundleInstallArtifact.getBundleManifest()
				.getBundleSymbolicName().getSymbolicName());
		assertEquals(new Version("0"), bundleInstallArtifact
				.getBundleManifest().getBundleVersion());
	}
}
