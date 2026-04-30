import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.process.CommandLineArgumentProvider
import java.time.Duration

val commonTestJvmArgs = listOf(
    "-Djava.awt.headless=true",
    "-Didea.force.use.core.classloader=true",
    "-Didea.use.core.classloader.for.plugin.path=true"
)

val commonTestModuleOpens = listOf(
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED"
)

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.kotlinx.serialization) // Kotlin Serialization Plugin
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

// Configure project's dependencies
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

// Changelog configuration (inlined to avoid DSL resolution issues in applied scripts)
changelog {
    groups.empty()
    repositoryUrl.set(providers.gradleProperty("pluginRepositoryUrl"))
}

// Kover configuration
kover {
    reports {
        total {
            xml { onCheck = true }
            // Exclude UI/adapters from coverage report
            filters {
                excludes {
                    // Entire UI/adapter packages
                    packages(
                        "org.zhongmiao.interceptwave.ui",
                        "org.zhongmiao.interceptwave.listeners",
                        "org.zhongmiao.interceptwave.startup",
                        "org.zhongmiao.interceptwave.events"
                    )
                    // UI-facing service (wraps IDE Console UI)
                    classes(
                        "org.zhongmiao.interceptwave.services.ConsoleService"
                    )
                }
            }
        }
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    constraints {
        testImplementation("org.assertj:assertj-core:3.27.7") {
            because("3.27.7 fixes CVE-2026-24400 in older assertj-core versions")
        }
    }

    implementation(libs.kotlinx.serialization.json)
    implementation("org.java-websocket:Java-WebSocket:1.6.0")

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)

    // JUnit 5 (Jupiter) for UI tests
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")

    // JUnit Vintage Engine to run JUnit 4 tests on JUnit Platform
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:6.0.3")

    // JUnit Platform Launcher (required for test execution)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")

    // UI Testing with Remote Robot
    testImplementation("com.intellij.remoterobot:remote-robot:0.11.23")
    testImplementation("com.intellij.remoterobot:remote-fixtures:0.11.23")
    testImplementation("com.squareup.okhttp3:okhttp:5.3.2") // Required by remote-robot

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        // Module Dependencies. Uses `platformBundledModules` property from the gradle.properties file for bundled IntelliJ Platform modules.
        bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',') })

        testFramework(TestFrameworkType.Platform)
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Ensure tests run on JUnit Platform (Jupiter + Vintage) and support tag filtering
tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        val include = System.getProperty("includeTags")?.takeIf { it.isNotBlank() }
        val exclude = System.getProperty("excludeTags")?.takeIf { it.isNotBlank() }
        include?.let { vals ->
            val arr = vals.split(',').map(String::trim).filter(String::isNotEmpty).toTypedArray()
            if (arr.isNotEmpty()) includeTags(*arr)
        }
        exclude?.let { vals ->
            val arr = vals.split(',').map(String::trim).filter(String::isNotEmpty).toTypedArray()
            if (arr.isNotEmpty()) excludeTags(*arr)
        }
        // Note: JUnit4 Categories (Vintage) are exposed as tags via their fully qualified names.
    }

    // Remote Robot + Gson on JDK 21 needs these packages opened for reflective
    // access when deserializing server-side errors and fixture payloads.
    jvmArgs(commonTestJvmArgs + commonTestModuleOpens)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Configure UI testing with robot-server plugin
// See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-faq.html#how-to-configure-ui-tests
val runIdeForUiTests by intellijPlatformTesting.runIde.registering {
    val uiProjectDir = layout.buildDirectory.dir("ui-test-project/intercept-wave-ui-project")

    task {
        dependsOn(prepareUiTestProject)
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf(
                "-Drobot-server.port=8082",
                "-Dide.mac.message.dialogs.as.sheets=false",
                "-Djb.privacy.policy.text=<!--999.999-->",
                "-Djb.consents.confirmation.enabled=false",
                "-Xshare:off", // 关闭 CDS
            )
        }
        args(uiProjectDir.get().asFile.absolutePath)
    }

    plugins { robotServerPlugin() }
}

val prepareUiTestProject = tasks.register("prepareUiTestProject") {
    val uiProjectDir = layout.buildDirectory.dir("ui-test-project/intercept-wave-ui-project")
    val uiProjectTemplateDir = layout.projectDirectory.dir("src/test/resources/ui-test-project-template")
    outputs.dir(uiProjectDir)

    doLast {
        val dir = uiProjectDir.get().asFile
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        dir.mkdirs()

        val templateDir = uiProjectTemplateDir.asFile
        if (!templateDir.exists()) {
            throw GradleException("Missing UI test project template: ${templateDir.absolutePath}")
        }

        templateDir.walkTopDown().forEach { source ->
            val relativePath = source.relativeTo(templateDir).path
            val target = dir.resolve(relativePath)
            if (source.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile.mkdirs()
                source.copyTo(target, overwrite = true)
            }
        }
    }
}

val prepareUiTestWelcomeState = tasks.register("prepareUiTestWelcomeState") {
    val platformType = providers.gradleProperty("platformType")
    val platformVersion = providers.gradleProperty("platformVersion")
    val uiProjectDir = layout.buildDirectory.dir("ui-test-project/intercept-wave-ui-project")
    val projectName = "intercept-wave-ui-project"
    val userHome = System.getProperty("user.home")
    val optionsDir = layout.buildDirectory.dir(
        platformType.zip(platformVersion) { type, version ->
            "idea-sandbox/$type-$version/config/options"
        }
    )

    outputs.dir(optionsDir)
    dependsOn(prepareUiTestProject)

    doLast {
        val projectPath = uiProjectDir.get().asFile.absolutePath
        val projectPathForIde = projectPath.replace(userHome, "${'$'}USER_HOME${'$'}")
        val type = platformType.get()
        val version = platformVersion.get()

        val optionDirs = listOf(
            optionsDir.get().asFile,
            layout.projectDirectory
                .dir(".intellijPlatform/sandbox/intercept-wave/$type-$version/config_runIdeForUiTests/options")
                .asFile
        ).distinctBy { it.absolutePath }

        fun writeUiState(dir: File) {
            dir.mkdirs()
            dir.resolve("trusted-paths.xml").writeText(
            """
            <application>
              <component name="Trusted.Paths">
                <option name="TRUSTED_PROJECT_PATHS">
                  <map>
                    <entry key="$projectPathForIde" value="true" />
                  </map>
                </option>
              </component>
            </application>
            """.trimIndent()
            )
            dir.resolve("trace_license_storage.xml").writeText(
            """
            <application>
              <component name="TraceLicenseStorage">
                <option name="lastUsedLicenseData" value="" />
              </component>
            </application>
            """.trimIndent()
            )
            dir.resolve("AIOnboardingPromoWindowAdvisor.xml").writeText(
            """
            <application>
              <component name="AIOnboardingPromoWindowAdvisor">
                <option name="attempts" value="0" />
                <option name="shouldShowNextTime" value="NO" />
              </component>
            </application>
            """.trimIndent()
            )
            dir.resolve("recentProjects.xml").writeText(
            """
            <application>
              <component name="RecentProjectsManager">
                <option name="additionalInfo">
                  <map>
                    <entry key="$projectPathForIde">
                      <value>
                        <RecentProjectMetaInfo frameTitle="$projectName" projectWorkspaceId="ui-tests-$projectName">
                          <option name="activationTimestamp" value="1760000000000" />
                          <option name="binFolder" value="${'$'}APPLICATION_HOME_DIR${'$'}/bin" />
                          <option name="build" value="${platformType.get()}-${platformVersion.get()}" />
                          <option name="productionCode" value="${platformType.get()}" />
                          <option name="projectOpenTimestamp" value="1760000000000" />
                        </RecentProjectMetaInfo>
                      </value>
                    </entry>
                  </map>
                </option>
                <option name="lastOpenedProject" value="$projectPathForIde" />
              </component>
            </application>
            """.trimIndent()
            )
        }

        optionDirs.forEach(::writeUiState)
    }
}


tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }

    // Tests configuration
    test {
        // Use JUnit Platform for both JUnit 4 and JUnit 5 tests
        useJUnitPlatform()

        // Set test timeout to prevent hanging
        timeout.set(Duration.ofMinutes(10))

        // Configure JVM args for headless testing
        // Increase heap size for tests
        maxHeapSize = "1024m"

        // IntelliJ Platform tests must run in a single forked JVM to avoid
        // VFS/index initialization failures and leaking thread checks.
        // Running with multiple forks can cause "Index data initialization failed".
        maxParallelForks = 1

        // Mark task as not compatible with Gradle Configuration Cache to avoid
        // capturing IntelliJ test initialization state that breaks VFS startup.
        notCompatibleWithConfigurationCache("IntelliJ Platform tests require fresh IDE boot per run")

        // Exclude Remote Robot UI tests from the regular test task.
        exclude("**/*UiTest.class")

        // In CI environment, exclude Platform tests that require IDE instance
        if (System.getenv("CI") == "true") {
            exclude("**/MockServerServiceTest.class", "**/ConfigServiceTest.class", "**/ProjectCloseListenerTest.class")
        }
    }

    // Separate UI test task
    register<Test>("testUi") {
        description = "Runs UI tests with a running IDE instance"
        group = "ui verification"
        val testSourceSet = sourceSets.named("test").get()

        // Use JUnit Platform for JUnit 5 tests
        useJUnitPlatform()

        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        systemProperty("intercept.wave.ui.projectDir", layout.buildDirectory.dir("ui-test-project/intercept-wave-ui-project").get().asFile.absolutePath)
        systemProperty("intercept.wave.ui.projectName", "intercept-wave-ui-project")
        systemProperty("intercept.wave.ui.fixtureResource", "/fixtures/diverse-config.json")

        // Include only Remote Robot UI tests that require a separately launched IDE.
        include("**/*UiTest.class")

        // Use the same JVM configuration as regular tests
        jvmArgs(
            *commonTestJvmArgs.toTypedArray(),
            *commonTestModuleOpens.toTypedArray()
        )

        maxHeapSize = "2048m" // UI tests may need more memory
        timeout.set(Duration.ofMinutes(20)) // UI tests take longer
        notCompatibleWithConfigurationCache("Remote Robot UI tests require a live IDE and runtime-only gating")

        dependsOn(prepareUiTestProject)
        shouldRunAfter(named<Test>("test"))
        onlyIf {
            System.getProperty("runUiTests") == "true" ||
                System.getenv("RUN_UI_TESTS") == "true"
        }
    }

    named("runIdeForUiTests") {
        dependsOn(prepareUiTestWelcomeState)
    }
}
