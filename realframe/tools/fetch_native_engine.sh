#!/usr/bin/env bash
# Downloads C++ sources to compile a truly on-device CPU diffusion engine.
# Does NOT download any model weights, and does NOT ship QNN/NPU support.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${ROOT}/android/app/src/main/cpp/vendor/stable-diffusion.cpp"
PIN="b9d7176bf88fe465576e0aafbe47ba253ffcfc2c"
if [[ ! -d "$TARGET/.git" ]]; then
    mkdir -p "$(dirname "$TARGET")"
    git clone --filter=blob:none --no-checkout https://github.com/leejet/stable-diffusion.cpp.git "$TARGET"
fi
git -C "$TARGET" fetch --no-tags --depth=1 origin "$PIN"
git -C "$TARGET" checkout --force --detach "$PIN"
git -C "$TARGET" submodule update --init --depth=1 ggml
actual="$(git -C "$TARGET" rev-parse HEAD)"
test "$actual" = "$PIN"
test -f "$TARGET/ggml/CMakeLists.txt"
echo "Pinned stable-diffusion.cpp ready: $actual"
