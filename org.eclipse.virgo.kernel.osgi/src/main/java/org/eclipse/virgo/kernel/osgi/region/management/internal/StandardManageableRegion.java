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

package org.eclipse.virgo.kernel.osgi.region.management.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.virgo.kernel.osgi.region.Region;
import org.eclipse.virgo.kernel.osgi.region.RegionDigraph;
import org.eclipse.virgo.kernel.osgi.region.RegionDigraph.FilteredRegion;
import org.eclipse.virgo.kernel.osgi.region.management.ManageableRegion;
import org.eclipse.virgo.kernel.osgi.region.management.ManageableRegionDigraph;

/**
 * {@link StandardManageableRegion} is the default implementation of {@link ManageableRegion}.
 * <p />
 * 
 * <strong>Concurrent Semantics</strong><br />
 * Thread safe.
 */
public class StandardManageableRegion implements ManageableRegion {

    private final Region region;

    private final ManageableRegionDigraph manageableRegionDigraph;

    private final RegionDigraph regionDigraph;

    public StandardManageableRegion(Region region, ManageableRegionDigraph manageableRegionDigraph, RegionDigraph regionDigraph) {
        this.region = region;
        this.manageableRegionDigraph = manageableRegionDigraph;
        this.regionDigraph = regionDigraph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return region.getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ManageableRegion[] getDependencies() {
        Set<FilteredRegion> edges = this.regionDigraph.getEdges(this.region);
        List<ManageableRegion> dependencies = new ArrayList<ManageableRegion>();
        for (FilteredRegion edge : edges) {
            ManageableRegion manageableRegion = this.manageableRegionDigraph.getRegion(edge.getRegion().getName());
            if (manageableRegion != null) {
                dependencies.add(manageableRegion);
            }
        }
        return dependencies.toArray(new ManageableRegion[dependencies.size()]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long[] getBundleIds() {
        Set<Long> bundleIds = this.region.getBundleIds();
        long[] result = new long[bundleIds.size()];
        int i = 0;
        for (Long bundleId : bundleIds) {
            result[i++] = bundleId;
        }
        return result;
    }

}
