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

package org.optaplanner.test.impl.score;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.api.score.constraint.ConstraintMatchTotal;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.score.director.ScoreDirectorFactoryConfig;
import org.optaplanner.core.impl.domain.solution.descriptor.SolutionDescriptor;
import org.optaplanner.core.impl.score.director.InnerScoreDirector;
import org.optaplanner.core.impl.score.director.InnerScoreDirectorFactory;
import org.optaplanner.core.impl.solver.DefaultSolverFactory;
import org.optaplanner.test.impl.score.buildin.hardsoft.HardSoftScoreVerifier;

/**
 * Used in unit tests to assert that 1 particular solution has a specific weight for a specific constraint.
 * This works well to verify that the implementation is in sync with the business constraints.
 * This does not work well to detect score corruption,
 * use {@link ScoreDirectorFactoryConfig#setAssertionScoreDirectorFactory(ScoreDirectorFactoryConfig)} for that instead.
 * <p>
 * Do not use this class directly, instead use the specific subclass for your {@link Score} type,
 * such as {@link HardSoftScoreVerifier}.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @see HardSoftScoreVerifier
 * @deprecated Score DRL is deprecated and will be removed in a future major version of OptaPlanner.
 *             See <a href="https://www.optaplanner.org/download/upgradeRecipe/drl-to-constraint-streams-migration.html">DRL to
 *             Constraint Streams migration recipe</a>.
 */
@Deprecated(forRemoval = true)
public abstract class AbstractScoreVerifier<Solution_> {

    protected final InnerScoreDirectorFactory<Solution_, ?> scoreDirectorFactory;

    /**
     * @param solverFactory never null, the {@link SolverFactory} of which you want to test the constraints.
     * @param expectedScoreClass never null, used to fail fast if a {@link SolverFactory} with another {@link Score} type is
     *        used.
     */
    public AbstractScoreVerifier(SolverFactory<Solution_> solverFactory, Class<? extends Score<?>> expectedScoreClass) {
        if (solverFactory == null) {
            throw new IllegalStateException("The solverFactory (" + solverFactory + ") cannot be null.");
        }
        scoreDirectorFactory = ((DefaultSolverFactory<Solution_>) solverFactory).getScoreDirectorFactory();
        SolutionDescriptor<Solution_> solutionDescriptor = scoreDirectorFactory.getSolutionDescriptor();
        Class<? extends Score<?>> scoreClass = solutionDescriptor.getScoreDefinition().getScoreClass();
        if (expectedScoreClass != scoreClass) {
            throw new IllegalStateException("The solution's scoreClass (" + scoreClass
                    + ") differs from the test's expectedScoreClass (" + expectedScoreClass + ").");
        }
    }

    /**
     * Assert that the constraint of {@link PlanningSolution} has the expected weight for that score level.
     *
     * @param constraintPackage sometimes null.
     *        When null, {@code constraintName} for the {@code scoreLevel} must be unique.
     * @param scoreLevel at least 0
     * @param constraintName never null, the name of the constraint
     * @param expectedWeight never null, the total weight for all matches of that 1 constraint
     * @param solution never null
     */
    protected void assertWeight(String constraintPackage, String constraintName, int scoreLevel, Number expectedWeight,
            Solution_ solution) {
        ConstraintMatchTotal<?> matchTotal;
        try (InnerScoreDirector<Solution_, ?> scoreDirector = scoreDirectorFactory.buildScoreDirector()) {
            scoreDirector.setWorkingSolution(solution);
            scoreDirector.calculateScore();
            matchTotal = findConstraintMatchTotal(constraintPackage, constraintName, scoreDirector);
        }
        // A matchTotal is null if the constraint did match now and never matched in a previous incremental calculation
        // (including those that are undone).
        // To avoid user pitfalls, the expectedWeight cannot be null and a matchTotal of null is treated as zero.
        if (expectedWeight == null) {
            throw new IllegalArgumentException("The expectedWeight (" + expectedWeight + ") cannot be null,"
                    + " regardless of the matchTotal (" + matchTotal + ").");
        }
        if (matchTotal == null) {
            if (expectedWeight instanceof Byte) {
                assertThat((byte) 0).isEqualTo(expectedWeight);
            } else if (expectedWeight instanceof Short) {
                assertThat((short) 0).isEqualTo(expectedWeight);
            } else if (expectedWeight instanceof Integer) {
                assertThat(expectedWeight).isEqualTo(0);
            } else if (expectedWeight instanceof Long) {
                assertThat(expectedWeight).isEqualTo(0L);
            } else if (expectedWeight instanceof Float) {
                assertThat(expectedWeight).isEqualTo(0F);
            } else if (expectedWeight instanceof Double) {
                assertThat(expectedWeight).isEqualTo(0D);
            } else if (expectedWeight instanceof BigInteger) {
                assertThat(expectedWeight).isEqualTo(BigInteger.ZERO);
            } else if (expectedWeight instanceof BigDecimal) {
                assertThat(expectedWeight).isEqualTo(BigDecimal.ZERO);
            } else {
                throw new IllegalStateException("Unsupported " + Number.class.getSimpleName()
                        + " type (" + expectedWeight.getClass() + ") for expectedWeight (" + expectedWeight + ").");
            }
        } else {
            assertThat(matchTotal.getScore().toLevelNumbers()[scoreLevel]).isEqualTo(expectedWeight);
        }
    }

    /**
     * @param constraintPackage sometimes null.
     *        When null, {@code constraintName} for the {@code scoreLevel} must be unique.
     * @param constraintName never null, the name of the constraint
     * @param scoreDirector never null
     * @return null if there is no constraint matched or the constraint doesn't exist
     */
    private ConstraintMatchTotal<?> findConstraintMatchTotal(String constraintPackage, String constraintName,
            InnerScoreDirector<Solution_, ?> scoreDirector) {
        if (constraintPackage != null) {
            String constraintId = ConstraintMatchTotal.composeConstraintId(constraintPackage, constraintName);
            return scoreDirector.getConstraintMatchTotalMap().get(constraintId);
        }
        ConstraintMatchTotal<?> matchTotal = null;
        for (ConstraintMatchTotal<?> selectedMatchTotal : scoreDirector.getConstraintMatchTotalMap().values()) {
            if (selectedMatchTotal.getConstraintName().equals(constraintName)) {
                if (matchTotal != null) {
                    throw new IllegalArgumentException("The constraintName (" + constraintName
                            + ") is used by 2 different constraintMatches (" + matchTotal.getConstraintId()
                            + " and " + selectedMatchTotal.getConstraintId() + ").");
                }
                matchTotal = selectedMatchTotal;
            }
        }
        return matchTotal;
    }

}
