package com.youflip.music.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.youflip.music.data.auth.AccountStore

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
        "&continue=https%3A%2F%2Fmusic.youtube.com%2F"

/**
 * Google's own sign-in page in a WebView. The user types their credentials into Google's page;
 * the app never sees them. Once Google redirects back to music.youtube.com with a session, we keep
 * only that site's cookie (see [AccountStore]).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onSignedIn: (String) -> Unit, onClose: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    var host by remember { mutableStateOf("accounts.google.com") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var delivered by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { webView?.apply { stopLoading(); destroy() } }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "取消登录") }
            Icon(Icons.Rounded.Lock, null, Modifier.padding(end = 6.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text("登录 YouTube", style = MaterialTheme.typography.titleMedium)
                // Show the real host so the user can verify they are on Google's page.
                Text(host, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (progress < 1f) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Google refuses sign-in from user agents that advertise an embedded WebView.
                    settings.userAgentString = WebSettings.getDefaultUserAgent(ctx)
                        .replace("; wv", "").replace(Regex("Version/\\S+\\s"), "")
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress / 100f }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            host = Uri.parse(url).host.orEmpty()
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            if (delivered || Uri.parse(url).host != "music.youtube.com") return
                            val cookie = cookies.getCookie(AccountStore.ORIGIN)
                            if (AccountStore.hasSession(cookie)) {
                                delivered = true
                                cookies.flush()
                                onSignedIn(cookie)
                            }
                        }
                    }
                    // Start from a clean session so a previous (signed-out) account can't leak in.
                    cookies.removeAllCookies { loadUrl(LOGIN_URL) }
                }
            },
        )
    }
}

/** Clears every WebView cookie, i.e. signs the embedded browser out of Google. */
fun clearWebSession() {
    CookieManager.getInstance().apply { removeAllCookies(null); flush() }
}
