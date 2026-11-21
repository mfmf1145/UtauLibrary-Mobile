#!/usr/bin/env bash
# 生成 UtauMarket Mobile 的 release 自签名 keystore。
# 用法：bash tools/make-keystore.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/keystore"
OUT="$OUT_DIR/utaumarket.jks"

PASS="${UTM_STORE_PASSWORD:-utaumarket}"
ALIAS="${UTM_KEY_ALIAS:-utaumarket}"

mkdir -p "$OUT_DIR"

if [ -f "$OUT" ]; then
  echo "keystore 已存在：$OUT"
  exit 0
fi

keytool -genkeypair \
  -keystore "$OUT" \
  -storetype JKS \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10950 \
  -storepass "$PASS" \
  -keypass "$PASS" \
  -dname "CN=UtauMarket Mobile, OU=Companion Tool, O=UtauMarket, L=, ST=, C="

echo "已生成：$OUT"
echo "storePassword / keyPassword 默认 utaumarket，可用环境变量 UTM_STORE_PASSWORD / UTM_KEY_PASSWORD 覆盖。"
echo "注意：正式分发请替换为你自己的密钥并妥善保存，丢失将无法覆盖安装更新。"
