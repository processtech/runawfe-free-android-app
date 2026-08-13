package ru.runa.wfe.data

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

class KeyStoreManager(context: Context) {
    private val aeadKeysetHandle by lazy {
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "keyset", "keyset_preference")
            .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
            .withMasterKeyUri(MASTERKEY_URI)
            .build()
            .keysetHandle
    }

    private val aead by lazy {
        aeadKeysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private val charset by lazy {
        Charsets.UTF_8
    }

    init {
        TinkConfig.register()
        AeadConfig.register()
    }

    fun encrypt(data: String): String {
        val ciphertext = aead.encrypt(data.toByteArray(charset), ByteArray(0))
        return android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)
    }

    fun decrypt(data: String): String {
        val ciphertext = android.util.Base64.decode(data, android.util.Base64.NO_WRAP)
        return aead.decrypt(ciphertext, ByteArray(0)).toString(charset)
    }

    companion object {
        const val ALIAS = "secureKey"
        const val MASTERKEY_URI = "android-keystore://$ALIAS"
    }
}