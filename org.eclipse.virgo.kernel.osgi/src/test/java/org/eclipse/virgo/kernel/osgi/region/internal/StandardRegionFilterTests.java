/*
 * This file is part of the Eclipse Virgo project.
 *
 * Copyright (c) 2011 VMware Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    VMware Inc. - initial contribution
 */

package org.eclipse.virgo.kernel.osgi.region.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.easymock.EasyMock;
import org.eclipse.virgo.kernel.osgi.region.RegionFilter;
import org.eclipse.virgo.teststubs.osgi.framework.StubBundle;
import org.eclipse.virgo.teststubs.osgi.framework.StubBundleContext;
import org.eclipse.virgo.teststubs.osgi.framework.StubServiceRegistration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;

public class StandardRegionFilterTests {

    private static final String BUNDLE_SYMBOLIC_NAME = "A";

    private static final Version BUNDLE_VERSION = new Version("0");

    private StubBundle stubBundle;

    private String packageImportPolicy = "(" + BundleRevision.PACKAGE_NAMESPACE + "=foo)";

    private String serviceImportPolicy = "(" + Constants.OBJECTCLASS + "=foo.Service)";

    private BundleCapability fooPackage;

    private BundleCapability barPackage;

    private ServiceRegistration<Object> fooService;

    private ServiceRegistration<Object> barService;

    @Before
    public void setUp() throws Exception {
        this.stubBundle = new StubBundle(BUNDLE_SYMBOLIC_NAME, BUNDLE_VERSION);
        this.fooService = new StubServiceRegistration<Object>(new StubBundleContext(), "foo.Service");
        this.barService = new StubServiceRegistration<Object>(new StubBundleContext(), "bar.Service");

        this.fooPackage = EasyMock.createMock(BundleCapability.class);
        Map<String, Object> fooAttrs = new HashMap<String, Object>();
        fooAttrs.put(BundleRevision.PACKAGE_NAMESPACE, "foo");
        EasyMock.expect(fooPackage.getNamespace()).andReturn(BundleRevision.PACKAGE_NAMESPACE).anyTimes();
        EasyMock.expect(fooPackage.getAttributes()).andReturn(fooAttrs).anyTimes();
        EasyMock.replay(fooPackage);

        this.barPackage = EasyMock.createMock(BundleCapability.class);
        Map<String, Object> barAttrs = new HashMap<String, Object>();
        barAttrs.put(BundleRevision.PACKAGE_NAMESPACE, "bar");
        EasyMock.expect(barPackage.getNamespace()).andReturn(BundleRevision.PACKAGE_NAMESPACE).anyTimes();
        EasyMock.expect(barPackage.getAttributes()).andReturn(barAttrs).anyTimes();
        EasyMock.replay(barPackage);
    }

    @After
    public void tearDown() throws Exception {
    }

    private RegionFilter createBundleFilter(String bundleSymbolicName, Version bundleVersion) throws InvalidSyntaxException {
        String filter = "(&(" + RegionFilter.VISIBLE_BUNDLE_NAMESPACE + "=" + bundleSymbolicName + ")(" + Constants.BUNDLE_VERSION_ATTRIBUTE + ">="
            + bundleVersion + "))";
        return new StandardRegionFilterBuilder().allow(RegionFilter.VISIBLE_BUNDLE_NAMESPACE, filter).build();
    }

    private RegionFilter createRegionFilter(String namespace, Collection<String> filters) throws InvalidSyntaxException {
        StandardRegionFilterBuilder builder = new StandardRegionFilterBuilder();
        for (String filter : filters) {
            builder.allow(namespace, filter);
        }
        return builder.build();
    }

    @Test
    public void testBundleAllow() throws InvalidSyntaxException {
        RegionFilter regionFilter = createBundleFilter(BUNDLE_SYMBOLIC_NAME, BUNDLE_VERSION);
        assertTrue(regionFilter.isAllowed(stubBundle));
    }

    @Test
    public void testBundleAllNotAllowed() {
        RegionFilter regionFilter = new StandardRegionFilterBuilder().build();
        assertFalse(regionFilter.isAllowed(stubBundle));
    }

    @Test
    public void testBundleAllAllowed() {
        RegionFilter regionFilter = new StandardRegionFilterBuilder().allowAll(RegionFilter.VISIBLE_BUNDLE_NAMESPACE).build();
        assertTrue(regionFilter.isAllowed(stubBundle));
    }

    @Test
    public void testBundleNotAllowedInRange() throws InvalidSyntaxException {
        RegionFilter regionFilter = createBundleFilter(BUNDLE_SYMBOLIC_NAME, new Version(1, 0, 0));
        assertFalse(regionFilter.isAllowed(stubBundle));
    }

    @Test
    public void testCapabilityAllowed() throws InvalidSyntaxException {
        RegionFilter regionFilter = createRegionFilter(RegionFilter.VISIBLE_PACKAGE_NAMESPACE, Arrays.asList(packageImportPolicy));
        assertTrue(regionFilter.isAllowed(fooPackage));
        assertEquals(Arrays.asList(this.packageImportPolicy), regionFilter.getSharingPolicy().get(RegionFilter.VISIBLE_PACKAGE_NAMESPACE));
    }

    @Test
    public void testCapabilityAllNotAllowed() {
        RegionFilter regionFilter = new StandardRegionFilterBuilder().build();
        assertFalse(regionFilter.isAllowed(barPackage));
    }

    @Test
    public void testCapabilityAllAllowed() {
        RegionFilter regionFilter = new StandardRegionFilterBuilder().allowAll(RegionFilter.VISIBLE_PACKAGE_NAMESPACE).build();
        assertTrue(regionFilter.isAllowed(barPackage));
    }

    @Test
    public void testCapabilityNotAllowed() throws InvalidSyntaxException {
        RegionFilter regionFilter = createRegionFilter(RegionFilter.VISIBLE_PACKAGE_NAMESPACE, Arrays.asList(packageImportPolicy));
        assertFalse(regionFilter.isAllowed(barPackage));
        assertEquals(Arrays.asList(this.packageImportPolicy), regionFilter.getSharingPolicy().get(RegionFilter.VISIBLE_PACKAGE_NAMESPACE));
    }

    @Test
    public void testServiceAllowed() throws InvalidSyntaxException {
        RegionFilter regionFilter = createRegionFilter(RegionFilter.VISIBLE_SERVICE_NAMESPACE, Arrays.asList(serviceImportPolicy));
        assertTrue(regionFilter.isAllowed(fooService.getReference()));
        assertEquals(Arrays.asList(serviceImportPolicy), regionFilter.getSharingPolicy().get(RegionFilter.VISIBLE_SERVICE_NAMESPACE));
    }

    @Test
    public void testServiceAllNotAllowed() {
        RegionFilter regionFilter = new StandardRegionFilterBuilder().build();
        assertFalse(regionFilter.isAllowed(fooService.getReference()));
    }

    @Test
    public void testServiceAllAllowed() {
        RegionFilter regionFilter = new StandardRegionFilterBuilder().allowAll(RegionFilter.VISIBLE_SERVICE_NAMESPACE).build();
        assertTrue(regionFilter.isAllowed(fooService.getReference()));
    }

    @Test
    public void testServiceNotAllowed() throws InvalidSyntaxException {
        RegionFilter regionFilter = createRegionFilter(RegionFilter.VISIBLE_SERVICE_NAMESPACE, Arrays.asList(serviceImportPolicy));
        assertFalse(regionFilter.isAllowed(barService.getReference()));
        assertEquals(Arrays.asList(serviceImportPolicy), regionFilter.getSharingPolicy().get(RegionFilter.VISIBLE_SERVICE_NAMESPACE));
    }

    @Test
    public void testAllNamespace() throws InvalidSyntaxException {
        RegionFilter regionFilterNotAllowed = new StandardRegionFilterBuilder().allow(RegionFilter.VISIBLE_ALL_NAMESPACE, "(all=namespace)").build();
        assertFalse(regionFilterNotAllowed.isAllowed(stubBundle));
        assertFalse(regionFilterNotAllowed.isAllowed(fooPackage));
        assertFalse(regionFilterNotAllowed.isAllowed(barPackage));
        assertFalse(regionFilterNotAllowed.isAllowed(fooService.getReference()));
        assertFalse(regionFilterNotAllowed.isAllowed(barService.getReference()));

        RegionFilter regionFilterAllAllowed = new StandardRegionFilterBuilder().allowAll(RegionFilter.VISIBLE_ALL_NAMESPACE).build();
        assertTrue(regionFilterAllAllowed.isAllowed(stubBundle));
        assertTrue(regionFilterAllAllowed.isAllowed(fooPackage));
        assertTrue(regionFilterAllAllowed.isAllowed(barPackage));
        assertTrue(regionFilterAllAllowed.isAllowed(fooService.getReference()));
        assertTrue(regionFilterAllAllowed.isAllowed(barService.getReference()));
    }
}
