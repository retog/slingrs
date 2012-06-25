/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.jaxrs;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.Servlet;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.wink.osgi.WinkRequestProcessor;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 * This component listens to registrations of services of type <code>java.lang.Object</code>
 * with the property <code>sling.ws.rs</code> set to true and registers servlets appropriate to
 * handle the requests.
 *
 */
@Component
@Reference(name="component", referenceInterface=Object.class, target="(sling.ws.rs=true)", 
		cardinality=ReferenceCardinality.OPTIONAL_MULTIPLE, policy=ReferencePolicy.DYNAMIC)
public class SlingRSRegistrar {
	
	@Reference
	private WinkRequestProcessor winkProvider;

	private final static Logger log = LoggerFactory.getLogger(SlingRSRegistrar.class);
	private Set<ServiceReference> pending = new HashSet<ServiceReference>();
	private Map<ServiceReference, ServiceRegistration> rs2ServletMap = new HashMap<ServiceReference, ServiceRegistration>();
	private ComponentContext context;

	protected void activate(ComponentContext context) {
		log.debug("Activating SlingRSRegistrar with {} components", pending.size());
		this.context = context;
		synchronized(pending) {
			for (ServiceReference component : pending) {
				registerComponent(component);
			}
		}
		pending.clear();
	}
	
	protected void deactivate(ComponentContext context) {
		this.context = null;
		for (Entry<ServiceReference, ServiceRegistration> entry : rs2ServletMap.entrySet()) {
			entry.getValue().unregister();
		}
		rs2ServletMap.clear();
	}
	
	/**
	 * @param serviceReference
	 *            The new Sling-RS component to bind.
	 */
	protected void bindComponent(ServiceReference serviceReference) {
		if (context != null) {
			registerComponent(serviceReference);
		} else {
			synchronized(pending) {
				pending.add(serviceReference);
			}
		}
	}
	/**
	 * @param serviceReference
	 *            The Sling-RS component to unbind.
	 */
	protected void unbindComponent(ServiceReference serviceReference) {
		if (context == null) {
			synchronized(pending) {
				pending.remove(serviceReference);
			}
		} else {
			rs2ServletMap.remove(serviceReference).unregister();
		}	
	}
	
	private void registerComponent(ServiceReference serviceReference) {
		Object component = context.getBundleContext().getService(serviceReference);
		log.debug("Registering sling-rs component: {}", component);
		Servlet servlet = new FixedResourceServlet(winkProvider, component);
		Dictionary<String, Object> properties = new Hashtable<String, Object>();
		for (String key : serviceReference.getPropertyKeys()) {
			properties.put(key, serviceReference.getProperty(key));
		}
		ServiceRegistration registration = context.getBundleContext()
				.registerService(Servlet.class.getName(), servlet, properties );
		rs2ServletMap.put(serviceReference, registration);
	}

	

}
