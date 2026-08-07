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
package org.drools.kiesession.debug;

import org.drools.base.common.NetworkNode;
import org.drools.core.reteoo.FromNode;
import org.drools.core.reteoo.FromNode.FromMemory;
import org.drools.core.reteoo.TupleImpl;
import org.drools.base.util.FastIterator;

import java.util.Collection;

public class FromNodeVisitor extends AbstractNetworkNodeVisitor {
    
    public static final FromNodeVisitor INSTANCE = new FromNodeVisitor();
    
    protected FromNodeVisitor() {
    }

    @Override
    protected void doVisit(NetworkNode node,
                           Collection<NetworkNode> nodeStack,
                           StatefulKnowledgeSessionInfo info) {
        FromNode fn = (FromNode) node;
        DefaultNodeInfo ni = info.getNodeInfo( node );
        final FromMemory memory = (FromMemory) info.getSession().getNodeMemory( fn );
        
        if( fn.isLeftTupleMemoryEnabled() ) {
            ni.setTupleMemorySize( memory.getBetaMemory().getLeftTupleMemory().size() );

            long                    handles = 0;
            FastIterator<TupleImpl> it      = memory.getBetaMemory().getLeftTupleMemory().fullFastIterator();
            for ( TupleImpl leftTuple = memory.getBetaMemory().getLeftTupleMemory().getFirst(null); leftTuple != null; leftTuple = it.next(leftTuple) ) {
                TupleImpl child = leftTuple.getFirstChild();
                while( child != null ) {
                    handles++;
                    child = child.getHandleNext();
                }
            }
            ni.setCreatedFactHandles( handles );
        } else {
            info.warn( "The left memory for this node is disabled, making it impossible to calculate the number of created handles" );
        }

    }

}
