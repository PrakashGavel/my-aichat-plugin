package com.github.prakashgavel.myaichatplugin.commit

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.nio.charset.StandardCharsets
import javax.swing.*
import kotlin.math.abs
import com.github.prakashgavel.myaichatplugin.api.GeminiApiService
import com.github.prakashgavel.myaichatplugin.api.GeminiKeyProviderImpl
import com.github.prakashgavel.myaichatplugin.app.GeminiApi.GeminiApiClient
import com.github.prakashgavel.myaichatplugin.ui.UiModels
import kotlinx.coroutines.runBlocking

/**
 * Smart Commit Message Generator
 * - Reads local Git diff for current project working tree (staged + unstaged via `git diff` and `git diff --staged`)
 * - Summarizes changes locally (no external calls) to create a short subject and optional detailed body
 * - Infers a Conventional Commit-type prefix and pulls a ticket ID from branch if present
 * - Lets user copy the message for quick paste into the Commit message field
 */
class SmartCommitAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Compute repository root (fallback to project.basePath)
        val basePath = project.basePath ?: return
        val repoRoot = LocalFileSystem.getInstance().findFileByPath(basePath)?.canonicalPath ?: basePath

        val (branch, _) = runGit(repoRoot, listOf("rev-parse", "--abbrev-ref", "HEAD"))
        val (unstaged, unstagedErr) = runGit(repoRoot, listOf("diff"))
        val (staged, stagedErr) = runGit(repoRoot, listOf("diff", "--staged"))

        if (unstagedErr.isNotBlank() || stagedErr.isNotBlank()) {
            val err = (stagedErr + "\n" + unstagedErr).trim()
            if (err.contains("not a git repository", ignoreCase = true) || err.contains("'git' is not recognized", ignoreCase = true)) {
                JOptionPane.showMessageDialog(null, "Git not available or project is not a repository.", TITLE, JOptionPane.WARNING_MESSAGE)
                return
            }
        }

        val aggregate = buildString {
            if (staged.isNotBlank()) {
                append("# STAGED\n")
                append(staged)
                append("\n\n")
            }
            if (unstaged.isNotBlank()) {
                append("# UNSTAGED\n")
                append(unstaged)
            }
        }.trim()

        if (aggregate.isBlank()) {
            JOptionPane.showMessageDialog(null, "No local changes detected.", TITLE, JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val summary = DiffSummarizer().summarize(aggregate, branch.trim())
        SmartCommitDialog(project, summary, aggregate).show()
    }

    private fun runGit(workingDir: String, args: List<String>): Pair<String, String> {
        return try {
            val pb = ProcessBuilder(listOf("git") + args)
                .directory(java.io.File(workingDir))
            val p = pb.start()
            val out = p.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            val err = p.errorStream.readBytes().toString(StandardCharsets.UTF_8)
            p.waitFor()
            out to err
        } catch (t: Throwable) {
            "" to (t.message ?: "")
        }
    }

    companion object {
        private const val TITLE = "Smart Commit"
    }
}

/**
 * Simple heuristics to turn unified diff into a subject and body.
 * Keeps everything local and fast.
 */
class DiffSummarizer {
    data class Result(val subject: String, val body: String)

    fun summarize(diff: String, branchName: String? = null): Result {
        val files = mutableListOf<FileDelta>()
        var current: FileDelta? = null
        diff.lineSequence().forEach { line ->
            when {
                line.startsWith("diff --git ") -> {
                    val parts = line.removePrefix("diff --git ").split(' ')
                    val path = parts.getOrNull(1)?.removePrefix("b/") ?: parts.lastOrNull()?.removePrefix("b/") ?: "unknown"
                    val fd = FileDelta(path)
                    current = fd
                    files += fd
                }
                line.startsWith("+++") || line.startsWith("---") || line.startsWith("index ") || line.startsWith("@@") -> {
                    // skip metadata lines
                }
                line.startsWith("+ ") || line == "+" || (line.startsWith('+') && !line.startsWith("+++")) -> current?.let { it.adds = it.adds + 1 }
                line.startsWith('-') && !line.startsWith("---") -> current?.let { it.dels = it.dels + 1 }
            }
        }

        val extCounts = files.groupingBy { it.extension() }.eachCount().toList().sortedByDescending { it.second }
        val topExt = extCounts.firstOrNull()?.first
        val changedFiles = files.size
        val totalAdds = files.sumOf { it.adds }
        val totalDels = files.sumOf { it.dels }

        val type = inferType(files, totalAdds, totalDels)
        val ticket = extractTicket(branchName)

        val subject = buildString {
            if (ticket != null) append("[$ticket] ")
            append("$type: ")
            append(verb(totalAdds, totalDels)).append(' ')
            if (!topExt.isNullOrBlank()) append(topExt).append(' ')
            append("files: $changedFiles, +$totalAdds/-$totalDels")
        }

        val body = buildString {
            appendLine("Summary:")
            appendLine("- Files changed: $changedFiles")
            appendLine("- Insertions: $totalAdds")
            appendLine("- Deletions: $totalDels")
            if (extCounts.isNotEmpty()) {
                appendLine()
                appendLine("By file type:")
                extCounts.take(6).forEach { (ext, count) ->
                    appendLine("  - ${ext.ifBlank { "(no ext)" }}: $count")
                }
            }
            val topFiles = files.sortedByDescending { (it.adds + it.dels) }.take(10)
            if (topFiles.isNotEmpty()) {
                appendLine()
                appendLine("Top files:")
                topFiles.forEach { f ->
                    appendLine("  - ${f.path}: +${f.adds}/-${f.dels}")
                }
            }
            if (ticket != null) {
                appendLine()
                appendLine("Related: $ticket (from branch)")
            }
        }

        return Result(subject, body.trimEnd())
    }

    private fun inferType(files: List<FileDelta>, adds: Int, dels: Int): String {
        val paths = files.map { it.path }
        val onlyDocs = paths.isNotEmpty() && paths.all { isDocPath(it) }
        if (onlyDocs) return "docs"
        val onlyTests = paths.isNotEmpty() && paths.all { it.contains("/test", ignoreCase = true) || it.contains("Test", ignoreCase = true) }
        if (onlyTests) return "test"
        val onlyBuild = paths.isNotEmpty() && paths.all { it.endsWith("build.gradle") || it.endsWith("build.gradle.kts") || it.endsWith("pom.xml") || it.contains("/gradle/") }
        if (onlyBuild) return "build"
        val mostlyConfig = paths.count { it.endsWith(".yml") || it.endsWith(".yaml") || it.endsWith(".json") || it.endsWith(".properties") || it.endsWith(".toml") } >= (paths.size.coerceAtLeast(1) * 0.6)
        if (mostlyConfig) return "chore"
        val refactorish = adds > 0 && dels > 0 && abs(adds - dels) < (adds + dels) * 0.2
        if (refactorish) return "refactor"
        return if (adds >= dels * 2) "feat" else if (dels > adds) "chore" else "feat"
    }

    private fun isDocPath(p: String): Boolean {
        val lower = p.lowercase()
        return lower.contains("readme") || lower.contains("changelog") || lower.contains("docs/") ||
                lower.endsWith(".md") || lower.endsWith(".adoc") || lower.endsWith(".rst")
    }

    private fun verb(adds: Int, dels: Int): String = when {
        adds > 0 && dels == 0 -> "Add"
        adds == 0 && dels > 0 -> "Remove"
        adds > 0 && dels > 0 -> "Update"
        else -> "Change"
    }

    private fun extractTicket(branch: String?): String? {
        if (branch.isNullOrBlank()) return null
        val m = Regex("([A-Z]{2,}-\\d+)").find(branch)
        return m?.groupValues?.getOrNull(1)
    }

    private data class FileDelta(val path: String, var adds: Int = 0, var dels: Int = 0) {
        fun extension(): String = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    }
}

private class SmartCommitDialog(project: Project, result: DiffSummarizer.Result, private val aggregateDiff: String) : DialogWrapper(project) {
    private val DIALOG_TITLE = "Smart Commit"
    private val subjectField = JTextField(result.subject)
    // New summarized short commit message area
    private val summaryArea = JTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 3
        toolTipText = "Summarized short commit message (AI-generated)"
        // Ensure caret/insertion point is visible even when empty
        caretPosition = 0
    }
    private val bodyArea = JTextArea(buildString {
        append(result.body)
        append("\n\n")
        append("Unified diff (staged + unstaged):\n")
        append(aggregateDiff)
    }).apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 12
    }

    // Use existing Gemini service wiring
    private val geminiService = GeminiApiService(GeminiApiClient(), GeminiKeyProviderImpl())
    private val defaultModelId = UiModels.defaultModels.firstOrNull()?.id ?: "gemini-2.5-flash-lite"

    init {
        title = DIALOG_TITLE
        init()
        // Set insertion point in the summary area even if empty
        SwingUtilities.invokeLater { summaryArea.requestFocusInWindow() }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(8, 8))
        val top = JPanel(BorderLayout(4, 4))
        top.add(JLabel("Subject:"), BorderLayout.WEST)
        top.add(subjectField, BorderLayout.CENTER)
        panel.add(top, BorderLayout.NORTH)

        // Summarized short commit message section
        val summaryScroll = JBScrollPane(summaryArea).apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }
        val summaryPanel = JPanel(BorderLayout(4, 4)).apply {
            add(JLabel("Summarized Short commit message:"), BorderLayout.NORTH)
            add(summaryScroll, BorderLayout.CENTER)
            // Button directly under the summary area
            val summaryButtons = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(Box.createHorizontalGlue())
                add(JButton("Generate Commit Message").apply {
                    addActionListener { generateCommitMessageFromDetails() }
                })
            }
            add(summaryButtons, BorderLayout.SOUTH)
        }
        panel.add(summaryPanel, BorderLayout.WEST)

        val bodyScroll = JBScrollPane(bodyArea).apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }
        val bodyPanel = JPanel(BorderLayout(4, 4)).apply {
            add(JLabel("Details (optional):"), BorderLayout.NORTH)
            add(bodyScroll, BorderLayout.CENTER)
        }
        panel.add(bodyPanel, BorderLayout.CENTER)

        val buttons = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(Box.createHorizontalGlue())
            add(JButton("Copy").apply { addActionListener { copyToClipboard() } })
            add(Box.createHorizontalStrut(8))
            add(JLabel("Open Commit tool window and paste with Ctrl+V"))
        }
        panel.add(buttons, BorderLayout.SOUTH)
        return panel
    }

    private fun composedMessage(): String = buildString {
        append(subjectField.text.trim())
        val body = bodyArea.text.trim()
        if (body.isNotEmpty()) {
            append("\n\n").append(body)
        }
    }

    private fun copyToClipboard() {
        val sel = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        sel.setContents(java.awt.datatransfer.StringSelection(composedMessage()), null)
        JOptionPane.showMessageDialog(null, "Copied to clipboard.", DIALOG_TITLE, JOptionPane.INFORMATION_MESSAGE)
    }

    // Use aggregate diff and optional details to generate short commit subject via Gemini
    private fun generateCommitMessageFromDetails() {
        val details = bodyArea.text.trim()
        summaryArea.text = "Generating short commit message via Gemini..."
        summaryArea.isEnabled = false

        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val prompt = buildString {
                    appendLine("You are a commit message assistant. Create a single concise commit subject line (<= 72 chars) using Conventional Commits style when appropriate (feat, fix, refactor, chore, docs, test, build).")
                    appendLine("Return ONLY the subject line, no trailing punctuation.")
                    appendLine()
                    appendLine("If a ticket ID like ABC-123 is present in context, include it as [ABC-123] prefix when relevant.")
                    appendLine()
                    appendLine("Here is the unified git diff (staged + unstaged):")
                    appendLine("""\
$aggregateDiff
""".trim())
                    if (details.isNotEmpty()) {
                        appendLine()
                        appendLine("Human-readable summary:")
                        appendLine(details)
                    }
                }
                val text: String = runBlocking {
                    geminiService.generateContent(defaultModelId, prompt)
                }.trim()

                SwingUtilities.invokeLater {
                    val finalText = text.lines().firstOrNull()?.trim().orEmpty()
                    summaryArea.text = finalText
                    // Also update the Subject field to the generated subject if present
                    if (finalText.isNotBlank()) {
                        subjectField.text = finalText
                    }
                    summaryArea.isEnabled = true
                }
            } catch (t: Throwable) {
                SwingUtilities.invokeLater {
                    summaryArea.isEnabled = true
                    summaryArea.text = "Failed to generate: ${t.message}"
                    JOptionPane.showMessageDialog(null, "Gemini error: ${t.message}", "Smart Commit", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }
}
