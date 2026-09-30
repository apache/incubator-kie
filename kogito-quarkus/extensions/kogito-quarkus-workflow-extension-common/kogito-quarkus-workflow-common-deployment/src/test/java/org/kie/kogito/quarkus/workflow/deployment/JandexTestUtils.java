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
package org.kie.kogito.quarkus.workflow.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.kie.kogito.codegen.data.Address;
import org.kie.kogito.codegen.data.Answer;
import org.kie.kogito.codegen.data.AnswerBroken;
import org.kie.kogito.codegen.data.AnswerBrokenV2;
import org.kie.kogito.codegen.data.AnswerWithAnnotations;
import org.kie.kogito.codegen.data.EmptyConstructor;
import org.kie.kogito.codegen.data.GeneratedPOJO;
import org.kie.kogito.codegen.data.Hello;
import org.kie.kogito.codegen.data.HelloModel;
import org.kie.kogito.codegen.data.JacksonData;
import org.kie.kogito.codegen.data.ListWithoutType;
import org.kie.kogito.codegen.data.NotEmptyConstructor;
import org.kie.kogito.codegen.data.ObjectVariableModel;
import org.kie.kogito.codegen.data.Person;
import org.kie.kogito.codegen.data.PersonSubClass;
import org.kie.kogito.codegen.data.PersonVarInfo;
import org.kie.kogito.codegen.data.PersonWithAddress;
import org.kie.kogito.codegen.data.PersonWithAddresses;
import org.kie.kogito.codegen.data.PersonWithBooleanGetAccessor;
import org.kie.kogito.codegen.data.PersonWithBooleanObject;
import org.kie.kogito.codegen.data.PersonWithList;
import org.kie.kogito.codegen.data.Question;
import org.kie.kogito.codegen.data.QuestionWithAnnotatedEnum;
import org.kie.kogito.codegen.data.Travels;

public final class JandexTestUtils {

    private JandexTestUtils() {
    }

    /**
     * NOTE: make sure to list here all the classes used in the tests (required by Jandex for indexing)
     */
    protected static Collection<Class<?>> testClasses = Arrays.asList(Address.class,
            Answer.class,
            AnswerWithAnnotations.class,
            AnswerBroken.class,
            AnswerBrokenV2.class,
            EmptyConstructor.class,
            GeneratedPOJO.class,
            Hello.class,
            HelloModel.class,
            NotEmptyConstructor.class,
            Person.class,
            PersonVarInfo.class,
            PersonWithAddress.class,
            PersonWithAddresses.class,
            PersonWithList.class,
            Question.class,
            QuestionWithAnnotatedEnum.class,
            Travels.class,
            PersonSubClass.class,
            JacksonData.class,
            ListWithoutType.class,
            ObjectVariableModel.class,
            PersonWithBooleanObject.class,
            PersonWithBooleanGetAccessor.class);

    private static void indexClass(Indexer indexer, Class<?> toIndex) {
        try {
            indexer.index(Objects.requireNonNull(JandexProtoGenerator.class.getClassLoader()
                    .getResourceAsStream(toPath(toIndex))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String toPath(Class<?> clazz) {
        return clazz.getCanonicalName().replace('.', '/') + ".class";
    }

    public static Index createTestIndex() {
        Indexer indexer = new Indexer();
        testClasses.forEach(clazz -> indexClass(indexer, clazz));
        return indexer.complete();
    }

    public static ClassInfo findClassInfo(Index index, Class<?> clazz) {
        return Optional.ofNullable(index.getClassByName(DotName.createSimple(clazz.getCanonicalName())))
                .orElseThrow(() -> new IllegalStateException("Class " + clazz.getCanonicalName() + " not found in the index, " +
                        "add the class to JandexTestUtils.testClasses collection"));
    }

}
