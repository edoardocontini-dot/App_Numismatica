"""
One-off check of the Numista API from GitHub's servers (the developer's workspace cannot reach Numista).
Downloads the issuer list and a few sample answers; the result is encrypted before it leaves the runner,
so no Numista data is published in this public repository.
"""
import json
import os
import urllib.error
import urllib.parse
import urllib.request

KEY = os.environ.get("NUMISTA_API_KEY", "").strip()
MODE = os.environ.get("PROBE_MODE", "full")
if not KEY and MODE == "full":
    raise SystemExit("NUMISTA_API_KEY secret is missing")

BASE = "https://api.numista.com/v3"
out = {}


def get(path, **params):
    url = BASE + path + ("?" + urllib.parse.urlencode(params) if params else "")
    req = urllib.request.Request(url, headers={"Numista-API-Key": KEY, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            status, body = r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        status, body = e.code, e.read().decode("utf-8", "replace")
    out[path + " " + json.dumps(params, sort_keys=True)] = {"status": status, "body": body}
    print(path, params, "->", status, len(body), "bytes")
    return status, body


if MODE == "keycheck":
    # How does Numista answer a wrong key / no key? (no quota used: these requests are rejected)
    for label, k in (("wrong", "0000000000000000000000000000000000000000"), ("empty", "")):
        url = BASE + "/types?q=euro&count=1&lang=it"
        req = urllib.request.Request(url, headers={"Numista-API-Key": k, "Accept": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                out[label] = {"status": r.status, "body": r.read().decode()[:300]}
        except urllib.error.HTTPError as e:
            out[label] = {"status": e.code, "body": e.read().decode("utf-8", "replace")[:300]}
    with open("probe.json", "w", encoding="utf-8") as f:
        json.dump(out, f)
    raise SystemExit(0)

status, issuers_en = get("/issuers", lang="en")
get("/issuers", lang="it")

_, search = get("/types", q="500 lire", lang="en", count=5)
try:
    tid = json.loads(search)["types"][0]["id"]
    get(f"/types/{tid}", lang="en")
    get(f"/types/{tid}/issues", lang="en")
except Exception as e:  # noqa: BLE001
    out["error_type"] = repr(e)

get("/types", q="500 lire", lang="it", count=5)

# Does filtering by a parent issuer include its sub-issuers?
try:
    issuers = json.loads(issuers_en)["issuers"]
    child = next((i for i in issuers if i.get("parent")), None)
    if child:
        parent = child["parent"]
        parent_code = parent.get("code") if isinstance(parent, dict) else parent
        get("/types", issuer=parent_code, count=1, lang="en")
        get("/types", issuer=child["code"], count=1, lang="en")
    else:
        out["note"] = "no issuer has a 'parent' field"
except Exception as e:  # noqa: BLE001
    out["error_hierarchy"] = repr(e)

with open("probe.json", "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False)
