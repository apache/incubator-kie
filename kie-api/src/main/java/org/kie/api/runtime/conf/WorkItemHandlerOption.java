/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.api.runtime.conf;

import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.annotations.KieProperty;

/**
 * WorkItemHandlers configuration option
 */
public class WorkItemHandlerOption implements MultiValueKieSessionOption {

    private static final long serialVersionUID = 510l;

    /**
     * The prefix for the property name for work item handlers
     */
    @KieProperty
    public static final String PROPERTY_NAME = "drools.workItemHandlers";

    /**
     * work item handler name
     */
    private final String name;

    /**
     * the accumulate function instance
     */
    private final WorkItemHandler handler;

    /**
     * Private constructor to enforce the use of the factory method
     * @param name
     */
    private WorkItemHandlerOption( final String name, final WorkItemHandler handler ) {
        this.name = name;
        this.handler = handler;
    }

    /**
     * This is a factory method for this WorkItemHandler configuration.
     * The factory method is a best practice for the case where the
     * actual object construction is changed in the future.
     *
     * @param name the name of the work item handler to be configured
     *
     * @return the actual type safe work item handler configuration.
     */
    public static WorkItemHandlerOption get( final String name, final WorkItemHandler handler ) {
        return new WorkItemHandlerOption( name, handler );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPropertyName() {
        return PROPERTY_NAME+name;
    }

    /**
     * @return the name of the configured work item handler
     */
    public String getName() {
        return name;
    }

    /**
     * @return the work item handler instance
     */
    public WorkItemHandler getHandler() {
        return handler;
    }

    @Override
    public String toString() {
        return "WorkItemHandler( name="+name+" handler="+handler+" )";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((handler == null) ? 0 : handler.getClass().hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj ) { return true; }
        if ( obj == null ) { return false; }
        if ( getClass() != obj.getClass() ) { return false; }
        WorkItemHandlerOption other = (WorkItemHandlerOption) obj;
        if ( handler == null ) {
            if ( other.handler != null ) {
                return false;
            }
        } else if ( other.handler == null ) {
            return false;
        } else if ( !handler.getClass().equals( other.handler.getClass() ) ) {
            return false;
        }
        if ( name == null ) {
            if ( other.name != null ) {
                return false;
            }
        } else if ( !name.equals( other.name ) ) {
            return false;
        }
        return true;
    }
}
