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

-- Process instance state: root_process_version for isolation filtering
ALTER TABLE Process_Instance_State_Log ADD COLUMN IF NOT EXISTS root_process_version VARCHAR(255);

-- Job execution log: all four isolation columns
ALTER TABLE Job_Execution_Log ADD COLUMN IF NOT EXISTS process_id           VARCHAR(255);
ALTER TABLE Job_Execution_Log ADD COLUMN IF NOT EXISTS process_version      VARCHAR(255);
ALTER TABLE Job_Execution_Log ADD COLUMN IF NOT EXISTS root_process_id      VARCHAR(255);
ALTER TABLE Job_Execution_Log ADD COLUMN IF NOT EXISTS root_process_version VARCHAR(255);

-- User task state log: all four isolation columns
ALTER TABLE Task_Instance_State_Log ADD COLUMN IF NOT EXISTS process_id           VARCHAR(255);
ALTER TABLE Task_Instance_State_Log ADD COLUMN IF NOT EXISTS process_version      VARCHAR(255);
ALTER TABLE Task_Instance_State_Log ADD COLUMN IF NOT EXISTS root_process_id      VARCHAR(255);
ALTER TABLE Task_Instance_State_Log ADD COLUMN IF NOT EXISTS root_process_version VARCHAR(255);


CREATE INDEX IF NOT EXISTS ix_pisl_pid_pver   ON Process_Instance_State_Log (process_id, process_version);
CREATE INDEX IF NOT EXISTS ix_pisl_rpid_rpver ON Process_Instance_State_Log (root_process_id, root_process_version);

CREATE INDEX IF NOT EXISTS ix_jel_procid      ON Job_Execution_Log (process_id);
CREATE INDEX IF NOT EXISTS ix_jel_pid_pver    ON Job_Execution_Log (process_id, process_version);
CREATE INDEX IF NOT EXISTS ix_jel_rpid_rpver  ON Job_Execution_Log (root_process_id, root_process_version);

CREATE INDEX IF NOT EXISTS ix_utsl_proc_id    ON Task_Instance_State_Log (process_id);
CREATE INDEX IF NOT EXISTS ix_utsl_pid_pver   ON Task_Instance_State_Log (process_id, process_version);
CREATE INDEX IF NOT EXISTS ix_utsl_rpid_rpver ON Task_Instance_State_Log (root_process_id, root_process_version);
