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
package org.kie.kogito.quarkus.common.deployment;

public class MissingRestCapabilityException extends RuntimeException {
    public MissingRestCapabilityException() {
        super("No REST capability detected! \n" +
                "Add RestEasy (quarkus-rest) and RestEasy Jackson (quarkus-rest-jackson) extensions or " +
                "add RestEasy Reactive (quarkus-rest) and RestEasy Reactive Jackson (quarkus-rest-jackson) extensions " +
                "if you want Kogito to generate REST endpoints automatically. \n" +
                "You may also disable automated REST generation by setting `kogito.generate.rest = false`. \n" +
                "You may also override this notice by setting `kogito.generate.rest = true` ");
    }
}
