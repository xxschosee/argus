#!/usr/bin/env bash
# Локальный тестовый Paper-сервер для проверки плагина Argus.
set -uo pipefail
DIR="C:/Users/honor/Downloads/argus-testserver"
mkdir -p "$DIR/plugins"
cd "$DIR" || exit 1

echo "=== ищем последний билд Paper 1.21.11 ==="
BUILD_JSON=$(curl -s "https://api.papermc.io/v2/projects/paper/versions/1.21.11/builds")
LATEST=$(echo "$BUILD_JSON" | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{const b=JSON.parse(s).builds;console.log(b[b.length-1].build)})")
echo "build: $LATEST"

URL="https://api.papermc.io/v2/projects/paper/versions/1.21.11/builds/$LATEST/downloads/paper-1.21.11-$LATEST.jar"
echo "url: $URL"
curl -sL -o paper.jar "$URL"
if [ ! -s paper.jar ] || [ $(stat -c%s paper.jar) -lt 1000000 ]; then
  echo "не удалось скачать через v2, пробуем v3"
  V3=$(curl -s "https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds" | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{const b=JSON.parse(s)[0];console.log(b.downloads['server:default'].url)})")
  echo "v3 url: $V3"
  curl -sL -o paper.jar "$V3"
fi
ls -la paper.jar

echo "=== ставим плагин ==="
cp "C:/Users/honor/Downloads/argus/build/libs/argus-0.1.0-SNAPSHOT.jar" plugins/ || exit 1
ls -la plugins/

echo "=== eula + server.properties ==="
echo "eula=true" > eula.txt
cat > server.properties <<'PROPS'
online-mode=false
level-type=flat
max-players=10
spawn-protection=0
view-distance=4
simulation-distance=4
sync-chunk-writes=false
enable-jmx-monitoring=false
PROPS

echo "=== старт сервера ==="
java -Xms1G -Xmx1G -jar paper.jar --nogui > server.log 2>&1 &
echo "pid: $!"
sleep 75
echo "=== лог ==="
tail -60 server.log
