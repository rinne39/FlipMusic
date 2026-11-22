# Flip Music

专为小折叠设备外屏模式适配的音乐播放器。

官网与下载：<https://player.pjsk.store>

非官方第三方客户端，与 Google、YouTube、网易云音乐、Samsung 无关。

## 功能

- **不同外屏都合适**：按外屏窗口的比例（1:1、横向、竖向）分别排版，不是把大屏界面缩小。
- **三星 Galaxy Z Flip7 / Flip8**：1:1 外屏铺满显示；播放进度沿着摄像头绕成跑道形进度条。
- **双平台**：YouTube Music 与网易云音乐。两边都在官方页面登录，App 不保存密码，登录状态用 Android Keystore 加密保存在本机。
- **网易云**：所有请求都由网易云网页版页面发出（隐藏的 WebView 里 `fetch`），不登录时与网页版游客模式完全一样，需要会员的歌必须登录有会员的账号才能播放。
- **App 内更新**：启动时检查 `https://player.pjsk.store/update.json`，下载后校验 SHA-256 再调起系统安装。

## 目录

| 路径 | 内容 |
| --- | --- |
| `YouFlipMusic/` | Android App（Kotlin + Jetpack Compose + Media3） |
| `site/` | 官网页面，以及 `site/deploy/nginx.conf` 服务器配置 |
| `release.sh` | 编译、生成 `update.json`、发布到服务器 |

## 构建

需要 JDK 17 和 Android SDK 36。

```bash
cd YouFlipMusic
./gradlew :app:assembleRelease
```

输出 `app/build/outputs/apk/release/app-release.apk`。目前用 debug 证书签名；自己分发请换成自己的签名证书（不要把证书提交到仓库）。

## 许可证

本项目以 [GPL-3.0](LICENSE) 开源。

用到的开源项目：

- [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor)（GPL-3.0）：YouTube 音频流解析
- [Android Jetpack](https://developer.android.com/jetpack/androidx)：Compose、Media3 / ExoPlayer、DataStore、WindowManager、Palette 等（Apache-2.0）
- [Kotlin](https://github.com/JetBrains/kotlin)、[kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)、[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)（Apache-2.0）
- [OkHttp](https://github.com/square/okhttp)（Apache-2.0）
- [Coil](https://github.com/coil-kt/coil)（Apache-2.0）
- [Material Icons](https://fonts.google.com/icons)（Apache-2.0）

参考资料：

- [NeteaseCloudMusicApi](https://github.com/Binaryify/NeteaseCloudMusicApi)（MIT）：网易云网页版接口与加密方式
- [LRCLIB](https://lrclib.net)：YouTube Music 歌曲的歌词来源

反馈：233.rinne@gmail.com
