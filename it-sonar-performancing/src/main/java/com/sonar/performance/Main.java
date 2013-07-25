/*
 * Copyright (C) 2013 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonar.performance;

import com.sonar.performance.tasks.*;

import java.util.Arrays;
import java.util.List;

/**
 * Prerequisites :
 * <ol>
 *   <li>Clone the Git repository it-sources and set the env variable $SONAR_IT_SOURCES</li>
 *   <li>Orchestrator settings (see http://confluence.internal.sonarsource.com/display/DEV/Integration+Test+Settings)</li>
 *   <li>If db migrations :
 *     <ul>
 *       <li>start database in a version prior or equal to the first version declared in {@link TestPlan#setVersionsOnExistingDb(String...)}</li>
 *       <li>check that the sonar user "admin" with password "admin" exists</li>
 *       <li>check that the quality profile "Sonar way with Findbugs" exists</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * CSV report is generated in target/
 * <p/>
 * Known regressions are listed in http://confluence.internal.sonarsource.com/display/DEV/Performance+Tests
 * <p/>
 * How to execute this test plan outside IDE : <code>mvn clean install exec:java -Dexec.mainClass=com.sonar.performance.Main</code>
 */
public class Main {

  public static void main(String[] args) throws Exception {
    new TestPlan()
      .setVersionsOnExistingDb("3.6.2", "3.7-SNAPSHOT")
      .setVersionsOnFreshDb("3.5", "3.6.2", "3.7-SNAPSHOT")
      .setTasks(tasks())
      .execute();
  }

  private static List<Task> tasks() {
    return Arrays.asList(
      new StartServer("Start Server"),
      new RestartServer("Start server - second time"),

      // Load cache of maven artifacts and build bytecode for Findbugs
      new InitializeBuildEnvironment(),

      // Fix limitation of Orchestrator, which does not support whitespaces in the profile "Sonar way with Findbugs"
      new RenameFindbugsProfile(),

      // Different scans
      new MavenScanStruts("Struts Maven Scan - no unit tests",
        "sonar.dynamicAnalysis", "false"
      ),
      new MavenScanStruts("Struts Dry Maven Scan - no unit tests",
        "sonar.dynamicAnalysis", "false", "sonar.dryRun", "true"
      ),
      new MavenScanStruts("Struts Maven Scan - no unit tests - findbugs",
        "sonar.dynamicAnalysis", "false",
        "sonar.profile", "findbugs-profile"
      ),
      new MavenScanStruts("Struts Maven scan - no unit tests - cross-project duplications",
        "sonar.dynamicAnalysis", "false",
        "sonar.cpd.cross_project", "true"
      ),
      new MavenScanStruts("Struts Maven Scan - unit tests",
        "sonar.dynamicAnalysis", "true"
      ),

      // Global pages
      new RequestUrl("Web - Homepage", "/"),
      new RequestUrl("Web - Quality Profiles", "/profiles"),
      new RequestUrl("Web - All Issues", "/issues/search"),
      new RequestUrl("Web - All Projects", "/all_projects?qualifier=TRK"),
      new RequestUrl("Web - Measures Filter", "/measures"),
      new RequestUrl("Web - Project Measures Filter", "/measures/search?qualifiers[]=TRK"),
      new RequestUrl("Web - File Measures Filter", "/measures/search?qualifiers[]=FIL"),

      // Project pages
      new RequestUrl("Web - Struts Dashboard", "/dashboard/index/org.apache.struts:struts-parent"),
      new RequestUrl("Web - Struts Issues", "/issues/search?componentRoots=org.apache.struts:struts-parent"),
      new RequestUrl("Web - Struts Violations Drilldown", "/drilldown/violations/org.apache.struts:struts-parent"),
      new RequestUrl("Web - Struts Issues Drilldown", "/drilldown/issues/org.apache.struts:struts-parent"),
      new RequestUrl("Web - Struts Measure Drilldown", "/drilldown/measures/org.apache.struts:struts-parent?metric=ncloc"),
      new RequestUrl("Web - Struts Cloud", "/cloud/index/org.apache.struts:struts-parent"),
      new RequestUrl("Web - Struts Hotspots", "/dashboard/index/org.apache.struts:struts-parent?name=Hotspots"),

      // Static pages
      new RequestUrl("Web - sonar.css", "/stylesheets/sonar.css"),
      new RequestUrl("Web - sonar.js", "/javascripts/sonar.js"),

      new StopServer("Stop Server")
    );
  }
}
