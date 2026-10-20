import zipfile
import re

z = zipfile.ZipFile(r"C:\Users\honor\Downloads\argus-testserver\paper.jar")
needles = [b"%d{HH:mm:ss", b"%logger", b"PatternLayout", b"[%d{"]
for n in z.namelist():
    if not n.endswith(".class"):
        continue
    data = z.read(n)
    if b"%d{HH:mm:ss" in data or (b"PatternLayout" in data and b"[" in data):
        # достаём строковые константы класса
        strings = re.findall(rb"[\x20-\x7e]{6,200}", data)
        interesting = [s.decode("latin-1") for s in strings
                       if "%d" in s.decode("latin-1") or "%logger" in s.decode("latin-1")
                       or "PatternLayout" in s.decode("latin-1")]
        if interesting:
            print("==", n)
            for s in interesting[:12]:
                print("   ", s)
