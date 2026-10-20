"""Минимальный RCON-клиент: отправка команд в консоль Minecraft-сервера."""
import socket
import struct
import sys

TYPE_AUTH = 3
TYPE_CMD = 2
TYPE_RESP = 0


class Rcon:
    def __init__(self, host, port, password, timeout=10):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.settimeout(timeout)
        self.i = 0
        if self._send(TYPE_AUTH, password) is None:
            raise RuntimeError("RCON auth failed")

    def _send(self, ptype, body):
        self.i += 1
        payload = struct.pack("<ii", self.i, ptype) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)
        return self._recv()

    def _recv(self):
        raw = self._read(4)
        if raw is None:
            return None
        length = struct.unpack("<i", raw)[0]
        body = self._read(length)
        if body is None:
            return None
        rid, ptype = struct.unpack("<ii", body[:8])
        return body[8:-2].decode("utf-8", errors="replace")

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                return None
            buf += chunk
        return buf

    def cmd(self, command):
        return self._send(TYPE_CMD, command)

    def close(self):
        try:
            self.sock.close()
        except Exception:
            pass


if __name__ == "__main__":
    # Консоль Windows по умолчанию cp1251 - принудительно UTF-8 с заменой,
    # иначе вывод упадёт на символах вроде ✔
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

    host = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 25575
    password = sys.argv[3] if len(sys.argv) > 3 else "argus"
    rc = Rcon(host, port, password)
    for c in sys.argv[4:]:
        out = rc.cmd(c)
        print(f"> {c}")
        print(out)
    rc.close()
