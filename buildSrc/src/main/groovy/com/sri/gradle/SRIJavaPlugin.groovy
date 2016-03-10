/*
 * Copyright 2016 SRI International
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sri.gradle

import java.text.SimpleDateFormat

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip

class SRIJavaPlugin implements Plugin<Project> {
    static final String EXTENSION_NAME = "SRIJava"
    static Closure manifestInfo

    void apply(Project project) {
        def rootProject = project.rootProject

        // Depends on the Java and Jacoco plugins.
        project.apply(plugin:JavaPlugin)
        project.apply(plugin:JacocoPlugin)

        // Configure Sonar.
        project.sonarRunner.sonarProperties {
            property "sonar.projectKey", "SAVE:${project.path}"
        }

        // Set up dependencies for Jacoco.
        project.jacocoTestReport.dependsOn(project.test)
        project.check.dependsOn(project.jacocoTestReport)

        // Set jar manifest info.
        def dateFormat = new SimpleDateFormat("yyyy-MM-dd")
        manifestInfo = {
            attributes 'Built-By': System.properties['user.name'],
            'Build-Date': dateFormat.format(new Date()),
            'Specification-Title': project.name,
            'Specification-Version': rootProject.release,
            'Specification-Build': rootProject.build,
            'Specification-Vendor': 'SRI International',
            'Implementation-Version': rootProject.revision
        }
        project.jar.manifest manifestInfo

        // Java 1.7
        project.sourceCompatibility = 1.7

        // Build a source jar.
        project.task([type: Jar], 'sourceJar') {
            from project.sourceSets.main.allSource
            classifier = 'sources'
            manifest manifestInfo
        }
        project.configurations.create('source')
        project.artifacts.add('source', project.sourceJar)

        // Make test jars available to other projects.
        project.task([type: Jar, dependsOn: project.testClasses], "testJar") {
            appendix = "test"
            from project.sourceSets.test.output
        }
        project.artifacts.add('testCompile', project.testJar)

        // All tests depend on TestNG.
        project.dependencies.add('testCompile', 'org.testng:testng:6.8')
        project.test.useTestNG()

        // Use the nicer report format for TestNG.
        project.test.reports.html.enabled = true

        // Provide more granular stdout and stderr.
        project.test.reports.junitXml.outputPerTestCase = true

        // New JVM for each test class. This may not be necessary.
        project.test.forkEvery = 1

        // Set test properties.
        project.test.options {
            def exclStr = project.rootProject.properties['test.excludes']
            excludeGroups exclStr.tokenize() as String[]
        }

        // Eclipse's TestNG plugin will create this dumb directory, so
        // we should have clean remove it.
        project.clean.delete(project.file("test-output"))

        // Use FindBugs for static code analysis.
        project.apply(plugin: 'findbugs')
        project.tasks.withType(FindBugs) {
            ignoreFailures true
        }

        // These two plugins will build IDE config files based on the
        // corresponding gradle config.
        project.apply(plugin: 'eclipse')
        project.apply(plugin: 'idea')
        project.eclipse.classpath.defaultOutputDir =
            project.file('.eclipse.bin')

        // If autobuild is running, don't fail the build at the first
        // sign of a broken test. Jenkins will parse our test result
        // files.
        project.gradle.taskGraph.whenReady { tg ->
            if (tg.hasTask(':autobuild')) {
                project.test.ignoreFailures = true
            }
        }
    }
}
