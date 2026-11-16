#!/usr/bin/env python3
"""HTTP-реле для ClientLink (когда на сервере нельзя поставить плагин).

Запуск:   python3 swing_relay.py
При старте сам печатает ссылку для мода и токен (готовые строки для вставки в ClientLinkModule.java).
Переменные (все необязательные): PORT (если нет, берётся SERVER_PORT панели, иначе 8765), SWING_TOKEN (общий секрет; если не задан,
создаётся случайный и запоминается в swing_token.txt рядом со скриптом), PUBLIC_URL (если ссылку не удалось определить сама),
TRUST_PROXY=1 (если хостинг стоит за прокси).
Хранит только в памяти: кто сейчас с клиентом на каком сервере и что слушает. Записи живут 90 секунд.
"""
import hmac
import json
import os
import re
import secrets
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

# --- Настройки (можно поменять тут или задать переменными окружения хостинга) ---
# Порт: PORT, иначе SERVER_PORT (его выставляют панели Pterodactyl/Wispbyte: это тот порт, который открыт снаружи), иначе 8765.
PORT = int(os.environ.get("PORT") or os.environ.get("SERVER_PORT") or "8765")
TOKEN = os.environ.get("SWING_TOKEN", "")         # общий пароль; если не задан, создаётся сам (см. load_token)
TRUST_PROXY = os.environ.get("TRUST_PROXY", "") == "1"  # 1, если хостинг стоит за прокси (берём IP из X-Forwarded-For)
TTL = 90                                          # запись живёт 90 с; клиент шлёт /hello каждые 30-45 с
MAX_SERVERS = 200
MAX_PER_SERVER = 500
UUID_RE = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
NAME_RE = re.compile(r"^[A-Za-z0-9_.]{1,32}$")

DATA = {}   # server -> {uuid: entry}
HITS = {}   # ip -> [timestamps]
LOCK = threading.Lock()


def clean(s):
    s = "".join(ch for ch in str(s) if ord(ch) >= 32 and ch != "\u00a7" and ord(ch) != 127)
    return s.strip()[:80]


def purge(now):
    for srv in list(DATA):
        d = DATA[srv]
        for u in [u for u, e in d.items() if now - e["ts"] > TTL]:
            del d[u]
        if not d:
            del DATA[srv]
    # Старые записи по IP не копим бесконечно.
    for ip in list(HITS):
        if not any(now - t < 10 for t in HITS[ip]):
            del HITS[ip]


def limited(ip, now):
    h = [t for t in HITS.get(ip, []) if now - t < 10]
    h.append(now)
    HITS[ip] = h
    return len(h) > 30


def load_token():
    """Токен из SWING_TOKEN; иначе из swing_token.txt; иначе создаём случайный и сохраняем (чтобы не менялся при перезапуске)."""
    if TOKEN:
        return TOKEN
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "swing_token.txt")
    try:
        with open(path, encoding="utf-8") as f:
            t = f.read().strip()
        if t:
            return t
    except OSError:
        pass
    t = secrets.token_urlsafe(18)
    try:
        with open(path, "w", encoding="utf-8") as f:
            f.write(t + "\n")
    except OSError:
        print("  (не удалось сохранить токен в %s, при перезапуске он изменится; задайте SWING_TOKEN)" % path)
    return t


def detect_public_url():
    """Адрес реле для мода: PUBLIC_URL, переменные популярных хостингов, иначе внешний IP хоста + порт."""
    for name in ("PUBLIC_URL", "RELAY_URL"):
        v = os.environ.get(name, "").strip().rstrip("/")
        if v:
            return v if v.startswith("http") else "https://" + v
    for name, tmpl in (("RAILWAY_PUBLIC_DOMAIN", "https://%s"), ("RENDER_EXTERNAL_URL", "%s"),
                       ("KOYEB_PUBLIC_DOMAIN", "https://%s"), ("FLY_APP_NAME", "https://%s.fly.dev")):
        v = os.environ.get(name, "").strip().rstrip("/")
        if v:
            return tmpl % v
    for svc in ("https://api.ipify.org", "https://ifconfig.me/ip", "https://icanhazip.com"):
        try:
            req = urllib.request.Request(svc, headers={"User-Agent": "swing-relay"})
            with urllib.request.urlopen(req, timeout=4) as r:
                ip = r.read().decode("utf-8").strip()
            if re.match(r"^[0-9a-fA-F:.]{3,45}$", ip):
                return "http://%s:%d" % (ip if ":" not in ip else "[%s]" % ip, PORT)
        except Exception:
            continue
    return ""


def print_banner(url, token):
    line = "=" * 62
    print(line)
    print(" SwingRelay запущен (порт %d)" % PORT)
    print(line)
    if url:
        print(" Ссылка для мода:  " + url)
    else:
        print(" Ссылку определить не удалось (нет доступа в интернет?).")
        print(" Узнай адрес в панели хостинга и впиши его вместо ССЫЛКА ниже,")
        print(" либо задай переменную PUBLIC_URL и перезапусти.")
        url = "ССЫЛКА"
    print(" Токен:            " + token)
    print("-" * 62)
    print(" Вставь в ClientLinkModule.java перед сборкой мода:")
    print('   private static final String DEFAULT_RELAY_URL = "%s";' % url)
    print('   private static final String DEFAULT_RELAY_TOKEN = "%s";' % token)
    print(" или (без пересборки) в config/swinganimations_modules.json, модуль client_link:")
    print('   "relay_url": "%s", "relay_token": "%s"' % (url, token))
    print("-" * 62)
    if url.startswith("https://") and not TRUST_PROXY:
        print(" Похоже, хостинг стоит за прокси: задай TRUST_PROXY=1, иначе все игроки")
        print(" будут для реле одним IP и начнут получать 429.")
    print(" Проверка: открой ссылку в браузере, должно быть {\"ok\": true, ...}")
    print(line, flush=True)


class Handler(BaseHTTPRequestHandler):
    server_version = "SwingRelay/1.0"

    def log_message(self, fmt, *args):
        pass

    def _send(self, code, obj=None):
        body = json.dumps(obj if obj is not None else {}).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _ip(self):
        if TRUST_PROXY:
            fwd = self.headers.get("X-Forwarded-For", "")
            if fwd:
                return fwd.split(",")[0].strip()[:64]
        return self.client_address[0]

    def _auth(self):
        if not TOKEN:
            return True
        got = self.headers.get("X-Swing-Token", "")
        return hmac.compare_digest(got.encode("utf-8"), TOKEN.encode("utf-8"))

    def do_POST(self):
        now = time.time()
        if not self._auth():
            return self._send(403)
        if urlparse(self.path).path != "/hello":
            return self._send(404)
        try:
            n = int(self.headers.get("Content-Length", "0"))
            if n <= 0 or n > 2048:
                return self._send(413)
            o = json.loads(self.rfile.read(n).decode("utf-8"))
            server = clean(o["server"]).lower()[:100]
            uid = str(o["uuid"]).lower()
            name = str(o["name"])
            if not server or not UUID_RE.match(uid) or not NAME_RE.match(name):
                return self._send(400)
            state = int(o.get("state", 0))
            entry = {"uuid": uid, "name": name, "state": state if state in (0, 1, 2) else 0,
                     "title": clean(o.get("title", "")), "artist": clean(o.get("artist", "")), "ts": now}
        except Exception:
            return self._send(400)
        with LOCK:
            if limited(self._ip(), now):
                return self._send(429)
            purge(now)
            if server not in DATA and len(DATA) >= MAX_SERVERS:
                return self._send(503)
            d = DATA.setdefault(server, {})
            if uid not in d and len(d) >= MAX_PER_SERVER:
                return self._send(503)
            d[uid] = entry
        self._send(200)

    def do_GET(self):
        now = time.time()
        u = urlparse(self.path)
        if u.path == "/":
            return self._send(200, {"ok": True, "service": "swing-relay"})  # проверка, что сервис жив
        if not self._auth():
            return self._send(403)
        if u.path != "/peers":
            return self._send(404)
        server = clean(parse_qs(u.query).get("server", [""])[0]).lower()[:100]
        with LOCK:
            if limited(self._ip(), now):
                return self._send(429)
            purge(now)
            out = [{k: v for k, v in e.items() if k != "ts"} for e in DATA.get(server, {}).values()]
        self._send(200, out)


if __name__ == "__main__":
    TOKEN = load_token()
    httpd = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)   # порт занят или закрыт: ошибка видна сразу
    print_banner(detect_public_url(), TOKEN)
    httpd.serve_forever()
