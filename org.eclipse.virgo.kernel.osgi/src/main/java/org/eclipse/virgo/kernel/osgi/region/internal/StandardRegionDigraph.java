/*******************************************************************************
 * This file is part of the Virgo Web Server.
 *
 * Copyright (c) 2011 VMware Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SpringSource, a division of VMware - initial API and implementation and/or initial documentation
 *******************************************************************************/

package org.eclipse.virgo.kernel.osgi.region.internal;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.virgo.kernel.osgi.region.Region;
import org.eclipse.virgo.kernel.osgi.region.RegionDigraph;
import org.eclipse.virgo.kernel.osgi.region.RegionDigraphPersistence;
import org.eclipse.virgo.kernel.osgi.region.RegionDigraphVisitor;
import org.eclipse.virgo.kernel.osgi.region.RegionFilter;
import org.eclipse.virgo.kernel.osgi.region.RegionFilterBuilder;
import org.eclipse.virgo.kernel.serviceability.NonNull;
import org.eclipse.virgo.util.math.OrderedPair;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

/**
 * {@link StandardRegionDigraph} is the default implementation of {@link RegionDigraph}.
 * <p />
 * 
 * <strong>Concurrent Semantics</strong><br />
 * 
 * Thread safe.
 * 
 */
public final class StandardRegionDigraph implements RegionDigraph {

    private final Object monitor = new Object();

    private final Set<Region> regions = new HashSet<Region>();

    private final Map<OrderedPair<Region, Region>, RegionFilter> filter = new HashMap<OrderedPair<Region, Region>, RegionFilter>();

    private final BundleContext bundleContext;

    private final ThreadLocal<Region> threadLocal;

    private final SubgraphTraverser subgraphTraverser;

    StandardRegionDigraph() {
        this(null, null);
    }

    public StandardRegionDigraph(BundleContext bundleContext, ThreadLocal<Region> threadLocal) {
        this.subgraphTraverser = new SubgraphTraverser();
        this.bundleContext = bundleContext;
        this.threadLocal = threadLocal;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Region createRegion(String regionName) throws BundleException {
        Region region = new BundleIdBasedRegion(regionName, this, this.bundleContext, this.threadLocal);
        synchronized (this.monitor) {
            if (getRegion(regionName) != null) {
                throw new BundleException("Region '" + regionName + "' already exists", BundleException.UNSUPPORTED_OPERATION);
            }
            this.regions.add(region);
        }
        notifyAdded(region);
        return region;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect(@NonNull Region tailRegion, @NonNull RegionFilter filter, @NonNull Region headRegion) throws BundleException {
        if (headRegion.equals(tailRegion)) {
            throw new BundleException("Cannot connect region '" + headRegion + "' to itself", BundleException.UNSUPPORTED_OPERATION);
        }
        OrderedPair<Region, Region> nodePair = new OrderedPair<Region, Region>(tailRegion, headRegion);
        boolean tailAdded = false;
        boolean headAdded = false;
        synchronized (this.monitor) {
            if (this.filter.containsKey(nodePair)) {
                throw new BundleException("Region '" + tailRegion + "' is already connected to region '" + headRegion,
                    BundleException.UNSUPPORTED_OPERATION);
            } else {
                checkFilterDoesNotAllowExistingBundle(tailRegion, filter);
                tailAdded = this.regions.add(tailRegion);
                headAdded = this.regions.add(headRegion);
                this.filter.put(nodePair, filter);
            }
        }
        if (tailAdded) {
            notifyAdded(tailRegion);
        }
        if (headAdded) {
            notifyAdded(headRegion);
        }
    }

    private void checkFilterDoesNotAllowExistingBundle(Region tailRegion, RegionFilter filter) throws BundleException {
        // TODO: enumerate the bundles in the region and check the filter does not allow any of them
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<Region> iterator() {
        synchronized (this.monitor) {
            Set<Region> snapshot = new HashSet<Region>(this.regions.size());
            snapshot.addAll(this.regions);
            return snapshot.iterator();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<FilteredRegion> getEdges(Region tailRegion) {
        Set<FilteredRegion> edges = new HashSet<FilteredRegion>();
        synchronized (this.monitor) {
            Set<OrderedPair<Region, Region>> regionPairs = this.filter.keySet();
            for (OrderedPair<Region, Region> regionPair : regionPairs) {
                if (tailRegion.equals(regionPair.getFirst())) {
                    edges.add(new StandardFilteredRegion(regionPair.getSecond(), this.filter.get(regionPair)));
                }
            }
        }
        return edges;
    }

    private static class StandardFilteredRegion implements FilteredRegion {

        private Region region;

        private RegionFilter regionFilter;

        private StandardFilteredRegion(Region region, RegionFilter regionFilter) {
            this.region = region;
            this.regionFilter = regionFilter;
        }

        @Override
        public Region getRegion() {
            return this.region;
        }

        @Override
        public RegionFilter getFilter() {
            return this.regionFilter;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Region getRegion(@NonNull String regionName) {
        synchronized (this.monitor) {
            for (Region region : this) {
                if (regionName.equals(region.getName())) {
                    return region;
                }
            }
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Region getRegion(@NonNull Bundle bundle) {
        synchronized (this.monitor) {
            for (Region region : this) {
                if (region.contains(bundle)) {
                    return region;
                }
            }
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Region getRegion(long bundleId) {
        synchronized (this.monitor) {
            for (Region region : this) {
                if (region.contains(bundleId)) {
                    return region;
                }
            }
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeRegion(@NonNull Region region) {
        notifyRemoving(region);
        synchronized (this.monitor) {
            this.regions.remove(region);
            Iterator<OrderedPair<Region, Region>> i = this.filter.keySet().iterator();
            while (i.hasNext()) {
                OrderedPair<Region, Region> regionPair = i.next();
                if (region.equals(regionPair.getFirst()) || region.equals(regionPair.getSecond())) {
                    i.remove();
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        synchronized (this.monitor) {
            StringBuffer s = new StringBuffer();
            boolean first = true;
            s.append("RegionDigraph{");
            for (Region r : this) {
                if (!first) {
                    s.append(", ");
                }
                s.append(r);
                first = false;
            }
            s.append("}");
            s.append("[");
            first = true;
            for (OrderedPair<Region, Region> regionPair : this.filter.keySet()) {
                if (!first) {
                    s.append(", ");
                }
                s.append(regionPair.getFirst() + "->" + regionPair.getSecond());
                first = false;
            }
            s.append("]");
            return s.toString();
        }
    }

    @Override
    public Set<Region> getRegions() {
        Set<Region> result = new HashSet<Region>();
        synchronized (this.monitor) {
            result.addAll(this.regions);
        }
        return result;
    }

    @Override
    public RegionFilterBuilder createRegionFilterBuilder() {
        return new StandardRegionFilterBuilder();
    }

    private void notifyAdded(Region region) {
        Set<RegionLifecycleListener> listeners = getListeners();
        for (RegionLifecycleListener listener : listeners) {
            listener.regionAdded(region);
        }
    }

    private void notifyRemoving(Region region) {
        Set<RegionLifecycleListener> listeners = getListeners();
        for (RegionLifecycleListener listener : listeners) {
            listener.regionRemoving(region);
        }
    }

    private Set<RegionLifecycleListener> getListeners() {
        Set<RegionLifecycleListener> listeners = new HashSet<RegionLifecycleListener>();
        if (this.bundleContext == null)
            return listeners;
        try {
            Collection<ServiceReference<RegionLifecycleListener>> listenerServiceReferences = this.bundleContext.getServiceReferences(
                RegionLifecycleListener.class, null);
            for (ServiceReference<RegionLifecycleListener> listenerServiceReference : listenerServiceReferences) {
                RegionLifecycleListener regionLifecycleListener = this.bundleContext.getService(listenerServiceReference);
                if (regionLifecycleListener != null) {
                    listeners.add(regionLifecycleListener);
                }
            }
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }
        return listeners;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void visitSubgraph(Region startingRegion, RegionDigraphVisitor visitor) {
        this.subgraphTraverser.visitSubgraph(startingRegion, visitor);
    }

    /**
     * Returns a snapshot of filtered regions
     * 
     * @return a snapshot of filtered regions
     */
    Map<Region, Set<FilteredRegion>> getFilteredRegions() {
        Map<Region, Set<FilteredRegion>> result = new HashMap<Region, Set<FilteredRegion>>();
        synchronized (this.monitor) {
            for (Region region : regions) {
                result.put(region, getEdges(region));
            }
        }
        return result;
    }

    @Override
    public RegionDigraphPersistence getRegionDigraphPersistence() {
        return new StandardRegionDigraphPersistence();
    }
}
