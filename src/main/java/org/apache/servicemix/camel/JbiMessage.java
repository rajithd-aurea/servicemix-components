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
package org.apache.servicemix.camel;

import java.util.Iterator;
import java.util.Map;

import javax.activation.DataHandler;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.NormalizedMessage;
import javax.xml.transform.Source;

import org.apache.camel.impl.DefaultMessage;

/**
 * A JBI {@link org.apache.camel.Message} which provides access to the underlying JBI features
 * such as {@link #getNormalizedMessage()}
 *
 * @version $Revision: 563665 $
 */
public class JbiMessage extends DefaultMessage {
    private NormalizedMessage normalizedMessage;

    public JbiMessage() {
    }

    public JbiMessage(NormalizedMessage normalizedMessage) {
        this.normalizedMessage = normalizedMessage;
    }

    @Override
    public String toString() {
        if (normalizedMessage != null) {
            return "JbiMessage: " + normalizedMessage;
        } else {
            return "JbiMessage: " + getBody();
        }
    }

    @Override
    public JbiExchange getExchange() {
        return (JbiExchange) super.getExchange();
    }

    /**
     * Returns the underlying JBI message
     *
     * @return the underlying JBI message
     */
    public NormalizedMessage getNormalizedMessage() {
        return normalizedMessage;
    }

    public void setNormalizedMessage(NormalizedMessage normalizedMessage) {
        this.normalizedMessage = normalizedMessage;
    }

    @Override
    public Object getHeader(String name) {
        Object answer = null;
        if (normalizedMessage != null) {
            answer = normalizedMessage.getProperty(name);
        }
        if (answer == null) {
            answer = super.getHeader(name);
        }
        return answer;
    }

    @Override
    public void setHeader(String name , Object value) {
        if (normalizedMessage != null) {
            normalizedMessage.setProperty(name, value);
        } else {
            super.setHeader(name, value);
        }
    }

    @Override
    public DataHandler getAttachment(String id) {
        DataHandler answer = null;
        if (normalizedMessage != null) {
            answer = normalizedMessage.getAttachment(id);
        }
        if (answer == null) {
            answer = super.getAttachment(id);
        }
        return answer;
    }

    @Override
    public void addAttachment(String id, DataHandler content) {
        if (normalizedMessage != null) {
            try {
                normalizedMessage.addAttachment(id, content);
            } catch (MessagingException e) {
                throw new JbiException(e);
            }
        } else {
            super.addAttachment(id, content);
        }
    }

    @Override
    public JbiMessage newInstance() {
        return new JbiMessage();
    }

    @Override
    protected Object createBody() {
        if (normalizedMessage != null) {
            return getExchange().getBinding().extractBodyFromJbi(getExchange(), normalizedMessage);
        }
        return null;
    }

    @Override
    protected void populateInitialHeaders(Map<String, Object> map) {
        if (normalizedMessage != null) {
            Iterator iter = normalizedMessage.getPropertyNames().iterator();
            while (iter.hasNext()) {
                String name = iter.next().toString();
                Object value = normalizedMessage.getProperty(name);
                map.put(name, value);
            }
        }
    }

    @Override
    protected void populateInitialAttachments(Map<String, DataHandler> map) {
        if (normalizedMessage != null) {
            Iterator iter = normalizedMessage.getAttachmentNames().iterator();
            while (iter.hasNext()) {
                String id = iter.next().toString();
                DataHandler content = normalizedMessage.getAttachment(id);
                map.put(id, content);
            }
        }
    }

//    @Override
    public void setBody(Object body) {
        if (normalizedMessage != null) {
            Source source = getExchange().getBinding().convertBodyToJbi(getExchange(), body);
            if (source != null) {
                try {
                    normalizedMessage.setContent(source);
                } catch (MessagingException e) {
                    throw new JbiException(e);
                }
            }
        }
        super.setBody(body);
    }
}