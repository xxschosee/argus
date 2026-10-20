import zipfile
import re

z = zipfile.ZipFile(r"C:\Users\honor\Downloads\argus-testserver\paper.jar")
pat = re.compile(rb"\[%d\{[^}]*\} %level\][^%]{0,40}")
hits = []
for n in z.namelist():
    if not n.endswith(".class"):
        continue
    try:
        data = z.read(n)
    except Exception:
        continue
    for m in pat.finditer(data):
        hits.append((n, m.group(0).decode("latin-1")))
        break

for n, p in hits[:10]:
    print(n)
    print("   ", p)
if not hits:
    print("паттерн не найден в классах")
