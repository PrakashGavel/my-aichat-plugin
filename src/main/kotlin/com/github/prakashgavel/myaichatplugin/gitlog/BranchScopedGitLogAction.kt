package com.github.prakashgavel.myaichatplugin.gitlog

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import git4idea.repo.GitRepositoryManager

class BranchScopedGitLogAction : AnAction("Branch Scoped Git Log") {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val repoManager = GitRepositoryManager.getInstance(project)
        val repository = repoManager.repositories.firstOrNull()
        if (repository == null) {
            Messages.showWarningDialog(project, "No Git repository found in this project.", TITLE)
            return
        }
        val currentBranch = repository.currentBranch?.name
        if (currentBranch.isNullOrBlank()) {
            Messages.showWarningDialog(project, "No checked-out branch detected.", TITLE)
            return
        }
        BranchScopedGitLogDialog(project, repository, currentBranch).show()
    }

    companion object {
        private const val TITLE = "Branch Scoped Git Log"
    }
}
