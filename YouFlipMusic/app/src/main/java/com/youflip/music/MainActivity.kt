package com.youflip.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Text
import androidx.compose.material.icons.rounded.Close
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.youflip.music.ui.LoginScreen
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import com.youflip.music.ui.MainViewModel
import com.youflip.music.ui.cover.CoverApp
import com.youflip.music.ui.main.MainApp
import com.youflip.music.ui.rememberUseCoverLayout
import com.youflip.music.ui.theme.YouFlipTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bar icon colours follow the chosen theme (set again in setContent once it is known).
        enableEdgeToEdge(SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        // Draw under the cover screen's camera cut-out even when the navigation bar is hidden
        // (with shortEdges the system shrinks the window above the cameras in that state).
        if (Build.VERSION.SDK_INT >= 30) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val themeMode by vm.themeMode.collectAsState()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val dark = when (themeMode) { com.youflip.music.data.ThemeMode.SYSTEM -> systemDark; com.youflip.music.data.ThemeMode.LIGHT -> false; com.youflip.music.data.ThemeMode.DARK -> true }
            androidx.compose.runtime.LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(if (dark) 0xFF0B0B0F.toInt() else Color.WHITE))
            }
            YouFlipTheme(dark) {
                val keepOn by vm.coverKeepScreenOn.collectAsState()
                val playing by vm.player.state.collectAsState()
                // Recomputed on every configuration change, i.e. when the phone is folded/unfolded.
                val cover = rememberUseCoverLayout()

                DisposableEffect(cover, keepOn, playing.isPlaying) {
                    if (cover && keepOn && playing.isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { }
                }

                // Surface provides the light content colour for all text/icons in both layouts.
                val showLogin by vm.showLogin.collectAsState()
                LaunchedEffect(Unit) {
                    vm.messages.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
                }
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box {
                        Crossfade(cover, label = "layout") { isCover ->
                            if (isCover) CoverApp(vm) else MainApp(vm)
                        }
                        com.youflip.music.ui.UpdateDialog(vm)
                        if (showLogin) {
                            BackHandler { vm.closeLogin() }
                            val src by vm.source.collectAsState()
                            if (src == com.youflip.music.data.MusicSource.NETEASE) com.youflip.music.ui.NeteaseLoginScreen(vm, onClose = vm::closeLogin)
                            else LoginScreen(onSignedIn = vm::onSignedIn, onClose = vm::closeLogin)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        vm.handleLink(text)
    }
}
