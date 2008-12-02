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
package org.apache.servicemix.cxfbc;

import java.util.List;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.servicemix.common.DefaultComponent;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="component" description="a JBI component for hosting endpoints that can use either SOAP/HTTP or SOAP/JMS."
 */
public class CxfBcComponent extends DefaultComponent {

    private CxfBcEndpointType[] endpoints;

    private Bus bus;

    private String busCfg;
    
    private CxfBcConfiguration configuration = new CxfBcConfiguration();
    
    /**
     * @return the endpoints
     */
    public CxfBcEndpointType[] getEndpoints() {
        return endpoints;
    }

    /**
    * Specifies the list of endpoints hosted by the component.
     * @param endpoints
     *            the endpoints to set
     * @org.apache.xbean.Property description="the list of endpoints hosted by the component"
     */
    public void setEndpoints(CxfBcEndpointType[] endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    protected List getConfiguredEndpoints() {
        return asList(endpoints);
    }

    @Override
    protected Class[] getEndpointClasses() {
        return new Class[] {CxfBcProvider.class, CxfBcConsumer.class};
    }

    @Override
    protected void doInit() throws Exception {
        if (getBusConfig() != null) {
            SpringBusFactory bf = new SpringBusFactory();
            bus = bf.createBus(getBusConfig());
        } else {
            bus = BusFactory.getDefaultBus();
        }
        if (getConfiguration().getAuthenticationService() == null) {
            try {
                String name = getConfiguration().getAuthenticationServiceName();
                Object as = context.getNamingContext().lookup(name);
                getConfiguration().setAuthenticationService(as);
            } catch (Throwable e) {
                try {
                    Class cl = Class.forName("org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService");
                    getConfiguration().setAuthenticationService(cl.newInstance());
                } catch (Throwable t) {
                    logger.warn("Unable to retrieve or create the authentication service");
                }
            }
        }
        super.doInit();
    }
    
    @Override
    protected void doShutDown() throws Exception {
        if (bus != null) {
            bus.shutdown(true);
            BusFactory.setThreadDefaultBus(null);
        }
        super.doShutDown();
    }

    public Bus getBus() {
        return bus;
    }

    /**
        * Specifies the location of the CXF configuraiton file used to configure
        * the CXF bus. This allows you to access features like JMS runtime 
        * behavior and WS-RM. The configuration set at the component level is
        * superceeded by any configuration specified by an endpoint.
        *
        * @param busCfg a string containing the relative path to the configuration file
        * @org.apache.xbean.Property description="the location of the CXF configuration file used to configure the CXF bus for all endpoints in the container. Endpoint-specific configuration overrides these settings. This allows you to configure features like WS-RM and JMS runtime behavior."
        **/
    public void setBusConfig(String busConfig) {
        this.busCfg = busConfig;
    }

    public String getBusConfig() {
        return busCfg;
    }

    public void setConfiguration(CxfBcConfiguration configuration) {
        this.configuration = configuration;
    }

    public CxfBcConfiguration getConfiguration() {
        return configuration;
    }
}
