package com.github.prakashgavel.myaichatplugin.api

class GeminiKeyProviderImpl(val keyStore: GeminiKeyStore = GeminiKeyStore()) : GeminiKeyProvider {
    override fun getApiKey(): String? = keyStore.getApiKey()
}