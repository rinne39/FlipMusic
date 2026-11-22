package com.youflip.music.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** NetEase Cloud Music sign-in: always NetEase's own login page (no in-app form). */
@Composable
fun NeteaseLoginScreen(vm: MainViewModel, onClose: () -> Unit) {
    NeteaseWebLogin(onSignedIn = vm::onNeteaseWebSignedIn, onClose = onClose)
}

private const val NCM_WEB_LOGIN = "https://y.music.163.com/m/login"

/**
 * NetEase's own mobile login page in a WebView (phone + password / SMS code, plus any verification
 * NetEase asks for). The app never sees the password; once the page has a session we keep only the
 * music.163.com cookie.
 */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NeteaseWebLogin(onSignedIn: (String) -> Unit, onClose: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    var host by remember { mutableStateOf("y.music.163.com") }
    var webView by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var delivered by remember { mutableStateOf(false) }
    val cookies = remember { android.webkit.CookieManager.getInstance() }

    fun check() {
        if (delivered) return
        val c = cookies.getCookie(NCM_ORIGIN) ?: return
        if (com.youflip.music.data.auth.AccountStore.cookieValue(c, "MUSIC_U").isNullOrBlank()) return
        delivered = true
        cookies.flush()
        onSignedIn(c)
    }
    // The login page is a single-page app: it may sign in without navigating, so poll the cookie too.
    LaunchedEffect(Unit) { while (!delivered) { delay(800); check() } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { webView?.apply { stopLoading(); destroy() } } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "取消登录") }
            Icon(androidx.compose.material.icons.Icons.Rounded.Lock, null, Modifier.padding(end = 6.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text("登录网易云音乐", style = MaterialTheme.typography.titleMedium)
                // Show the real host so the user can check this is NetEase's page.
                Text(host, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (progress < 1f) androidx.compose.material3.LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
        }
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: android.webkit.WebView, newProgress: Int) { progress = newProgress / 100f }
                    }
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageStarted(view: android.webkit.WebView, url: String, favicon: android.graphics.Bitmap?) {
                            host = android.net.Uri.parse(url).host.orEmpty()
                        }
                        override fun shouldOverrideUrlLoading(view: android.webkit.WebView, request: android.webkit.WebResourceRequest): Boolean {
                            // Don't hand "open in app" links (orpheus://, intent://) to the system.
                            return request.url.scheme !in setOf("http", "https")
                        }
                        override fun onPageFinished(view: android.webkit.WebView, url: String) = check()
                    }
                    // Drop any earlier NetEase web session so the user signs in fresh.
                    for (name in listOf("MUSIC_U", "__csrf", "MUSIC_A")) {
                        for (domain in listOf(".music.163.com", ".163.com")) {
                            cookies.setCookie(NCM_ORIGIN, "$name=; Max-Age=0; Domain=$domain; Path=/")
                        }
                        cookies.setCookie(NCM_ORIGIN, "$name=; Max-Age=0; Path=/")
                    }
                    cookies.flush()
                    loadUrl(NCM_WEB_LOGIN)
                }
            },
        )
    }
}

private const val NCM_ORIGIN = "https://music.163.com"
