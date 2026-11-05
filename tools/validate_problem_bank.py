#!/usr/bin/env python3
"""Validate the static MathGate problem bank before building an APK."""
import json, math, pathlib, collections
path = pathlib.Path("app/src/main/assets/problem_bank.json")
bank = json.loads(path.read_text(encoding="utf-8"))
assert bank["schema_version"] == 1
assert isinstance(bank["revision"], int) and bank["revision"] >= 1
items = bank["problems"]
assert 12 <= len(items) <= 500
assert len({p["id"] for p in items}) == len(items)
levels = collections.Counter()
for p in items:
    assert p["level"] in ("foundation","standard","advanced"), p
    levels[p["level"]] += 1
    assert 3 <= len(p["id"]) <= 80
    assert 5 <= len(p["prompt"]) <= 1000
    assert 3 <= len(p["latex"]) <= 1500
    assert p["latex"].count("{") == p["latex"].count("}"), p["id"]
    assert p["latex"].count("\\left") == p["latex"].count("\\right"), p["id"]
    assert math.isfinite(float(p["answer"]))
    assert math.isfinite(float(p["tolerance"])) and 0 < float(p["tolerance"]) <= 1
    assert 3 <= len(p["hint"]) <= 500
assert all(levels[x] >= 5 for x in ("foundation","standard","advanced")), levels
print(f"Validated {len(items)} problems at bank revision {bank['revision']}: {dict(levels)}")
