package com.github.prakashgavel.myaichatplugin.api

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe

class GeminiKeyStore(private val serviceName: String = "com.github.prakashgavel.myaichatplugin.gemini") {
    private val credentialsAttributes = CredentialAttributes(serviceName, "api-key")

    fun saveApiKey(key: String) {
        val creds = Credentials(null, key.toCharArray())
        PasswordSafe.instance.set(credentialsAttributes, creds)
    }

    // kotlin
    fun getApiKey(): String? {
        val creds: Credentials? = PasswordSafe.instance.get(credentialsAttributes)
        return creds?.password?.toCharArray()?.concatToString()
    }

    fun clear() {
        PasswordSafe.instance.set(credentialsAttributes, null)
    }
}