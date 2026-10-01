/**
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


String log = new File(basedir, 'build.log').text;

log.contains("[INFO] empty .............................................. SUCCESS [  0.179 s]")
log.contains("[INFO] module1 ............................................ SUCCESS [  0.008 s]")
log.contains("[INFO] module2 ............................................ FAILURE [  0.009 s]")
log.contains("[INFO] BUILD FAILURE")
log.contains("org.kie.noexternalmanageddependencyrule.NoExternalManagedDependencyRule failed with message:")
log.contains("The current pom org.foo.bar:module2:999-SNAPSHOT-2026-05-14 has the following invalid managed dependencies:")
log.contains("org.drools:drools-docs")
