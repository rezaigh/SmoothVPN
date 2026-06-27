# Drop libv2ray.aar here

This file is the Xray-core mobile binding (AndroidLibXrayLite). It is NOT shipped
in this repo because it's a compiled Go artifact (~30 MB). Get it one of two ways:

## Option A — build it (recommended, reproducible)
Requires Go 1.21+ and the Android NDK.

    go install golang.org/x/mobile/cmd/gomobile@latest
    gomobile init
    git clone https://github.com/2dust/AndroidLibXrayLite
    cd AndroidLibXrayLite
    # pulls Xray-core as a module; then:
    gomobile bind -target=android -androidapi 24 -o libv2ray.aar ./

Copy the resulting `libv2ray.aar` into this folder.

## Option B — reuse a prebuilt one
The AndroidLibXrayLite GitHub Actions produce `libv2ray.aar` as a CI artifact on
every push. Download from the repo's Actions tab and place it here.

The Kotlin in app/src/main/java/com/smoothvpn/service/XrayVpnService.kt is written
against this library's API: `Libv2ray.newV2RayPoint(...)`, `V2RayPoint`,
`V2RayVPNServiceSupportsSet`.
