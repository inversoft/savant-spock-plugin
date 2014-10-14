/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.plugin.spock

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.savantbuild.dep.domain.ArtifactID
import org.savantbuild.domain.Project
import org.savantbuild.lang.Classpath
import org.savantbuild.output.Output
import org.savantbuild.plugin.dep.DependencyPlugin
import org.savantbuild.plugin.groovy.BaseGroovyPlugin
import org.savantbuild.runtime.RuntimeConfiguration

/**
 * Spock plugin.
 *
 * @author Daniel DeGroff
 */
class SpockPlugin extends BaseGroovyPlugin {

  final String GROOVY_ERROR_MESSAGE = "You must create the file [~/.savant/plugins/org.savantbuild.plugin.groovy.properties] " +
      "that contains the system configuration for the Groovy plugin. This file should include the location of the GDK " +
      "(groovy and groovyc) by version. These properties look like this:\n\n" +
      "  2.1=/Library/Groovy/Versions/2.1/Home\n" +
      "  2.2=/Library/Groovy/Versions/2.2/Home\n"
  final String JAVA_ERROR_MESSAGE = "You must create the file [~/.savant/plugins/org.savantbuild.plugin.java.properties] " +
      "that contains the system configuration for the Java system. This file should include the location of the JDK " +
      "(java and javac) by version. These properties look like this:\n\n" +
      "  1.6=/Library/Java/JavaVirtualMachines/1.6.0_65-b14-462.jdk/Contents/Home\n" +
      "  1.7=/Library/Java/JavaVirtualMachines/jdk1.7.0_10.jdk/Contents/Home\n" +
      "  1.8=/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home\n"

  DependencyPlugin dependencyPlugin

  Path groovyPath

  String javaHome

  Properties javaProperties

  Properties properties

  SpockSettings settings = new SpockSettings()

  SpockPlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
    super(project, runtimeConfiguration, output)
    properties = loadConfiguration(new ArtifactID("org.savantbuild.plugin", "groovy", "groovy", "jar"), GROOVY_ERROR_MESSAGE)
    javaProperties = loadConfiguration(new ArtifactID("org.savantbuild.plugin", "java", "java", "jar"), JAVA_ERROR_MESSAGE)
    dependencyPlugin = new DependencyPlugin(project, runtimeConfiguration, output)
  }

  private void initialize() {
    if (!settings.groovyVersion) {
      fail("You must configure the Groovy version to use with the settings object. It will look something like this:\n\n" +
          "  groovy.settings.groovyVersion=\"2.1\"")
    }

    String groovyHome = properties.getProperty(settings.groovyVersion)
    if (!groovyHome) {
      fail("No GDK is configured for version [${settings.groovyVersion}].\n\n" + GROOVY_ERROR_MESSAGE)
    }

    groovyPath = Paths.get(groovyHome, "bin/groovy")
    if (!Files.isRegularFile(groovyPath)) {
      fail("The groovy compiler [${groovyPath.toAbsolutePath()}] does not exist.")
    }
    if (!Files.isExecutable(groovyPath)) {
      fail("The groovy compiler [${groovyPath.toAbsolutePath()}] is not executable.")
    }

    if (!settings.javaVersion) {
      fail("You must configure the Java version to use with the settings object. It will look something like this:\n\n" +
          "  groovy.settings.javaVersion=\"1.7\"")
    }

    javaHome = javaProperties.getProperty(settings.javaVersion)
    if (!javaHome) {
      fail("No JDK is configured for version [${settings.javaVersion}].\n\n" + JAVA_ERROR_MESSAGE)
    }
  }

  /**
   * Runs the Spock tests.
   */
  void test() {

    output.info("[Spock] Running:\n")
    if (runtimeConfiguration.switches.booleanSwitches.contains("skipTests")) {
      output.info("Skipping tests")
      return
    }

    initialize()

    Classpath classpath = dependencyPlugin.classpath {
      settings.dependencies.each { dependency -> dependencies(dependency) }
      project.publications.group("main").each { publication -> path(location: publication.file) }
      project.publications.group("test").each { publication -> path(location: publication.file) }
    }

    List singleTests = runtimeConfiguration.switches.valueSwitches.get("test")
    SpockSuite suite = new SpockSuite(output: output, singleTests: singleTests, sourceTestDirectory: settings.sourceTestDirectory)
    suite.initialize()

    SpockRunner runner = new SpockRunner(groovyPath: groovyPath, output: output, project: project, settings: settings)
    def result = runner.doRun(classpath, suite)
    if (result != 0) {
      fail("Build failed. SpockRunner return code [%d]", result)
    }

  }

}