package org.zhongmiao.interceptwave.util

import com.intellij.openapi.application.ApplicationManager

object Env {
    @JvmStatic
    fun isUnitTestMode(): Boolean = try {
        ApplicationManager.getApplication()?.isUnitTestMode == true
    } catch (_: Throwable) {
        false
    }
}

