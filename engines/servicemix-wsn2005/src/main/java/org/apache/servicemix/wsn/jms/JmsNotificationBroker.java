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
package org.apache.servicemix.wsn.jms;

import java.net.URI;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.xml.namespace.QName;
import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeFactory;

import org.apache.servicemix.wsn.AbstractNotificationBroker;
import org.apache.servicemix.wsn.AbstractPublisher;
import org.apache.servicemix.wsn.AbstractSubscription;
import org.oasis_open.docs.wsrf.rp_2.GetResourcePropertyResponse;
import org.oasis_open.docs.wsrf.rp_2.InvalidResourcePropertyQNameFaultType;
import org.oasis_open.docs.wsrf.rw_2.ResourceUnavailableFault;
import org.oasis_open.docs.wsrf.rw_2.ResourceUnknownFault;
import org.oasis_open.docs.wsrf.rpw_2.InvalidResourcePropertyQNameFault;

public abstract class JmsNotificationBroker extends AbstractNotificationBroker {

    private ConnectionFactory connectionFactory;

    private Connection connection;

    public JmsNotificationBroker(String name) {
        super(name);
    }

    public void init() throws Exception {
        if (connection == null) {
            connection = connectionFactory.createConnection();
            connection.start();
        }
        super.init();
    }

    public void destroy() throws Exception {
        if (connection != null) {
            connection.close();
        }
        super.destroy();
    }

    @Override
    protected AbstractPublisher createPublisher(String name) {
        JmsPublisher publisher = createJmsPublisher(name);
        publisher.setManager(getManager());
        publisher.setConnection(connection);
        return publisher;
    }

    @Override
    protected AbstractSubscription createSubcription(String name) {
        JmsSubscription subscription = createJmsSubscription(name);
        subscription.setManager(getManager());
        subscription.setConnection(connection);
        return subscription;
    }

    protected abstract JmsSubscription createJmsSubscription(String name);

    protected abstract JmsPublisher createJmsPublisher(String name);

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    protected GetResourcePropertyResponse handleGetResourceProperty(QName property)
            throws ResourceUnavailableFault, ResourceUnknownFault, InvalidResourcePropertyQNameFault {
        if (TOPIC_EXPRESSION_QNAME.equals(property)) {
            // TODO
        } else if (FIXED_TOPIC_SET_QNAME.equals(property)) {
            // TODO
        } else if (TOPIC_EXPRESSION_DIALECT_QNAME.equals(property)) {
            GetResourcePropertyResponse r = new GetResourcePropertyResponse();
            r.getAny().add(new JAXBElement(TOPIC_EXPRESSION_DIALECT_QNAME, URI.class, JmsTopicExpressionConverter.SIMPLE_DIALECT));
            return r;
        } else if (TOPIC_SET_QNAME.equals(property)) {
            // TODO
        }
        return super.handleGetResourceProperty(property);
    }

}
