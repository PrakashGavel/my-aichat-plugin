package com.github.prakashgavel.myaichatplugin.api

import kotlinx.serialization.Serializable

/**

Data models for Gemini API requests/responses.

Adjust field names/types to match Gemini's real API schema.
 */
object GeminiApiRoutes {
    @Serializable
    data class GenerateContentRequest(
        val model: String,
        val contents: String
    )

    @Serializable
    data class GenerateContentResponse(
        val text: String
    )
}