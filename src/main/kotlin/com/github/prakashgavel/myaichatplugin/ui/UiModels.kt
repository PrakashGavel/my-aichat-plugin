package com.github.prakashgavel.myaichatplugin.ui

enum class UiMode { ASK, AGENT, EDIT }

data class ModelEntry(val displayName: String, val id: String)

enum class ContextView {
    BACK,
    FILES,
    FOLDERS
}

// Example: Provide default models for use in UI
object UiModels {
    val defaultModels = listOf(
        ModelEntry("Gemini 2.5 Flash Lite", "gemini-2.5-flash-lite")
    )
    val defaultModes = UiMode.values().toList()
}