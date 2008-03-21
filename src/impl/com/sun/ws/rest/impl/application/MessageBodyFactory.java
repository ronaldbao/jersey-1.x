/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").  You may not use this file
 * except in compliance with the License. 
 * 
 * You can obtain a copy of the License at:
 *     https://jersey.dev.java.net/license.txt
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at:
 *     https://jersey.dev.java.net/license.txt
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 *     "Portions Copyrighted [year] [name of copyright owner]"
 */

package com.sun.ws.rest.impl.application;

import com.sun.ws.rest.impl.model.MediaTypeHelper;
import com.sun.ws.rest.spi.container.MessageBodyContext;
import com.sun.ws.rest.spi.service.ServiceFinder;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.ConsumeMime;
import javax.ws.rs.ProduceMime;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWorkers;
import javax.ws.rs.ext.MessageBodyWriter;

/**
 *
 * @author Paul.Sandoz@Sun.Com
 */
public final class MessageBodyFactory implements MessageBodyContext, MessageBodyWorkers {
    private static final Logger LOGGER = Logger.getLogger(MessageBodyFactory.class.getName());
    
    private final ComponentProviderCache componentProviderCache;
    
    private final Map<MediaType, List<MessageBodyReader>> readerProviders;
    
    private final Map<MediaType, List<MessageBodyWriter>> writerProviders;
    
    public MessageBodyFactory(ComponentProviderCache componentProviderCache) {
        this.componentProviderCache = componentProviderCache;
        this.readerProviders = getProviderMap(MessageBodyReader.class, ConsumeMime.class);    
        this.writerProviders = getProviderMap(MessageBodyWriter.class, ProduceMime.class);
    }
                
    private <T> Map<MediaType, List<T>> getProviderMap(
            Class<T> serviceClass,
            Class<?> annotationClass) {

        // Get the application-defined provider classes that implement serviceClass
        Set<Class> pcs =new LinkedHashSet<Class>(
                componentProviderCache.getProviderClasses(serviceClass)); 
        
        // Get the service-defined provider classes that implement serviceClass
        LOGGER.log(Level.CONFIG, "Searching for providers that implement: " + serviceClass);
        Class<T>[] pca = ServiceFinder.find(serviceClass, true).toClassArray();
        for (Class pc : pca)
            LOGGER.log(Level.CONFIG, "    Provider found: " + pc);
        
        // Add service-defined providers to the set after application-defined
        for (Class pc : pca)
            pcs.add(pc);
                        
        Map<MediaType, List<T>> s = new HashMap<MediaType, List<T>>();
        for (Class providerClass : pcs) {
            Object o = componentProviderCache.getComponent(providerClass);
            if (o == null) continue;
            
            T provider = serviceClass.cast(o);
            
            String values[] = getAnnotationValues(providerClass, annotationClass);
            if (values==null)
                getClassCapability(s, provider, MediaTypeHelper.GENERAL_MEDIA_TYPE);
            else
                for (String type: values)
                    getClassCapability(s, provider, MediaType.parse(type));            
        }
        
        return s;
    }

    private <T> void getClassCapability(Map<MediaType, List<T>> capabilities, 
            T provider, MediaType mediaType) {
        if (!capabilities.containsKey(mediaType))
            capabilities.put(mediaType, new ArrayList<T>());
        
        List<T> providers = capabilities.get(mediaType);
        providers.add(provider);
    }
    
    private String[] getAnnotationValues(Class<?> clazz, Class<?> annotationClass) {
        String values[] = null;
        if (annotationClass.equals(ConsumeMime.class)) {
            ConsumeMime consumes = clazz.getAnnotation(ConsumeMime.class);
            if (consumes != null)
                values = consumes.value();
        } else if (annotationClass.equals(ProduceMime.class)) {
            ProduceMime produces = clazz.getAnnotation(ProduceMime.class);
            if (produces != null)
                values = produces.value();
        }
        return values;
    }
    
    // MessageBodyContext
    
    @SuppressWarnings("unchecked")
    public <T> MessageBodyReader<T> getMessageBodyReader(Class<T> type, MediaType mediaType) {
        List<MediaType> searchTypes = createSearchList(mediaType);
        for (MediaType t: searchTypes) {
            List<MessageBodyReader> readers = readerProviders.get(t);
            if (readers==null)
                continue;
            for (MessageBodyReader p: readers) {
                if (p.isReadable(type, null, null))
                    return p;
            }
        }
        
        throw new IllegalArgumentException("A message body reader for Java type, " + type + 
                ", and MIME media type, " + mediaType + ", was not found");    
    }

    @SuppressWarnings("unchecked")
    public <T> MessageBodyWriter<T> getMessageBodyWriter(Class<T> type, MediaType mediaType) {
        List<MediaType> searchTypes = createSearchList(mediaType);
        for (MediaType t: searchTypes) {
            List<MessageBodyWriter> writers = writerProviders.get(t);
            if (writers==null)
                continue;
            for (MessageBodyWriter p: writers) {
                if (p.isWriteable(type, null, null))
                    return p;
            }
        }
        
        throw new IllegalArgumentException("A message body writer for Java type, " + type + 
                ", and MIME media type, " + mediaType + ", was not found");
    }

    private List<MediaType> createSearchList(MediaType mediaType) {
        if (mediaType==null)
            return Arrays.asList(MediaTypeHelper.GENERAL_MEDIA_TYPE);
        else
            return Arrays.asList(mediaType, 
                    new MediaType(mediaType.getType(), MediaType.MEDIA_TYPE_WILDCARD), 
                    MediaTypeHelper.GENERAL_MEDIA_TYPE);
    }

    // MessageBodyWorkers
    
    public <T> List<MessageBodyReader<T>> getMessageBodyReaders(
            MediaType mediaType, Class<T> type, Type genericType, Annotation annotations[]) {
        return Collections.singletonList(getMessageBodyReader(type, mediaType));
    }

    public <T> List<MessageBodyWriter<T>> getMessageBodyWriters(
            MediaType mediaType, Class<T> type, Type genericType, Annotation annotations[]) {
        return Collections.singletonList(getMessageBodyWriter(type, mediaType));
    }
}