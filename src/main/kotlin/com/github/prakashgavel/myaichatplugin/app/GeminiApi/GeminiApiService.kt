package com.github.prakashgavel.myaichatplugin.api

import GeminiApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-level service to coordinate Gemini API calls.
 * This class uses GeminiApiClient to call Gemini and GeminiKeyService to retrieve the API key.
 */
class GeminiApiService(
    private val client: GeminiApiClient,
    private val keyProvider: GeminiKeyProvider
) {

    /**
     * Generates content from Gemini for the given model and contents.
     * This runs on an IO thread to avoid blocking the UI thread.
     */
    suspend fun generateContent(model: String, contents: String): String {
        val apiKey = keyProvider.getApiKey() ?: throw IllegalStateException("Gemini API key is not configured")
        return withContext(Dispatchers.IO) {
            client.generateContent(apiKey, model, contents)
        }
    }
}

/**
 * A small abstraction over how you fetch/store the API key.
 * You can replace this with your existing PasswordSafe-backed service.
 */
interface GeminiKeyProvider {
    fun getApiKey(): String?
}