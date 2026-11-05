#!/usr/bin/env python3
"""Validate MathGate's offline cat-meme catalogue and bundled artwork."""
import json
from pathlib import Path

root = Path("app/src/main/assets")
data = json.loads((root / "cat_memes.json").read_text(encoding="utf-8"))
assert data["schema_version"] == 1
assert data["revision"] >= 1
memes = data["memes"]
assert 2 <= len(memes) <= 100
assert len(set(m["id"] for m in memes)) == len(memes)
for m in memes:
    assert 2 <= len(m["id"]) <= 40
    assert 4 <= len(m["title"]) <= 100
    assert 8 <= len(m["message"]) <= 350
    assert m["image"].startswith("cat_") and m["image"].endswith(".webp")
    assert len(m["image"]) <= 32
    file = root / "memes" / m["image"]
    assert file.is_file(), f"Missing image {file}"
    assert 100 < file.stat().st_size < 350_000
    assert file.open("rb").read(4) == b"RIFF"
print(f"Validated {len(memes)} cat meme entries, revision {data['revision']}.")
