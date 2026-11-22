package com.youflip.music.ui

import androidx.compose.runtime.remember
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.font.FontWeight
import com.youflip.music.ui.components.Artwork
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.youflip.music.data.MusicSource

@Composable
fun SettingsContent(vm: MainViewModel, compact: Boolean) {
    val autoplay by vm.autoplay.collectAsState()
    val hq by vm.highQuality.collectAsState()
    val keepOn by vm.coverKeepScreenOn.collectAsState()
    val marquee by vm.coverMarquee.collectAsState()
    val pad = if (compact) 14.dp else 20.dp

    val signedIn by vm.signedIn.collectAsState()
    val account by vm.account.collectAsState()
    val source by vm.source.collectAsState()
    val ncm = source == MusicSource.NETEASE

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Header("音乐来源", pad)
        MusicSource.entries.forEach { m ->
            Row(Modifier.fillMaxWidth().clickable { vm.setMusicSource(m) }.padding(horizontal = pad, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = source == m, onClick = { vm.setMusicSource(m) })
                Text(m.label)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        // Switching source, signing in or out, and the profile arriving all cross-fade (and resize) in place.
        androidx.compose.animation.AnimatedContent(
            targetState = Triple(ncm, signedIn, account?.name),
            transitionSpec = {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 80)) +
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(320)) { it / 6 })
                    .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)))
                    .using(androidx.compose.animation.SizeTransform(clip = false))
            },
            label = "account",
        ) { (isNcm, isIn, name) ->
            Column(Modifier.fillMaxWidth()) {
                Header(if (isNcm) "网易云音乐账号" else "YouTube 账号", pad)
                if (isIn) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(account?.avatar, Modifier.size(44.dp), CircleShape)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(name ?: "已登录", fontWeight = FontWeight.SemiBold)
                            account?.handle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        OutlinedButton(onClick = vm::signOut) { Text("退出") }
                    }
                } else {
                    Button(onClick = vm::openLogin, modifier = Modifier.padding(horizontal = pad, vertical = 4.dp)) { Text(if (isNcm) "登录网易云音乐" else "登录 YouTube 账号") }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Header("外观", pad)
        val theme by vm.themeMode.collectAsState()
        com.youflip.music.data.ThemeMode.entries.forEach { m ->
            Row(Modifier.fillMaxWidth().clickable { vm.setThemeMode(m) }.padding(horizontal = pad, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = theme == m, onClick = { vm.setThemeMode(m) })
                Text(m.label)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Header("外屏镜头区域", pad)
        val strip by vm.cameraStrip.collectAsState()
        com.youflip.music.data.CameraStrip.entries.forEach { m ->
            Row(Modifier.fillMaxWidth().clickable { vm.setCameraStrip(m) }.padding(horizontal = pad, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = strip == m, onClick = { vm.setCameraStrip(m) })
                Text(m.label)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Toggle("外屏播放时保持亮屏", "外屏上正在播放时不自动熄屏", keepOn, vm::setCoverKeepScreenOn, pad)
        Toggle("显示不全时自动滚动", "歌名、歌手、专辑过长时滚动显示", marquee, vm::setCoverMarquee, pad)
        Toggle("自动电台续播", "队列快播完时自动加入相似歌曲", autoplay, vm::setAutoplay, pad)
        Toggle("高音质", "关闭后使用低码率以节省流量", hq, vm::setHighQuality, pad)
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
        val update by vm.update.collectAsState()
        // Tap to check for updates (player.pjsk.store); a newer build shows here and opens the update dialog.
        Row(Modifier.fillMaxWidth().clickable { vm.checkUpdate(manual = true) }.padding(horizontal = pad, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("版本")
                Text(
                    update.info?.let { "有新版本 ${it.versionName}" } ?: "点按检查更新",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (update.info != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(version, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // On the cover screen the cameras sit in the bottom-right corner: keep this last text to the left
        // half so it wraps beside them instead of running underneath.
        Text(
            "Flip Music 是非官方第三方客户端，与 Google / YouTube / 网易云音乐 / Samsung 无关。",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = (if (compact) Modifier.fillMaxWidth(0.48f) else Modifier).padding(horizontal = pad, vertical = 8.dp),
        )
    }
}

@Composable
private fun Header(text: String, pad: androidx.compose.ui.unit.Dp) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = pad, vertical = 8.dp))

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, pad: androidx.compose.ui.unit.Dp) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = pad, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
