import json, os, tempfile, time
d = tempfile.mkdtemp()
os.environ.update(TAJMI3_DB=d+"/t.db", TAJMI3_SECRET_FILE=d+"/k", GOOGLE_CLIENT_ID="cid.apps.googleusercontent.com", ALLOWED_EMAILS="a@gmail.com")
import app as A
from fastapi.testclient import TestClient
c = TestClient(A.app)

def fake(email, aud="cid.apps.googleusercontent.com", verified=True, sub="s1"):
    return lambda tok: {"sub": sub, "email": email, "name": "Hosni", "picture": "p", "aud": aud,
                        "iss": "accounts.google.com", "email_verified": verified}

def test_bad_token_rejected():
    r = c.post("/auth/google", json={"id_token": "garbage"})
    assert r.status_code == 401          # real verifier rejects junk

def test_flow():
    orig = A.verify_google
    # wrong audience / unverified are rejected by the real checks
    import google.oauth2.id_token as idt
    idt_orig = idt.verify_oauth2_token
    idt.verify_oauth2_token = lambda t, r, audience=None: fake("a@gmail.com", aud="other")(t)
    assert c.post("/auth/google", json={"id_token": "x"}).status_code == 401
    idt.verify_oauth2_token = lambda t, r, audience=None: fake("a@gmail.com", verified=False)(t)
    assert c.post("/auth/google", json={"id_token": "x"}).status_code == 401
    idt.verify_oauth2_token = lambda t, r, audience=None: fake("evil@gmail.com", sub="s2")(t)
    assert c.post("/auth/google", json={"id_token": "x"}).status_code == 403
    idt.verify_oauth2_token = lambda t, r, audience=None: fake("a@gmail.com")(t)
    r = c.post("/auth/google", json={"id_token": "x"}); assert r.status_code == 200
    tok = r.json()["token"]; H = {"Authorization": "Bearer " + tok}
    assert c.get("/me", headers=H).json()["email"] == "a@gmail.com"
    assert c.get("/sync").status_code == 401
    assert c.get("/sync", headers={"Authorization": "Bearer abc.def"}).status_code == 401
    assert c.get("/sync", headers=H).json() == {"data": None, "ts": 0}
    p = json.dumps({"pages": [1]})
    assert c.put("/sync", headers=H, json={"data": p, "ts": 100}).json()["ok"]
    assert c.get("/sync", headers=H).json() == {"data": p, "ts": 100}
    r = c.put("/sync", headers=H, json={"data": p, "ts": 50}); assert r.status_code == 409   # older write refused
    assert c.put("/sync", headers=H, json={"data": "not json", "ts": 200}).status_code == 400
    assert c.put("/sync", headers=H, json={"data": p, "ts": 300}).json()["ts"] == 300
    # tampered token
    bad = tok[:-2] + ("AA" if not tok.endswith("AA") else "BB")
    assert c.get("/me", headers={"Authorization": "Bearer " + bad}).status_code == 401
    # allowlist removed later -> blocked even with valid session
    A.ALLOWED.clear(); A.ALLOWED.add("someone@else.com")
    assert c.get("/me", headers=H).status_code == 403
    idt.verify_oauth2_token = idt_orig

def test_open_signup_and_limits():
    import google.oauth2.id_token as idt
    A.ALLOWED.clear()
    o = idt.verify_oauth2_token
    idt.verify_oauth2_token = lambda t, r, audience=None: fake("new@gmail.com", sub="n1")(t)
    r = c.post("/auth/google", json={"id_token": "x"}); assert r.status_code == 200
    H = {"Authorization": "Bearer " + r.json()["token"]}
    big = json.dumps({"x": "a" * (A.MAX_BYTES + 5000)})
    assert c.put("/sync", headers=H, json={"data": big, "ts": 1}).status_code == 413
    A.MAX_USERS = 1
    idt.verify_oauth2_token = lambda t, r, audience=None: fake("other@gmail.com", sub="n2")(t)
    assert c.post("/auth/google", json={"id_token": "x"}).status_code == 503
    idt.verify_oauth2_token = lambda t, r, audience=None: fake("new@gmail.com", sub="n1")(t)
    assert c.post("/auth/google", json={"id_token": "x"}).status_code == 200   # existing user still fine
    idt.verify_oauth2_token = o
