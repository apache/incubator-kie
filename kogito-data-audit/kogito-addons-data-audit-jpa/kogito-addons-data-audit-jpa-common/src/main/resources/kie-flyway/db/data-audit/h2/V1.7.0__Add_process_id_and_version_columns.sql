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

ALTER TABLE Process_Instance_State_Log    ADD COLUMN IF NOT EXISTS root_process_version VARCHAR(255);
ALTER TABLE Process_Instance_Node_Log     ADD COLUMN IF NOT EXISTS root_process_version VARCHAR(255);
ALTER TABLE Process_Instance_Variable_Log ADD COLUMN IF NOT EXISTS root_process_version VARCHAR(255);
ALTER TABLE Process_Instance_Error_Log    ADD COLUMN IF NOT EXISTS root_process_version VARCHAR(255);

ALTER TABLE Job_Execution_Log ADD COLUMN IF NOT EXISTS process_id      VARCHAR(255);
ALTER TABLE Job_Execution_Log ADD COLUMN IF NOT EXISTS process_version VARCHAR(255);
ALTER TABLE Job_Execution_Log ADD COLUMN IF NOT EXISTS root_process_id      VARCHAR(255);
ALTER TABLE Job_Execution_Log ADD COLUMN IF NOT EXISTS root_process_version VARCHAR(255);

CREATE INDEX IF NOT EXISTS ix_pisl_pid    ON Process_Instance_State_Log    (process_id);
CREATE INDEX IF NOT EXISTS ix_pinl_pid    ON Process_Instance_Node_Log     (process_id);
CREATE INDEX IF NOT EXISTS ix_pivl_pid    ON Process_Instance_Variable_Log (process_id);
CREATE INDEX IF NOT EXISTS ix_piel_pid    ON Process_Instance_Error_Log    (process_id);

CREATE INDEX IF NOT EXISTS ix_pisl_rpid   ON Process_Instance_State_Log    (root_process_id);
CREATE INDEX IF NOT EXISTS ix_pinl_rpid   ON Process_Instance_Node_Log     (root_process_id);
CREATE INDEX IF NOT EXISTS ix_pivl_rpid   ON Process_Instance_Variable_Log (root_process_id);
CREATE INDEX IF NOT EXISTS ix_piel_rpid   ON Process_Instance_Error_Log    (root_process_id);

CREATE INDEX IF NOT EXISTS ix_pisl_pver   ON Process_Instance_State_Log    (process_version);
CREATE INDEX IF NOT EXISTS ix_pinl_pver   ON Process_Instance_Node_Log     (process_version);
CREATE INDEX IF NOT EXISTS ix_pivl_pver   ON Process_Instance_Variable_Log (process_version);
CREATE INDEX IF NOT EXISTS ix_piel_pver   ON Process_Instance_Error_Log    (process_version);

CREATE INDEX IF NOT EXISTS ix_pisl_rpver  ON Process_Instance_State_Log    (root_process_version);
CREATE INDEX IF NOT EXISTS ix_pinl_rpver  ON Process_Instance_Node_Log     (root_process_version);
CREATE INDEX IF NOT EXISTS ix_pivl_rpver  ON Process_Instance_Variable_Log (root_process_version);
CREATE INDEX IF NOT EXISTS ix_piel_rpver  ON Process_Instance_Error_Log    (root_process_version);

CREATE INDEX IF NOT EXISTS ix_jel_procid  ON Job_Execution_Log (process_id);
CREATE INDEX IF NOT EXISTS ix_jel_rprocid ON Job_Execution_Log (root_process_id);
CREATE INDEX IF NOT EXISTS ix_jel_pver    ON Job_Execution_Log (process_version);
CREATE INDEX IF NOT EXISTS ix_jel_rpver   ON Job_Execution_Log (root_process_version);
