package com.youflip.music.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds the music.youtube.com session cookie captured after the user signs in through Google's own
 * page. The cookie is encrypted with an AES key that never leaves the Android Keystore, and is only
 * ever sent to music.youtube.com.
 */
class AccountStore(
    context: Context,
    prefsName: String = "account",
    private val alias: String = "youflip_account",
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val _cookie = MutableStateFlow(load())
    val cookie: StateFlow<String?> = _cookie.asStateFlow()

    val isSignedIn: Boolean get() = _cookie.value != null

    fun save(cookie: String) {
        prefs.edit().putString(KEY_COOKIE, encrypt(cookie)).apply()
        _cookie.value = cookie
    }

    fun clear() {
        prefs.edit().remove(KEY_COOKIE).apply()
        _cookie.value = null
    }

    /** Headers that authenticate an InnerTube request as the signed-in user, or empty when signed out. */
    fun authHeaders(origin: String = ORIGIN): Map<String, String> {
        val cookie = _cookie.value ?: return emptyMap()
        val ts = System.currentTimeMillis() / 1000
        fun hash(id: String) = MessageDigest.getInstance("SHA-1").digest("$ts $id $origin".toByteArray())
            .joinToString("") { "%02x".format(it) }
        // Same scheme as the YouTube Music web client: SAPISIDHASH plus the 1P/3P variants.
        val parts = listOfNotNull(
            (cookieValue(cookie, "SAPISID") ?: cookieValue(cookie, "__Secure-3PAPISID"))?.let { "SAPISIDHASH ${ts}_${hash(it)}" },
            cookieValue(cookie, "__Secure-1PAPISID")?.let { "SAPISID1PHASH ${ts}_${hash(it)}" },
            cookieValue(cookie, "__Secure-3PAPISID")?.let { "SAPISID3PHASH ${ts}_${hash(it)}" },
        )
        if (parts.isEmpty()) return emptyMap()
        return mapOf(
            "Cookie" to cookie,
            "Authorization" to parts.joinToString(" "),
            "X-Goog-AuthUser" to "0",
            "X-Origin" to origin,
        )
    }

    private fun load(): String? = runCatching { prefs.getString(KEY_COOKIE, null)?.let(::decrypt) }.getOrNull()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return String(c.doFinal(bytes, 12, bytes.size - 12))
    }

    companion object {
        const val ORIGIN = "https://music.youtube.com"
        private const val KEY_COOKIE = "cookie"

        fun cookieValue(cookie: String, name: String): String? =
            cookie.split(';').map { it.trim() }.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')

        fun hasSession(cookie: String?): Boolean =
            cookie != null && (cookieValue(cookie, "SAPISID") != null || cookieValue(cookie, "__Secure-3PAPISID") != null)
    }
}
