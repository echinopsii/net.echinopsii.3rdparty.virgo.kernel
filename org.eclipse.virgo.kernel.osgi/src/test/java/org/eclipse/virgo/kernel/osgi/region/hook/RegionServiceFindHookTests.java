/*******************************************************************************
 * Copyright (c) 2011 VMware Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   VMware Inc. - initial contribution
 *******************************************************************************/

package org.eclipse.virgo.kernel.osgi.region.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import junit.framework.Assert;

import org.eclipse.virgo.kernel.osgi.region.Region;
import org.eclipse.virgo.kernel.osgi.region.RegionFilter;
import org.eclipse.virgo.kernel.osgi.region.internal.StandardRegionDigraph;
import org.eclipse.virgo.teststubs.osgi.framework.StubBundle;
import org.eclipse.virgo.teststubs.osgi.framework.StubBundleContext;
import org.eclipse.virgo.teststubs.osgi.framework.StubServiceReference;
import org.eclipse.virgo.teststubs.osgi.framework.StubServiceRegistration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.hooks.service.FindHook;

/**
 * This testcase was based on {@link RegionBundleFindHookTests}.
 */
public class RegionServiceFindHookTests {

    private static final String BUNDLE_X = "X";

    private static final Version BUNDLE_VERSION = new Version("0");

    private long bundleId;

    private static final String REGION_A = "RegionA";

    private static final String BUNDLE_A = "BundleA";

    private static final String REGION_B = "RegionB";

    private static final String BUNDLE_B = "BundleB";

    private static final String REGION_C = "RegionC";

    private static final String BUNDLE_C = "BundleC";

    private static final String REGION_D = "RegionD";

    private static final String BUNDLE_D = "BundleD";

    private StandardRegionDigraph digraph;

    private FindHook bundleFindHook;

    private Map<String, Region> regions;

    private Map<String, Bundle> bundles;

    private Map<String, ServiceReference<Object>> serviceReferences;

    private Collection<ServiceReference<?>> candidates;

    private ThreadLocal<Region> threadLocal;

    @Before
    public void setUp() throws Exception {
        this.bundleId = 1L;
        this.regions = new HashMap<String, Region>();
        this.bundles = new HashMap<String, Bundle>();
        this.serviceReferences = new HashMap<String, ServiceReference<Object>>();
        
        StubBundle stubSystemBundle = new StubBundle(0L, "osgi.framework", new Version("0"), "loc");
        StubBundleContext stubBundleContext = new StubBundleContext();
        stubBundleContext.addInstalledBundle(stubSystemBundle);
        this.threadLocal = new ThreadLocal<Region>();
        this.digraph = new StandardRegionDigraph(stubBundleContext, this.threadLocal);
        this.bundleFindHook = new RegionServiceFindHook(this.digraph);
        this.candidates = new HashSet<ServiceReference<?>>();

        // Create regions A, B, C, D containing bundles A, B, C, D, respectively.
        createRegion(REGION_A, BUNDLE_A);
        createRegion(REGION_B, BUNDLE_B);
        createRegion(REGION_C, BUNDLE_C);
        createRegion(REGION_D, BUNDLE_D);

        createBundle(BUNDLE_X);

    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testFindInSameRegion() {
        this.candidates.add(serviceReference(BUNDLE_A));
        this.bundleFindHook.find(bundleContext(BUNDLE_A), "", "", false, this.candidates);
        assertTrue(this.candidates.contains(serviceReference(BUNDLE_A)));
    }

    @Test
    public void testFindInDisconnectedRegion() {
        this.candidates.add(serviceReference(BUNDLE_B));
        this.bundleFindHook.find(bundleContext(BUNDLE_A), "", "", false, this.candidates);
        assertFalse(this.candidates.contains(serviceReference(BUNDLE_B)));
    }

    @Test
    public void testFindConnectedRegionAllowed() throws BundleException {
        RegionFilter filter = createFilter(BUNDLE_B);
        region(REGION_A).connectRegion(region(REGION_B), filter);

        this.candidates.add(serviceReference(BUNDLE_B));
        this.bundleFindHook.find(bundleContext(BUNDLE_A), "", "", false, this.candidates);
        assertTrue(this.candidates.contains(serviceReference(BUNDLE_B)));
    }

    @Test
    public void testFindConnectedRegionFiltering() throws BundleException {
        region(REGION_A).connectRegion(region(REGION_B), createFilter(BUNDLE_B));
        Bundle x = createBundle(BUNDLE_X);
        region(REGION_B).addBundle(x);

        this.candidates.add(serviceReference(BUNDLE_B));
        this.candidates.add(serviceReference(BUNDLE_X));
        this.bundleFindHook.find(bundleContext(BUNDLE_A), "", "", false, this.candidates);
        assertTrue(this.candidates.contains(serviceReference(BUNDLE_B)));
        assertFalse(this.candidates.contains(serviceReference(BUNDLE_X)));
    }

    @Test
    public void testFindTransitive() throws BundleException {
        region(REGION_A).connectRegion(region(REGION_B), createFilter(BUNDLE_C));
        region(REGION_B).connectRegion(region(REGION_C), createFilter(BUNDLE_C));
        region(REGION_C).addBundle(bundle(BUNDLE_X));

        this.candidates.add(serviceReference(BUNDLE_B));
        this.candidates.add(serviceReference(BUNDLE_C));
        this.bundleFindHook.find(bundleContext(BUNDLE_A), "", "", false, this.candidates);
        assertTrue(this.candidates.contains(serviceReference(BUNDLE_C)));
        assertFalse(this.candidates.contains(serviceReference(BUNDLE_B)));
        assertFalse(this.candidates.contains(serviceReference(BUNDLE_X)));

    }

    @Test
    public void testFindInCyclicGraph() throws BundleException {
        region(REGION_D).addBundle(bundle(BUNDLE_X));

        region(REGION_A).connectRegion(region(REGION_B), createFilter(BUNDLE_D, BUNDLE_X));
        region(REGION_B).connectRegion(region(REGION_A), createFilter());

        region(REGION_B).connectRegion(region(REGION_D), createFilter(BUNDLE_D));
        region(REGION_D).connectRegion(region(REGION_B), createFilter());

        region(REGION_B).connectRegion(region(REGION_C), createFilter(BUNDLE_X));
        region(REGION_C).connectRegion(region(REGION_B), createFilter());

        region(REGION_C).connectRegion(region(REGION_D), createFilter(BUNDLE_X));
        region(REGION_D).connectRegion(region(REGION_C), createFilter());

        region(REGION_A).connectRegion(region(REGION_C), createFilter());
        region(REGION_C).connectRegion(region(REGION_A), createFilter());

        region(REGION_D).connectRegion(region(REGION_A), createFilter());
        region(REGION_A).connectRegion(region(REGION_D), createFilter());

        // Find from region A.
        this.candidates.add(serviceReference(BUNDLE_B));
        this.candidates.add(serviceReference(BUNDLE_C));
        this.candidates.add(serviceReference(BUNDLE_D));
        this.candidates.add(serviceReference(BUNDLE_X));

        this.bundleFindHook.find(bundleContext(BUNDLE_A), "", "", false, this.candidates);
        assertEquals(2, this.candidates.size());
        assertTrue(this.candidates.contains(serviceReference(BUNDLE_D)));
        assertTrue(this.candidates.contains(serviceReference(BUNDLE_X)));

        // Find from region B
        this.candidates.add(serviceReference(BUNDLE_B));
        this.candidates.add(serviceReference(BUNDLE_C));
        this.candidates.add(serviceReference(BUNDLE_D));
        this.candidates.add(serviceReference(BUNDLE_X));

        this.bundleFindHook.find(bundleContext(BUNDLE_B), "", "", false, this.candidates);
        assertEquals(3, this.candidates.size());
        assertTrue(this.candidates.contains(serviceReference(BUNDLE_B)));
        assertTrue(this.candidates.contains(serviceReference(BUNDLE_D)));
        assertTrue(this.candidates.contains(serviceReference(BUNDLE_X)));
    }

    @Test
    public void testFindFromSystemBundle() {
        this.candidates.add(serviceReference(BUNDLE_A));

        Bundle stubBundle = new StubBundle(0L, "sys", BUNDLE_VERSION, "");
        this.bundleFindHook.find(stubBundle.getBundleContext(), "", "", false, this.candidates);
        assertEquals(1, this.candidates.size());
        assertTrue(this.candidates.contains(serviceReference(BUNDLE_A)));
    }

    @Test
    public void testFindFromBundleInNoRegion() {
        this.candidates.add(serviceReference(BUNDLE_A));

        Bundle stranger = createBundle("stranger");
        this.bundleFindHook.find(stranger.getBundleContext(), "", "", false, this.candidates);
        assertEquals(0, this.candidates.size());
    }

    private Region createRegion(String regionName, String... bundleSymbolicNames) throws BundleException {
        Region region = this.digraph.createRegion(regionName);
        for (String bundleSymbolicName : bundleSymbolicNames) {
            Bundle stubBundle = createBundle(bundleSymbolicName);
            region.addBundle(stubBundle);
        }
        this.regions.put(regionName, region);
        return region;
    }

    private Region region(String regionName) {
        return this.regions.get(regionName);
    }

    private RegionFilter createFilter(final String... referenceNames) {
        RegionFilter filter = new RegionFilter();
        if (referenceNames.length == 0)
        	return filter;
        Collection<String> filters = new ArrayList<String>(referenceNames.length);
        for (String referenceName : referenceNames) {
            StringBuilder builder = new StringBuilder();
            builder.append('(').append(Constants.OBJECTCLASS).append('=').append(referenceName).append(')');
            filters.add(builder.toString());
        }
       	try {
			filter.setFilters(RegionFilter.VISIBLE_SERVICE_NAMESPACE, filters);
		} catch (InvalidSyntaxException e) {
			Assert.fail(e.getMessage());
		}
        return filter;
    }

    private Bundle createBundle(String bundleSymbolicName) {
        Bundle stubBundle = new StubBundle(this.bundleId++, bundleSymbolicName, BUNDLE_VERSION, "loc:" + bundleSymbolicName);
        this.bundles.put(bundleSymbolicName, stubBundle);
        createServiceReference(stubBundle, bundleSymbolicName);
        return stubBundle;
    }

    private StubServiceReference<Object> createServiceReference(Bundle stubBundle, String referenceName) {
        StubServiceRegistration<Object> stubServiceRegistration = new StubServiceRegistration<Object>(
            (StubBundleContext) stubBundle.getBundleContext(), referenceName);
        StubServiceReference<Object> stubServiceReference = new StubServiceReference<Object>(stubServiceRegistration);
        this.serviceReferences.put(referenceName, stubServiceReference);
        return stubServiceReference;
    }

    private BundleContext bundleContext(String bundleSymbolicName) {
        return bundle(bundleSymbolicName).getBundleContext();
    }

    private Bundle bundle(String bundleSymbolicName) {
        Bundle bundleA = this.bundles.get(bundleSymbolicName);
        return bundleA;
    }

    private ServiceReference<Object> serviceReference(String referenceName) {
        return this.serviceReferences.get(referenceName);
    }

}
