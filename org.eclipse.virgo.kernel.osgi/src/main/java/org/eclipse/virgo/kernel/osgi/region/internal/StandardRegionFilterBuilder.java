/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.virgo.kernel.osgi.region.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.virgo.kernel.osgi.region.RegionFilter;
import org.eclipse.virgo.kernel.osgi.region.RegionFilterBuilder;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;

public class StandardRegionFilterBuilder implements RegionFilterBuilder {

    private final Object monitor = new Object();

    private final Map<String, Collection<Filter>> policy = new HashMap<String, Collection<Filter>>();

    @Override
    public RegionFilterBuilder allow(String namespace, String filter) throws InvalidSyntaxException {
        if (namespace == null)
            throw new IllegalArgumentException("The namespace must not be null.");
        if (filter == null)
            throw new IllegalArgumentException("The filter must not be null.");
        synchronized (this.monitor) {
            Collection<Filter> namespaceFilters = policy.get(namespace);
            if (namespaceFilters == null) {
                namespaceFilters = new ArrayList<Filter>();
                policy.put(namespace, namespaceFilters);
            }
            // TODO need to use BundleContext.createFilter here
            namespaceFilters.add(FrameworkUtil.createFilter(filter));
        }
        return this;
    }

    @Override
    public RegionFilter build() {
        synchronized (this.monitor) {
            return new StandardRegionFilter(policy);
        }
    }
}
