#!/usr/bin/env python3
"""One-time, local-only Jackett/qBittorrent credential bootstrap."""
import http.cookiejar
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parent
ENV = ROOT / ".env"
JACKETT_CONFIG = ROOT / "data/jackett/Jackett/ServerConfig.json"
QBIT_URL = "http://127.0.0.1:8081"
TIMEOUT = 120


def run(*args):
    return subprocess.run(args, cwd=ROOT, text=True, capture_output=True, check=True).stdout


def read_env():
    values = {}
    if ENV.exists():
        for line in ENV.read_text().splitlines():
            if line and not line.lstrip().startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                values[k] = v.strip().strip("'\"")
    return values


def save_env(values):
    # Keep values safe to source from bash; never print secrets.
    import shlex
    lines = []
    existing = ENV.read_text().splitlines() if ENV.exists() else []
    seen = set()
    for line in existing:
        match = re.match(r"^([A-Z][A-Z0-9_]*)=", line)
        if match and match.group(1) in values:
            key = match.group(1)
            lines.append(key + "=" + shlex.quote(values[key]))
            seen.add(key)
        else:
            lines.append(line)
    for key, value in values.items():
        if key not in seen:
            lines.append(key + "=" + shlex.quote(value))
    fd = os.open(ENV, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as out:
        out.write("\n".join(lines) + "\n")
    os.chmod(ENV, 0o600)


def wait_for(url):
    until = time.monotonic() + TIMEOUT
    while time.monotonic() < until:
        try:
            urllib.request.urlopen(url, timeout=3)
            return
        except urllib.error.HTTPError:
            return  # Web UI is listening, even if it requires auth.
        except (urllib.error.URLError, TimeoutError):
            time.sleep(2)
    raise RuntimeError("Service unavailable: " + url)


def qbit_login(password):
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    body = urllib.parse.urlencode({"username": "admin", "password": password}).encode()
    request = urllib.request.Request(
        QBIT_URL + "/api/v2/auth/login", data=body,
        headers={"Referer": QBIT_URL + "/", "Origin": QBIT_URL},
    )
    with opener.open(request, timeout=8) as response:
        if response.read().strip() != b"Ok.":
            raise RuntimeError("qBittorrent rejected the admin credentials")
    return opener


def configure_qbit(env):
    if env.get("QBIT_PASSWORD"):
        qbit_login(env["QBIT_PASSWORD"])
        print("qBittorrent: existing credentials verified")
        return
    logs = run("docker", "compose", "logs", "--no-color", "qbittorrent")
    matches = re.findall(r"temporary password is provided for this session:\\s*(\\S+)", logs, re.I)
    if not matches:
        raise RuntimeError("Cannot find qBittorrent temporary password in Compose logs. "
                           "If the Web UI was already configured, set QBIT_PASSWORD in .env.")
    opener = qbit_login(matches[-1])
    new_password = secrets.token_urlsafe(30)
    payload = urllib.parse.urlencode({
        "json": json.dumps({"web_ui_password": new_password})
    }).encode()
    req = urllib.request.Request(
        QBIT_URL + "/api/v2/app/setPreferences", data=payload,
        headers={"Referer": QBIT_URL + "/", "Origin": QBIT_URL},
    )
    with opener.open(req, timeout=8) as response:
        if response.status != 200:
            raise RuntimeError("qBittorrent refused password update")
    qbit_login(new_password)  # Verify before saving.
    env["QBIT_USER"] = "admin"
    env["QBIT_PASSWORD"] = new_password
    save_env(env)
    print("qBittorrent: generated and verified a persistent admin password")


def configure_jackett(env):
    until = time.monotonic() + TIMEOUT
    while time.monotonic() < until:
        if JACKETT_CONFIG.exists():
            try:
                key = json.loads(JACKETT_CONFIG.read_text()).get("APIKey")
                if key:
                    env["JACKETT_API_KEY"] = key
                    save_env(env)
                    print("Jackett: API key imported from persistent configuration")
                    return
            except (OSError, ValueError):
                pass
        time.sleep(2)
    raise RuntimeError("Jackett API key not found in " + str(JACKETT_CONFIG))


def main():
    if not ENV.exists():
        example = ROOT / ".env.example"
        ENV.write_text(example.read_text())
        os.chmod(ENV, 0o600)
    env = read_env()
    print("Starting Jackett and qBittorrent via Docker Compose")
    run("docker", "compose", "up", "-d")
    wait_for("http://127.0.0.1:9117/UI/Dashboard")
    wait_for(QBIT_URL + "/api/v2/app/version")
    configure_jackett(env)
    configure_qbit(env)
    print("Infrastructure ready. Add TELEGRAM_BOT_TOKEN and TELEGRAM_ALLOWED_USER_ID "
          "to .env and configure an authorized indexer in Jackett before ./start.sh")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, urllib.error.URLError, OSError) as exc:
        print(f"Setup failed: {exc}", file=sys.stderr)
        sys.exit(1)
