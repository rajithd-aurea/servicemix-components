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
package org.apache.servicemix.http;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.jbi.messaging.MessageExchange;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.servicemix.common.BaseServiceUnitManager;
import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.Deployer;
import org.apache.servicemix.common.Endpoint;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.util.IntrospectionSupport;
import org.apache.servicemix.common.util.URISupport;
import org.apache.servicemix.common.security.AuthenticationService;
import org.apache.servicemix.common.security.KeystoreManager;
import org.apache.servicemix.common.xbean.BaseXBeanDeployer;
import org.apache.servicemix.http.endpoints.HttpConsumerEndpoint;
import org.apache.servicemix.http.endpoints.HttpProviderEndpoint;
import org.apache.servicemix.http.jetty.JCLLogger;
import org.apache.servicemix.http.jetty.JettyContextManager;
import org.mortbay.thread.BoundedThreadPool;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="component" description="An http component"
 */
public class HttpComponent extends DefaultComponent {

    public static final String[] EPR_PROTOCOLS = {"http:", "https"};

    static {
        JCLLogger.init();
    }

    protected ContextManager server;
    protected HttpClient client;
    protected MultiThreadedHttpConnectionManager connectionManager;
    protected org.mortbay.jetty.client.HttpClient connectionPool;
    protected HttpConfiguration configuration = new HttpConfiguration();
    protected HttpEndpointType[] endpoints;

    protected String protocol;
    protected String host;
    protected int port = 80;
    protected String path;

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @param host
     *            the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * @param path
     *            the path to set
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port
     *            the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return the protocol
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * @param protocol
     *            the protocol to set
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * @return the endpoints
     */
    public HttpEndpointType[] getEndpoints() {
        return endpoints;
    }

    /**
     * @param endpoints
     *            the endpoints to set
     */
    public void setEndpoints(HttpEndpointType[] endpoints) {
        this.endpoints = endpoints;
    }

    public ContextManager getServer() {
        return server;
    }

    public void setServer(ContextManager server) {
        this.server = server;
    }

    public HttpClient getClient() {
        return client;
    }

    public void setClient(HttpClient client) {
        this.client = client;
    }

    public org.mortbay.jetty.client.HttpClient getConnectionPool() {
        return connectionPool;
    }
    
    public void setConnectionPool(org.mortbay.jetty.client.HttpClient connectionPool) {
        this.connectionPool = connectionPool;
    }

    /**
     * @return Returns the configuration.
     * @org.apache.xbean.Flat
     */
    public HttpConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(HttpConfiguration configuration) {
        this.configuration = configuration;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.servicemix.common.BaseComponentLifeCycle#getExtensionMBean()
     */
    protected Object getExtensionMBean() throws Exception {
        return configuration;
    }

    protected void doInit() throws Exception {
        super.doInit();
        // Load configuration
        configuration.setRootDir(context.getWorkspaceRoot());
        configuration.setComponentName(context.getComponentName());
        configuration.load();
        // Lookup keystoreManager and authenticationService
        if (configuration.getKeystoreManager() == null) {
            try {
                String name = configuration.getKeystoreManagerName();
                Object km = context.getNamingContext().lookup(name);
                configuration.setKeystoreManager(km);
            } catch (Throwable e) {
                // ignore
            }
        }
        if (configuration.getAuthenticationService() == null) {
            try {
                String name = configuration.getAuthenticationServiceName();
                Object as = context.getNamingContext().lookup(name);
                configuration.setAuthenticationService(as);
            } catch (Throwable e) {
                try {
                    Class cl = Class.forName("org.apache.servicemix.jbi.security.auth.impl.JAASAuthenticationService");
                    configuration.setAuthenticationService(cl.newInstance());
                } catch (Throwable t) {
                    logger.warn("Unable to retrieve or create the authentication service");
                }
            }
        }
        // Create client
        if (client == null) {
            connectionManager = new MultiThreadedHttpConnectionManager();
            HttpConnectionManagerParams params = new HttpConnectionManagerParams();
            params.setDefaultMaxConnectionsPerHost(configuration.getMaxConnectionsPerHost());
            params.setMaxTotalConnections(configuration.getMaxTotalConnections());
            connectionManager.setParams(params);
            client = new HttpClient(connectionManager);
        }
        // Create connectionPool
        if (connectionPool == null) {
            connectionPool = new org.mortbay.jetty.client.HttpClient();
            BoundedThreadPool btp = new BoundedThreadPool();
            btp.setMaxThreads(this.configuration.getJettyClientThreadPoolSize());
            connectionPool.setThreadPool(btp);
            connectionPool.start();
        }
        // Create serverManager
        if (configuration.isManaged()) {
            server = new ManagedContextManager();
        } else {
            JettyContextManager jcm = new JettyContextManager();
            jcm.setMBeanServer(context.getMBeanServer());
            this.server = jcm;
        }
        server.setConfiguration(configuration);
        server.init();
    }

    protected void doShutDown() throws Exception {
        super.doShutDown();
        if (server != null) {
            ContextManager s = server;
            server = null;
            s.shutDown();
        }
        if (connectionPool != null) {
            connectionPool.stop();
            connectionPool = null;
        }
        if (connectionManager != null) {
            connectionManager.shutdown();
            connectionManager = null;
            client = null;
        }
    }

    protected void doStart() throws Exception {
        server.start();
        super.doStart();
    }

    protected void doStop() throws Exception {
        super.doStop();
        server.stop();
    }

    protected String[] getEPRProtocols() {
        return EPR_PROTOCOLS;
    }

    protected Endpoint getResolvedEPR(ServiceEndpoint ep) throws Exception {
        // We receive an exchange for an EPR that has not been used yet.
        // Register a provider endpoint and restart processing.
        HttpEndpoint httpEp = new HttpEndpoint();
        httpEp.setServiceUnit(new ServiceUnit(component));
        httpEp.setService(ep.getServiceName());
        httpEp.setEndpoint(ep.getEndpointName());
        httpEp.setRole(MessageExchange.Role.PROVIDER);
        URI uri = new URI(ep.getEndpointName());
        Map map = URISupport.parseQuery(uri.getQuery());
        if (IntrospectionSupport.setProperties(httpEp, map, "http.")) {
            uri = URISupport.createRemainingURI(uri, map);
        }
        if (httpEp.getLocationURI() == null) {
            httpEp.setLocationURI(uri.toString());
        }
        httpEp.activateDynamic();
        return httpEp;
    }

    /**
     * @return the keystoreManager
     */
    public Object getKeystoreManager() {
        return configuration.getKeystoreManager();
    }

    /**
     * @param keystoreManager
     *            the keystoreManager to set
     */
    public void setKeystoreManager(Object keystoreManager) {
        this.configuration.setKeystoreManager(keystoreManager);
    }

    /**
     * @return the authenticationService
     */
    public Object getAuthenticationService() {
        return configuration.getAuthenticationService();
    }

    /**
     * @param authenticationService
     *            the authenticationService to set
     */
    public void setAuthenticationService(Object authenticationService) {
        this.configuration.setAuthenticationService(authenticationService);
    }

    /**
     * When servicemix-http is embedded inside a web application and configured to reuse the existing servlet container,
     * this method will create and return the HTTPProcessor which will handle all servlet calls
     */
    public HttpProcessor getMainProcessor() {
        return server.getMainProcessor();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.servicemix.common.BaseComponent#createServiceUnitManager()
     */
    public BaseServiceUnitManager createServiceUnitManager() {
        Deployer[] deployers = 
            new Deployer[] {new BaseXBeanDeployer(this, getEndpointClasses()),
                            new HttpWsdl1Deployer(this)};
        return new BaseServiceUnitManager(this, deployers);
    }

    protected List getConfiguredEndpoints() {
        return asList(endpoints);
    }

    protected Class[] getEndpointClasses() {
        return new Class[] {HttpEndpoint.class, HttpConsumerEndpoint.class, HttpProviderEndpoint.class};
    }

}