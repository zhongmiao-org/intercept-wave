import java.time.Duration

tasks {
    test {
        // Use JUnit Platform for both JUnit 4 and JUnit 5 tests
        useJUnitPlatform()

        // Set test timeout to prevent hanging
        timeout.set(Duration.ofMinutes(10))

        // Configure JVM args for headless testing
        jvmArgs(
            "-Djava.awt.headless=true",
            "-Didea.force.use.core.classloader=true",
            "-Didea.use.core.classloader.for.plugin.path=true"
        )

        // Increase heap size for tests
        maxHeapSize = "1024m"

        // IntelliJ Platform tests must run in a single forked JVM to avoid
        // VFS/index initialization failures and leaking thread checks.
        // Running with multiple forks can cause "Index data initialization failed".
        maxParallelForks = 1

        // Mark task as not compatible with Gradle Configuration Cache to avoid
        // capturing IntelliJ test initialization state that breaks VFS startup.
        notCompatibleWithConfigurationCache("IntelliJ Platform tests require fresh IDE boot per run")

        // Exclude UI tests from regular test task
        exclude("**/ui/**")

        // In CI environment, exclude Platform tests that require IDE instance
        if (System.getenv("CI") == "true") {
            exclude("**/MockServerServiceTest.class", "**/ConfigServiceTest.class", "**/ProjectCloseListenerTest.class")
        }
    }
}

