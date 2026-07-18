#!/usr/bin/env python3
"""End-to-end seed + smoke test for ONEMEP-FEATURE-SERVICE via the API Gateway.

Logs in against the identity service, then exercises the feature-service master
data + project APIs with real MEP engineering data. Idempotent: existing records
(unique-constraint 409s) are looked up and reused instead of failing the run.
"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://16.192.90.23:9000"
EMAIL = "user@onemep.com"
PASSWORD = "ChangeMe@123"

TOKEN = None


def call(method, path, body=None, auth=True, expect=(200, 201)):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if auth and TOKEN:
        req.add_header("Authorization", "Bearer " + TOKEN)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            payload = json.loads(r.read().decode())
            return r.status, payload
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, {"raw": raw}


def login():
    global TOKEN
    status, resp = call(
        "POST", "/auth/login",
        {"email": EMAIL, "password": PASSWORD}, auth=False,
    )
    data = resp.get("data") or {}
    if data.get("twoFactorRequired"):
        print("ERROR: 2FA required on this account; cannot proceed headless.")
        sys.exit(1)
    TOKEN = data.get("accessToken")
    if not TOKEN:
        print("ERROR: no accessToken in login response:", resp)
        sys.exit(1)
    print(f"[auth] logged in as {EMAIL} (token len {len(TOKEN)})")


def data_of(resp):
    return resp.get("data") if isinstance(resp, dict) else None


# ---- Master data seeds ------------------------------------------------------

CATEGORIES = [
    ("HVAC", "HVAC"),
    ("Electrical", "ELE"),
    ("Plumbing", "PLM"),
    ("Fire Fighting", "FF"),
    ("Fire Alarm", "FA"),
    ("Drainage", "DRN"),
    ("Extra Low Voltage", "ELV"),
    ("Building Management System", "BMS"),
    ("Mechanical", "MEC"),
    ("Public Health Engineering", "PHE"),
    ("Chilled Water", "CHW"),
    ("Compressed Air", "CA"),
]

TIERS = ["Leadership", "Engineering", "Support"]

TEAM_ROLES = [
    ("Project Manager", "Leadership"),
    ("Discipline Lead", "Leadership"),
    ("Senior Design Engineer", "Engineering"),
    ("Design Engineer", "Engineering"),
    ("BIM Modeler", "Engineering"),
    ("Draftsman", "Support"),
    ("QA/QC Engineer", "Support"),
]

UNITS = [
    ("Cubic Feet per Minute", "CFM", "DECIMAL"),
    ("Refrigeration Ton", "TR", "DECIMAL"),
    ("Kilowatt", "kW", "DECIMAL"),
    ("Pascal", "Pa", "DECIMAL"),
    ("Millimeter", "mm", "INTEGER"),
    ("Cubic Meter per Hour", "m3/h", "DECIMAL"),
    ("Liters per Minute", "LPM", "DECIMAL"),
    ("Bar", "bar", "DECIMAL"),
    ("Degree Celsius", "degC", "DECIMAL"),
    ("Volt", "V", "INTEGER"),
]

PROJECTS = [
    ("Marina Bay Tower - Central Chiller Plant", "HVAC", "HIGH"),
    ("Downtown Mall - VRF System Design", "HVAC", "MEDIUM"),
    ("Airport Terminal 3 - Power Distribution", "Electrical", "CRITICAL"),
    ("Hospital Wing B - Emergency Power", "Electrical", "HIGH"),
    ("Residential Complex - Domestic Water Supply", "Plumbing", "MEDIUM"),
    ("Office Park - Wet Riser & Sprinklers", "Fire Fighting", "HIGH"),
    ("Data Center - VESDA Fire Alarm", "Fire Alarm", "CRITICAL"),
    ("Basement Parking - Storm Drainage", "Drainage", "LOW"),
    ("Corporate HQ - Structured Cabling", "Extra Low Voltage", "MEDIUM"),
    ("Smart Building - Integrated BMS", "Building Management System", "HIGH"),
    ("Industrial Plant - Compressed Air Network", "Compressed Air", "MEDIUM"),
    ("University Campus - Chilled Water Loop", "Chilled Water", "HIGH"),
]


def seed_categories():
    print("\n== Categories ==")
    _, resp = call("GET", "/categories/active")
    existing = {c["name"]: c["id"] for c in (data_of(resp) or [])}
    ids = {}
    for name, prefix in CATEGORIES:
        if name in existing:
            ids[name] = existing[name]
            print(f"  = {name} ({prefix}) exists id={existing[name]}")
            continue
        st, r = call("POST", "/categories",
                     {"name": name, "prefix": prefix, "active": True})
        d = data_of(r)
        if st in (200, 201) and d:
            ids[name] = d["id"]
            print(f"  + {name} -> {d.get('categoryNumber')} id={d['id']}")
        else:
            print(f"  ! {name} failed ({st}): {r.get('message') or r}")
    return ids


def seed_tiers():
    print("\n== Tiers ==")
    _, resp = call("GET", "/tiers/active")
    existing = {t["name"]: t["id"] for t in (data_of(resp) or [])}
    ids = {}
    for name in TIERS:
        if name in existing:
            ids[name] = existing[name]
            print(f"  = {name} exists id={existing[name]}")
            continue
        st, r = call("POST", "/tiers", {"name": name, "active": True})
        d = data_of(r)
        if d:
            ids[name] = d["id"]
            print(f"  + {name} id={d['id']}")
        else:
            print(f"  ! {name} failed ({st}): {r.get('message') or r}")
    return ids


def seed_team_roles(tier_ids):
    print("\n== Team Roles ==")
    _, resp = call("GET", "/team-roles/active")
    existing = {t["name"]: t["id"] for t in (data_of(resp) or [])}
    ids = {}
    for name, tier in TEAM_ROLES:
        if name in existing:
            ids[name] = existing[name]
            print(f"  = {name} exists id={existing[name]}")
            continue
        st, r = call("POST", "/team-roles",
                     {"name": name, "tierId": tier_ids.get(tier), "active": True})
        d = data_of(r)
        if d:
            ids[name] = d["id"]
            print(f"  + {name} [{tier}] id={d['id']}")
        else:
            print(f"  ! {name} failed ({st}): {r.get('message') or r}")
    return ids


def seed_units():
    print("\n== Units ==")
    _, resp = call("GET", "/units/active")
    existing = {u["symbol"]: u["id"] for u in (data_of(resp) or [])}
    ids = {}
    for name, symbol, itype in UNITS:
        if symbol in existing:
            ids[symbol] = existing[symbol]
            print(f"  = {name} ({symbol}) exists id={existing[symbol]}")
            continue
        st, r = call("POST", "/units",
                     {"name": name, "symbol": symbol, "acceptedInputType": itype,
                      "active": True})
        d = data_of(r)
        if d:
            ids[symbol] = d["id"]
            print(f"  + {name} ({symbol}) id={d['id']}")
        else:
            print(f"  ! {name} failed ({st}): {r.get('message') or r}")
    return ids


def seed_projects(cat_ids, role_ids):
    print("\n== Projects ==")
    role_id = next(iter(role_ids.values()), None)
    created = []
    for name, cat, priority in PROJECTS:
        cid = cat_ids.get(cat)
        if not cid:
            print(f"  ! {name}: category '{cat}' missing, skipping")
            continue
        body = {
            "name": name,
            "categoryId": cid,
            "priority": priority,
            "description": f"{cat} discipline scope for {name}.",
            "leadUserIds": [1],
        }
        if role_id:
            body["members"] = [{"userId": 1, "teamRoleId": role_id}]
        st, r = call("POST", "/projects", body)
        d = data_of(r)
        if st in (200, 201) and d:
            created.append(d["id"])
            print(f"  + {d.get('projectNumber')} {name} [{priority}] id={d['id']}")
        else:
            print(f"  ! {name} failed ({st}): {r.get('message') or r}")
    return created


def smoke_tests(cat_ids, project_ids):
    print("\n== Smoke tests ==")
    # 1. Paginated project list
    st, r = call("POST", "/projects/list",
                 {"paginationAndSorting": {"pageNumber": 0, "pageSize": 5,
                                           "sortBy": "name", "sortDirection": "ASC"},
                  "filters": {}})
    d = data_of(r) or {}
    print(f"  [list] projects total={d.get('totalElements')} "
          f"page0Count={len(d.get('content', []))} sortBy=name ASC")

    # 2. Filter by priority
    st, r = call("POST", "/projects/list",
                 {"filters": {"priority": "CRITICAL"}})
    d = data_of(r) or {}
    print(f"  [filter] CRITICAL projects={d.get('totalElements')}")

    # 3. Get one project detail
    if project_ids:
        st, r = call("GET", f"/projects/{project_ids[0]}")
        d = data_of(r) or {}
        print(f"  [get] project {project_ids[0]} -> {d.get('projectNumber')} "
              f"members={len(d.get('members', []))} leads={d.get('leadUserIds')}")

        # 4. Lifecycle transition DRAFT -> ACTIVE
        st, r = call("PATCH", f"/projects/{project_ids[0]}/lifecycle?lifecycleStatus=ACTIVE")
        print(f"  [patch] lifecycle ACTIVE -> {st} {r.get('message')}")

        # 5. Priority change
        st, r = call("PATCH", f"/projects/{project_ids[0]}/priority?priority=CRITICAL")
        print(f"  [patch] priority CRITICAL -> {st} {r.get('message')}")

    # 6. Category list paginated
    st, r = call("POST", "/categories/list", {"filters": {}})
    d = data_of(r) or {}
    print(f"  [list] categories total={d.get('totalElements')}")

    # 7. Unauthorized check
    st, r = call("GET", "/categories/active", auth=False)
    print(f"  [authz] no-token /categories/active -> {st} (expect 401)")


def main():
    login()
    cat_ids = seed_categories()
    tier_ids = seed_tiers()
    role_ids = seed_team_roles(tier_ids)
    seed_units()
    project_ids = seed_projects(cat_ids, role_ids)
    smoke_tests(cat_ids, project_ids)
    print(f"\nDONE. categories={len(cat_ids)} tiers={len(tier_ids)} "
          f"roles={len(role_ids)} projects_created={len(project_ids)}")


if __name__ == "__main__":
    main()
