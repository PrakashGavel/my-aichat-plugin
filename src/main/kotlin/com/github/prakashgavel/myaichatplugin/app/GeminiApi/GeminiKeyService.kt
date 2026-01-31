package com.github.prakashgavel.myaichatplugin.api

/**

A small façade over the GeminiKeyStore to keep UI/UI-layer decoupled from

PasswordSafe specifics. You can extend this to include UI prompts, migration logic, etc.
 */
class GeminiKeyService(private val keyStore: GeminiKeyStore = GeminiKeyStore()) {

    fun saveApiKey(key: String) {
        keyStore.saveApiKey(key)
    }

    fun getApiKey(): String? {
        return keyStore.getApiKey()
    }

    fun clearKey() {
        keyStore.clear()
    }
}