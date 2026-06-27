#!/usr/bin/env bash
# Builds badvpn-tun2socks for each Android ABI and drops the binaries into
# app/src/main/jniLibs/<abi>/libtun2socks.so (the v2rayNG convention: an
# executable shipped under a lib*.so name so it lands in nativeLibraryDir).
#
# This is the single fiddliest part of the whole build. It usually works as-is,
# but if a CI run fails HERE specifically, it does NOT block the APK — the app
# still builds and installs; only the live tunnel needs this .so. You can also
# drop a prebuilt libtun2socks.so into each jniLibs/<abi>/ and delete this step.
set -euo pipefail

NDK="${ANDROID_NDK_HOME:?ANDROID_NDK_HOME not set}"
TOOLCHAIN="$NDK/build/cmake/android.toolchain.cmake"
API=24
ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")

ROOT="$(pwd)"
WORK="$(mktemp -d)"
cd "$WORK"

git clone --depth=1 https://github.com/ambrop72/badvpn.git
cd badvpn

for ABI in "${ABIS[@]}"; do
  echo "==== building tun2socks for $ABI ===="
  rm -rf "build-$ABI" && mkdir "build-$ABI" && cd "build-$ABI"
  cmake .. \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="android-$API" \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_NOTHING_BY_DEFAULT=1 \
    -DBUILD_TUN2SOCKS=1
  make tun2socks -j"$(nproc)"

  DEST="$ROOT/app/src/main/jniLibs/$ABI"
  mkdir -p "$DEST"
  # badvpn outputs the binary at tun2socks/tun2socks
  cp "tun2socks/tun2socks" "$DEST/libtun2socks.so"
  echo "  -> $DEST/libtun2socks.so"
  cd ..
done

echo "tun2socks build complete."
