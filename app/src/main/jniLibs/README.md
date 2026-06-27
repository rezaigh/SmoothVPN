# Drop tun2socks here, one .so per ABI

Layout expected by the build:

    jniLibs/arm64-v8a/libtun2socks.so
    jniLibs/armeabi-v7a/libtun2socks.so
    jniLibs/x86_64/libtun2socks.so

tun2socks bridges the TUN device into Xray's local SOCKS port. The service launches
it as a native process and passes the TUN fd over a LocalSocket (see
XrayVpnService.runTun2socks / sendTunFd).

Recommended source: badvpn-tun2socks or go-tun2socks, compiled with the Android NDK.
v2rayNG ships a working build you can study at:
  https://github.com/2dust/AndroidLibXrayLite (and the v2rayNG repo's jniLibs)

If your tun2socks build uses different CLI flags (e.g. hev-socks5-tunnel uses a
YAML config instead of flags), adjust the `cmd` array in XrayVpnService.runTun2socks().
