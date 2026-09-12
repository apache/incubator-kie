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
package org.drools.modelcompiler.domain;

public final class SensorEvents {

    private SensorEvents() { }

    public static final class MonitoringStation {
        private final String id;

        public MonitoringStation(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static final class SensorActivated {
        private final String sensorId;

        public SensorActivated(String sensorId) {
            this.sensorId = sensorId;
        }

        public String getSensorId() {
            return sensorId;
        }
    }

    public static final class HeartbeatOk {
        private final String sensorId;

        public HeartbeatOk(String sensorId) {
            this.sensorId = sensorId;
        }

        public String getSensorId() {
            return sensorId;
        }
    }

    public static final class AlarmRaised {
        private final String sensorId;
        private final String severity;

        public AlarmRaised(String sensorId, String severity) {
            this.sensorId = sensorId;
            this.severity = severity;
        }

        public String getSensorId() {
            return sensorId;
        }

        public String getSeverity() {
            return severity;
        }
    }

    public static final class CalibrationPassed {
        private final String sensorId;

        public CalibrationPassed(String sensorId) {
            this.sensorId = sensorId;
        }

        public String getSensorId() {
            return sensorId;
        }
    }

    public static final class MaintenanceScheduled {
        private final String sensorId;

        public MaintenanceScheduled(String sensorId) {
            this.sensorId = sensorId;
        }

        public String getSensorId() {
            return sensorId;
        }
    }

    public static final class OperatorAcknowledged {
        private final String sensorId;
        private final String operator;

        public OperatorAcknowledged(String sensorId, String operator) {
            this.sensorId = sensorId;
            this.operator = operator;
        }

        public String getSensorId() {
            return sensorId;
        }

        public String getOperator() {
            return operator;
        }
    }
}
