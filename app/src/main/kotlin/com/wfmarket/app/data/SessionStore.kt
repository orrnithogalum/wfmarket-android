package com.wfmarket.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds the session token, encrypted with an AES key that lives in the Android Keystore and can't
 * leave it. The password itself is never stored.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    @Volatile
    var token: String? = decrypt(prefs.getString(KEY_TOKEN, null))
        private set

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE, it).apply()
        }

    fun save(newToken: String) {
        token = newToken
        prefs.edit().putString(KEY_TOKEN, encrypt(newToken)).apply()
    }

