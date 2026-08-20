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
package org.kie.api.runtime.rule;

import java.util.Collection;
import java.util.NoSuchElementException;

import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.ObjectFilter;

/**
 * <p>An entry-point is an abstract channel through where facts are inserted into the engine.</p>
 * <p>KIE 6 supports multiple entry-points into a single {@link org.kie.api.KieBase}: the
 * default, anonymous entry-point, as well as as many user declared entry points the application
 * requires.</p>
 *
 * <p>To get a reference to an entry point, just request the session:</p>
 * <pre>
 * KieSession session = kbase.newStatelessKieSession();
 * ...
 * WorkingMemoryEntryPoint entrypoint = session.getEntryPoint("my entry point");
 * </pre>
 * <p>Once a reference to an entry point is acquired, the application can insert, update and retract facts
 * to/from that entry-point as usual:</p>
 * <pre>
 * ...
 * FactHandle factHandle = entrypoint.insert( fact );
 * ...
 * entrypoint.update( factHandle, newFact );
 * ...
 * entrypoint.retract( factHandle );
 * ...
 * </pre>
 */
public interface EntryPoint {

    /**
     * @return the String Id of this entry point
     */
    String getEntryPointId();

    /**
     * Inserts a new fact into this entry point
     *
     * @param object
     *        the fact to be inserted
     *
     * @return the fact handle created for the given fact
     */
    FactHandle insert(Object object);

    /**
     * Retracts the fact for which the given FactHandle was assigned.
     *
     * @param handle the handle whose fact is to be retracted.
     * @deprecated since 7.0.0, use {@link #delete(FactHandle)} instead. The {@code delete} method
     *             provides clearer semantics and aligns with standard terminology for removing facts
     *             from the working memory.
     */
    @Deprecated(since = "7.0.0", forRemoval = true)
    void retract(FactHandle handle);

    /**
     * Retracts the fact for which the given FactHandle was assigned
     * regardless if it has been explicitly or logically inserted.
     *
     * @param handle the handle whose fact is to be retracted.
     */
    void delete(FactHandle handle);

    /**
     * Retracts the fact for which the given FactHandle was assigned.
     *
     * @param handle the handle whose fact is to be retracted.
     * @param fhState defines how the handle has to be retracted:
     *                as an explicitly inserted fact (STATED), from the
     *                Truth Maintenance System (LOGICAL), or both (ALL)
     */
    void delete(FactHandle handle, FactHandle.State fhState);

    /**
     * Updates the fact for which the given FactHandle was assigned with the new
     * fact set as the second parameter in this method.
     *
     * @param handle the FactHandle for the fact to be updated.
     * @param object the new value for the fact being updated.
     */
    void update(FactHandle handle,
                Object object);

    /**
     * Updates the fact for which the given FactHandle was assigned with the new
     * fact set as the second parameter in this method, also specifying the set of
     * properties that have been modified.
     *
     * @param handle the FactHandle for the fact to be updated.
     * @param object the new value for the fact being updated.
     * @param modifiedProperties the list of the names of the object's properties modified by this update.
     */
    void update(FactHandle handle,
                Object object,
                String... modifiedProperties);

    /**
     * Returns the fact handle associated with the given object. It is important to note that this
     * method behaves in accordance with the configured assert behaviour for this {@link org.kie.api.KieBase}
     * (either IDENTITY or EQUALITY).
     *
     * @param object
     *               the fact for which the fact handle will be returned.
     *
     * @return the fact handle for the given object, or null in case no fact handle was found for the
     *         given object.
     *
     * @see org.kie.api.KieBaseConfiguration
     */
    FactHandle getFactHandle(Object object);

    /**
     * @return the object associated with the given FactHandle.
     */
    Object getObject(FactHandle factHandle);

    /**
     * <p>This class is <i>not</i> a general-purpose <tt>Collection</tt>
     * implementation!  While this class implements the <tt>Collection</tt> interface, it
     * intentionally violates <tt>Collection</tt> general contract, which mandates the
     * use of the <tt>equals</tt> method when comparing objects.</p>
     *
     * <p>Instead the approach used when comparing objects with the <tt>contains(Object)</tt>
     * method is dependent on the WorkingMemory configuration, where it can be configured for <tt>Identity</tt>
     * or for <tt>Equality</tt>.</p>
     *
     * @return all facts from the current session as a Collection.
     */
    Collection<? extends Object> getObjects();

    /**
     * @param filter the filter to be applied to the returned collection of facts.
     * @return all facts from the current session that are accepted by the given <code>ObjectFilter</code>.
     */
    Collection<? extends Object> getObjects(ObjectFilter filter);

    /**
     * Returns a Collection of objects in this entry-point that are instances of the given class.
     * @param clazz the class of objects to be retrieved
     * @return all facts from this entry-point that are instance of the given class.
     */
    default <T> Collection<T> getInstancesOf(Class<T> clazz) {
        return (Collection<T>) getObjects(new ClassObjectFilter(clazz));
    }

    /**
     * Returns the only object in this entry-point that is an instance of the given class.
     * @param clazz the class of object to be retrieved
     * @return the only object from this entry-point that is an instance of the given class.
     * @throws NoSuchElementException if there isn't any object of the given class in this entry-point
     * @throws IllegalStateException if there is more than one object of the given class in this entry-point
     */
    default <T> T getSingleInstanceOf(Class<T> clazz) {
        Collection<T> ts = (Collection<T>) getObjects(new ClassObjectFilter(clazz));
        int size = ts.size();
        if (size == 0) {
            throw new NoSuchElementException();
        }
        if (size > 1) {
            throw new IllegalStateException("Found " + size + " instances instead of the expected single one");
        }
        return ts.iterator().next();
    }

    /**
     * @return all <code>FactHandle</code>s from the current session.
     */
    <T extends FactHandle> Collection< T > getFactHandles();

    /**
     * @param filter the filter to be applied to the returned collection of <code>FactHandle</code>s.
     * @return all <code>FactHandle</code>s from the current session for which the facts are accepted by
     * the given filter.
     */
    <T extends FactHandle> Collection< T > getFactHandles(ObjectFilter filter);

    /**
     * @return the total number of facts currently in this entry point
     */
    long getFactCount();
}
