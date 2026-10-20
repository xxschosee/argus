#!/usr/bin/env bash
# Дымовой тест Argus на живом Paper-сервере.
# Поднимает сервер с агрессивными порогами, создаёт нагрузку и проверяет,
# что плагин поймал деградацию, нашёл виновника и записал инцидент.
set -uo pipefail
ROOT="C:/Users/honor/Downloads/argus"
DIR="C:/Users/honor/Downloads/argus-testserver"
export PYTHONIOENCODING=utf-8
RCON="python $ROOT/tools/rcon.py 127.0.0.1 25575 argustest"
LOGGREP="python $ROOT/tools/loggrep.py server.log"
cd "$DIR" || exit 1

FAIL=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAIL=1; }

echo "=== 1. перезапуск со свежим JAR ==="
taskkill //F //IM java.exe >/dev/null 2>&1
sleep 4
cp "$ROOT/build/libs/argus-0.1.0-SNAPSHOT.jar" plugins/ || exit 1
rm -f server.log
java -Xms1G -Xmx1G -jar paper.jar --nogui > server.log 2>&1 &
for i in $(seq 1 40); do
  sleep 3
  grep -q 'Done (' server.log 2>/dev/null && break
done
grep -q 'Done (' server.log && pass "сервер поднялся" || { fail "сервер не поднялся"; tail -20 server.log; exit 1; }

echo "=== 2. плагин загрузился ==="
if $LOGGREP "Enabling Argus" | grep -q "Enabling Argus"; then
  pass "плагин включился"
  $LOGGREP "Enabling Argus" | tail -1
else
  fail "плагин не включился"; tail -30 server.log; exit 1
fi

echo "=== 3. /argus status отвечает сразу ==="
OUT=$($RCON "argus status" 2>&1)
echo "$OUT" | tail -8
echo "$OUT" | grep -q "TPS" && pass "статус отдаёт метрики" || fail "статус пустой"

echo "=== 4. создаём нагрузку ==="
$RCON "forceload add -16 -16 16 16" >/dev/null 2>&1
for i in $(seq 1 45); do $RCON "summon minecraft:pig ~ ~ ~" >/dev/null 2>&1; done
echo "нагрузка создана, ждём срабатывания"
sleep 15

echo "=== 5. в логе есть диагнозы ==="
$LOGGREP "CRITICAL" | tail -4
$LOGGREP "WARNING" | tail -4
if $LOGGREP "CRITICAL" | grep -q "CRITICAL" || $LOGGREP "WARNING" | grep -q "WARNING"; then
  pass "диагноз зафиксирован"
else
  fail "диагнозов нет"
fi

echo "=== 6. виновник найден ==="
$LOGGREP "Виновник" | tail -3
if $LOGGREP "Виновник" | grep -q "Виновник"; then
  pass "виновник найден"
else
  fail "виновник не найден"
fi

echo "=== 7. /argus history показывает инцидент ==="
HIST=$($RCON "argus history" 2>&1)
echo "$HIST" | tail -6
echo "$HIST" | grep -qE "entity_overload|chunk_overload|heap_pressure|high_mspt|tps_degraded" \
  && pass "история заполнена" || fail "история пуста"

echo "=== 8. двойного префикса нет ==="
if $LOGGREP "Argus" | grep -q "\[Argus\] \[Argus\]"; then
  fail "в логе двойной префикс [Argus] [Argus]"
else
  pass "префикс лога корректный"
fi

echo "=== 9. при выздоровлении приходит событие восстановления ==="
$LOGGREP "Проблема ушла" | tail -2
$LOGGREP "Проблема ушла" | grep -q "Проблема ушла" && pass "восстановление отмечено" || fail "события восстановления нет"

echo "============================================="
if [ "$FAIL" -eq 0 ]; then echo "СМОК-ТЕСТ ПРОЙДЕН"; else echo "СМОК-ТЕСТ ПРОВАЛЕН"; fi
exit $FAIL
