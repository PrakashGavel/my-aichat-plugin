package com.github.prakashgavel.myaichatplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.*
import java.awt.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class UIContainer(private val project: Project) : JPanel(BorderLayout()) {
    // UI components
    private val models = UiModels.defaultModels
    private val modes = UiModels.defaultModes

    private val chatArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        text =
            "Hi @user, how can I help you?\nI'm powered by AI, so surprises and mistakes are possible. Verify code or suggestions and share feedback."
    }

    private val inputPane = JPanel(BorderLayout())

    private val inInputTopPanel = JPanel(BorderLayout()).apply {
        val addContextBtn = JButton("Add context…").apply {
            addActionListener { showContextDialog() }
        }
        add(addContextBtn, BorderLayout.WEST)
        add(JPanel(), BorderLayout.CENTER) // spacer
    }

    private val inputField = JTextField().apply {
        addActionListener {
            val userInput = this.text
            this.text = ""
            appendChat("You", userInput)
            // placeholder response
            appendChat(
                "Copilot", "Output (hard-coded):\n" +
                        "fun main() {\n    println(\"Hello, World!\")\n}"
            )
        }
    }


    private val modeCombo = JComboBox(modes.map { it.name }.toTypedArray())
    private val modelCombo = JComboBox(models.map { it.displayName }.toTypedArray())

    // Left: dynamic tree
    private val leftRoot = DefaultMutableTreeNode("Root")
    private val leftTreeModel = DefaultTreeModel(leftRoot)

    // Center chat panel
    private val chatAreaPanel = JPanel(BorderLayout()).apply {
        add(JScrollPane(chatArea), BorderLayout.CENTER)
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
    }

    // Panel to hold selected context chips in the input area
    private val selectedContextPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        border = BorderFactory.createTitledBorder("Context")
    }

    init {
        // Build a vertical layout: chat on top, then input (with button above)
        val chatAndInput = JPanel(BorderLayout())
        // Chat area (read-only)
        chatAndInput.add(chatAreaPanel, BorderLayout.CENTER)

        // Input area with button on top
        val inputWrapper = JPanel(BorderLayout())
        inputWrapper.add(inInputTopPanel, BorderLayout.NORTH)
        inputWrapper.add(inputPane, BorderLayout.CENTER)

        chatAndInput.add(inputWrapper, BorderLayout.SOUTH)

        // Add to tool window (replace previous center/main splits)
        add(topControls, BorderLayout.NORTH) // keep if you want mode/model bar
        add(chatAndInput, BorderLayout.CENTER)

        // Prepare input area
        inputPane.minimumSize = Dimension(0, 120)
        inputPane.preferredSize = Dimension(0, 140)
        inputPane.add(inputField, BorderLayout.CENTER)
        inputPane.add(JPanel(), BorderLayout.EAST)
        inputPane.add(selectedContextPanel, BorderLayout.NORTH) // show chips above the input field

        // Populate context tree only if you still need it elsewhere
        refreshContextTree()
    }

    private fun appendChat(sender: String, text: String) {
        val current = chatArea.text
        chatArea.text = "$$current\n$$sender: $text"
    }

    // Build real context tree sections
    private fun refreshContextTree() {
        leftRoot.removeAllChildren()
        // Section header: Context
        val sectionFiles = DefaultMutableTreeNode("Files")
        val sectionFolders = DefaultMutableTreeNode("Folders")

        // Fetch all files and folders from the current project
        val allFiles = mutableListOf<VirtualFile>()
        val allFolders = mutableListOf<VirtualFile>()

        // Quick naive collect: iterate project base dir recursively
        val baseDir = project.baseDir
        if (baseDir != null && baseDir.exists()) {
            collectFilesAndFolders(baseDir, allFiles, allFolders)
        }

        // Add files
        for (f in allFiles) {
            val rel = VfsUtilCore.getRelativePath(f, project.baseDir, '/')
            sectionFiles.add(DefaultMutableTreeNode(rel ?: f.path))
        }
        // Add folders
        for (f in allFolders) {
            val rel = VfsUtilCore.getRelativePath(f, project.baseDir, '/')
            sectionFolders.add(DefaultMutableTreeNode(rel ?: f.path))
        }

        leftRoot.add(sectionFiles)
        leftRoot.add(sectionFolders)
        // Add a back node to return from sections
        leftRoot.add(DefaultMutableTreeNode("Back"))
        leftTreeModel.reload()
    }

    // Recursive walk to collect files and folders
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

//    private fun showContextDialog() {
//        val dialogContent = JPanel(BorderLayout())
//        val cards = JPanel(CardLayout())
//
//        // --- Prepare data once ---
//        val allFiles = mutableListOf<VirtualFile>()
//        val allFolders = mutableListOf<VirtualFile>()
//        val baseDir = project.baseDir
//        if (baseDir != null && baseDir.exists()) {
//            collectFilesAndFolders(baseDir, allFiles, allFolders)
//        }
//
//        // --- Open files (only for landing) ---
//        val openFiles = FileEditorManager.getInstance(project).openFiles
//        val openList = JList(openFiles.map { it.name }.toTypedArray())
//        val openPanel = JPanel(BorderLayout()).apply {
//            add(JButton("+ Add All Open Files").apply {
//                addActionListener { addOpenFilesToContext(openFiles) }
//            }, BorderLayout.NORTH)
//            add(JScrollPane(openList), BorderLayout.CENTER)
//        }
//
//        // --- Card 1: Landing page with buttons + open files ---
//        val landing = JPanel(BorderLayout()).apply {
//            val buttons = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
//                add(JButton("Files").apply {
//                    addActionListener { (cards.layout as CardLayout).show(cards, "filesCard") }
//                })
//                add(JButton("Folders").apply {
//                    addActionListener { (cards.layout as CardLayout).show(cards, "foldersCard") }
//                })
//            }
//            add(JLabel("<html><b>Add Context</b></html>"), BorderLayout.NORTH)
//            add(buttons, BorderLayout.CENTER)
//            // place open files directly below buttons (removes large gap)
//            add(openPanel, BorderLayout.SOUTH)
//        }
//
//        // --- Card 2: Files list with back (no open files section here) ---
//        val filesList = JList(
//            allFiles.map { VfsUtilCore.getRelativePath(it, project.baseDir, '/') ?: it.path }.toTypedArray()
//        )
//        val filesCard = JPanel(BorderLayout()).apply {
//            add(JLabel("Files"), BorderLayout.NORTH)
//            add(JScrollPane(filesList), BorderLayout.CENTER)
//            add(JButton("Back").apply {
//                addActionListener { (cards.layout as CardLayout).show(cards, "landing") }
//            }, BorderLayout.SOUTH)
//        }
//
//        // --- Card 3: Folders list with back (no open files section here) ---
//        val foldersList = JList(
//            allFolders.map { VfsUtilCore.getRelativePath(it, project.baseDir, '/') ?: it.path }.toTypedArray()
//        )
//        val foldersCard = JPanel(BorderLayout()).apply {
//            add(JLabel("Folders"), BorderLayout.NORTH)
//            add(JScrollPane(foldersList), BorderLayout.CENTER)
//            add(JButton("Back").apply {
//                addActionListener { (cards.layout as CardLayout).show(cards, "landing") }
//            }, BorderLayout.SOUTH)
//        }
//
//        // --- Assemble cards ---
//        cards.add(landing, "landing")
//        cards.add(filesCard, "filesCard")
//        cards.add(foldersCard, "foldersCard")
//
//        dialogContent.add(cards, BorderLayout.CENTER)
//
//        val dialog = JOptionPane(dialogContent, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION)
//        val dlg = dialog.createDialog(this, "Add Context")
//        dlg.isVisible = true
//    }
//
//    private fun addOpenFilesToContext(openFiles: Array<VirtualFile>) {
//        // For demo: just print to chat
//        val names = openFiles.joinToString(", ") { it.name }
//        appendChat("Context", "Added open files: $names")
//        // In real usage, update internal context model accordingly
//    }

    private fun showContextDialog() {
        val dialogContent = JPanel(BorderLayout())
        val cards = JPanel(CardLayout())

        // --- Prepare data once ---
        val allFiles = mutableListOf<VirtualFile>()
        val allFolders = mutableListOf<VirtualFile>()
        val baseDir = project.baseDir
        if (baseDir != null && baseDir.exists()) {
            collectFilesAndFolders(baseDir, allFiles, allFolders)
        }

        // --- Open files (only for landing) ---
        val openFiles = FileEditorManager.getInstance(project).openFiles
        val openList = JList(openFiles.map { it.name }.toTypedArray())
        val openPanel = JPanel(BorderLayout()).apply {
            add(JButton("+ Add All Open Files").apply {
                addActionListener { addOpenFilesToContext(openFiles) }
            }, BorderLayout.NORTH)
            add(JScrollPane(openList), BorderLayout.CENTER)
        }

        // --- Card 1: Landing page with buttons + open files ---
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

        // --- Card 2: Files list with back + double-click add ---
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

        // --- Card 3: Folders list with back + double-click add ---
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

        // --- Assemble cards ---
        cards.add(landing, "landing")
        cards.add(filesCard, "filesCard")
        cards.add(foldersCard, "foldersCard")

        dialogContent.add(cards, BorderLayout.CENTER)

        // Use modal JDialog without `apply`
        val ownerWindow = SwingUtilities.getWindowAncestor(this)
        val dialog = if (ownerWindow is Frame) {
            javax.swing.JDialog(ownerWindow, "Add Context", true)
        } else if (ownerWindow is Dialog) {
            javax.swing.JDialog(ownerWindow, "Add Context", true)
        } else {
            javax.swing.JDialog(null as Frame?, "Add Context", true)
        }

        dialog.contentPane = dialogContent
        dialog.pack()
        dialog.setLocationRelativeTo(ownerWindow)
        dialog.isVisible = true
    }

    private fun addOpenFilesToContext(openFiles: Array<VirtualFile>) {
        openFiles.forEach { addChipToInput(it.name) }
    }

    // Create a removable chip and add it to the selectedContextPanel
    private fun addChipToInput(name: String) {
        // Avoid duplicates
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

    private fun collectFiles(dir: VirtualFile, acc: MutableList<VirtualFile>) {
        if (!dir.isDirectory) {
            acc.add(dir)
            return
        }
        for (child in dir.children) {
            if (child.isDirectory) continue
            acc.add(child)
            // also dive deeper for nested files
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