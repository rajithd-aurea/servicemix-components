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
package org.apache.servicemix.jsr181;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOut;
import javax.xml.namespace.QName;

import org.apache.servicemix.client.DefaultServiceMixClient;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.jbi.jaxp.StringSource;
import org.apache.servicemix.tck.SpringTestSupport;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;

import test.Echo;

public class Jsr181SpringTest extends SpringTestSupport {

    private final Logger logger =  LoggerFactory.getLogger(Jsr181ComponentTest.class);

    public void test() throws Exception {
        DefaultServiceMixClient client = new DefaultServiceMixClient(jbi);
        InOut me = client.createInOutExchange();
        me.setInterfaceName(new QName("http://test", "EchoPortType"));
        me.getInMessage().setContent(new StringSource("<echo xmlns='http://test'><echoin0>world</echoin0></echo>"));
        client.sendSync(me);
        if (me.getStatus() == ExchangeStatus.ERROR) {
            if (me.getFault() != null) {
                fail("Received fault: " + new SourceTransformer().toString(me.getFault().getContent()));
            } else if (me.getError() != null) {
                throw me.getError();
            } else {
                fail("Received ERROR status");
            }
        } else {
            logger.info(new SourceTransformer().toString(me.getOutMessage().getContent()));
        }
    }
    
    public void testProxy() throws Exception {
        Echo echo = (Echo) context.getBean("proxy");
        String result = echo.echo("world");
        assertEquals("world", result);
    }
    
    public void testProxyOneWay() throws Exception {
        Echo echo = (Echo) context.getBean("proxy");
        echo.oneWay("world");
    }
    
    protected AbstractXmlApplicationContext createBeanFactory() {
        return new ClassPathXmlApplicationContext("org/apache/servicemix/jsr181/spring.xml");
    }
    
}
