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


import java.util.Objects;
import org.kie.api.annotations.KieProperty;

/**
 * A class for the session persistence configuration.
 */
public class PersistedSessionOption implements SingleValueKieSessionOption {

    public enum PersistenceStrategy {
        FULL, STORES_ONLY
    }

    public enum PersistenceObjectsStrategy {
        SIMPLE, OBJECT_REFERENCES
    }

    public enum SafepointStrategy {
        ALWAYS, AFTER_FIRE, EXPLICIT;

        public boolean useSafepoints() {
            return this != ALWAYS;
        }
    }

    /**
     * NONE : On restoring a session, filter firing based on StoredObject.isPropagated
     * ACTIVATION_KEY : On restoring a session, filter firing based on StoredObject.isPropagated and persisted ActivationKey
     */
    public enum ActivationStrategy {
        NONE, ACTIVATION_KEY
    }

    /**
     * The property name for the clock type configuration
     */
    @KieProperty
    public static final String PROPERTY_NAME = "drools.persistedsession";

    private final long sessionId;

    private PersistenceStrategy persistenceStrategy = PersistenceStrategy.FULL;

    private PersistenceObjectsStrategy persistenceObjectsStrategy = PersistenceObjectsStrategy.SIMPLE;

    private SafepointStrategy safepointStrategy = SafepointStrategy.ALWAYS;

    private ActivationStrategy activationStrategy = ActivationStrategy.NONE;

    private PersistedSessionOption() {
        this(-1L);
    }

    private PersistedSessionOption(long sessionId) {
        this.sessionId = sessionId;
    }

    public static PersistedSessionOption newSession() {
        return new PersistedSessionOption();
    }

    public static PersistedSessionOption fromSession(long sessionId) {
        return new PersistedSessionOption(sessionId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPropertyName() {
        return PROPERTY_NAME;
    }

    public long getSessionId() {
        return sessionId;
    }

    public PersistenceStrategy getPersistenceStrategy() {
        return persistenceStrategy;
    }

    public PersistenceObjectsStrategy getPersistenceObjectsStrategy() {return persistenceObjectsStrategy;}

    public PersistedSessionOption withPersistenceObjectsStrategy(PersistenceObjectsStrategy persistenceObjectsStrategy){
        this.persistenceObjectsStrategy = persistenceObjectsStrategy;
        return this;
    }

    public PersistedSessionOption withPersistenceStrategy(PersistenceStrategy persistenceStrategy) {
        this.persistenceStrategy = persistenceStrategy;
        return this;
    }

    public SafepointStrategy getSafepointStrategy() {
        return safepointStrategy;
    }

    public PersistedSessionOption withSafepointStrategy(SafepointStrategy safepointStrategy) {
        this.safepointStrategy = safepointStrategy;
        return this;
    }

    public ActivationStrategy getActivationStrategy() {
        return activationStrategy;
    }

    public PersistedSessionOption withActivationStrategy(ActivationStrategy activationStrategy) {
        this.activationStrategy = activationStrategy;
        return this;
    }

    public boolean isNewSession() {
        return sessionId < 0;
    }

    @Override
    public String toString() {
        return "PersistedSessionOption( "+ sessionId +" )";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PersistedSessionOption that = (PersistedSessionOption) o;
        return sessionId == that.sessionId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }
}
