# Building SmoothVPN

This is a complete Android Studio project. The Kotlin is finished; you supply two
compiled native artifacts (Xray-core + tun2socks), then build. Plan ~30–60 min the
first time, mostly waiting on the native builds.

## What's already done (no work needed)
- Share-link engine: `vmess://`, `vless://`, `trojan://`, `ss://` (both forms)
- Subscription fetch + base64 decode + parse
- Xray `config.json` generator (transports: tcp/ws/grpc/h2/kcp/httpupgrade; security: tls/reality; Mux; routing; DNS)
- Room storage, latency testing, full Compose UI, VpnService + tun2socks plumbing

## What you supply
Two compiled binaries that can't ship in source form:

### 1. `app/libs/libv2ray.aar`  — Xray-core mobile bindings
```bash
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
git clone https://github.com/2dust/AndroidLibXrayLite
cd AndroidLibXrayLite
gomobile bind -target=android -androidapi 24 -o libv2ray.aar ./
```
Copy `libv2ray.aar` into `app/libs/`.
(Faster path: grab the `libv2ray.aar` CI artifact from that repo's GitHub Actions.)

### 2. `app/src/main/jniLibs/<abi>/libtun2socks.so` — TUN↔SOCKS bridge
Build badvpn-tun2socks or go-tun2socks with the NDK for each ABI:
```
jniLibs/arm64-v8a/libtun2socks.so
jniLibs/armeabi-v7a/libtun2socks.so
jniLibs/x86_64/libtun2socks.so
```
If your tun2socks build takes a YAML config instead of CLI flags (hev-socks5-tunnel),
edit the `cmd` array in `XrayVpnService.runTun2socks()` to match.

### 3. (optional) geo data
For `geosite:`/`geoip:` routing rules, place `geosite.dat` and `geoip.dat` in
`app/src/main/assets/` and copy them to `filesDir` on first run, or strip the
domestic-direct rules in `XrayConfigBuilder.buildRouting()`.

## Build
**Easiest: let GitHub Actions do it.** Push to `main` (or publish a Release) and the
APK appears under Actions → Artifacts (or attached to the Release). See README.

Local build:
```bash
./gradlew assembleDebug      # installable debug APK
# or
./gradlew assembleRelease    # needs a signing config
```
APK lands in `app/build/outputs/apk/<type>/`.

> The Gradle wrapper jar **is** included, so `./gradlew` works out of the box.

## First run
1. Add a server: copy a share link, tap **Paste link** — or add a **Subscription** URL.
2. Tap **Ping** to measure latency, **Fastest** to auto-select the best server.
3. Tap the big circle → grant the VPN consent dialog → connected.
