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
package org.jbpm.compiler.canonical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

public class ProcessMetaData {

    private final String processPackageName;
    private final String processBaseClassName;
    private String processClassName;

    private String processId;

    private String extractedProcessId;

    private String processName;

    private String processVersion;

    private CompilationUnit generatedClassModel;

    private Set<String> workItems = new HashSet<>();
    private Map<String, String> subProcesses = new HashMap<>();

    private Map<String, String> signals = new HashMap<>();

    private List<TriggerMetaData> triggers = new ArrayList<>();

    private Map<String, Collection<CompilationUnit>> consumers = new HashMap<>();
    private Map<String, Collection<CompilationUnit>> producers = new HashMap<>();

    private boolean startable;
    private boolean dynamic;

    private String modelPackageName;
    private String modelClassName;

    private Map<String, CompilationUnit> generatedHandlers = new HashMap<>();
    private Set<CompilationUnit> generatedListeners = new HashSet<>();

    /** Extra private-static helper methods to be added to the generated XxxProcess class. */
    private List<MethodDeclaration> processHelperMethods = new ArrayList<>();

    public ProcessMetaData(String processId, String extractedProcessId, String processName, String processVersion, String processPackageName, String processClassName) {
        super();
        this.processId = processId;
        this.extractedProcessId = extractedProcessId;
        this.processName = processName;
        this.processVersion = processVersion;
        this.processPackageName = processPackageName;
        this.processClassName = processPackageName == null ? processClassName : processPackageName + "." + processClassName;
        this.processBaseClassName = processClassName;
    }

    public String getPackageName() {
        return processPackageName;
    }

    public String getProcessBaseClassName() {
        return processBaseClassName;
    }

    public String getProcessClassName() {
        return processClassName;
    }

    public void setProcessClassName(String processClassName) {
        this.processClassName = processClassName;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getExtractedProcessId() {
        return extractedProcessId;
    }

    public void setExtractedProcessId(String extractedProcessId) {
        this.extractedProcessId = extractedProcessId;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getProcessVersion() {
        return processVersion;
    }

    public void setProcessVersion(String processVersion) {
        this.processVersion = processVersion;
    }

    public CompilationUnit getGeneratedClassModel() {
        return generatedClassModel;
    }

    public void setGeneratedClassModel(CompilationUnit generatedClassModel) {
        this.generatedClassModel = generatedClassModel;
    }

    public Set<String> getWorkItems() {
        return workItems;
    }

    public void setWorkItems(Set<String> workItems) {
        this.workItems = workItems;
    }

    public Map<String, String> getSubProcesses() {
        return subProcesses;
    }

    public ProcessMetaData addSubProcess(String processId, String subProcessId) {
        subProcesses.put(processId, subProcessId);
        return this;
    }

    public Map<String, CompilationUnit> getGeneratedHandlers() {
        return generatedHandlers;
    }

    public ProcessMetaData addGeneratedHandler(String workName, CompilationUnit handlerClass) {
        generatedHandlers.put(workName, handlerClass);
        return this;
    }

    public Set<CompilationUnit> getGeneratedListeners() {
        return generatedListeners;
    }

    public void setGeneratedListeners(Set<CompilationUnit> generatedListeners) {
        this.generatedListeners = generatedListeners;
    }

    public Map<String, Collection<CompilationUnit>> getConsumers() {
        return Collections.unmodifiableMap(consumers);
    }

    public void addConsumer(String triggerName, CompilationUnit unit) {
        consumers.computeIfAbsent(triggerName, k -> new ArrayList<>()).add(unit);
    }

    public Map<String, Collection<CompilationUnit>> getProducers() {
        return Collections.unmodifiableMap(producers);
    }

    public void addProducer(String triggerName, CompilationUnit unit) {
        producers.computeIfAbsent(triggerName, k -> new ArrayList<>()).add(unit);
    }

    public Map<String, String> getSignals() {
        return signals;
    }

    public ProcessMetaData addSignal(String name, String value) {
        signals.put(name, value);
        return this;
    }

    public List<TriggerMetaData> getTriggers() {
        return triggers;
    }

    public ProcessMetaData addTrigger(TriggerMetaData trigger) {
        triggers.add(trigger);
        return this;
    }

    public boolean isStartable() {
        return startable;
    }

    public void setStartable(boolean startable) {
        this.startable = startable;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public void setDynamic(boolean dynamic) {
        this.dynamic = dynamic;
    }

    public List<MethodDeclaration> getProcessHelperMethods() {
        return processHelperMethods;
    }

    public void addProcessHelperMethod(MethodDeclaration method) {
        processHelperMethods.add(method);
    }

    @Override
    public String toString() {
        return "ProcessMetaData [processClassName=" + processClassName +
                ", processId=" + processId + ", extractedProcessId=" + extractedProcessId +
                ", processName=" + processName + ", processVersion=" + processVersion +
                ", workItems=" + workItems + "]";
    }

    public String getModelPackageName() {
        return modelPackageName;
    }

    public void setModelPackageName(String modelPackageName) {
        this.modelPackageName = modelPackageName;
    }

    public String getModelClassName() {
        return modelClassName;
    }

    public void setModelClassName(String modelClassName) {
        this.modelClassName = modelClassName;
    }

}
