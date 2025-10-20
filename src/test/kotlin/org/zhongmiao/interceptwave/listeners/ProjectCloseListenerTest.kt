package org.zhongmiao.interceptwave.listeners

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for ProjectCloseListener
 */
class ProjectCloseListenerTest : BasePlatformTestCase() {

    private lateinit var listener: ProjectCloseListener

    override fun setUp() {
        super.setUp()
        listener = ProjectCloseListener()
    }

    fun `test listener can be instantiated`() {
        val newListener = ProjectCloseListener()
        assertNotNull(newListener)
    }

    fun `test projectClosing with no running servers`() {
        // Should not throw exception when there are no running servers
        listener.projectClosing(project)

        // Verify method completed without error
        assertTrue(true)
    }

    fun `test projectClosing is safe to call multiple times`() {
        // Call multiple times - should not throw
        listener.projectClosing(project)
        listener.projectClosing(project)
        listener.projectClosing(project)

        // Verify method completed without error
        assertTrue(true)
    }
}