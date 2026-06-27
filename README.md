# SmoothVPN

## ⬇️ Download

| | |
|---|---|
| **Full APK** (real VPN) | **[Download](https://github.com/rezaigh/SmoothVPN/releases/latest/download/SmoothVPN.apk)** |
| **Demo APK** (UI only, always builds) | **[Download](https://github.com/rezaigh/SmoothVPN/releases/latest/download/SmoothVPN-demo.apk)** |

On install, tap **More details → Install anyway** if Play Protect warns (normal for sideloaded apps).


[![Build APK](https://github.com/rezaigh/SmoothVPN/actions/workflows/build.yml/badge.svg)](https://github.com/rezaigh/SmoothVPN/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

An open-source Android VPN client for **Xray / V2Ray** configs and subscription
links. Same proven architecture as v2rayNG — `VpnService` + TUN + Xray-core (via
libv2ray) + tun2socks — with a Jetpack Compose UI and a from-scratch, correct
config/subscription engine.

> Replace `rezaigh` in the badge/clone URLs if your GitHub username or repo name differs.

## 📥 Get the APK — built for you by GitHub Actions
The app ships in two flavors, and CI builds both on every push:

| Flavor | Artifact | What it is |
|--------|----------|------------|
| **mock** | `SmoothVPN-demo-apk` | Full UI + config/subscription engine, **no native binaries**. Always builds → always installable. Great for trying the app and showing it off. |
| **full** | `SmoothVPN-full-apk` | Real working tunnel (Xray-core + tun2socks). Built best-effort; needs the native compile to succeed. |

**To download:** repo → **Actions** tab → latest run → **Artifacts**. Or publish a
**Release** and the APK(s) attach to it automatically.

On your phone: enable *Install unknown apps* for your browser/files app, open the
APK, install. (The demo build installs as a separate app — id suffix `.demo`.)

## ✨ Features
- **Protocols:** VMess, VLESS (incl. REALITY + XTLS-Vision), Trojan, Shadowsocks
- **Transports:** TCP, WebSocket, gRPC, HTTP/2, mKCP, HTTPUpgrade — with TLS/uTLS
- **Subscription links:** add a URL, auto-decode (base64 or plain), one-tap refresh
- **Paste-to-import** any share link from the clipboard
- **Latency ranking + auto-fastest** server selection
- **Full-device tunnel** via Android's VpnService
- **Smart routing:** bypass LAN, block ads, optional domestic-direct (e.g. `.ir`)

## 🏗 Architecture
```
 System (all apps) --TUN fd--> tun2socks --SOCKS 127.0.0.1:10808--> Xray-core --> proxy server
```
The `mock` flavor swaps the native Xray-core for a tiny Kotlin stub
(`app/src/mock/java/libv2ray/Libv2ray.kt`) so everything compiles and runs with
zero native dependencies. The `full` flavor links the real `libv2ray.aar`.

| Layer | Files |
|-------|-------|
| Engine | `core/` — OutboundParser, XrayConfigBuilder, SubscriptionParser, RoutingOptions, Profile |
| Storage | `data/` — Room DB, DAOs, ProfileRepository, SettingsStore |
| Network | `net/` — SubscriptionUpdater, LatencyTester |
| Tunnel | `service/` — XrayVpnService, GeoAssets |
| UI | `ui/` — Compose MainActivity, MainViewModel, Theme |

## 🛠 Building locally (optional)
```bash
./gradlew assembleMockDebug   # installable demo, no native deps
./gradlew assembleFullDebug   # real tunnel — needs app/libs/libv2ray.aar + jniLibs/*/libtun2socks.so
```
See [BUILD.md](BUILD.md) for how to produce the native binaries.

## ⚖️ License & legitimacy
MIT (see [LICENSE](LICENSE)). Xray-core, libv2ray, and tun2socks carry their own
licenses (MPL-2.0 / GPL family) — review them before distributing binaries. This is
a privacy / censorship-circumvention tool for reaching the open internet; use it in
line with the laws that apply to you.
