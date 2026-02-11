package com.github.prakashgavel.myaichatplugin.gitlog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import git4idea.commands.Git
import git4idea.commands.GitLineHandler
import git4idea.commands.GitCommand
import git4idea.repo.GitRepository
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.table.DefaultTableModel

class BranchScopedGitLogDialog(
    private val project: Project,
    private val repository: GitRepository,
    private val branchName: String
) : DialogWrapper(project) {

    private val authorField = JTextField()
    private val sinceField = JTextField() // yyyy-MM-dd
    private val untilField = JTextField() // yyyy-MM-dd
    private val pathField = JTextField()
    private val tableModel = DefaultTableModel(arrayOf("Commit Hash", "Author", "Date", "Subject"), 0)
    private val table = com.intellij.ui.table.JBTable(tableModel)

    init {
        title = "Branch Scoped Git Log"
        init()
        sinceField.toolTipText = "Since date (yyyy-MM-dd)"
        untilField.toolTipText = "Until date (yyyy-MM-dd)"
        authorField.toolTipText = "Filter by author (string match)"
        pathField.toolTipText = "Filter by file path (relative to repo)"
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(8, 8))

        val filters = JPanel()
        filters.layout = BoxLayout(filters, BoxLayout.Y_AXIS)
        filters.border = JBUI.Borders.empty(8)

        fun row(label: String, field: JComponent): JPanel {
            return JPanel(BorderLayout(4, 4)).apply {
                add(JLabel(label), BorderLayout.WEST)
                add(field, BorderLayout.CENTER)
            }
        }

        filters.add(row("Branch:", JLabel(branchName)))
        filters.add(row("Author:", authorField))
        filters.add(row("Since (yyyy-MM-dd):", sinceField))
        filters.add(row("Until (yyyy-MM-dd):", untilField))
        filters.add(row("File path:", pathField))

        val runBtn = JButton("Run").apply { addActionListener { runGitLog() } }
        val topBar = JPanel(BorderLayout()).apply {
            add(filters, BorderLayout.CENTER)
            add(runBtn, BorderLayout.EAST)
        }
        panel.add(topBar, BorderLayout.NORTH)

        val scroll = JBScrollPane(table)
        panel.add(scroll, BorderLayout.CENTER)
        return panel
    }

    private fun runGitLog() {
        tableModel.rowCount = 0

        val handler = GitLineHandler(project, repository.root, GitCommand.LOG)
        // Restrict to the current branch and follow only the first-parent history to avoid traversing merged branches
        handler.addParameters("--first-parent", branchName)
        // Pretty format for parsing. Use ISO date for reliability.
        handler.addParameters("--pretty=format:%H|%an|%ad|%s", "--date=iso")
        handler.addParameters("--no-merges")

        val author = authorField.text.trim()
        if (author.isNotEmpty()) {
            handler.addParameters("--author=$author")
        }
        val since = sinceField.text.trim()
        if (since.isNotEmpty()) {
            handler.addParameters("--since=$since")
        }
        val until = untilField.text.trim()
        if (until.isNotEmpty()) {
            handler.addParameters("--until=$until")
        }
        val path = pathField.text.trim()
        if (path.isNotEmpty()) {
            handler.endOptions()
            handler.addParameters("--", path)
        }

        val result = Git.getInstance().runCommand(handler)
        if (result.success()) {
            val text = buildString {
                result.output.forEach { appendLine(it) }
            }
            parseAndDisplay(text)
        } else {
            val err = result.errorOutput.joinToString("\n")
            JOptionPane.showMessageDialog(null, "Git log failed:\n$err", "Git Log", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun parseAndDisplay(text: String) {
        // Each line: %H|%an|%ad|%s with ISO date
        text.lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                val parts = line.split('|')
                if (parts.size >= 4) {
                    val hash = parts[0]
                    val author = parts[1]
                    val date = parts[2]
                    val subject = parts.drop(3).joinToString("|") // in case subject contains '|'
                    tableModel.addRow(arrayOf(hash, author, date, subject))
                }
            }
    }
}
