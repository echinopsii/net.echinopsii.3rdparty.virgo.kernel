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

package org.eclipse.virgo.kernel.osgi.region;

import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipse.virgo.kernel.osgi.region.hook.RegionBundleEventHook;
import org.eclipse.virgo.kernel.osgi.region.hook.RegionBundleFindHook;
import org.eclipse.virgo.kernel.osgi.region.hook.RegionResolverHookFactory;
import org.eclipse.virgo.kernel.osgi.region.hook.RegionServiceEventHook;
import org.eclipse.virgo.kernel.osgi.region.hook.RegionServiceFindHook;
import org.eclipse.virgo.kernel.osgi.region.internal.StandardRegionDigraph;
import org.eclipse.virgo.util.osgi.ServiceRegistrationTracker;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.hooks.bundle.EventHook;
import org.osgi.framework.hooks.bundle.FindHook;
import org.osgi.framework.hooks.resolver.ResolverHookFactory;

/**
 * Creates and manages the user {@link Region regions}.
 * <p />
 * 
 * <strong>Concurrent Semantics</strong><br />
 * 
 * Threadsafe.
 * 
 */
final class RegionManager {

    private static final String REGION_KERNEL = "org.eclipse.virgo.region.kernel";

    private final ServiceRegistrationTracker tracker = new ServiceRegistrationTracker();

    private final BundleContext bundleContext;

    public RegionManager(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void start() throws BundleException {
        RegionDigraph regionDigraph = createRegionDigraph();
        registerRegionHooks(regionDigraph);
    }

    private RegionDigraph createRegionDigraph() throws BundleException {
        RegionDigraph regionDigraph = new StandardRegionDigraph();
        Region kernelRegion = createKernelRegion(regionDigraph);
        registerRegionDigraph(regionDigraph, this.bundleContext);
        createBundleListener(regionDigraph, kernelRegion);
        return regionDigraph;
    }

    private void createBundleListener(final RegionDigraph regionDigraph, final Region kernelRegion) {
        BundleContext systemBundleContext = getSystemBundleContext();
        systemBundleContext.addBundleListener(new SynchronousBundleListener() {

            @Override
            public void bundleChanged(BundleEvent event) {
                Bundle bundle = event.getBundle();
                switch (event.getType()) {
                    case BundleEvent.INSTALLED:
                        Bundle originBundle = event.getOrigin();
                        /*
                         * The system bundle is used, by BundleIdBasedRegion, to install bundles into arbitrary regions,
                         * so ignore it as an origin.
                         */
                        if (originBundle.getBundleId() != 0L) {
                            Region originRegion = regionDigraph.getRegion(originBundle);
                            if (originRegion != null) {
                                try {
                                    originRegion.addBundle(bundle);
                                } catch (BundleException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        break;
                    case BundleEvent.UNINSTALLED:
                        Region region = regionDigraph.getRegion(bundle);
                        if (region != null) {
                            region.removeBundle(bundle);
                        }
                        break;
                    default:
                        break;
                }

            }
        });

    }

    private Region createKernelRegion(RegionDigraph regionDigraph) throws BundleException {
        Region kernelRegion = new BundleIdBasedRegion(REGION_KERNEL, regionDigraph, getSystemBundleContext());
        regionDigraph.addRegion(kernelRegion);

        for (Bundle bundle : this.bundleContext.getBundles()) {
            kernelRegion.addBundle(bundle);
        }

        registerRegionService(kernelRegion);

        return kernelRegion;
    }

    private void registerRegionHooks(RegionDigraph regionDigraph) {
        registerResolverHookFactory(new RegionResolverHookFactory(regionDigraph));

        RegionBundleFindHook bundleFindHook = new RegionBundleFindHook(regionDigraph);

        registerBundleFindHook(bundleFindHook);

        registerBundleEventHook(new RegionBundleEventHook(bundleFindHook));

        RegionServiceFindHook serviceFindHook = new RegionServiceFindHook(regionDigraph);

        registerServiceFindHook(serviceFindHook);

        registerServiceEventHook(new RegionServiceEventHook(serviceFindHook));
    }

    private BundleContext getSystemBundleContext() {
        return this.bundleContext.getBundle(0L).getBundleContext();
    }

    private void registerRegionDigraph(RegionDigraph regionDigraph, BundleContext userRegionBundleContext) {
        this.tracker.track(this.bundleContext.registerService(RegionDigraph.class, regionDigraph, null));
        if (userRegionBundleContext != null) {
            this.tracker.track(userRegionBundleContext.registerService(RegionDigraph.class, regionDigraph, null));
        }
    }

    private void registerServiceFindHook(org.osgi.framework.hooks.service.FindHook serviceFindHook) {
        this.tracker.track(this.bundleContext.registerService(org.osgi.framework.hooks.service.FindHook.class, serviceFindHook, null));
    }

    private void registerServiceEventHook(org.osgi.framework.hooks.service.EventHook serviceEventHook) {
        this.tracker.track(this.bundleContext.registerService(org.osgi.framework.hooks.service.EventHook.class, serviceEventHook, null));
    }

    private void registerBundleFindHook(FindHook findHook) {
        this.tracker.track(this.bundleContext.registerService(FindHook.class, findHook, null));
    }

    private void registerBundleEventHook(EventHook eventHook) {
        this.tracker.track(this.bundleContext.registerService(EventHook.class, eventHook, null));

    }

    private void registerResolverHookFactory(ResolverHookFactory resolverHookFactory) {
        this.tracker.track(this.bundleContext.registerService(ResolverHookFactory.class, resolverHookFactory, null));
    }

    private void registerRegionService(Region region) {
        Dictionary<String, String> props = new Hashtable<String, String>();
        props.put("org.eclipse.virgo.kernel.region.name", region.getName());
        this.tracker.track(this.bundleContext.registerService(Region.class, region, props));
    }

    public void stop() {
        this.tracker.unregisterAll();
    }

}
