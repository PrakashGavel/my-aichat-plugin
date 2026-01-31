package com.github.prakashgavel.myaichatplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import javax.swing.JPanel
import com.intellij.openapi.diagnostic.thisLogger

class MyUiPanelFactory {
    init {
        thisLogger().info("Initializing MyAichat UI Panel (UI-only, Gemini integration wired).")
    }

    // Build content for ToolWindow
    fun createContent(project: Project, toolWindow: ToolWindow): JPanel {
        val panel = JBPanel<Nothing?>().apply {
            border = JBUI.Borders.empty()
            layout = java.awt.BorderLayout()
            val ui = UIContainer(project)
            add(ui, java.awt.BorderLayout.CENTER)
        }
        return panel
    }
}