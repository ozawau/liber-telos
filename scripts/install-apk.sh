#!/usr/bin/env bash
# 仅安装 APK，绝不推送/覆盖设备上的账本数据库（files/ledger/）。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

./gradlew assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK" >&2
  exit 1
fi

DEVICES="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
if [[ -z "$DEVICES" ]]; then
  echo "No adb devices" >&2
  exit 1
fi

echo "$DEVICES" | while read -r serial; do
  [[ -z "$serial" ]] && continue
  echo "Installing to $serial (APK only, database untouched)"
  adb -s "$serial" install -r -t "$APK"
done

echo "Done. Database path on device: files/ledger/liber_telos.db"
