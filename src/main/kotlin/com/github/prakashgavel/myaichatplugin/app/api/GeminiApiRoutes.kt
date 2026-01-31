//package com.github.prakashgavel.myaichatplugin.api
//
//import kotlinx.serialization.Serializable
//
///**
//
//Data models for Gemini API requests/responses.
//
//Adjust field names/types to match Gemini's real API schema.
// */
//object GeminiApiRoutes {
//    @Serializable
//    data class GenerateContentRequest(
//        val model: String,
//        val contents: String
//    )
//
//    @Serializable
//    data class GenerateContentResponse(
//        val text: String
//    )
//}
// kotlin
package com.github.prakashgavel.myaichatplugin.api

import kotlinx.serialization.Serializable

/**
 * Request/response models for Gemini Generative Language API.
 * Schema: https://ai.google.dev/api/rest/v1beta/models/generateContent
 */
object GeminiApiRoutes {
    @Serializable
    data class Part(
        val text: String? = null
        // Add other part types if needed: inline_data, file_data, etc.
    )

    @Serializable
    data class Content(
        val role: String = "user",
        val parts: List<Part>
    )

    @Serializable
    data class GenerateContentRequest(
        val contents: List<Content>
    )

    @Serializable
    data class GenerateContentResponse(
        val candidates: List<Candidate> = emptyList()
    ) {
        @Serializable
        data class Candidate(
            val content: Content? = null
        )
    }
}