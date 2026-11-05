#!/usr/bin/env python3
"""Fail CI if repository assets differ from the owner's OFFLINE signed manifest.

Dependencies: Python stdlib + the OpenSSL CLI already installed on Ubuntu.
The P-256 private key must NEVER be present in this repository.
"""
import base64
import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SECURITY = ROOT / "security"
MANIFEST = SECURITY / "asset_manifest.json"
SIGNATURE = SECURITY / "asset_manifest.sig"
PUBLIC = SECURITY / "owner_public_key.pem"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
HEX = re.compile(r"[0-9a-f]{64}\Z")

def verify():
    manifest = MANIFEST.read_bytes()
    assert len(manifest) < 16 * 1024
    signature = base64.b64decode(SIGNATURE.read_text().strip(), validate=True)
    assert 48 <= len(signature) <= 128
    with tempfile.TemporaryDirectory() as directory:
        rawsig = Path(directory) / "signature.der"
        rawsig.write_bytes(signature)
        result = subprocess.run(
            ["openssl", "dgst", "-sha256", "-verify", str(PUBLIC),
             "-signature", str(rawsig), str(MANIFEST)],
            text=True, capture_output=True,
        )
        assert result.returncode == 0, "Owner signature rejected: " + result.stderr
    data = json.loads(manifest)
    assert data["algorithm"] == "SHA256withECDSA-P256"
    assert data["repository"] == "fedefirefox/MathGate"
    assert data["schema_version"] == 1
    assert data["revision"] > 0
    hashes = data["files"]
    actual_paths = [
        "app/src/main/assets/problem_bank.json",
        "app/src/main/assets/cat_memes.json",
    ]
    actual_paths += [
        path.relative_to(ROOT).as_posix()
        for path in sorted((ASSETS / "memes").glob("cat_[0-9][0-9].webp"))
    ]
    assert sorted(hashes) == sorted(actual_paths), (
        "Manifest must cover exactly the question bank, cat catalogue, and cat images"
    )
    for path, expected in hashes.items():
        assert HEX.fullmatch(expected) and path.startswith("app/src/main/assets/")
        blob = (ROOT / path).read_bytes()
        actual = hashlib.sha256(blob).hexdigest()
        assert actual == expected, f"Unsigned modification detected: {path}"
    assert not any(ROOT.rglob("*PRIVATE*KEY*.pem")), (
        "Never commit a private signing key"
    )
    print(f"OWNER SIGNATURE VERIFIED · revision {data['revision']} · {len(hashes)} protected assets")

if __name__ == "__main__":
    verify()
