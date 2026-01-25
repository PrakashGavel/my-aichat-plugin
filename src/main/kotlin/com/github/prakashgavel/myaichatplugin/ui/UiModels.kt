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
        ModelEntry("Gemini Pro", "gemini-pro"),
        ModelEntry("Gemini 1.5 Flash", "gemini-1.5-flash")
    )
    val defaultModes = UiMode.values().toList()
}