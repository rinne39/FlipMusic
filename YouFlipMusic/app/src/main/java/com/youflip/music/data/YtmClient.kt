package com.youflip.music.data

import com.youflip.music.data.auth.AccountStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

class NotSignedInException(message: String = "需要登录 YouTube 账号") : IllegalStateException(message)

/** Minimal YouTube Music InnerTube client. Blocking — call from Dispatchers.IO. */
class YtmClient(private val http: OkHttpClient, private val account: AccountStore) {
    private val json = Json { ignoreUnknownKeys = true }

    enum class Client(val body: String, val userAgent: String?) {
        WEB_REMIX("""{"clientName":"WEB_REMIX","clientVersion":"1.20251110.01.00","hl":"$HL","gl":"$GL"}""", WEB_UA),
        ANDROID_MUSIC(
            """{"clientName":"ANDROID_MUSIC","clientVersion":"7.27.52","androidSdkVersion":34,"hl":"$HL","gl":"$GL"}""",
            "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14) gzip",
        ),
    }

    /**
     * @param body extra top-level fields, e.g. `"browseId":"FEmusic_home"`.
     * @param auth send the signed-in session (WEB_REMIX only). With [requireAuth], fails when signed out.
     */
    fun call(
        endpoint: String,
        body: JsonObject = JsonObject(emptyMap()),
        client: Client = Client.WEB_REMIX,
        auth: Boolean = true,
        requireAuth: Boolean = false,
    ): JsonElement {
        if (requireAuth && !account.isSignedIn) throw NotSignedInException()
        val payload = buildJsonObject {
            put("context", json.parseToJsonElement("""{"client":${client.body}}"""))
            body.forEach { (k, v) -> put(k, v) }
        }
        val url = "https://music.youtube.com/youtubei/v1/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("prettyPrint", "false").build()
        val req = Request.Builder().url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Origin", AccountStore.ORIGIN)
            .header("Referer", "${AccountStore.ORIGIN}/")
            .apply { client.userAgent?.let { header("User-Agent", it) } }
            .apply { if (auth && client == Client.WEB_REMIX) account.authHeaders().forEach { (k, v) -> header(k, v) } }
            .build()
        http.newCall(req).execute().use { r ->
            if (r.code == 401 || r.code == 403) {
                if (requireAuth) throw NotSignedInException()
            }
            val text = r.body!!.string()
            // Diagnostics only: endpoint, status, and whether YouTube treated the request as signed in. Never the cookie.
            val loggedIn = Regex("\"key\":\"logged_in\",\"value\":\"(\\d)\"").find(text)?.groupValues?.get(1)
            android.util.Log.i("YtmClient", "$endpoint ${body["browseId"] ?: if (body.containsKey("continuation")) "(continuation)" else ""} " +
                "HTTP ${r.code} auth=${auth && client == Client.WEB_REMIX && account.isSignedIn} logged_in=$loggedIn bytes=${text.length}")
            if (!r.isSuccessful) android.util.Log.w("YtmClient", "$endpoint error: ${text.take(300)}")
            check(r.isSuccessful) { "InnerTube $endpoint HTTP ${r.code}" }
            return json.parseToJsonElement(text)
        }
    }

    fun browse(browseId: String, requireAuth: Boolean = false): JsonElement =
        call("browse", buildJsonObject { put("browseId", browseId) }, requireAuth = requireAuth)

    fun continuation(token: String, requireAuth: Boolean = false): JsonElement =
        call("browse", buildJsonObject { put("continuation", token) }, requireAuth = requireAuth)

    companion object {
        private val locale: Locale = Locale.getDefault()
        /** InnerTube wants "zh-CN"/"zh-TW", not script tags like "zh-Hans-CN". */
        private val HL = when {
            locale.language != "zh" -> locale.toLanguageTag().let { t -> if (locale.script.isNotEmpty()) "${locale.language}-${locale.country}".trimEnd('-') else t }
            locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO") -> "zh-TW"
            else -> "zh-CN"
        }
        /** YouTube isn't offered in mainland China and rejects gl=CN with HTTP 400; use Hong Kong instead. */
        private val GL = when (val c = locale.country) {
            "", "CN" -> if (locale.language == "zh") "HK" else "US"
            else -> c
        }
        private const val WEB_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
