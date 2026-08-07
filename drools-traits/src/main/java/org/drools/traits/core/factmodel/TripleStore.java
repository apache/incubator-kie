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
package org.drools.traits.core.factmodel;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.drools.base.util.SingleLinkedEntry;
import org.kie.api.runtime.rule.Variable;

public class TripleStore implements Externalizable {

    public static final String TYPE = "rdfs:type";
    public static final String PROXY = "drools:proxy";
    public static final String VALUE = "drools:hasValue";

    private String id;

    private Map<Triple, Triple> map;

    public TripleStore() {
        super();
        map = new HashMap<>();
    }

    public TripleStore(final int capacity, final float loadFactor) {
        map = new HashMap<>(capacity, loadFactor);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        map = (Map) in.readObject();
        id = (String) in.readObject();

    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(map);
        out.writeObject(id);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean put(final Triple triple, boolean checkExists) {
        return put(triple);
    }

    public boolean put(final Triple triple) {
        final Object val = triple.getValue();
        ((TripleImpl) triple).setValue(Variable.v);

        Object prev = map.compute(triple, (key, existing) -> {
            if (existing != null) {
                ((TripleImpl)existing).setValue(val);
                return existing;
            }
            return key;
        });

        ((TripleImpl) triple).setValue(val);

        return prev != triple;
    }

    public boolean add(final Triple triple) {
        map.put(triple, triple);
        return false;
    }

    public Triple get(final Triple triple) {
        return map.get(triple);
    }

    public Collection<Triple> getAll(final Triple triple) {
        List<Triple> list = new ArrayList<>();

        if (triple.getInstance() != Variable.v && triple.getProperty() != Variable.v) {
            Triple collector = new TripleCollector(list, triple);
            map.get(collector);
            return list;
        }

        Iterator<Triple> iter = map.values().iterator();
        Triple tx;
        while (iter.hasNext()) {
            tx = iter.next();
            if (AbstractTriple.equals(triple, tx)) {
                list.add(tx);
            }
        }

        return list;
    }

    public int removeAll(final Triple triple) {
        int removed = 0;
        Collection<Triple> coll = getAll(triple);
        for (Triple t : coll) {
            if (remove(t)) {
                removed++;
            }
        }
        return removed;
    }

    public boolean remove(final Triple triple) {
        return map.remove(triple) != null;
    }

    public boolean contains(final Triple triple) {
        return map.containsKey(triple);
    }

    public int size() {
        return map.size();
    }


    public class TripleCollector extends AbstractTriple {
        List<Triple> list;

        private Triple triple;

        public TripleCollector(List<Triple> list, Triple triple) {
            this.list = list;
            this.triple = triple;
        }

        @Override
        public void setNext(SingleLinkedEntry next) {

        }

        @Override
        public SingleLinkedEntry getNext() {
            return null;
        }

        @Override
        public Object getInstance() {
            return triple.getInstance();
        }

        @Override
        public Object getProperty() {
            return triple.getProperty();
        }

        @Override
        public Object getValue() {
            return Variable.v;
        }

    }
}
