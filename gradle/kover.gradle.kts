// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
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
