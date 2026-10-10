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
package org.kie.dmn.model.api;

import javax.xml.namespace.QName;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TUnaryTestsTest {

    /**
     * DMN v1.1 and v1.2: tUnaryTests does not extend tExpression, so typeRef must throw.
     */
    @Test
    void typeRefThrowsOnDMNv1_1() {
        UnaryTests ut = new org.kie.dmn.model.v1_1.TUnaryTests();
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(ut::getTypeRef);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> ut.setTypeRef(null));
    }

    @Test
    void typeRefThrowsOnDMNv1_2() {
        UnaryTests ut = new org.kie.dmn.model.v1_2.TUnaryTests();
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(ut::getTypeRef);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> ut.setTypeRef(null));
    }

    /**
     * DMN v1.3+: tUnaryTests extends tExpression, so typeRef must be readable/writable.
     */
    @Test
    void typeRefWorksOnDMNv1_3() {
        UnaryTests ut = new org.kie.dmn.model.v1_3.TUnaryTests();
        QName qname = new QName("string");
        ut.setTypeRef(qname);
        assertThat(ut.getTypeRef()).isEqualTo(qname);
    }

    @Test
    void typeRefWorksOnDMNv1_4() {
        UnaryTests ut = new org.kie.dmn.model.v1_4.TUnaryTests();
        QName qname = new QName("string");
        ut.setTypeRef(qname);
        assertThat(ut.getTypeRef()).isEqualTo(qname);
    }

    @Test
    void typeRefWorksOnDMNv1_5() {
        UnaryTests ut = new org.kie.dmn.model.v1_5.TUnaryTests();
        QName qname = new QName("string");
        ut.setTypeRef(qname);
        assertThat(ut.getTypeRef()).isEqualTo(qname);
    }

    @Test
    void typeRefWorksOnDMNv1_6() {
        UnaryTests ut = new org.kie.dmn.model.v1_6.TUnaryTests();
        QName qname = new QName("string");
        ut.setTypeRef(qname);
        assertThat(ut.getTypeRef()).isEqualTo(qname);
    }
}
