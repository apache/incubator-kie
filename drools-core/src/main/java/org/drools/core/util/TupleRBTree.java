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
package org.drools.core.util;

import org.drools.base.util.Iterator;
import org.drools.core.util.index.TupleList;

public class TupleRBTree<K extends Comparable< ? super K>> {

    public static final boolean VERIFY_RBTREE = false;
    private static final int    INDENT_STEP   = 4;

    public Node<K>        root;
    public Node<K>        nullNode;

    public void verifyProperties() {
        if ( VERIFY_RBTREE ) {
            verifyProperty1( root );
            verifyProperty2( root );
            // Property 3 is implicit
            verifyProperty4( root );
            verifyProperty5( root );
        }
    }

    private static void verifyProperty1(Node< ? > n) {
        assert nodeColor( n ) == Color.RED || nodeColor( n ) == Color.BLACK;
        if ( n == null ) {
            return;
        }
        verifyProperty1( n.left );
        verifyProperty1( n.right );
    }

    private static void verifyProperty2(Node< ? > root) {
        assert nodeColor( root ) == Color.BLACK;
    }

    private static Color nodeColor(Node< ? > n) {
        return n == null ? Color.BLACK : n.color;
    }

    private static void verifyProperty4(Node< ? > n) {
        if ( nodeColor( n ) == Color.RED ) {
            assert nodeColor( n.left ) == Color.BLACK;
            assert nodeColor( n.right ) == Color.BLACK;
            assert nodeColor( n.parent ) == Color.BLACK;
        }
        if ( n == null ) {
            return;
        }
        verifyProperty4( n.left );
        verifyProperty4( n.right );
    }

    private static void verifyProperty5(Node< ? > root) {
        verifyProperty5Helper( root, 0, -1 );
    }

    private static int verifyProperty5Helper(Node< ? > n,
                                             int blackCount,
                                             int pathBlackCount) {
        if ( nodeColor( n ) == Color.BLACK ) {
            blackCount++;
        }
        if ( n == null ) {
            if ( pathBlackCount == -1 ) {
                pathBlackCount = blackCount;
            } else {
                assert blackCount == pathBlackCount;
            }
            return pathBlackCount;
        }
        pathBlackCount = verifyProperty5Helper( n.left, blackCount, pathBlackCount );
        pathBlackCount = verifyProperty5Helper( n.right, blackCount, pathBlackCount );
        return pathBlackCount;
    }

    public Node<K> lookup(K key) {
        if (key == null) {
            return nullNode;
        }
        Node<K> n = root;
        while ( n != null ) {
            int compResult = key.compareTo( n.key );
            if ( compResult == 0 ) {
                return n;
            } else if ( compResult < 0 ) {
                n = n.left;
            } else {
                n = n.right;
            }
        }
        return n;
    }

    public enum Boundary {
        LOWER, UPPER
    };

    public static class RangeFastIterator<K extends Comparable< ? super K>> implements Iterator<Node<K>> {
        private Node<K> upperBound;
        private Node<K> next;

        public RangeFastIterator(Node<K> lowerNearest,
                                 Node<K> upperNearest) {
            this.next = lowerNearest;
            this.upperBound = upperNearest;
        }

        public Node<K> next() {
            Node<K> temp = next;
            next = checkUpperBound( recurse( next ) );
            return temp;
        }

        private Node<K> recurse(Node<K> current) {
            if (current == null) {
                return null;
            }

            if (current.right != null) {
                Node<K> p = current.right;
                while (p.left != null) {
                    p = p.left;
                }
                return p;
            }

            Node<K> p = current.parent;
            Node<K> ch = current;
            while (p != null && ch == p.right) {
                ch = p;
                p = p.parent;
            }
            return p;
        }

        public Node<K> checkUpperBound(Node<K> current) {
            if (upperBound == null) {
                return current;
            }
            return current == null || current.compareTo(upperBound) > 0 ? null : current;
        }
    }

    public boolean isEmpty() {
        return root == null;
    }

    public Node<K> first() {
        if (root == null) {
            return null;
        }
        Node<K> n = root;
        while (n.left != null) {
            n = n.left;
        }
        return n;
    }

    public Node<K> last() {
        if (root == null) {
            return null;
        }
        Node<K> n = root;
        while (n.right != null) {
            n = n.right;
        }
        return n;
    }

    public Iterator<Node<K>> iterator() {
        return root == null ? EMPTY : new RangeFastIterator( first(), null );
    }

    @Override
    public String toString() {
        Iterator<Node<K>> iterator = iterator();
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Node<K> entry = iterator.next(); entry != null; entry = iterator.next()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(entry);
        }
        sb.append("]");
        return sb.toString();
    }

    public Iterator<Node<K>> range(K lowerBound,
                              boolean testLowerEqual,
                              K upperBound,
                              boolean testUpperEqual) {
        Node<K> lowerNearest = findNearestNode( lowerBound, testLowerEqual, Boundary.LOWER );
        Node<K> upperNearest = findNearestNode( upperBound, testUpperEqual, Boundary.UPPER );

        if ( lowerNearest == null || upperNearest == null ) {
            return EMPTY;
        }

        if ( lowerNearest.key.compareTo( upperNearest.key  ) > 0 ) {
            return EMPTY;
        }

        return new RangeFastIterator( lowerNearest, upperNearest );
    }

    public void rangeUperBounded(K upperBound,
                                 boolean testUpperEqual) {
        Node<K> upperNearest = findNearestNode( upperBound, testUpperEqual, Boundary.UPPER );
    }

    public void rangeLowerBounded(K upperBound,
                                  boolean testUpperEqual) {
        Node<K> upperNearest = findNearestNode( upperBound, testUpperEqual, Boundary.UPPER );
    }

    public Node<K> findNearestNode(K key, boolean allowEqual, Boundary boundary) {
        if (key == null) {
            return allowEqual ? nullNode : null;
        }

        Node<K> nearest = null;
        Node<K> n = root;

        while ( n != null ) {
            int compResult = key.compareTo( n.key );
            if ( allowEqual && compResult == 0 ) {
                return n;
            }

            boolean accepted = acceptNode(compResult, boundary);
            if ( acceptNode( compResult, boundary ) && ( nearest == null || acceptNode( n.key.compareTo( nearest.key ), boundary ) ) ) {
                nearest = n;
            }

            if ( compResult == 0 ) {
                n = boundary == Boundary.LOWER ? n.right : n.left;
            } else {
                n = accepted ^ boundary == Boundary.LOWER ? n.right : n.left;
            }
        }

        return nearest;
    }

    private boolean acceptNode(int compResult, Boundary boundary) {
        return compResult != 0 && ( compResult > 0 ^ boundary == Boundary.LOWER );
    }

    private void rotateLeft(Node<K> n) {
        Node<K> r = n.right;
        replaceNode( n, r );
        n.right = r.left;
        if ( r.left != null ) {
            r.left.parent = n;
        }
        r.left = n;
        n.parent = r;
    }

    private void rotateRight(Node<K> n) {
        Node<K> l = n.left;
        replaceNode( n, l );
        n.left = l.right;
        if ( l.right != null ) {
            l.right.parent = n;
        }
        l.right = n;
        n.parent = l;
    }

    private void replaceNode(Node<K> oldn,
                             Node<K> newn) {
        if ( oldn.parent == null ) {
            root = newn;
        } else {
            if ( oldn == oldn.parent.left ) {
                oldn.parent.left = newn;
            }
            else {
                oldn.parent.right = newn;
            }
        }
        if ( newn != null ) {
            newn.parent = oldn.parent;
        }
    }

    public Node<K> insert(K key) {
        if (key == null) {
            if (nullNode == null) {
                nullNode = new Node<>( key );
            }
            return nullNode;
        }
        Node<K> insertedNode;
        if ( root == null ) {
            insertedNode = new Node<>( key );
            root = insertedNode;
        } else {
            Node<K> n = root;
            while ( true ) {
                int compResult = key.compareTo( n.key );
                if ( compResult == 0 ) {
                    return n;
                } else if ( compResult < 0 ) {
                    if ( n.left == null ) {
                        insertedNode = new Node<>( key );
                        n.left = insertedNode;
                        break;
                    } else {
                        n = n.left;
                    }
                } else {
                    if ( n.right == null ) {
                        insertedNode = new Node<>( key );
                        n.right = insertedNode;
                        break;
                    } else {
                        n = n.right;
                    }
                }
            }
            insertedNode.parent = n;
        }
        insertCase1( insertedNode );
        // verifyProperties();
        return insertedNode;
    }

    private void insertCase1(Node<K> n) {
        if ( n.parent == null ) {
            n.color = Color.BLACK;
        }
        else {
            insertCase2( n );
        }
    }

    private void insertCase2(Node<K> n) {
        if ( nodeColor( n.parent ) == Color.BLACK ) {
            return; // Tree is still valid
        }
        else {
            insertCase3( n );
        }
    }

    void insertCase3(Node<K> n) {
        if ( nodeColor( n.uncle() ) == Color.RED ) {
            n.parent.color = Color.BLACK;
            n.uncle().color = Color.BLACK;
            n.grandparent().color = Color.RED;
            insertCase1( n.grandparent() );
        } else {
            insertCase4( n );
        }
    }

    void insertCase4(Node<K> n) {
        if ( n == n.parent.right && n.parent == n.grandparent().left ) {
            rotateLeft( n.parent );
            n = n.left;
        } else if ( n == n.parent.left && n.parent == n.grandparent().right ) {
            rotateRight( n.parent );
            n = n.right;
        }
        insertCase5(n);
    }

    void insertCase5(Node<K> n) {
        n.parent.color = Color.BLACK;
        n.grandparent().color = Color.RED;
        if ( n == n.parent.left && n.parent == n.grandparent().left ) {
            rotateRight( n.grandparent() );
        } else {
            rotateLeft( n.grandparent() );
        }
    }

    public void delete(K key) {
        Node<K> n = lookup(key);
        if ( n == null ) {
            return; // Key not found, do nothing
        }
        if ( n.left != null && n.right != null ) {
            // Copy key/value from predecessor and then delete it instead
            Node<K> pred = maximumNode( n.left );
            pred.copyStateInto(n);
            n = pred;
        }

        Node<K> child = (n.right == null) ? n.left : n.right;
        if ( nodeColor( n ) == Color.BLACK ) {
            n.color = nodeColor( child );
            deleteCase1( n );
        }
        replaceNode( n, child );

        if ( nodeColor( root ) == Color.RED ) {
            root.color = Color.BLACK;
        }

        if ( nodeColor( root ) == Color.RED ) {
            root.color = Color.BLACK;
        }

        // verifyProperties();
    }

    private static <K extends Comparable< ? super K>, V> Node<K> maximumNode(Node<K> n) {
        while ( n.right != null ) {
            n = n.right;
        }
        return n;
    }

    private void deleteCase1(Node<K> n) {
        if ( n.parent == null ) {
            return;
        }
        else {
            deleteCase2( n );
        }
    }

    private void deleteCase2(Node<K> n) {
        if ( nodeColor( n.sibling() ) == Color.RED ) {
            n.parent.color = Color.RED;
            n.sibling().color = Color.BLACK;
            if ( n == n.parent.left ) {
                rotateLeft( n.parent );
            }
            else {
                rotateRight( n.parent );
            }
        }
        deleteCase3( n );
    }

    private void deleteCase3(Node<K> n) {
        if ( nodeColor( n.parent ) == Color.BLACK &&
                nodeColor( n.sibling() ) == Color.BLACK &&
                nodeColor( n.sibling().left ) == Color.BLACK &&
                nodeColor( n.sibling().right ) == Color.BLACK ) {
            n.sibling().color = Color.RED;
            deleteCase1( n.parent );
        }
        else {
            deleteCase4( n );
        }
    }

    private void deleteCase4(Node<K> n) {
        if ( nodeColor( n.parent ) == Color.RED &&
                nodeColor( n.sibling() ) == Color.BLACK &&
                nodeColor( n.sibling().left ) == Color.BLACK &&
                nodeColor( n.sibling().right ) == Color.BLACK ) {
            n.sibling().color = Color.RED;
            n.parent.color = Color.BLACK;
        }
        else {
            deleteCase5( n );
        }
    }

    private void deleteCase5(Node<K> n) {
        if ( n == n.parent.left &&
                nodeColor( n.sibling() ) == Color.BLACK &&
                nodeColor( n.sibling().left ) == Color.RED &&
                nodeColor( n.sibling().right ) == Color.BLACK ) {
            n.sibling().color = Color.RED;
            n.sibling().left.color = Color.BLACK;
            rotateRight( n.sibling() );
        }
        else if ( n == n.parent.right &&
                nodeColor( n.sibling() ) == Color.BLACK &&
                nodeColor( n.sibling().right ) == Color.RED &&
                nodeColor( n.sibling().left ) == Color.BLACK ) {
            n.sibling().color = Color.RED;
            n.sibling().right.color = Color.BLACK;
            rotateLeft( n.sibling() );
        }
        deleteCase6( n );
    }

    private void deleteCase6(Node<K> n) {
        n.sibling().color = nodeColor( n.parent );
        n.parent.color = Color.BLACK;
        if ( n == n.parent.left ) {
            n.sibling().right.color = Color.BLACK;
            rotateLeft( n.parent );
        }
        else {
            n.sibling().left.color = Color.BLACK;
            rotateRight( n.parent );
        }
    }

    public void print() {
        printHelper( root, 0 );
    }

    private static void printHelper(Node< ? > n,
                                    int indent) {
        if ( n == null ) {
            System.out.print( "<empty tree>" );
            return;
        }

        if ( n.right != null ) {
            printHelper( n.right, indent + INDENT_STEP );
        }

        for ( int i = 0; i < indent; i++ ) {
            System.out.print( " " );
        }

        if ( n.color == Color.BLACK ) {
            System.out.println( n.key );
        } else {
            System.out.println( "<" + n.key + ">" );
        }

        if ( n.left != null ) {
            printHelper( n.left, indent + INDENT_STEP );
        }
    }

    public enum Color {
        RED, BLACK
    }

    public static class Node<K extends Comparable< ? super K>> extends TupleList {
        public  K       key;
        private Node<K> left;
        private Node<K> right;
        private Node<K> parent;
        private Color   color = Color.RED;

        public Node(K key) {
            this.key = key;
        }

        public Node<K> grandparent() {
            return parent.parent;
        }

        public Node<K> sibling() {
            return this == parent.left ? parent.right :parent.left;
        }

        public Node<K> uncle() {
            return parent.sibling();
        }

        public String toString() {
            return "Node key=" + key;
        }

        public void setNext(TupleList next) {
            // TODO Auto-generated method stub

        }

        public TupleList getNext() {
            // TODO Auto-generated method stub
            return null;
        }

        public int compareTo(Node<K> other) {
            return key.compareTo(other.key);
        }

        protected void copyStateInto(Node<K> other) {
            super.copyStateInto(other);
            other.key = key;
        }
    }

    public Iterator<Node<K>> EMPTY = new Iterator<>() {
        public Node<K> next() {
            return null;
        }
    };
}
