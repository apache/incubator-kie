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
package org.kie.test.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit Platform {@link TestExecutionListener} that records test lifecycle events
 * to {@code target/testStatusListener.*.log}.
 * <p>
 * Registration is opt-in: enable the {@code enableTestStatusListener} Maven profile
 * (for example {@code -DenableTestStatusListener} on {@code drools-compiler}), which
 * puts a {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}
 * descriptor on the Surefire classpath so the Platform launcher discovers this class.
 */
public class TestStatusListener implements TestExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestStatusListener.class);

    private final BufferedWriter writer;

    public TestStatusListener() {
        try {
            this.writer = Files.newBufferedWriter(
                    Paths.get("./target/testStatusListener"
                                      + "." + ManagementFactory.getRuntimeMXBean().getName().replaceAll("\\W+", "")
                                      + "." + System.nanoTime()
                                      + ".log"));
        } catch (final IOException e) {
            throw new RuntimeException( "TestStatusListener unable to open writer for logging to file test status updates.", e );
        }
    }

    @Override
    public void testPlanExecutionStarted(final TestPlan testPlan) {
        try {
            write("testPlanExecutionStarted", "tests=" + testPlan.countTestIdentifiers(TestIdentifier::isTest));
        } catch (final IOException e) {
            LOG.error("Exception", e);
        }
    }

    @Override
    public void testPlanExecutionFinished(final TestPlan testPlan) {
        try {
            write("testPlanExecutionFinished", "tests=" + testPlan.countTestIdentifiers(TestIdentifier::isTest));
        } catch (final IOException e) {
            LOG.error("Exception", e);
        }
    }

    @Override
    public void executionStarted(final TestIdentifier testIdentifier) {
        try {
            write("executionStarted", testIdentifier);
        } catch (final IOException e) {
            LOG.error("Exception", e);
        }
    }

    @Override
    public void executionFinished(final TestIdentifier testIdentifier, final TestExecutionResult testExecutionResult) {
        try {
            if (testExecutionResult.getStatus() == TestExecutionResult.Status.FAILED) {
                final Optional<Throwable> throwable = testExecutionResult.getThrowable();
                if (throwable.isPresent() && isTimeoutFailure(throwable.get())) {
                    writeThreadDump();
                }
                write("executionFailed", testIdentifier.getDisplayName() + "\t" + throwable.map(Throwable::toString).orElse(""));
            } else if (testExecutionResult.getStatus() == TestExecutionResult.Status.ABORTED) {
                write("executionAborted", testIdentifier.getDisplayName() + "\t"
                        + testExecutionResult.getThrowable().map(Throwable::toString).orElse(""));
            } else {
                write("executionFinished", testIdentifier);
            }
        } catch (final IOException e) {
            LOG.error("Exception", e);
        }
    }

    @Override
    public void executionSkipped(final TestIdentifier testIdentifier, final String reason) {
        try {
            write("executionSkipped", testIdentifier.getDisplayName() + "\t" + reason);
        } catch (final IOException e) {
            LOG.error("Exception", e);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (writer != null) {
            writer.close();
        }
    }

    private synchronized void write(final String method, final TestIdentifier testIdentifier) throws IOException {
        writer.write(method);
        writer.write("\t");
        writer.write(testIdentifier.getUniqueId());
        writer.write("\t");
        writer.write(testIdentifier.getDisplayName());
        writer.newLine();
        writer.flush();
    }

    private synchronized void write(final String method, final String detail) throws IOException {
        writer.write(method);
        writer.write("\t");
        if (detail != null) {
            writer.write(detail);
        }
        writer.newLine();
        writer.flush();
    }

    private synchronized void writeThreadDump() throws IOException {
        final ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        for (final ThreadInfo ti : threadMxBean.dumpAllThreads(true, true)) {
            writer.write( ti.toString() );
            if ( ti.getStackTrace().length > 8 ) { writer.write("full stacktrace:\n"); writer.write(fullStackTrace(ti)); writer.write("\n"); }
        }
        writer.newLine();
        writer.flush();
    }

    private static String fullStackTrace(final ThreadInfo ti) {
        final StringBuilder sb = new StringBuilder();
        Stream.of( ti.getStackTrace() )
            .forEach( ste -> {
                sb.append(" ");
                sb.append(ste.toString());
                sb.append("\n");
                Stream.of( ti.getLockedMonitors() )
                    .filter( m -> ste.equals(m.getLockedStackFrame()) )
                    .forEach( lm -> {
                        sb.append("  (locked: ");
                        sb.append(lm);
                        sb.append(" )\n");
                    });
            });
        return sb.toString();
    }

    private static boolean isTimeoutFailure(final Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof TimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            final String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
        }
        return false;
    }

}
