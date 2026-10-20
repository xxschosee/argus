#!/usr/bin/env bash
# Обёртка: сама проверка живёт в validate-plugin-layout.mjs (Node нужен и в Windows, и в CI).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec node "$ROOT/scripts/validate-plugin-layout.mjs" "$@"
