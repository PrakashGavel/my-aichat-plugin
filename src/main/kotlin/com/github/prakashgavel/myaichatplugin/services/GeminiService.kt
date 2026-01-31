package com.github.prakashgavel.myaichatplugin.services

import GeminiApiClient
import com.github.prakashgavel.myaichatplugin.api.GeminiApiService
import com.github.prakashgavel.myaichatplugin.api.GeminiKeyProvider
import com.github.prakashgavel.myaichatplugin.api.GeminiKeyProviderImpl

/**

High-level service to orchestrate Gemini calls from the UI layer.

Creates a client and a service to generate content from Gemini.
Stores/reads API key via GeminiKeyStore (PasswordSafe-backed). */
class GeminiService( private val keyProvider: GeminiKeyProvider = GeminiKeyProviderImpl(), private val client: GeminiApiClient = GeminiApiClient(), ) {
    private val apiService = GeminiApiService(client, keyProvider)

    /**

    Ask Gemini a question using the specified model and contents.
    This should be called from a coroutine scope (UI should dispatch to IO). */ suspend fun askGemini(model: String, contents: String): String { return apiService.generateContent(model, contents) }
// Optional helpers

    // Save API key (wrapper around key store)
    fun saveApiKey(key: String) {
        if (keyProvider is GeminiKeyProviderImpl) {
            (keyProvider as GeminiKeyProviderImpl).let { impl ->
                val keyStore = impl.keyStore
                keyStore.saveApiKey(key)
            }
        } else {
// Fallback if you swap implementations
        }
    }

    fun getApiKey(): String? = keyProvider.getApiKey()
}