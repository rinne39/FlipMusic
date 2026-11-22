package com.youflip.music.data.netease

import android.util.Base64
import java.math.BigInteger
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The web client's "weapi" request encoding (double AES-CBC + textbook RSA of the random key). */
object NeteaseCrypto {
    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val PUB_EXP = "010001"
    private const val MODULUS =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"
    private const val CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private val random = SecureRandom()

    /** @return (params, encSecKey) form fields. */
    fun weapi(json: String): Pair<String, String> {
        val secret = String(CharArray(16) { CHARS[random.nextInt(CHARS.length)] })
        val params = aes(aes(json, PRESET_KEY), secret)
        val rsa = BigInteger(1, secret.reversed().toByteArray())
            .modPow(BigInteger(PUB_EXP, 16), BigInteger(MODULUS, 16))
            .toString(16).padStart(256, '0')
        return params to rsa
    }

    private fun aes(text: String, key: String): String {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(), "AES"), IvParameterSpec(IV.toByteArray()))
        return Base64.encodeToString(c.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
}
