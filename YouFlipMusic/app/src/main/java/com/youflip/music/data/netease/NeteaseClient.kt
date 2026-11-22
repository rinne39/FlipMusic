package com.youflip.music.data.netease

import com.youflip.music.data.NotSignedInException
import com.youflip.music.data.at
import com.youflip.music.data.auth.AccountStore
import com.youflip.music.data.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** An error reported by NetEase in the response body (`code` != 200). */
class NeteaseApiException(val code: Int, message: String) : Exception(message)

/**
 * NetEase Cloud Music web ("weapi") client. Every request is sent by NetEase's own web page through
 * [web] (see [NeteaseWebBridge]), never by the app's HTTP stack. Blocking — call from Dispatchers.IO.
 * The session cookie lives in [account] (Keystore-encrypted) and is only handed to music.163.com.
 */
class NeteaseClient(private val web: NeteaseWebBridge, private val http: okhttp3.OkHttpClient, private val account: AccountStore) {
    private val json = Json { ignoreUnknownKeys = true }

    class Response(val body: JsonElement, val setCookies: List<String>)

    fun call(path: String, data: JsonObject = JsonObject(emptyMap()), requireAuth: Boolean = false): JsonElement =
        raw(path, data, requireAuth).body

    fun raw(path: String, data: JsonObject = JsonObject(emptyMap()), requireAuth: Boolean = false): Response {
        if (requireAuth && !account.isSignedIn) throw NotSignedInException(NOT_SIGNED_IN)
        web.syncSession(account.cookie.value)
        val csrf = AccountStore.cookieValue(web.cookies.getCookie(ORIGIN).orEmpty(), "__csrf").orEmpty()
        val payload = buildJsonObject {
            data.forEach { (k, v) -> put(k, v) }
            put("csrf_token", csrf)
        }
        val (params, encSecKey) = NeteaseCrypto.weapi(payload.toString())
        fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
        val (status, text, setCookies) = try {
            val (st, tx) = web.post(path, "csrf_token=${enc(csrf)}", "params=${enc(params)}&encSecKey=${enc(encSecKey)}")
            // The page's cookie jar already took any Set-Cookie; hand back what it now holds.
            Triple(st, tx, web.cookies.getCookie(ORIGIN).orEmpty().split(';').map { it.trim() }.filter { '=' in it })
        } catch (e: NeteaseWebBridge.BridgeUnavailable) {
            // Only when the web page itself can't carry the request: send it directly so the app keeps working.
            android.util.Log.w("FlipMusic", "ncm-web unavailable (${e.message}), direct request for $path")
            direct(path, csrf, params, encSecKey, account.cookie.value)
        }
        val body = runCatching { json.parseToJsonElement(text) }.getOrElse { throw NeteaseApiException(status, "网易云返回了无法识别的数据") }
        val code = body.at("code").str?.toIntOrNull()
        if (code == 301 && account.isSignedIn) throw NotSignedInException(NOT_SIGNED_IN)
        return Response(body, setCookies)
    }

    private fun direct(path: String, csrf: String, params: String, encSecKey: String, cookie: String?): Triple<Int, String, List<String>> {
        val req = okhttp3.Request.Builder()
            .url("$ORIGIN/weapi/$path?csrf_token=$csrf")
            .post(okhttp3.FormBody.Builder().add("params", params).add("encSecKey", encSecKey).build())
            .header("Referer", ORIGIN).header("Origin", ORIGIN).header("User-Agent", UA)
            .header("Cookie", listOfNotNull("__remember_me=true", "os=pc", cookie?.takeIf { it.isNotBlank() }).joinToString("; "))
            .build()
        http.newCall(req).execute().use { r -> return Triple(r.code, r.body!!.string(), r.headers("Set-Cookie")) }
    }

    companion object {
        const val ORIGIN = "https://music.163.com"
        const val NOT_SIGNED_IN = "需要登录网易云音乐"
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        /** Turns Set-Cookie headers into a "name=value; name2=value2" cookie string (skipping deletions). */
        fun cookieString(setCookies: List<String>): String =
            setCookies.map { it.substringBefore(';').trim() }
                .filter { it.contains('=') && it.substringAfter('=').isNotBlank() && it.substringAfter('=') != "deleted" }
                .associateBy { it.substringBefore('=') }.values.joinToString("; ")
    }
}
