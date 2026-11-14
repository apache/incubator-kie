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
package org.kie.kogito.persistence.protobuf.quarkus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.persistence.protobuf.ProtobufService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class ProtobufMonitorServiceTest {

    @Mock
    ProtobufService protobufService;

    @InjectMocks
    ProtobufMonitorService protobufMonitorService;

    @Test
    void testLoadingAndUpdatingFiles() throws Exception {
        Path dir = null;
        try {
            dir = Files.createTempDirectory(this.getClass().getName());
            Path sub = Files.createDirectory(dir.resolve("proto"));
            Files.createFile(dir.resolve("kogito-application.proto"));
            Path file1 = Files.createFile(dir.resolve("test1.proto"));
            Path file2 = Files.createFile(sub.resolve("test2.proto"));
            CountDownLatch latch = new CountDownLatch(4);
            doAnswer(args -> {
                latch.countDown();
                return null;
            }).when(protobufService).registerProtoBufferType(any());

            protobufMonitorService.monitor = true;
            protobufMonitorService.protoFiles = Optional.of(dir.toAbsolutePath().toString());
            protobufMonitorService.onFolderWatch = path -> {
                try {
                    Files.write(file2, "test".getBytes());
                    Files.write(file1, "test".getBytes());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
            protobufMonitorService.startMonitoring(null);

            latch.await(1, TimeUnit.MINUTES);
            assertThat(latch.getCount()).isEqualTo(0);
        } finally {
            if (dir != null) {
                try {
                    Files.deleteIfExists(dir);
                } catch (IOException e) {
                }
            }
        }
    }

    @Test
    void testAddingSubFolderAfterStart() throws Exception {
        Path dir = null;
        try {
            dir = Files.createTempDirectory(this.getClass().getName());
            Files.createFile(dir.resolve("kogito-application.proto"));
            Files.createFile(dir.resolve("test1.proto")); // valid proto file
            CountDownLatch latch = new CountDownLatch(2); // should only register test1 and test2 proto files
            Path proto = dir.resolve("proto");
            doAnswer(args -> {
                latch.countDown();
                return null;
            }).when(protobufService).registerProtoBufferType(any());

            protobufMonitorService.monitor = true;
            protobufMonitorService.protoFiles = Optional.of(dir.toAbsolutePath().toString());
            protobufMonitorService.onFolderWatch = path -> {
                try {
                    Path sub = Files.createDirectory(proto);
                    Files.createFile(sub.resolve("test2.txt"));
                    Files.createFile(sub.resolve("test2.proto")); // valid proto file
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
            protobufMonitorService.startMonitoring(null);

            latch.await(1, TimeUnit.MINUTES);
            assertThat(latch.getCount()).isEqualTo(0);
        } finally {
            if (dir != null) {
                try {
                    Files.deleteIfExists(dir);
                } catch (IOException e) {
                }
            }
        }
    }
}
