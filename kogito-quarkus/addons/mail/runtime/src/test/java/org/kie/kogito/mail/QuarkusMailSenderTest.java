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
package org.kie.kogito.mail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.event.usertask.UserTaskInstanceDeadlineDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceDeadlineEventBody;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class QuarkusMailSenderTest {

    @Inject
    MockMailbox mailBox;

    @Inject
    QuarkusMailSender sender;

    @BeforeEach
    void init() {
        mailBox.clear();
    }

    private static final String TO = "javierito@doesnotexist.com";
    private static final String SUBJECT = "Here we are";
    private static final String TEXT = "For singing you a song";

    @Test
    void testMail() {
        Map<String, Object> notification = new HashMap<>();
        notification.put(MailInfo.SUBJECT_PROPERTY, SUBJECT);
        notification.put(MailInfo.BODY_PROPERTY, TEXT);
        notification.put(MailInfo.FROM_PROPERTY, "realbetisbalompie@gmail.com");
        notification.put(MailInfo.TO_PROPERTY, TO + ",fulanito@doesnotexist.com");

        UserTaskInstanceDeadlineEventBody eventData = UserTaskInstanceDeadlineEventBody.create().userTaskInstanceId("1").notification(notification).build();
        UserTaskInstanceDeadlineDataEvent event = UserTaskInstanceDeadlineDataEvent.builder()
                .data(eventData)
                .build();
        sender.onDeadline(event);
        List<Mail> messages = mailBox.getMessagesSentTo(TO);
        assertThat(messages.size()).isEqualTo(1);
        Mail message = messages.get(0);
        assertThat(message.getText()).isEqualTo(TEXT);
        assertThat(message.getSubject()).isEqualTo(SUBJECT);
    }
}
