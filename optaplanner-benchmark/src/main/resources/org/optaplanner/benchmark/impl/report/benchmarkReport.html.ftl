<#ftl output_format="HTML"> <#-- So that Freemarker escapes automatically. -->
<#-- @ftlvariable name="benchmarkReport" type="org.optaplanner.benchmark.impl.report.BenchmarkReport" -->
<!DOCTYPE html>
<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one
  ~ or more contributor license agreements.  See the NOTICE file
  ~ distributed with this work for additional information
  ~ regarding copyright ownership.  The ASF licenses this file
  ~ to you under the Apache License, Version 2.0 (the
  ~ "License"); you may not use this file except in compliance
  ~ with the License.  You may obtain a copy of the License at
  ~
  ~   http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing,
  ~ software distributed under the License is distributed on an
  ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~ KIND, either express or implied.  See the License for the
  ~ specific language governing permissions and limitations
  ~ under the License.
  -->
<html lang="en">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>${benchmarkReport.plannerBenchmarkResult.name} Planner benchmark report</title>
    <link href="twitterbootstrap/css/bootstrap.css" rel="stylesheet">
    <link href="twitterbootstrap/css/bootstrap-responsive.css" rel="stylesheet"/>
    <link href="twitterbootstrap/css/prettify.css" rel="stylesheet"/>
    <link href="website/css/benchmarkReport.css" rel="stylesheet"/>
    <!-- Le HTML5 shim, for IE6-8 support of HTML5 elements -->
    <!--[if lt IE 9]>
    <script src="http://html5shim.googlecode.com/svn/trunk/html5.js"></script>
    <![endif]-->
</head>
<#macro addSolverBenchmarkBadges solverBenchmarkResult>
    <#if solverBenchmarkResult.favorite>
        <span class="badge badge-success">${solverBenchmarkResult.ranking}</span>
    <#elseif solverBenchmarkResult.ranking??>
        <span class="badge">${solverBenchmarkResult.ranking}</span>
    </#if>

    <#if solverBenchmarkResult.hasAnyFailure()>
        <span class="badge badge-important" data-toggle="tooltip" title="Failed benchmark">F</span>
    <#elseif solverBenchmarkResult.hasAnyUninitializedSolution()>
        <span class="badge badge-important" data-toggle="tooltip" title="Has an uninitialized solution">!</span>
    <#elseif solverBenchmarkResult.hasAnyInfeasibleScore()>
        <span class="badge badge-warning" data-toggle="tooltip" title="Has an infeasible score">!</span>
    </#if>
</#macro>
<#macro addProlblemBenchmarkBadges problemBenchmarkResult>
    <#if problemBenchmarkResult.hasAnyFailure()>
        <span class="badge badge-important" data-toggle="tooltip" title="Failed benchmark">F</span>
    </#if>
</#macro>
<#macro addSolverProblemBenchmarkResultBadges solverProblemBenchmarkResult>
    <#if solverProblemBenchmarkResult.winner>
        <span class="badge badge-success">${solverProblemBenchmarkResult.ranking}</span>
    <#elseif solverProblemBenchmarkResult.ranking??>
        <span class="badge">${solverProblemBenchmarkResult.ranking}</span>
    </#if>

    <#if solverProblemBenchmarkResult.hasAnyFailure()>
        <span class="badge badge-important" data-toggle="tooltip" title="Failed benchmark">F</span>
    <#elseif !solverProblemBenchmarkResult.initialized>
        <span class="badge badge-important" data-toggle="tooltip" title="Uninitialized solution">!</span>
    <#elseif !solverProblemBenchmarkResult.scoreFeasible>
        <span class="badge badge-warning" data-toggle="tooltip" title="Infeasible score">!</span>
    </#if>
</#macro>
<#macro addScoreLevelChartList chartFileList idPrefix>
    <div class="tabbable tabs-right">
        <ul class="nav nav-tabs">
        <#assign scoreLevelIndex = 0>
        <#list chartFileList as chartFile>
            <li<#if scoreLevelIndex == benchmarkReport.defaultShownScoreLevelIndex> class="active"</#if>>
                <a href="#${idPrefix}_chart_${scoreLevelIndex}" data-toggle="tab">${benchmarkReport.plannerBenchmarkResult.findScoreLevelLabel(scoreLevelIndex)?capitalize}</a>
            </li>
            <#assign scoreLevelIndex = scoreLevelIndex + 1>
        </#list>
        </ul>
        <div class="tab-content">
        <#assign scoreLevelIndex = 0>
        <#list chartFileList as chartFile>
            <div class="tab-pane<#if scoreLevelIndex == benchmarkReport.defaultShownScoreLevelIndex> active</#if>" id="${idPrefix}_chart_${scoreLevelIndex}">
                <div class="benchmark-chart">
                    <img src="summary/${chartFile.name}"/>
                </div>
            </div>
            <#assign scoreLevelIndex = scoreLevelIndex + 1>
        </#list>
        </div>
    </div>
</#macro>
<body onload="prettyPrint()">

<div class="container-fluid">
    <div class="row-fluid">
        <div class="span2">
            <div class="benchmark-report-nav">
                <div style="margin-top: 10px;">
                    <a href="https://www.optaplanner.org">
                        <img src="website/img/optaPlannerLogo.png" alt="OptaPlanner"/>
                    </a>
                </div>
                <div style="margin-top: 10px; margin-bottom: 10px;">
                    <ul class="nav nav-list">
                        <li><a href="#summary">Summary</a></li>
                        <li>
                            <ul class="nav nav-list">
                                <li><a href="#summary_result">Result</a></li>
                                <li><a href="#summary_performance">Performance</a></li>
                            </ul>
                        </li>
                        <li class="divider"></li>
                        <li><a href="#problemBenchmarkResult">Problem benchmarks</a></li>
                        <li>
                            <ul class="nav nav-list">
                            <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                <li><a href="#problemBenchmark_${problemBenchmarkResult.anchorId}">${problemBenchmarkResult.name}&nbsp;<@addProlblemBenchmarkBadges problemBenchmarkResult=problemBenchmarkResult/></a></li>
                            </#list>
                            </ul>
                        </li>
                        <li class="divider"></li>
                        <li><a href="#solverBenchmarkResult">Solver benchmarks</a></li>
                        <li>
                            <ul class="nav nav-list">
                            <#list benchmarkReport.plannerBenchmarkResult.solverBenchmarkResultList as solverBenchmarkResult>
                                <li><a href="#solverBenchmark_${solverBenchmarkResult.anchorId}">${solverBenchmarkResult.name}&nbsp;<@addSolverBenchmarkBadges solverBenchmarkResult=solverBenchmarkResult/></a></li>
                            </#list>
                            </ul>
                        </li>
                        <li class="divider"></li>
                        <li><a href="#benchmarkInformation">Benchmark information</a></li>
                    </ul>
                </div>
            </div>
        </div>
        <div class="span10">
            <header class="main-page-header">
                <h1>Benchmark report</h1>
            </header>
            <section id="summary">
                <div class="page-header">
                    <h1>Summary</h1>
                </div>
            <#if benchmarkReport.plannerBenchmarkResult.hasAnyFailure()>
                <div class="alert alert-error">
                    <p>${benchmarkReport.plannerBenchmarkResult.failureCount} benchmarks have failed!</p>
                </div>
            </#if>
            <#list benchmarkReport.warningList as warning>
                <div class="alert alert-error">
                    <p>${warning}</p>
                </div>
            </#list>

                <section id="summary_result">
                    <h2>Result summary</h2>
                    <div class="tabbable">
                        <ul class="nav nav-pills">
                            <li class="active">
                                <a href="#summary_bestScore" data-toggle="tab">Best score</a>
                            </li>
                            <li>
                                <a href="#summary_bestScoreScalability" data-toggle="tab">Best score scalability</a>
                            </li>
                            <li>
                                <a href="#summary_bestScoreDistribution" data-toggle="tab">Best score distribution</a>
                            </li>
                            <li>
                                <a href="#summary_winningScoreDifference" data-toggle="tab">Winning score difference</a>
                            </li>
                            <li>
                                <a href="#summary_worstScoreDifferencePercentage" data-toggle="tab">Worst score difference percentage (ROI)</a>
                            </li>
                        </ul>
                        <div class="tab-content">
                            <div class="tab-pane active" id="summary_bestScore">
                                <h3>Best score summary</h3>
                                <p>Useful for visualizing the best solver configuration.</p>
                                <@addScoreLevelChartList chartFileList=benchmarkReport.bestScoreSummaryChartFileList idPrefix="summary_bestScore" />
                                <table class="benchmark-table table table-striped table-bordered">
                                    <tr>
                                        <th rowspan="2">Solver</th>
                                        <th rowspan="2">Total</th>
                                        <th rowspan="2">Average</th>
                                        <th rowspan="2">Standard Deviation</th>
                                        <th colspan="${benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList?size}">Problem</th>
                                    </tr>
                                    <tr>
                                    <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                        <th>${problemBenchmarkResult.name}</th>
                                    </#list>
                                    </tr>
                                <#list benchmarkReport.plannerBenchmarkResult.solverBenchmarkResultList as solverBenchmarkResult>
                                    <tr<#if solverBenchmarkResult.favorite> class="favoriteSolverBenchmark"</#if>>
                                        <th>${solverBenchmarkResult.name}&nbsp;<@addSolverBenchmarkBadges solverBenchmarkResult=solverBenchmarkResult/></th>
                                        <td>${solverBenchmarkResult.totalScore!""}</td>
                                        <td>${solverBenchmarkResult.averageScore!""}</td>
                                        <td>${solverBenchmarkResult.standardDeviationString!""}</td>
                                        <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                            <#if !solverBenchmarkResult.findSingleBenchmark(problemBenchmarkResult)??>
                                                <td></td>
                                            <#else>
                                                <#assign singleBenchmarkResult = solverBenchmarkResult.findSingleBenchmark(problemBenchmarkResult)>
                                                <#if !singleBenchmarkResult.hasAllSuccess()>
                                                    <td><span class="label label-important">Failed</span></td>
                                                <#else>
                                                    <#if solverBenchmarkResult.subSingleCount lte 1>
                                                        <td>${singleBenchmarkResult.averageScore!""}&nbsp;<@addSolverProblemBenchmarkResultBadges solverProblemBenchmarkResult=singleBenchmarkResult/></td>
                                                    <#else>
                                                        <td><div class="dropdown">
                                                            <span class="nav nav-pills dropdown-toggle" id="dLabel" type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                                                                ${singleBenchmarkResult.averageScore!""}&nbsp;<@addSolverProblemBenchmarkResultBadges solverProblemBenchmarkResult=singleBenchmarkResult/>
                                                                <span class="caret"></span>
                                                            </span>
                                                            <ul class="dropdown-menu" aria-labelledby="dLabel">
                                                                <li class="dropdown-header"><strong>Individual runs</strong></li>
                                                                <li role="separator" class="divider"></li>
                                                                <#list singleBenchmarkResult.subSingleBenchmarkResultList as subSingleBenchmarkResult>
                                                                    <li class="dropdown-header"><strong>Run #${subSingleBenchmarkResult.getSubSingleBenchmarkIndex()}</strong></li>
                                                                    <li>${subSingleBenchmarkResult.score!""}&nbsp;<@addSolverProblemBenchmarkResultBadges solverProblemBenchmarkResult=subSingleBenchmarkResult/></li>
                                                                </#list>
                                                                <li role="separator" class="divider"></li>
                                                                <li class="dropdown-header"><strong>Average</strong></li>
                                                                <li>${singleBenchmarkResult.averageScore!""}</li>
                                                                <li class="dropdown-header"><strong>Standard Deviation</strong></li>
                                                                <li>${singleBenchmarkResult.standardDeviationString!""}</li>
                                                                <li class="dropdown-header"><strong>Best</strong></li>
                                                                <li>${singleBenchmarkResult.best.score!""}</li>
                                                                <li class="dropdown-header"><strong>Worst</strong></li>
                                                                <li>${singleBenchmarkResult.worst.score!""}</li>
                                                                <li class="dropdown-header"><strong>Median</strong></li>
                                                                <li>${singleBenchmarkResult.median.score!""}</li>
                                                            </ul>
                                                        </div></td>
                                                    </#if>
                                                </#if>
                                            </#if>
                                        </#list>
                                    </tr>
                                </#list>
                                </table>
                            </div>
                            <div class="tab-pane" id="summary_bestScoreScalability">
                                <h3>Best score scalability summary</h3>
                                <p>Useful for visualizing the scalability of each solver configuration.</p>
                                <@addScoreLevelChartList chartFileList=benchmarkReport.bestScoreScalabilitySummaryChartFileList idPrefix="summary_bestScoreScalability" />
                            </div>
                            <div class="tab-pane" id="summary_bestScoreDistribution">
                                <h3>Best score distribution summary</h3>
                                <p>Useful for visualizing the reliability of each solver configuration.</p>
                                <#assign maximumSubSingleCount = benchmarkReport.plannerBenchmarkResult.getMaximumSubSingleCount()>
                                <p>Maximum subSingle count: ${maximumSubSingleCount!""}<br/></p>
                                <#if maximumSubSingleCount lte 1>
                                    <div class="alert alert-error">
                                        <p>The benchmarker did not run multiple subSingles, so there is no distribution and therefore no reliability indication.</p>
                                    </div>
                                </#if>
                                <@addScoreLevelChartList chartFileList=benchmarkReport.bestScoreDistributionSummaryChartFileList idPrefix="summary_bestScoreDistribution" />
                            </div>
                            <div class="tab-pane" id="summary_winningScoreDifference">
                                <h3>Winning score difference summary</h3>
                                <p>Useful for zooming in on the results of the best score summary.</p>
                                <@addScoreLevelChartList chartFileList=benchmarkReport.winningScoreDifferenceSummaryChartFileList idPrefix="summary_winningScoreDifference" />
                                <table class="benchmark-table table table-striped table-bordered">
                                    <tr>
                                        <th rowspan="2">Solver</th>
                                        <th rowspan="2">Total</th>
                                        <th rowspan="2">Average</th>
                                        <th colspan="${benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList?size}">Problem</th>
                                    </tr>
                                    <tr>
                                    <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                        <th>${problemBenchmarkResult.name}</th>
                                    </#list>
                                    </tr>
                                <#list benchmarkReport.plannerBenchmarkResult.solverBenchmarkResultList as solverBenchmarkResult>
                                    <tr<#if solverBenchmarkResult.favorite> class="favoriteSolverBenchmark"</#if>>
                                        <th>${solverBenchmarkResult.name}&nbsp;<@addSolverBenchmarkBadges solverBenchmarkResult=solverBenchmarkResult/></th>
                                        <td>${solverBenchmarkResult.totalWinningScoreDifference!""}</td>
                                        <td>${solverBenchmarkResult.averageWinningScoreDifference!""}</td>
                                        <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                            <#if !solverBenchmarkResult.findSingleBenchmark(problemBenchmarkResult)??>
                                                <td></td>
                                            <#else>
                                                <#assign singleBenchmarkResult = solverBenchmarkResult.findSingleBenchmark(problemBenchmarkResult)>
                                                <#if !singleBenchmarkResult.hasAllSuccess()>
                                                    <td><span class="label label-important">Failed</span></td>
                                                <#else>
                                                    <td>${singleBenchmarkResult.winningScoreDifference}&nbsp;<@addSolverProblemBenchmarkResultBadges solverProblemBenchmarkResult=singleBenchmarkResult/></td>
                                                </#if>
                                            </#if>
                                        </#list>
                                    </tr>
                                </#list>
                                </table>
                            </div>
                            <div class="tab-pane" id="summary_worstScoreDifferencePercentage">
                                <h3>Worst score difference percentage summary (ROI)</h3>
                                <p>Useful for visualizing the return on investment (ROI) to decision makers.</p>
                                <@addScoreLevelChartList chartFileList=benchmarkReport.worstScoreDifferencePercentageSummaryChartFileList idPrefix="summary_worstScoreDifferencePercentage" />
                                <table class="benchmark-table table table-striped table-bordered">
                                    <tr>
                                        <th rowspan="2">Solver</th>
                                        <th rowspan="2">Average</th>
                                        <th colspan="${benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList?size}">Problem</th>
                                    </tr>
                                    <tr>
                                    <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                        <th>${problemBenchmarkResult.name}</th>
                                    </#list>
                                    </tr>
                                <#list benchmarkReport.plannerBenchmarkResult.solverBenchmarkResultList as solverBenchmarkResult>
                                    <tr<#if solverBenchmarkResult.favorite> class="favoriteSolverBenchmark"</#if>>
                                        <th>${solverBenchmarkResult.name}&nbsp;<@addSolverBenchmarkBadges solverBenchmarkResult=solverBenchmarkResult/></th>
                                        <#if !solverBenchmarkResult.averageWorstScoreDifferencePercentage??>
                                            <td></td>
                                        <#else>
                                            <td>${solverBenchmarkResult.averageWorstScoreDifferencePercentage.toString(.locale_object)}</td>
                                        </#if>
                                        <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                            <#if !solverBenchmarkResult.findSingleBenchmark(problemBenchmarkResult)??>
                                                <td></td>
                                            <#else>
                                                <#assign singleBenchmarkResult = solverBenchmarkResult.findSingleBenchmark(problemBenchmarkResult)>
                                                <#if !singleBenchmarkResult.hasAllSuccess()>
                                                    <td><span class="label label-important">Failed</span></td>
                                                <#else>
                                                    <td>${singleBenchmarkResult.worstScoreDifferencePercentage.toString(.locale_object)}&nbsp;<@addSolverProblemBenchmarkResultBadges solverProblemBenchmarkResult=singleBenchmarkResult/></td>
                                                </#if>
                                            </#if>
                                        </#list>
                                    </tr>
                                </#list>
                                </table>
                            </div>
                        </div>
                    </div>
                </section>

                <section id="summary_performance">
                    <h2>Performance summary</h2>
                    <div class="tabbable">
                        <ul class="nav nav-pills">
                            <li class="active">
                                <a href="#summary_scoreCalculationSpeed" data-toggle="tab">Score calculation speed</a>
                            </li>
                            <li>
                                <a href="#summary_worstScoreCalculationSpeedDifferencePercentage" data-toggle="tab">Worst score calculation speed difference percentage</a>
                            </li>
                            <li>
                                <a href="#summary_timeSpent" data-toggle="tab">Time spent</a>
                            </li>
                            <li>
                                <a href="#summary_timeSpentScalability" data-toggle="tab">Time spent scalability</a>
                            </li>
                            <li>
                                <a href="#summary_bestScorePerTimeSpent" data-toggle="tab">Best score per time spent</a>
                            </li>
                        </ul>
                        <div class="tab-content">
                            <div class="tab-pane active" id="summary_scoreCalculationSpeed">
                                <h3>Score calculation speed summary</h3>
                                <p>
                                    Useful for comparing different score calculators and/or constraint implementations
                                    (presuming that the solver configurations do not differ otherwise).
                                    Also useful to measure the scalability cost of an extra constraint.
                                </p>
                                <div class="benchmark-chart">
                                    <img src="summary/${benchmarkReport.scoreCalculationSpeedSummaryChartFile.name}"/>
                                </div>
                                <table class="benchmark-table table table-striped table-bordered">
                                    <tr>
                                        <th rowspan="2">Solver</th>
                                        <th rowspan="2">Average</th>
                                        <th colspan="${benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList?size}">Problem</th>
                                    </tr>
                                    <tr>
                                    <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                        <th>${problemBenchmarkResult.name}</th>
                                    </#list>
                                    </tr>
                                    <tr>
                                        <th class="problemScale">Problem scale</th>
                                        <td class="problemScale">${benchmarkReport.plannerBenchmarkResult.averageProblemScale!""}</td>
                                    <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                        <td class="problemScale">${problemBenchmarkResult.problemScale!""}</td>
                                    </#list>
                                    </tr>
                                <#list benchmarkReport.plannerBenchmarkResult.solverBenchmarkResultList as solverBenchmarkResult>
                                    <tr<#if solverBenchmarkResult.favorite> class="favoriteSolverBenchmark"</#if>>
                                        <th>${solverBenchmarkResult.name}&nbsp;<@addSolverBenchmarkBadges solverBenchmarkResult=solverBenchmarkResult/></th>
                                        <td>${solverBenchmarkResult.averageScoreCalculationSpeed!""}/s</td>
                                        <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                            <#if !solverBenchmarkResult.findSingleBenchmark(problemBenchmarkResult)??>
                                                <td></td>
                                            <#else>
                                                <#assign singleBenchmarkResult = solverBenchmarkResult.findSingleBenchmark(problemBenchmarkResult)>
                                                <#if !singleBenchmarkResult.hasAllSuccess()>
                                                    <td><span class="label label-important">Failed</span></td>
                                                <#else>
                                                    <#if solverBenchmarkResult.subSingleCount lte 1>
                                                        <td>${singleBenchmarkResult.scoreCalculationSpeed}/s</td>
                                                    <#else>
                                                        <td><div class="dropdown">
                                                            <span class="nav nav-pills dropdown-toggle" id="dLabel" type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                                                                ${singleBenchmarkResult.scoreCalculationSpeed!""}/s
                                                                <span class="caret"></span>
                                                            </span>
                                                        <ul class="dropdown-menu" aria-labelledby="dLabel">
                                                            <li class="dropdown-header"><strong>Individual runs</strong></li>
                                                            <li role="separator" class="divider"></li>
                                                            <#list singleBenchmarkResult.subSingleBenchmarkResultList as subSingleBenchmarkResult>
                                                                <li class="dropdown-header"><strong>Run #${subSingleBenchmarkResult.getSubSingleBenchmarkIndex()}</strong></li>
                                                                <li>${subSingleBenchmarkResult.scoreCalculationSpeed!""}/s</li>
                                                            </#list>
                                                        </ul>
                                                      </div></td>
                                                    </#if>
                                                </#if>
                                            </#if>
                                        </#list>
                                    </tr>
                                </#list>
                                </table>
                            </div>
                            <div class="tab-pane" id="summary_worstScoreCalculationSpeedDifferencePercentage">
                                <h3>Worst score calculation speed difference percentage</h3>
                                <p>
                                    Useful for comparing different score calculators and/or constraint implementations
                                    (presuming that the solver configurations do not differ otherwise).
                                    Also useful to measure the scalability cost of an extra constraint.
                                </p>
                                <div class="benchmark-chart">
                                    <img src="summary/${benchmarkReport.worstScoreCalculationSpeedDifferencePercentageSummaryChartFile.name}"/>
                                </div>
                                <table class="benchmark-table table table-striped table-bordered">
                                    <tr>
                                        <th rowspan="2">Solver</th>
                                        <th rowspan="2">Average</th>
                                        <th colspan="${benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList?size}">Problem</th>
                                    </tr>
                                    <tr>
                                    <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                        <th>${problemBenchmarkResult.name}</th>
                                    </#list>
                                    </tr>
                                <#list benchmarkReport.plannerBenchmarkResult.solverBenchmarkResultList as solverBenchmarkResult>
                                    <tr<#if solverBenchmarkResult.favorite> class="favoriteSolverBenchmark"</#if>>
                                        <th>${solverBenchmarkResult.name}&nbsp;<@addSolverBenchmarkBadges solverBenchmarkResult=solverBenchmarkResult/></th>
                                        <#if solverBenchmarkResult.averageWorstScoreCalculationSpeedDifferencePercentage??>
                                            <td>${solverBenchmarkResult.averageWorstScoreCalculationSpeedDifferencePercentage?string["0.00%"]!""}</td>
                                        <#else>
                                            <td></td>
                                        </#if>
                                        <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                            <#if !solverBenchmarkResult.findSingleBenchmark(problemBenchmarkResult)??>
                                                <td></td>
                                            <#else>
                                                <#assign singleBenchmarkResult = solverBenchmarkResult.findSingleBenchmark(problemBenchmarkResult)>
                                                <#if !singleBenchmarkResult.hasAllSuccess()>
                                                    <td><span class="label label-important">Failed</span></td>
                                                <#else>
                                                    <td>${singleBenchmarkResult.worstScoreCalculationSpeedDifferencePercentage?string["0.00%"]!""}</td>
                                                </#if>
                                            </#if>
                                        </#list>
                                    </tr>
                                </#list>
                                </table>
                            </div>
                            <div class="tab-pane" id="summary_timeSpent">
                                <h3>Time spent summary</h3>
                                <p>Useful for visualizing the performance of construction heuristics (presuming that no other solver phases are configured).</p>
                                <div class="benchmark-chart">
                                    <img src="summary/${benchmarkReport.timeSpentSummaryChartFile.name}"/>
                                </div>
                                <table class="benchmark-table table table-striped table-bordered">
                                    <tr>
                                        <th rowspan="2">Solver</th>
                                        <th rowspan="2">Average</th>
                                        <th colspan="${benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList?size}">Problem</th>
                                    </tr>
                                    <tr>
                                    <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                        <th>${problemBenchmarkResult.name}</th>
                                    </#list>
                                    </tr>
                                    <tr>
                                        <th class="problemScale">Problem scale</th>
                                        <td class="problemScale">${benchmarkReport.plannerBenchmarkResult.averageProblemScale!""}</td>
                                    <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                        <td class="problemScale">${problemBenchmarkResult.problemScale!""}</td>
                                    </#list>
                                    </tr>
                                <#list benchmarkReport.plannerBenchmarkResult.solverBenchmarkResultList as solverBenchmarkResult>
                                    <tr<#if solverBenchmarkResult.favorite> class="favoriteSolverBenchmark"</#if>>
                                        <th>${solverBenchmarkResult.name}&nbsp;<@addSolverBenchmarkBadges solverBenchmarkResult=solverBenchmarkResult/></th>
                                        <td>${solverBenchmarkResult.averageTimeMillisSpent!0?string.@msDuration}</td>
                                        <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                                            <#if !solverBenchmarkResult.findSingleBenchmark(problemBenchmarkResult)??>
                                                <td></td>
                                            <#else>
                                                <#assign singleBenchmarkResult = solverBenchmarkResult.findSingleBenchmark(problemBenchmarkResult)>
                                                <#if !singleBenchmarkResult.hasAllSuccess()>
                                                    <td><span class="label label-important">Failed</span></td>
                                                <#else>
                                                    <#if solverBenchmarkResult.subSingleCount lte 1>
                                                        <td>${singleBenchmarkResult.timeMillisSpent?string.@msDuration}</td>
                                                    <#else>
                                                        <td><div class="dropdown">
                                                            <span class="nav nav-pills dropdown-toggle" id="dLabel" type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                                                                ${singleBenchmarkResult.timeMillisSpent?string.@msDuration}
                                                                <span class="caret"></span>
                                                            </span>
                                                            <ul class="dropdown-menu" aria-labelledby="dLabel">
                                                                <li class="dropdown-header"><strong>Individual runs</strong></li>
                                                                <li role="separator" class="divider"></li>
                                                                <#list singleBenchmarkResult.subSingleBenchmarkResultList as subSingleBenchmarkResult>
                                                                    <li class="dropdown-header"><strong>Run #${subSingleBenchmarkResult.getSubSingleBenchmarkIndex()}</strong></li>
                                                                    <li>${subSingleBenchmarkResult.timeMillisSpent?string.@msDuration}</li>
                                                                </#list>
                                                            </ul>
                                                        </div></td>
                                                    </#if>
                                                </#if>
                                            </#if>
                                        </#list>
                                    </tr>
                                </#list>
                                </table>
                            </div>
                            <div class="tab-pane" id="summary_timeSpentScalability">
                                <h3>Time spent scalability summary</h3>
                                <p>Useful for extrapolating the scalability of construction heuristics (presuming that no other solver phases are configured).</p>
                                <div class="benchmark-chart">
                                    <img src="summary/${benchmarkReport.timeSpentScalabilitySummaryChartFile.name}"/>
                                </div>
                            </div>
                            <div class="tab-pane" id="summary_bestScorePerTimeSpent">
                                <h3>Best score per time spent summary</h3>
                                <p>Useful for visualizing trade-off between the best score versus the time spent for construction heuristics (presuming that no other solver phases are configured).</p>
                                <@addScoreLevelChartList chartFileList=benchmarkReport.bestScorePerTimeSpentSummaryChartFileList idPrefix="summary_bestScorePerTimeSpent" />
                            </div>
                        </div>
                    </div>
                </section>
            </section>

            <section id="problemBenchmarkResult">
                <div class="page-header">
                    <h1>Problem benchmarks</h1>
                </div>
            <#list benchmarkReport.plannerBenchmarkResult.unifiedProblemBenchmarkResultList as problemBenchmarkResult>
                <section id="problemBenchmark_${problemBenchmarkResult.anchorId}">
                    <h2>${problemBenchmarkResult.name}</h2>
                    <#if problemBenchmarkResult.hasAnyFailure()>
                        <div class="alert alert-error">
                            <p>${problemBenchmarkResult.failureCount} benchmarks have failed!</p>
                        </div>
                    </#if>
                    <p>
                        Entity count: ${problemBenchmarkResult.entityCount!""}<br/>
                        Variable count: ${problemBenchmarkResult.variableCount!""}<br/>
                        Maximum value count: ${problemBenchmarkResult.maximumValueCount!""}<br/>
                        Problem scale: ${problemBenchmarkResult.problemScale!""}
                        <#if problemBenchmarkResult.inputSolutionLoadingTimeMillisSpent??>
                            <br/>Time spent to load the inputSolution:&nbsp;
                            <#if problemBenchmarkResult.inputSolutionLoadingTimeMillisSpent lt 1>
                                &lt; 1 ms
                            <#else>
                                ${problemBenchmarkResult.inputSolutionLoadingTimeMillisSpent?string.@msDuration}
                            </#if>
                        </#if>
                        <#if problemBenchmarkResult.averageUsedMemoryAfterInputSolution??>
                            <br/>Memory usage after loading the inputSolution (before creating the Solver): ${problemBenchmarkResult.averageUsedMemoryAfterInputSolution?string.number} bytes on average.
                        </#if>
                    </p>
                    <#if problemBenchmarkResult.hasAnySuccess() && problemBenchmarkResult.hasAnyStatistic()>
                        <#if problemBenchmarkResult.getMaximumSubSingleCount() gt 1 >
                             <p>Only the median sub single run of each solver is shown in the statistics below.</p>
                        </#if>
                        <div class="tabbable">
                            <ul class="nav nav-tabs">
                                <#assign firstRow = true>
                                <#list problemBenchmarkResult.problemStatisticList as problemStatistic>
                                    <li<#if firstRow> class="active"</#if>>
                                        <a href="#problemStatistic_${problemStatistic.anchorId}" data-toggle="tab">${problemStatistic.problemStatisticType.label?capitalize}</a>
                                    </li>
                                    <#assign firstRow = false>
                                </#list>
                                <#list problemBenchmarkResult.extractSingleStatisticTypeList() as singleStatisticType>
                                    <li<#if firstRow> class="active"</#if>>
                                        <a href="#singleStatistic_${problemBenchmarkResult.anchorId}_${singleStatisticType.anchorId}" data-toggle="tab">${singleStatisticType.label?capitalize}</a>
                                    </li>
                                    <#assign firstRow = false>
                                </#list>
                            </ul>
                            <div class="tab-content">
                                <#assign firstRow = true>
                                <#list problemBenchmarkResult.problemStatisticList as problemStatistic>
                                    <div class="tab-pane<#if firstRow> active</#if>" id="problemStatistic_${problemStatistic.anchorId}">
                                        <#list problemStatistic.warningList as warning>
                                            <div class="alert alert-error">
                                                <p>${warning}</p>
                                            </div>
                                        </#list>
                                        <#if problemStatistic.graphFileList?? && problemStatistic.graphFileList?size != 0>
                                            <#if problemStatistic.problemStatisticType.hasScoreLevels()>
                                                <div class="tabbable tabs-right">
                                                    <ul class="nav nav-tabs">
                                                        <#assign scoreLevelIndex = 0>
                                                        <#list problemStatistic.graphFileList as graphFile>
                                                            <li<#if scoreLevelIndex == benchmarkReport.defaultShownScoreLevelIndex> class="active"</#if>>
                                                                <a href="#problemStatistic_${problemStatistic.anchorId}_${scoreLevelIndex}" data-toggle="tab">${problemBenchmarkResult.findScoreLevelLabel(scoreLevelIndex)?capitalize}</a>
                                                            </li>
                                                            <#assign scoreLevelIndex = scoreLevelIndex + 1>
                                                        </#list>
                                                    </ul>
                                                    <div class="tab-content">
                                                        <#assign scoreLevelIndex = 0>
                                                        <#list problemStatistic.graphFileList as graphFile>
                                                            <div class="tab-pane<#if scoreLevelIndex == benchmarkReport.defaultShownScoreLevelIndex> active</#if>" id="problemStatistic_${problemStatistic.anchorId}_${scoreLevelIndex}">
                                                                <div class="benchmark-chart">
                                                                    <img src="${benchmarkReport.getRelativePathToBenchmarkReportDirectory(graphFile)}"/>
                                                                </div>
                                                            </div>
                                                            <#assign scoreLevelIndex = scoreLevelIndex + 1>
                                                        </#list>
                                                    </div>
                                                </div>
                                            <#else>
                                                <div class="benchmark-chart">
                                                    <img src="${benchmarkReport.getRelativePathToBenchmarkReportDirectory(problemStatistic.graphFile)}"/>
                                                </div>
                                            </#if>
                                        <#else>
                                            <p>Graph unavailable (statistic unavailable for this solver configuration or benchmark failed).</p>
                                        </#if>
                                        <#if !benchmarkReport.plannerBenchmarkResult.aggregation>
                                            <span>CSV files per solver:</span>
                                            <div class="btn-group download-btn-group">
                                            <#list problemStatistic.subSingleStatisticList as subSingleStatistic>
                                                <button class="btn btn-secondary btn-sm" onclick="window.location.href='${subSingleStatistic.relativeCsvFilePath}'">&#x2B07;</button>
                                            </#list>
                                            </div>
                                        </#if>
                                    </div>
                                    <#assign firstRow = false>
                                </#list>
                                <#list problemBenchmarkResult.extractSingleStatisticTypeList() as singleStatisticType>
                                    <div class="tab-pane<#if firstRow> active</#if>" id="singleStatistic_${problemBenchmarkResult.anchorId}_${singleStatisticType.anchorId}">
                                        <#list problemBenchmarkResult.extractPureSubSingleStatisticList(singleStatisticType) as pureSubSingleStatistic>
                                            <h3>${pureSubSingleStatistic.subSingleBenchmarkResult.singleBenchmarkResult.solverBenchmarkResult.name}</h3>
                                            <#if pureSubSingleStatistic.graphFileList?? && pureSubSingleStatistic.graphFileList?size != 0>
                                                <#if singleStatisticType.hasScoreLevels()>
                                                    <div class="tabbable tabs-right">
                                                        <ul class="nav nav-tabs">
                                                            <#assign scoreLevelIndex = 0>
                                                            <#list pureSubSingleStatistic.graphFileList as graphFile>
                                                                <li<#if scoreLevelIndex == benchmarkReport.defaultShownScoreLevelIndex> class="active"</#if>>
                                                                    <a href="#subSingleStatistic_${pureSubSingleStatistic.anchorId}_${scoreLevelIndex}" data-toggle="tab">${problemBenchmarkResult.findScoreLevelLabel(scoreLevelIndex)?capitalize}</a>
                                                                </li>
                                                                <#assign scoreLevelIndex = scoreLevelIndex + 1>
                                                            </#list>
                                                        </ul>
                                                        <div class="tab-content">
                                                            <#assign scoreLevelIndex = 0>
                                                            <#list pureSubSingleStatistic.graphFileList as graphFile>
                                                                <div class="tab-pane<#if scoreLevelIndex == benchmarkReport.defaultShownScoreLevelIndex> active</#if>" id="subSingleStatistic_${pureSubSingleStatistic.anchorId}_${scoreLevelIndex}">
                                                                    <div class="benchmark-chart">
                                                                        <img src="${benchmarkReport.getRelativePathToBenchmarkReportDirectory(graphFile)}"/>
                                                                    </div>
                                                                </div>
                                                                <#assign scoreLevelIndex = scoreLevelIndex + 1>
                                                            </#list>
                                                        </div>
                                                    </div>
                                                <#else>
                                                    <div class="benchmark-chart">
                                                        <img src="${benchmarkReport.getRelativePathToBenchmarkReportDirectory(pureSubSingleStatistic.graphFile)}"/>
                                                    </div>
                                                </#if>
                                            <#else>
                                                <p>Graph unavailable (statistic unavailable for this solver configuration or benchmark failed).</p>
                                            </#if>
                                            <#if !benchmarkReport.plannerBenchmarkResult.aggregation>
                                                <span>CSV file:</span>
                                                <div class="btn-group download-btn-group">
                                                    <button class="btn btn-secondary btn-sm" onclick="window.location.href='${pureSubSingleStatistic.relativeCsvFilePath}'">&#x2B07;</button>
                                                </div>
                                            </#if>
                                        </#list>
                                    </div>
                                    <#assign firstRow = false>
                                </#list>
                            </div>
                        </div>
                    </#if>
                    <#list problemBenchmarkResult.singleBenchmarkResultList as singleBenchmarkResult>
                        <section id="singleBenchmark_${singleBenchmarkResult.anchorId}">
                            <h3>${singleBenchmarkResult.name}</h3>
                            <#if singleBenchmarkResult.hasAnyFailure()>
                                <div class="alert alert-error">
                                    <p>${singleBenchmarkResult.failureCount} benchmarks have failed!</p>
                                </div>
                            <#else>
                                <#if singleBenchmarkResult.getSubSingleCount() gt 1 >
                                    <p>Only the median sub single run of each solver is shown in the statistics below.</p>
                                </#if>
                                <#if singleBenchmarkResult.getScoreExplanationSummary()??>
                                    <pre class="prettyprint">${singleBenchmarkResult.scoreExplanationSummary}</pre>
                                <#else>
                                    <p>Score summary not provided.</p>
                                </#if>
                            </#if>
                        </section>
                    </#list>
                </section>
            </#list>
            </section>

            <section id="solverBenchmarkResult">
                <div class="page-header">
                    <h1>Solver benchmarks</h1>
                </div>
            <#list benchmarkReport.plannerBenchmarkResult.solverBenchmarkResultList as solverBenchmarkResult>
                <section id="solverBenchmark_${solverBenchmarkResult.anchorId}">
                    <h2>${solverBenchmarkResult.name}&nbsp;<@addSolverBenchmarkBadges solverBenchmarkResult=solverBenchmarkResult/></h2>
                    <#if solverBenchmarkResult.hasAnyFailure()>
                        <div class="alert alert-error">
                            <p>${solverBenchmarkResult.failureCount} benchmarks have failed!</p>
                        </div>
                    </#if>
                    <button class="btn showSolverConfiguration" data-toggle="collapse" data-target="#solverBenchmark_${solverBenchmarkResult.anchorId}_config">
                        Show/hide Solver configuration
                    </button>
                    <div id="solverBenchmark_${solverBenchmarkResult.anchorId}_config" class="collapse in">
                        <pre class="prettyprint lang-xml">${solverBenchmarkResult.solverConfigAsString}</pre>
                    </div>
                </section>
            </#list>
            </section>

            <section id="benchmarkInformation">
                <div class="page-header">
                    <h1>Benchmark information</h1>
                </div>
                <table class="benchmark-table table table-striped">
                    <tr>
                        <th>Name</th>
                        <td>${benchmarkReport.plannerBenchmarkResult.name}</td>
                    </tr>
                    <tr>
                        <th>Aggregation</th>
                        <td>${benchmarkReport.plannerBenchmarkResult.aggregation?string}</td>
                    </tr>
                    <tr>
                        <th>Failure count</th>
                        <td>${benchmarkReport.plannerBenchmarkResult.failureCount}</td>
                    </tr>
                    <tr>
                        <th>Starting timestamp</th>
                        <td>${(benchmarkReport.plannerBenchmarkResult.startingTimestampAsMediumString)!"Differs"}</td>
                    </tr>
                    <tr>
                        <th>Warm up time spent</th>
                        <#if benchmarkReport.plannerBenchmarkResult.warmUpTimeMillisSpentLimit??>
                            <td>${benchmarkReport.plannerBenchmarkResult.warmUpTimeMillisSpentLimit?string.@msDuration}</td>
                        <#else>
                            <td>Differs</td>
                        </#if>
                    </tr>
                    <tr>
                        <th>Parallel benchmark count / available processors</th>
                        <#if benchmarkReport.plannerBenchmarkResult.parallelBenchmarkCount?? && benchmarkReport.plannerBenchmarkResult.availableProcessors??>
                            <td>${benchmarkReport.plannerBenchmarkResult.parallelBenchmarkCount} / ${benchmarkReport.plannerBenchmarkResult.availableProcessors}</td>
                        <#else>
                            <td>Differs</td>
                        </#if>
                    </tr>
                    <tr>
                        <th>Benchmark time spent</th>
                        <#if benchmarkReport.plannerBenchmarkResult.benchmarkTimeMillisSpent??>
                            <td>${benchmarkReport.plannerBenchmarkResult.benchmarkTimeMillisSpent?string.@msDuration}</td>[
                        <#else>
                            <td>Differs</td>
                        </#if>
                    </tr>
                    <tr>
                        <th>Environment mode</th>
                        <td>${benchmarkReport.plannerBenchmarkResult.environmentMode!"Differs"}</td>
                    </tr>
                    <tr>
                        <th>Logging level org.optaplanner.core</th>
                        <td>${benchmarkReport.plannerBenchmarkResult.loggingLevelOptaPlannerCore!"Differs"}</td>
                    </tr>
                    <tr>
                        <th>Logging level org.drools.core</th>
                        <td>${benchmarkReport.plannerBenchmarkResult.loggingLevelDroolsCore!"Differs"}</td>
                    </tr>
                    <tr>
                        <th>Solver ranking class</th>
                        <td>
                            <span data-toggle="tooltip" title="${benchmarkReport.solverRankingClassFullName!"Unknown"}">
                                ${benchmarkReport.solverRankingClassSimpleName!"Unknown"}
                            </span>
                        </td>
                    </tr>
                    <tr>
                        <th>VM max memory (as in -Xmx but lower)</th>
                        <#if (benchmarkReport.plannerBenchmarkResult.maxMemory?string.number)??>
                            <td>${benchmarkReport.plannerBenchmarkResult.maxMemory?string.number} bytes</td>
                        <#else>
                            <td>Differs</td>
                        </#if>
                    </tr>
                    <tr>
                        <th>OptaPlanner version</th>
                        <td>${benchmarkReport.plannerBenchmarkResult.optaPlannerVersion!"Differs"}</td>
                    </tr>
                    <tr>
                        <th>Java version</th>
                        <td>${benchmarkReport.plannerBenchmarkResult.javaVersion!"Differs"}</td>
                    </tr>
                    <tr>
                        <th>Java VM</th>
                        <td>${benchmarkReport.plannerBenchmarkResult.javaVM!"Differs"}</td>
                    </tr>
                    <tr>
                        <th>Operating system</th>
                        <td>${benchmarkReport.plannerBenchmarkResult.operatingSystem!"Differs"}</td>
                    </tr>
                    <tr>
                        <th>Report locale</th>
                        <td>${benchmarkReport.locale_object!"Unknown"}</td>
                    </tr>
                    <tr>
                        <th>Report timezone</th>
                        <td>${benchmarkReport.timezoneId!"Unknown"}</td>
                    </tr>
                </table>
            </section>
        </div>
    </div>
</div>

<script src="twitterbootstrap/js/jquery.js"></script>
<script src="twitterbootstrap/js/bootstrap.js"></script>
<script src="twitterbootstrap/js/prettify.js"></script>
<script>
$(function () {
    $('[data-toggle="tooltip"]').tooltip()
})
</script>
</body>
</html>
