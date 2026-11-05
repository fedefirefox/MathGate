#!/usr/bin/env python3
"""Locally authorize a MathGate asset-bank update with the OWNER'S PRIVATE key.

After editing the question/meme assets, run:
  python3 tools/sign_assets.py --key /secure/path/MathGate_OWNER_PRIVATE_SIGNING_KEY.pem
  python3 tools/verify_assets.py

Commit the JSON/images plus BOTH security/asset_manifest files together.
Store the private key outside GitHub and outside the project checkout.
"""
import argparse
import base64
import hashlib
import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SECURITY = ROOT / "security"
PUBLIC = SECURITY / "owner_public_key.pem"
MANIFEST = SECURITY / "asset_manifest.json"
SIG = SECURITY / "asset_manifest.sig"
ASSETS = ROOT / "app" / "src" / "main" / "assets"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--key", type=Path, required=True,
                        help="Local private key, NEVER committed to GitHub")
    args = parser.parse_args()
    key = args.key.resolve()
    assert key.is_file() and not key.is_relative_to(ROOT), (
        "Keep the private key OUTSIDE the repository"
    )
    check = subprocess.run(
        ["openssl", "pkey", "-in", str(key), "-pubout"],
        capture_output=True, check=True,
    )
    assert check.stdout.strip() == PUBLIC.read_bytes().strip(), (
        "Wrong owner key. Do not rotate keys without rebuilding MathGate."
    )
    assets = [
        ASSETS / "problem_bank.json",
        ASSETS / "cat_memes.json",
    ] + sorted((ASSETS / "memes").glob("cat_[0-9][0-9].webp"))
    assert 4 <= len(assets) <= 60
    previous = json.loads(MANIFEST.read_text()) if MANIFEST.is_file() else {}
    revision = previous.get("revision", 0) + 1
    data = {
        "schema_version": 1,
        "revision": revision,
        "algorithm": "SHA256withECDSA-P256",
        "repository": "fedefirefox/MathGate",
        "files": {
            file.relative_to(ROOT).as_posix(): hashlib.sha256(file.read_bytes()).hexdigest()
            for file in assets
        },
    }
    payload = (json.dumps(data, sort_keys=True, separators=(",", ":"),
                          ensure_ascii=False) + "\n").encode("utf-8")
    SECURITY.mkdir(exist_ok=True)
    MANIFEST.write_bytes(payload)
    with tempfile.TemporaryDirectory() as work:
        signature = Path(work) / "signature.der"
        subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", str(key),
             "-out", str(signature), str(MANIFEST)],
            check=True,
        )
        SIG.write_text(base64.b64encode(signature.read_bytes()).decode("ascii") + "\n")
    print(f"Signed {len(assets)} files · manifest revision {revision}")
    subprocess.run(["python3", str(ROOT / "tools" / "verify_assets.py")], check=True)

if __name__ == "__main__":
    main()
