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
package org.kie.kogito.calendar;

import java.util.Date;

/**
 * <b>Business Calendar</b> extension point.
 *
 * <p>
 * Calculates business working hours, weekend exclusions, and corporate holiday schedules
 * for workflow timer nodes, SLA tracking, and user task deadline escalations.
 */
public interface BusinessCalendar {

    /**
     * Calculates the duration in milliseconds from the current time to the business time matching the expression.
     *
     * @param timeExpression time expression that is supported by business calendar implementation.
     * @return duration expressed in milliseconds; always &gt;= 0
     * @see #calculateBusinessTimeAsDate(String)
     */
    long calculateBusinessTimeAsDuration(String timeExpression);

    /**
     * Calculates the future date matching the expression within valid business calendar hours.
     *
     * @param timeExpression the non-null ISO-8601 or custom cron/interval time expression
     * @return the future {@link Date} when the time expression matches; never {@code null}
     */
    Date calculateBusinessTimeAsDate(String timeExpression);
}
