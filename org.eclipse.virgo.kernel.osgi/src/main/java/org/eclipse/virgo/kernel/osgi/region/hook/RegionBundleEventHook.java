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

package org.eclipse.virgo.kernel.osgi.region.hook;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.virgo.kernel.core.FatalKernelException;
import org.eclipse.virgo.kernel.osgi.region.Region;
import org.eclipse.virgo.kernel.osgi.region.RegionDigraph;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.hooks.bundle.EventHook;
import org.osgi.framework.hooks.bundle.FindHook;

/**
 * {@link RegionBundleEventHook} manages the visibility of bundle events across regions according to the
 * {@link RegionDigraph}.
 * <p>
 * The current implementation delegates to {@link RegionBundleFindHook}. This is likely to perform adequately because of
 * the low frequency of bundle events and the typically small number of bundle listeners.
 * <p />
 * 
 * <strong>Concurrent Semantics</strong><br />
 * Thread safe.
 */
public final class RegionBundleEventHook implements EventHook {

    private final RegionDigraph regionDigraph;

    private final FindHook bundleFindHook;

    private final ThreadLocal<Region> threadLocal;

    public RegionBundleEventHook(RegionDigraph regionDigraph, FindHook bundleFindBook, ThreadLocal<Region> threadLocal) {
        this.regionDigraph = regionDigraph;
        this.bundleFindHook = bundleFindBook;
        this.threadLocal = threadLocal;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void event(BundleEvent event, Collection<BundleContext> contexts) {
        Bundle eventBundle = event.getBundle();
        if (event.getType() == BundleEvent.INSTALLED) {
            bundleInstalled(eventBundle, event.getOrigin());
        }
        Iterator<BundleContext> i = contexts.iterator();
        while (i.hasNext()) {
            if (!find(i.next(), eventBundle)) {
                i.remove();
            }
        }
        if (event.getType() == BundleEvent.UNINSTALLED) {
            bundleUninstalled(eventBundle);
        }
    }

    private boolean find(BundleContext finderBundleContext, Bundle candidateBundle) {
        Set<Bundle> candidates = new HashSet<Bundle>();
        candidates.add(candidateBundle);
        this.bundleFindHook.find(finderBundleContext, candidates);
        return !candidates.isEmpty();
    }

    private void bundleInstalled(Bundle eventBundle, Bundle originBundle) {
        /*
         * BundleIdBasedRegion sets thread local to install bundles into arbitrary regions. If this is not set, the
         * bundle inherits the region of the origin bundle.
         */
        Region installRegion = this.threadLocal.get();
        if (installRegion != null) {
            try {
                installRegion.addBundle(eventBundle);
            } catch (BundleException e) {
                e.printStackTrace();
                throw new FatalKernelException("Bundle could not be added to region", e);
            }
        } else {
            Region originRegion = this.regionDigraph.getRegion(originBundle);
            if (originRegion != null) {
                try {
                    originRegion.addBundle(eventBundle);
                } catch (BundleException e) {
                    e.printStackTrace();
                    throw new FatalKernelException("Bundle could not be added to region", e);
                }
            }
        }
    }

    private void bundleUninstalled(Bundle eventBundle) {
        Region region = this.regionDigraph.getRegion(eventBundle);
        if (region != null) {
            region.removeBundle(eventBundle);
        }
    }

}
