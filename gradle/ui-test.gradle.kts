import java.time.Duration

// Configure UI testing with robot-server plugin
// See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-faq.html#how-to-configure-ui-tests
val runIdeForUiTests by intellijPlatformTesting.runIde.registering {
    task {
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf(
                "-Drobot-server.port=8082",
                "-Dide.mac.message.dialogs.as.sheets=false",
                "-Djb.privacy.policy.text=<!--999.999-->",
                "-Djb.consents.confirmation.enabled=false",
            )
        }
    }

    plugins { robotServerPlugin() }
}

tasks.register<Test>("testUi") {
    description = "Runs UI tests with a running IDE instance"
    group = "verification"

    // Use JUnit Platform for JUnit 5 tests
    useJUnitPlatform()

    // Include only UI tests
    include("**/ui/**")

    // Use the same JVM configuration as regular tests
    jvmArgs(
        "-Djava.awt.headless=true",
        "-Didea.force.use.core.classloader=true",
        "-Didea.use.core.classloader.for.plugin.path=true"
    )

    maxHeapSize = "2048m" // UI tests may need more memory
    timeout.set(Duration.ofMinutes(20)) // UI tests take longer

    shouldRunAfter(tasks.named<Test>("test"))
}

