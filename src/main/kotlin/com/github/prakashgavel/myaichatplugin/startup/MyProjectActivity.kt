package com.github.prakashgavel.myaichatplugin.startup

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        thisLogger().debug("my-aichat-plugin started for project: ${project.name}")
    }
}
