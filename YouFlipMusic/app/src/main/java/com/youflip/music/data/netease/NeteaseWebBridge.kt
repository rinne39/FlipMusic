package com.youflip.music.data.netease

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs every NetEase request inside NetEase's own web player: a hidden WebView keeps music.163.com
 * open and each call is a `fetch` made by that page, so the request carries exactly the browser
 * environment and cookies the web version has (including the session from the official login page).
 *
 * [post] blocks the calling (background) thread until the page answers.
 */
class NeteaseWebBridge(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<Int, CompletableFuture<Pair<Int, String>>>()
    private val ids = AtomicInteger()
    @Volatile private var web: WebView? = null
    @Volatile private var ready = CountDownLatch(1)

    val cookies: CookieManager get() = CookieManager.getInstance()

    /** POSTs a form body to `music.163.com/weapi/<path>` from inside the web page. Returns (HTTP status, body). */
    fun post(path: String, query: String, form: String, timeoutSec: Long = 25): Pair<Int, String> {
        check(Looper.myLooper() != Looper.getMainLooper()) { "NeteaseWebBridge.post must not run on the main thread" }
        ensurePage()
        if (!ready.await(20, TimeUnit.SECONDS)) {
            log("page not ready after 20 s, reloading")
            reload()
            throw BridgeUnavailable("网页版加载超时")
        }
        val id = ids.incrementAndGet()
        val future = CompletableFuture<Pair<Int, String>>()
        pending[id] = future
        val url = org.json.JSONObject.quote("/weapi/$path?$query")
        val body = org.json.JSONObject.quote(form)
        val js = """
            (function(){
              fetch($url, {method:'POST', credentials:'include',
                headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:$body})
              .then(function(r){ return r.text().then(function(t){ $BRIDGE.done($id, r.status, t); }); })
              .catch(function(e){ $BRIDGE.done($id, -1, String(e)); });
            })();
        """.trimIndent()
        main.post { web?.evaluateJavascript(js, null) ?: future.complete(-1 to "WebView gone") }
        val res = try {
            future.get(timeoutSec, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            log("#$id $path timed out")
            throw BridgeUnavailable("请求超时")
        } finally {
            pending.remove(id)
        }
        log("#$id $path -> ${res.first} ${res.second.take(160)}")
        if (res.first <= 0) throw BridgeUnavailable(res.second)
        return res
    }

    /** The web page itself couldn't carry the request (not loaded, fetch failed, timed out). */
    class BridgeUnavailable(why: String) : Exception(why)

    private fun log(msg: String) { android.util.Log.w("FlipMusic", "ncm-web: $msg") }

    /** Sets (or removes, for null) the session cookies the web page sends. */
    fun syncSession(sessionCookie: String?) {
        val cm = cookies
        val current = cm.getCookie(NeteaseClient.ORIGIN).orEmpty()
        if (sessionCookie == null) {
            if (com.youflip.music.data.auth.AccountStore.cookieValue(current, "MUSIC_U").isNullOrBlank()) return
            for (name in SESSION_NAMES) for (domain in listOf(".music.163.com", ".163.com")) {
                cm.setCookie(NeteaseClient.ORIGIN, "$name=; Max-Age=0; Domain=$domain; Path=/")
            }
            cm.flush()
            return
        }
        val want = com.youflip.music.data.auth.AccountStore.cookieValue(sessionCookie, "MUSIC_U")
        if (want != null && com.youflip.music.data.auth.AccountStore.cookieValue(current, "MUSIC_U") == want) return
        sessionCookie.split(';').map { it.trim() }.filter { '=' in it }.forEach {
            cm.setCookie(NeteaseClient.ORIGIN, "$it; Domain=.music.163.com; Path=/")
        }
        cm.flush()
    }

    private fun ensurePage() {
        if (web != null) return
        val created = CountDownLatch(1)
        main.post { if (web == null) create(); created.countDown() }
        created.await(5, TimeUnit.SECONDS)
    }

    private fun reload() {
        ready = CountDownLatch(1)
        main.post { web?.loadUrl(HOME) }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun create() {
        cookies.setAcceptCookie(true)
        web = WebView(app).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = NeteaseClient.UA
            settings.blockNetworkImage = true   // nobody sees this page; skip the pictures
            cookies.setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(Callback(), BRIDGE)
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                    if (m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) log("console: ${m.message()}")
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    log("page finished: $url")
                    if (android.net.Uri.parse(url).host == "music.163.com") ready.countDown()
                }
                override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                    if (request.isForMainFrame) log("page error ${error.errorCode}: ${error.description} (${request.url})")
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest) =
                    request.url.scheme !in setOf("http", "https")
            }
            loadUrl(HOME)
        }
    }

    private inner class Callback {
        @JavascriptInterface
        fun done(id: Int, status: Int, text: String) { pending[id]?.complete(status to text) }
    }

    private companion object {
        const val BRIDGE = "FlipNcm"
        const val HOME = "https://music.163.com/"
        val SESSION_NAMES = listOf("MUSIC_U", "__csrf", "MUSIC_A")
    }
}
