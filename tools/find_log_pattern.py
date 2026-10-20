import zipfile
import re

z = zipfile.ZipFile(r"C:\Users\honor\Downloads\argus-testserver\paper.jar")
names = [n for n in z.namelist() if "log4j" in n.lower()]
print("конфиги логирования в paper.jar:", names[:10])

for n in names[:8]:
    try:
        t = z.read(n).decode("utf-8", "replace")
    except Exception:
        continue
    patterns = re.findall(r"<PatternLayout[^>]*?pattern=(?:&quot;|\")([^\"&]+)", t)
    for p in patterns:
        print(f"  {n}\n    pattern: {p}")
