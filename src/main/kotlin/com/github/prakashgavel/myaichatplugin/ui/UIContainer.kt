package com.github.prakashgavel.myaichatplugin.ui

import GeminiApiClient
import com.github.prakashgavel.myaichatplugin.api.GeminiApiService
import com.github.prakashgavel.myaichatplugin.api.GeminiKeyProviderImpl
import com.github.prakashgavel.myaichatplugin.api.GeminiKeyStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.*
import java.awt.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UIContainer(private val project: Project) : JPanel(BorderLayout()) {
    // UI components
    private val models = UiModels.defaultModels
    private val modes = UiModels.defaultModes

    // Initial greeting text constant
    private val initialGreeting = "Hi @user, how can I help you?\nI'm powered by AI, so surprises and mistakes are possible. Verify code or suggestions and share feedback."

    private val chatArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        text = initialGreeting
        caretPosition = 0
    }

    private val inputPane = JPanel(BorderLayout())

    private val inInputTopPanel = JPanel(BorderLayout()).apply {
        val addContextBtn = JButton("Add context…").apply {
            addActionListener { showContextDialog() }
        }
        val setKeyBtn = JButton("Set Gemini Key…").apply {
            addActionListener { showSetKeyDialog() }
        }
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(addContextBtn)
            add(setKeyBtn)
        }
        add(left, BorderLayout.WEST)
        add(JPanel(), BorderLayout.CENTER) // spacer
    }

    // Gemini integration components
    private val geminiClient = GeminiApiClient()
    private val geminiKeyStore = GeminiKeyStore()
    private val geminiKeyProvider = GeminiKeyProviderImpl(geminiKeyStore)
    private val geminiService = GeminiApiService(geminiClient, geminiKeyProvider)

    // Replace single-line inputField with multi-line inputTextArea supporting Shift+Enter for newline, Enter to send
    private val inputTextArea = JTextArea().apply {
        border = BorderFactory.createEmptyBorder(6, 0, 0, 0)
        lineWrap = true
        wrapStyleWord = true
        rows = 3
        font = UIManager.getFont("TextField.font")
        // Ensure caret is visible even when the text area is empty
        caret.isVisible = true
        caretPosition = 0
        // Key bindings: Enter to send, Shift+Enter to newline
        val sendKey = KeyStroke.getKeyStroke("ENTER")
        val newlineKey = KeyStroke.getKeyStroke("shift ENTER")
        val im = getInputMap(JComponent.WHEN_FOCUSED)
        im.put(sendKey, "send")
        im.put(newlineKey, "newline")
        actionMap.put("send", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val userInput = text.trim()
                if (userInput.isEmpty()) return
                text = ""
                // After clearing, keep focus and show caret at start in the empty input
                requestFocusInWindow()
                caretPosition = 0
                appendChat("You", userInput)
                // Launch Gemini request
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val selectedModelDisplay = modelCombo.selectedItem as? String ?: models.first().displayName
                        val modelId = models.firstOrNull { it.displayName == selectedModelDisplay }?.id ?: models.first().id
                        val response = geminiService.generateContent(modelId, userInput)
                        appendChat("Gemini", response)
                    } catch (e: Exception) {
                        appendChat("Gemini", "Error: ${e.message}")
                    }
                }
            }
        })
        actionMap.put("newline", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                append("\n")
            }
        })
    }

    private val modeCombo = JComboBox(modes.map { it.name }.toTypedArray())
    private val modelCombo = JComboBox(models.map { it.displayName }.toTypedArray())

    // Left: dynamic tree
    private val leftRoot = DefaultMutableTreeNode("Root")
    private val leftTreeModel = DefaultTreeModel(leftRoot)

    // Center chat panel
    private val chatAreaScroll = JScrollPane(chatArea).apply {
        verticalScrollBar.unitIncrement = 16
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    }
    private val chatAreaPanel = JPanel(BorderLayout()).apply {
        add(chatAreaScroll, BorderLayout.CENTER)
    }

    // Top controls
    private val topControls = JPanel(BorderLayout()).apply {
        val left = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel("Mode: "))
            add(modeCombo)
            add(JLabel("Model: "))
            add(modelCombo)
        }
        add(left, BorderLayout.WEST)
        // Add Clear Chat button on the right
        val right = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(JButton("Clear Chat").apply {
                addActionListener { clearChat() }
            })
        }
        add(right, BorderLayout.EAST)
    }

    // Panel to hold selected context chips in the input area
    private val selectedContextPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        border = BorderFactory.createTitledBorder("Context")
    }

    // Add a wrapper to provide a small vertical gap above the context chips box
    private val selectedContextWrapper = JPanel(BorderLayout()).apply {
        // add a small top padding to create vertical space between input area and context box
        border = BorderFactory.createEmptyBorder(6, 0, 0, 0)
        add(selectedContextPanel, BorderLayout.CENTER)
    }

    init {
        // Build layout: chat on top, then input (button above)
        val chatAndInput = JPanel(BorderLayout())
        // Chat area
        chatAndInput.add(chatAreaPanel, BorderLayout.CENTER)

        // Input area with button on top
        val inputWrapper = JPanel(BorderLayout())
        inputWrapper.add(inInputTopPanel, BorderLayout.NORTH)
        inputWrapper.add(inputPane, BorderLayout.CENTER)

        chatAndInput.add(inputWrapper, BorderLayout.SOUTH)

        // Assemble main layout
        add(topControls, BorderLayout.NORTH) // mode/model bar
        add(chatAndInput, BorderLayout.CENTER)

        // Prepare input area
        inputPane.minimumSize = Dimension(0, 120)
        inputPane.preferredSize = Dimension(0, 140)
        // Use a scroll pane for multi-line input
        inputPane.add(JScrollPane(inputTextArea).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }, BorderLayout.CENTER)
        inputPane.add(JPanel(), BorderLayout.EAST)
        // Replace direct context panel add with wrapper to introduce gap
        inputPane.add(selectedContextWrapper, BorderLayout.NORTH) // chips above input

        // Populate context tree
        refreshContextTree()

        // Focus input at startup and place caret at 0 in chat display
        SwingUtilities.invokeLater {
            inputTextArea.requestFocusInWindow()
            // Ensure caret is visible and at position 0 when empty
            inputTextArea.caret.isVisible = true
            inputTextArea.caretPosition = 0
            chatArea.caretPosition = 0
            chatAreaScroll.verticalScrollBar.value = 0
        }

        // Prompt for Gemini key on first open if missing
        if (geminiKeyStore.getApiKey().isNullOrBlank()) {
            SwingUtilities.invokeLater { showSetKeyDialog(initial = "hgfaehgfyiwhfhabfuf") }
        }
    }

    private fun showSetKeyDialog(initial: String? = null) {
        val field = JPasswordField().apply {
            if (initial != null) text = initial
            columns = 32
        }
        val panel = JPanel(BorderLayout(8, 8)).apply {
            add(JLabel("Enter your Gemini API key:"), BorderLayout.NORTH)
            add(field, BorderLayout.CENTER)
        }
        val result = JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(this),
            panel,
            "Set Gemini Key",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )
        if (result == JOptionPane.OK_OPTION) {
            val key = String(field.password).trim()
            if (key.isNotEmpty()) {
                geminiKeyStore.saveApiKey(key)
                appendChat("System", "Gemini API key saved.")
            } else {
                // User pressed OK with empty key: clear any stored key so future calls fail appropriately
                geminiKeyStore.clear()
                appendChat("System", "Gemini API key not set.")
            }
        }
    }

    private fun clearChat() {
        // Reset chat area to initial greeting only and scroll to top
        chatArea.text = initialGreeting
        chatArea.caretPosition = 0
        chatAreaScroll.verticalScrollBar.value = 0
    }

    private fun appendChat(sender: String, text: String) {
        val current = chatArea.text
        chatArea.text = "$current\n$sender: $text"
        // Scroll to bottom to show newest message
        chatArea.caretPosition = chatArea.document.length
    }

    // Build real context tree sections
    private fun refreshContextTree() {
        leftRoot.removeAllChildren()
        // Section headers
        val sectionFiles = DefaultMutableTreeNode("Files")
        val sectionFolders = DefaultMutableTreeNode("Folders")

        val allFiles = mutableListOf<VirtualFile>()
        val allFolders = mutableListOf<VirtualFile>()

        val baseDir = project.baseDir
        if (baseDir != null && baseDir.exists()) {
            collectFilesAndFolders(baseDir, allFiles, allFolders)
        }

        for (f in allFiles) {
            val rel = VfsUtilCore.getRelativePath(f, project.baseDir, '/')
            sectionFiles.add(DefaultMutableTreeNode(rel ?: f.path))
        }
        for (f in allFolders) {
            val rel = VfsUtilCore.getRelativePath(f, project.baseDir, '/')
            sectionFolders.add(DefaultMutableTreeNode(rel ?: f.path))
        }

        leftRoot.add(sectionFiles)
        leftRoot.add(sectionFolders)
        leftRoot.add(DefaultMutableTreeNode("Back"))
        leftTreeModel.reload()
    }

    // Recursive walk
    private fun collectFilesAndFolders(
        dir: VirtualFile,
        files: MutableList<VirtualFile>,
        folders: MutableList<VirtualFile>
    ) {
        if (!dir.isDirectory) {
            files.add(dir)
            return
        }
        folders.add(dir)
        for (child in dir.children) {
            if (child.isDirectory) {
                collectFilesAndFolders(child, files, folders)
            } else {
                files.add(child)
            }
        }
    }

    private fun showContextDialog() {
        val dialogContent = JPanel(BorderLayout())
        val cards = JPanel(CardLayout())

        val allFiles = mutableListOf<VirtualFile>()
        val allFolders = mutableListOf<VirtualFile>()
        val baseDir = project.baseDir
        if (baseDir != null && baseDir.exists()) {
            collectFilesAndFolders(baseDir, allFiles, allFolders)
        }

        val openFiles = FileEditorManager.getInstance(project).openFiles
        val openList = JList(openFiles.map { it.name }.toTypedArray())
        val openPanel = JPanel(BorderLayout()).apply {
            add(JButton("+ Add All Open Files").apply {
                addActionListener { addOpenFilesToContext(openFiles) }
            }, BorderLayout.NORTH)
            add(JScrollPane(openList), BorderLayout.CENTER)
        }

        val landing = JPanel(BorderLayout()).apply {
            val buttons = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JButton("Files").apply {
                    addActionListener { (cards.layout as CardLayout).show(cards, "filesCard") }
                })
                add(JButton("Folders").apply {
                    addActionListener { (cards.layout as CardLayout).show(cards, "foldersCard") }
                })
            }
            add(JLabel("<html><b>Add Context</b></html>"), BorderLayout.NORTH)
            add(buttons, BorderLayout.CENTER)
            add(openPanel, BorderLayout.SOUTH)
        }

        val filesDisplayNames = allFiles.map { VfsUtilCore.getRelativePath(it, project.baseDir, '/') ?: it.path }
        val filesList = JList(filesDisplayNames.toTypedArray()).apply {
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    if (e.clickCount == 2) {
                        val idx = locationToIndex(e.point)
                        if (idx >= 0) addChipToInput(filesDisplayNames[idx])
                    }
                }
            })
        }
        val filesCard = JPanel(BorderLayout()).apply {
            add(JLabel("Files"), BorderLayout.NORTH)
            add(JScrollPane(filesList), BorderLayout.CENTER)
            add(JButton("Back").apply {
                addActionListener { (cards.layout as CardLayout).show(cards, "landing") }
            }, BorderLayout.SOUTH)
        }

        val foldersDisplayNames = allFolders.map { VfsUtilCore.getRelativePath(it, project.baseDir, '/') ?: it.path }
        val foldersList = JList(foldersDisplayNames.toTypedArray()).apply {
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    if (e.clickCount == 2) {
                        val idx = locationToIndex(e.point)
                        if (idx >= 0) addChipToInput(foldersDisplayNames[idx])
                    }
                }
            })
        }
        val foldersCard = JPanel(BorderLayout()).apply {
            add(JLabel("Folders"), BorderLayout.NORTH)
            add(JScrollPane(foldersList), BorderLayout.CENTER)
            add(JButton("Back").apply {
                addActionListener { (cards.layout as CardLayout).show(cards, "landing") }
            }, BorderLayout.SOUTH)
        }

        cards.add(landing, "landing")
        cards.add(filesCard, "filesCard")
        cards.add(foldersCard, "foldersCard")

        val dialogContainer = JPanel(BorderLayout()).apply {
            add(cards, BorderLayout.CENTER)
        }

        val ownerWindow = SwingUtilities.getWindowAncestor(this)
        val dialog = if (ownerWindow is Frame) {
            JDialog(ownerWindow, "Add Context", true)
        } else if (ownerWindow is Dialog) {
            JDialog(ownerWindow, "Add Context", true)
        } else {
            JDialog(null as Frame?, "Add Context", true)
        }

        dialog.contentPane = dialogContainer
        dialog.pack()
        dialog.setLocationRelativeTo(ownerWindow)
        dialog.isVisible = true
    }

    private fun addOpenFilesToContext(openFiles: Array<VirtualFile>) {
        openFiles.forEach { addChipToInput(it.name) }
    }

    private fun addChipToInput(name: String) {
        val existing = (0 until selectedContextPanel.componentCount)
            .map { selectedContextPanel.getComponent(it) }
            .filterIsInstance<JPanel>()
            .any { (it.getComponent(0) as? JLabel)?.text == name }
        if (existing) return

        val chip = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
            border = BorderFactory.createLineBorder(Color.GRAY)
            add(JLabel(name))
            add(JButton("x").apply {
                margin = Insets(0, 4, 0, 4)
                addActionListener {
                    selectedContextPanel.remove(this@apply.parent)
                    selectedContextPanel.revalidate()
                    selectedContextPanel.repaint()
                }
            })
        }
        selectedContextPanel.add(chip)
        selectedContextPanel.revalidate()
        selectedContextPanel.repaint()
    }

    // Helpers for files
    private fun collectFiles(dir: VirtualFile, acc: MutableList<VirtualFile>) {
        if (!dir.isDirectory) {
            acc.add(dir)
            return
        }
        for (child in dir.children) {
            if (child.isDirectory) continue
            acc.add(child)
            if (child.isDirectory) collectFiles(child, acc)
        }
    }

    private fun collectFolders(dir: VirtualFile, acc: MutableList<VirtualFile>) {
        if (!dir.isDirectory) return
        acc.add(dir)
        for (child in dir.children) {
            if (child.isDirectory) collectFolders(child, acc)
        }
    }
}
