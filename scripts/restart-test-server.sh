#!/usr/bin/env bash
# Перезапуск тестового сервера с RCON и агрессивными порогами Argus,
# чтобы проверить весь конвейер: детект -> лечение -> алерт.
set -uo pipefail
DIR="C:/Users/honor/Downloads/argus-testserver"
cd "$DIR" || exit 1

echo "=== останавливаем старый сервер ==="
taskkill //F //IM java.exe >/dev/null 2>&1 && echo "killed" || echo "нечего убивать"
sleep 4

echo "=== настройки сервера ==="
cat > server.properties <<'PROPS'
online-mode=false
level-type=flat
max-players=10
spawn-protection=0
view-distance=4
simulation-distance=4
sync-chunk-writes=false
enable-rcon=true
rcon.port=25575
rcon.password=argustest
broadcast-rcon-to-ops=false
PROPS

echo "=== агрессивные пороги Argus для проверки ==="
PLUGIN_CFG="plugins/Argus/config.yml"
if [ ! -f "$PLUGIN_CFG" ]; then
  echo "конфиг не найден: $PLUGIN_CFG"
  ls -la plugins/ 2>/dev/null
else
  cat > "$PLUGIN_CFG" <<'CFG'
enabled: true
sample:
  interval_ticks: 20
thresholds:
  warn_mspt: 5.0
  critical_mspt: 8.0
  warn_tps: 19.5
  critical_tps: 19.0
  warn_heap_ratio: 0.50
  critical_heap_ratio: 0.60
  max_entities: 30
  max_chunks: 40
  samples_before_alarm: 2
heal_actions:
  tps_degraded: [notify_only]
  high_mspt: [notify_only]
  heap_pressure: [notify_only]
  entity_overload: [notify_only]
  chunk_overload: [notify_only]
healer:
  autosave_restore_ticks: 1200
hotspot:
  chunks_per_tick: 40
  top_chunks: 5
incident_log_size: 50
telegram:
  bot_token: ""
  chat_id: 0
  timeout_seconds: 10
notify:
  language: ru
  server_name: "Argus test server"
CFG
  echo "пороги записаны"
fi

echo "=== старт ==="
rm -f server.log
java -Xms1G -Xmx1G -Dfile.encoding=UTF-8 -jar paper.jar --nogui > server.log 2>&1 &
echo "pid: $!"
for i in $(seq 1 40); do
  sleep 3
  if grep -q 'Done (' server.log 2>/dev/null; then
    echo "сервер поднялся за $((i*3)) с"
    break
  fi
done
grep -E "Argus|Done \(" server.log | tail -6
