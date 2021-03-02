import org.jetbrains.intellij.tasks.RunPluginVerifierTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.EnumSet
import com.google.protobuf.gradle.*
import groovy.lang.GroovyObject
import org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig

plugins {
    kotlin("jvm") version "1.4.30"
    id("org.jetbrains.intellij") version "0.6.5"
    id("org.sonarqube") version "3.0"
    java
    jacoco
    id("com.github.hierynomus.license") version "0.15.0"
    id("com.jfrog.artifactory") version "4.11.0"
    id("com.google.protobuf") version "0.8.10"
    id("net.linguica.maven-settings") version "0.5"
    idea
}

group = "org.sonarsource.sonarlint.intellij"
description = "SonarLint for IntelliJ CLion"

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        apiVersion = "1.3"
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

intellij {
    version = "CL-2020.1.3"
    pluginName = "sonarlint-intellij"
    updateSinceUntilBuild = false
}

tasks.runPluginVerifier {
    setIdeVersions(listOf("CL-2020.1.3", intellij.version))
    setFailureLevel(
        EnumSet.complementOf(
            EnumSet.of(
                // these are the only issues we tolerate
                RunPluginVerifierTask.FailureLevel.DEPRECATED_API_USAGES,
                RunPluginVerifierTask.FailureLevel.EXPERIMENTAL_API_USAGES,
                RunPluginVerifierTask.FailureLevel.NOT_DYNAMIC,
                RunPluginVerifierTask.FailureLevel.OVERRIDE_ONLY_API_USAGES
            )
        )
    )
}

protobuf {
    // Configure the protoc executable
    protoc {
        // Download from repositories. Must be the same as the one used in sonarlint-core
        artifact = "com.google.protobuf:protoc:3.13.0"
    }
}

tasks.test {
    useJUnit()
    systemProperty("sonarlint.telemetry.disabled", "true")
}

tasks.runIde {
    systemProperty("sonarlint.telemetry.disabled", "true")
}

// enable loading of credentials from Maven settings.xml:
apply(plugin="net.linguica.maven-settings")

repositories {
    jcenter()
    mavenLocal()
    maven("https://repox.jfrog.io/repox/sonarsource") {
        content { excludeGroup("typescript") }
        if (System.getenv("ARTIFACTORY_PRIVATE_USERNAME") != null) {
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                username = System.getenv("ARTIFACTORY_PRIVATE_USERNAME")
                password = System.getenv("ARTIFACTORY_PRIVATE_PASSWORD")
            }
        }
    }
    ivy("https://repox.jfrog.io/repox/api/npm/npm") {
        patternLayout {
            artifact("[organization]/-/[module]-[revision].[ext]")
            metadataSources { artifact() }
        }
        content { includeGroup("typescript") }
    }
}

configurations {
    create("sqplugins") { isTransitive = false }
    create("typescript") { isCanBeConsumed = false }
}

val sonarlintCoreVersion: String by project
val typescriptVersion: String by project
val jettyVersion: String by project

dependencies {
    // Don't change to implementation until https://github.com/JetBrains/gradle-intellij-plugin/issues/239 is fixed
    compile("org.sonarsource.sonarlint.core:sonarlint-core:$sonarlintCoreVersion")
    compile("commons-lang:commons-lang:2.6")
    compileOnly("com.google.code.findbugs:jsr305:2.0.2")
    compile ("org.apache.httpcomponents.client5:httpclient5:5.0.3") {
        exclude(module = "slf4j-api")
    }
    testImplementation("junit:junit:4.12")
    testImplementation("org.assertj:assertj-core:3.16.1")
    testImplementation("org.mockito:mockito-core:2.19.0")
    testImplementation("org.eclipse.jetty:jetty-server:$jettyVersion")
    testImplementation("org.eclipse.jetty:jetty-servlet:$jettyVersion")
    testImplementation("org.eclipse.jetty:jetty-proxy:$jettyVersion")
    "sqplugins"("com.sonarsource.cpp:sonar-cfamily-plugin:6.17.0.27551@jar")
    //    files('/Users/mpaladin/projects/sonar-cpp/sonar-cfamily-plugin/build/libs/sonar-cfamily-plugin-6.10-SNAPSHOT-all.jar'),
    "typescript"("typescript:typescript:$typescriptVersion@tgz")
}

tasks.prepareSandbox {
    doLast {
        val tsBundlePath = project.configurations.get("typescript").iterator().next()
        copy {
            from(tarTree(tsBundlePath))
            exclude(
                "**/loc/**",
                "**/lib/*/diagnosticMessages.generated.json"
            )
            into(file("$destinationDir/$pluginName"))
        }
        file("$destinationDir/$pluginName/package").renameTo(file("$destinationDir/$pluginName/typescript"))
        copy {
            from(project.configurations.get("sqplugins"))
            into(file("$destinationDir/$pluginName/plugins"))
        }
    }
}

sonarqube {
    properties {
        property("sonar.projectName", "SonarLint for IntelliJ IDEA")
    }
}

license {
    mapping(
        mapOf(
            "java" to "SLASHSTAR_STYLE",
            "kt" to "SLASHSTAR_STYLE"
        )
    )
    strictCheck = true
}

tasks.jacocoTestReport {
    classDirectories.setFrom(files("build/classes/java/main-instrumented"))
    reports {
        xml.setEnabled(true)
    }
}

artifactory {
    clientConfig.info.setBuildName("sonarlint-intellij")
    clientConfig.info.setBuildNumber(System.getenv("BUILD_BUILDID"))
    clientConfig.setIncludeEnvVars(true)
    clientConfig.setEnvVarsExcludePatterns("*password*,*PASSWORD*,*secret*,*MAVEN_CMD_LINE_ARGS*,sun.java.command,*token*,*TOKEN*,*LOGIN*,*login*")
    clientConfig.info.addEnvironmentProperty(
        "ARTIFACTS_TO_DOWNLOAD",
        "org.sonarsource.sonarlint.intellij:sonarlint-intellij:zip"
    )
    setContextUrl(System.getenv("ARTIFACTORY_URL"))
    publish(delegateClosureOf<PublisherConfig> {
        repository(delegateClosureOf<GroovyObject> {
            setProperty("repoKey", System.getenv("ARTIFACTORY_DEPLOY_REPO"))
            setProperty("username", System.getenv("ARTIFACTORY_DEPLOY_USERNAME"))
            setProperty("password", System.getenv("ARTIFACTORY_DEPLOY_PASSWORD"))
        })
        defaults(delegateClosureOf<GroovyObject> {
            setProperty(
                "properties", mapOf(
                    "vcs.revision" to System.getenv("BUILD_SOURCEVERSION"),
                    "vcs.branch" to (System.getenv("SYSTEM_PULLREQUEST_TARGETBRANCH")
                        ?: System.getenv("BUILD_SOURCEBRANCHNAME")),
                    "build.name" to "sonarlint-intellij",
                    "build.number" to System.getenv("BUILD_BUILDID")
                )
            )
            invokeMethod("publishConfigs", "archives")
            setProperty("publishPom", true) // Publish generated POM files to Artifactory (true by default)
            setProperty("publishIvy", false) // Publish generated Ivy descriptor files to Artifactory (true by default)
        })
    })
}


