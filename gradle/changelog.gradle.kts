// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
// Use explicit extension configuration to avoid unresolved DSL in applied scripts
import org.jetbrains.changelog.ChangelogPluginExtension

val repoUrl = providers.gradleProperty("pluginRepositoryUrl")

extensions.configure<ChangelogPluginExtension> {
    // Equivalent to `groups.empty()` in the DSL
    groups.set(emptyList())
    // Use Gradle Property API
    repositoryUrl.set(repoUrl)
}
