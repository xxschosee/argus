"""Убрать ручной префикс '[Argus] ' из сообщений: логгер Bukkit уже добавляет его сам."""
import pathlib
import re

ROOT = pathlib.Path(r"C:\Users\honor\Downloads\argus\src\main\java")
changed = []
for path in ROOT.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    new = re.sub(r'"\[\s*Argus\s*\]\s*', '"', text)
    if new != text:
        path.write_text(new, encoding="utf-8", newline="\n")
        changed.append(path.name)
print("изменено файлов:", len(changed))
for c in changed:
    print("  " + c)
