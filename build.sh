#!/bin/bash
# 民宿记账 APK —— 不用 Gradle / Android Studio，直接用 SDK 命令行工具打一个 APK。
# 依赖：JDK(javac/keytool) + build-tools(aapt2/d8/zipalign/apksigner) + platform(android.jar)
set -e

PROJ="$(cd "$(dirname "$0")" && pwd)"
SDK="${SDK:-/tmp/and}"
BT="$(ls -d $SDK/bt/*/ | head -1)"          # 例如 /tmp/and/bt/android-14/
AJAR="$(ls $SDK/pl/android-*/android.jar | head -1)"
OUT="$PROJ/build"
NAME="homestay-ledger"

echo "== 1/7 生成图标 =="
python3 "$PROJ/gen_icon.py"

echo "== 2/7 清理 =="
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/gen" "$OUT/res"

echo "== 3/7 aapt2 compile 资源 =="
"$BT/aapt2" compile --dir "$PROJ/res" -o "$OUT/res.zip"

echo "== 4/7 aapt2 link 打包资源 + manifest =="
"$BT/aapt2" link \
    -o "$OUT/$NAME.unaligned.apk" \
    -I "$AJAR" \
    --manifest "$PROJ/AndroidManifest.xml" \
    --java "$OUT/gen" \
    --min-sdk-version 21 \
    --target-sdk-version 34 \
    --version-code 1 --version-name 1.0 \
    "$OUT/res.zip"

echo "== 5/7 javac 编译 Java =="
find "$PROJ/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac --release 8 -encoding UTF-8 -nowarn \
      -classpath "$AJAR" \
      -d "$OUT/classes" \
      @"$OUT/sources.txt"

echo "== 6/7 d8 转 dex =="
mkdir -p "$OUT/dex"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --release --min-api 21 --lib "$AJAR" \
      --output "$OUT/dex" @"$OUT/classes.txt"

python3 - "$OUT/$NAME.unaligned.apk" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile, shutil
apk, dex = sys.argv[1], sys.argv[2]
tmp = apk + ".tmp"
shutil.copy(apk, tmp)
with zipfile.ZipFile(tmp, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
shutil.move(tmp, apk)
print("   classes.dex 已写入 apk")
PY

echo "== 7/7 zipalign + 签名 =="
"$BT/zipalign" -f -p 4 "$OUT/$NAME.unaligned.apk" "$OUT/$NAME.apk"

KS="$PROJ/debug.keystore"
if [ ! -f "$KS" ]; then
    keytool -genkeypair -v -keystore "$KS" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --v1-signing-enabled true --v2-signing-enabled true \
    "$OUT/$NAME.apk"
"$BT/apksigner" verify --print-certs "$OUT/$NAME.apk" | head -4

echo
echo "✅ 产物：$OUT/$NAME.apk"
ls -lh "$OUT/$NAME.apk"
