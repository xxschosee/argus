"""Достать из лога сервера строки плагина, разобрав кодировку корректно.

Лог Paper на Windows пишется в системной кодировке (cp1251/cp866), поэтому
grep с UTF-8-паттернами по нему не находит кириллицу. Здесь кодировка
определяется автоматически.
"""
import re
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "server.log"
pattern = sys.argv[2] if len(sys.argv) > 2 else "Argus"

raw = open(path, "rb").read()
text = None
for enc in ("utf-8", "cp1251", "cp866", "latin-1"):
    try:
        text = raw.decode(enc)
        break
    except Exception:
        continue
if text is None:
    text = raw.decode("latin-1", errors="replace")

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

hits = [line for line in text.splitlines() if pattern in line]
for line in hits:
    print(line)
print(f"--- совпадений: {len(hits)} ---", file=sys.stderr)
