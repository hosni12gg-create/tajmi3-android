"""tajmi3 sync server: Google sign-in + one JSON blob per user (SQLite)."""
import base64
import hashlib
import hmac
import json
import os
import secrets
import sqlite3
import time
from contextlib import closing
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

BASE = Path(__file__).resolve().parent
DB_PATH = os.environ.get("TAJMI3_DB", str(BASE / "data" / "tajmi3.db"))
CLIENT_IDS = [c.strip() for c in os.environ.get("GOOGLE_CLIENT_ID", "").split(",") if c.strip()]
ALLOWED = {e.strip().lower() for e in os.environ.get("ALLOWED_EMAILS", "").split(",") if e.strip()}
SESSION_DAYS = int(os.environ.get("SESSION_DAYS", "60"))
MAX_BYTES = int(os.environ.get("MAX_BYTES", str(40 * 1024 * 1024)))
SECRET_FILE = Path(os.environ.get("TAJMI3_SECRET_FILE", str(BASE / "data" / "session.key")))


def _load_secret() -> bytes:
    SECRET_FILE.parent.mkdir(parents=True, exist_ok=True)
    if not SECRET_FILE.exists():
        SECRET_FILE.write_text(secrets.token_hex(32))
        try:
            SECRET_FILE.chmod(0o600)
        except OSError:
            pass
    return SECRET_FILE.read_text().strip().encode()


SECRET = _load_secret()


def db():
    Path(DB_PATH).parent.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(DB_PATH, timeout=10)
    con.row_factory = sqlite3.Row
    con.execute("PRAGMA journal_mode=WAL")
    return con


def init_db():
    with closing(db()) as con:
        con.executescript(
            """
            CREATE TABLE IF NOT EXISTS users(
              sub TEXT PRIMARY KEY, email TEXT, name TEXT, picture TEXT,
              created INTEGER, last_login INTEGER);
            CREATE TABLE IF NOT EXISTS blobs(
              sub TEXT PRIMARY KEY, data TEXT, ts INTEGER, updated INTEGER);
            """
        )
        con.commit()


init_db()


# ---------- Google ID token verification ----------
def verify_google(id_token_str: str) -> dict:
    """Verify signature (Google public keys), issuer, expiry and audience."""
    from google.auth.transport import requests as greq
    from google.oauth2 import id_token

    if not CLIENT_IDS:
        raise HTTPException(500, "GOOGLE_CLIENT_ID is not configured")
    try:
        info = id_token.verify_oauth2_token(id_token_str, greq.Request(), audience=None)
    except Exception as e:  # bad signature, expired, wrong issuer...
        raise HTTPException(401, f"invalid token: {e}")
    if info.get("aud") not in CLIENT_IDS:
        raise HTTPException(401, "token audience mismatch")
    if info.get("iss") not in ("accounts.google.com", "https://accounts.google.com"):
        raise HTTPException(401, "bad issuer")
    if not info.get("email_verified"):
        raise HTTPException(401, "email not verified")
    return info


# ---------- our own signed session token ----------
def _b64(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def _unb64(s: str) -> bytes:
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


def make_token(sub: str) -> str:
    payload = _b64(json.dumps({"sub": sub, "exp": int(time.time()) + SESSION_DAYS * 86400}).encode())
    sig = _b64(hmac.new(SECRET, payload.encode(), hashlib.sha256).digest())
    return f"{payload}.{sig}"


def read_token(token: str) -> str:
    try:
        payload, sig = token.split(".")
        good = _b64(hmac.new(SECRET, payload.encode(), hashlib.sha256).digest())
        if not hmac.compare_digest(sig, good):
            raise ValueError
        d = json.loads(_unb64(payload))
        if d["exp"] < time.time():
            raise ValueError
        return d["sub"]
    except Exception:
        raise HTTPException(401, "invalid session")


def current_user(authorization: str | None) -> sqlite3.Row:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(401, "missing token")
    sub = read_token(authorization[7:].strip())
    with closing(db()) as con:
        u = con.execute("SELECT * FROM users WHERE sub=?", (sub,)).fetchone()
    if not u:
        raise HTTPException(401, "unknown user")
    if ALLOWED and (u["email"] or "").lower() not in ALLOWED:
        raise HTTPException(403, "email not allowed")
    return u


# ---------- app ----------
app = FastAPI(title="tajmi3", docs_url=None, redoc_url=None, openapi_url=None)
app.add_middleware(
    CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"], max_age=3600
)


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/auth/google")
async def auth_google(request: Request):
    body = await request.json()
    info = verify_google(str(body.get("id_token", "")))
    email = (info.get("email") or "").lower()
    if ALLOWED and email not in ALLOWED:
        raise HTTPException(403, "email not allowed")
    now = int(time.time())
    with closing(db()) as con:
        con.execute(
            """INSERT INTO users(sub,email,name,picture,created,last_login) VALUES(?,?,?,?,?,?)
               ON CONFLICT(sub) DO UPDATE SET email=excluded.email,name=excluded.name,
               picture=excluded.picture,last_login=excluded.last_login""",
            (info["sub"], email, info.get("name") or email, info.get("picture") or "", now, now),
        )
        con.commit()
    return {
        "token": make_token(info["sub"]),
        "user": {"email": email, "name": info.get("name") or email, "picture": info.get("picture") or ""},
    }


@app.get("/me")
def me(authorization: str | None = Header(default=None)):
    u = current_user(authorization)
    return {"email": u["email"], "name": u["name"], "picture": u["picture"]}


@app.get("/sync")
def get_sync(authorization: str | None = Header(default=None)):
    u = current_user(authorization)
    with closing(db()) as con:
        r = con.execute("SELECT data, ts FROM blobs WHERE sub=?", (u["sub"],)).fetchone()
    if not r:
        return {"data": None, "ts": 0}
    return {"data": r["data"], "ts": r["ts"]}


@app.put("/sync")
async def put_sync(request: Request, authorization: str | None = Header(default=None)):
    u = current_user(authorization)
    raw = await request.body()
    if len(raw) > MAX_BYTES:
        raise HTTPException(413, "too large")
    try:
        body = json.loads(raw)
        data, ts = body["data"], int(body["ts"])
        if not isinstance(data, str):
            raise ValueError
        json.loads(data)  # must itself be valid JSON
    except Exception:
        raise HTTPException(400, "bad body")
    with closing(db()) as con:
        r = con.execute("SELECT ts FROM blobs WHERE sub=?", (u["sub"],)).fetchone()
        if r and r["ts"] > ts:
            # server copy is newer: do not overwrite, tell the client
            return JSONResponse({"ok": False, "conflict": True, "ts": r["ts"]}, status_code=409)
        con.execute(
            """INSERT INTO blobs(sub,data,ts,updated) VALUES(?,?,?,?)
               ON CONFLICT(sub) DO UPDATE SET data=excluded.data,ts=excluded.ts,updated=excluded.updated""",
            (u["sub"], data, ts, int(time.time())),
        )
        con.commit()
    return {"ok": True, "ts": ts}
