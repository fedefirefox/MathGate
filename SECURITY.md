# MathGate security and owner-controlled updates

## What a public repository allows

MathGate is publicly **readable**, and people can clone or fork it. Public access does **not** grant permission to push changes into \`fedefirefox/MathGate\`. Only the owner and explicitly authorized write-capable accounts/apps can change this repository. People can submit pull requests, but proposals are not changes to \`main\` until merged by someone with permission.

**Status as inspected on November 4, 2026:** this repository exposed **zero active repository rulesets**. The connected GitHub app does not have the administrative permission needed to configure them, so **they have not been enabled**.

## One-time repository-owner actions (required for full GitHub protection)

1. Open [MathGate → Settings → Collaborators](https://github.com/fedefirefox/MathGate/settings/access). Check who has access and remove any collaborator/app that should not have write access. The connected tool could not enumerate these private settings.
2. Open [MathGate → Settings → Rules](https://github.com/fedefirefox/MathGate/settings/rules) and create an **active** ruleset targeting the default branch \`main\`.
3. Enable **Block force pushes**, **Restrict deletions**, and **Require status checks**. Require the \`apk\` check (the Android job validates the bank, memes, and owner signature before compiling). Optionally require pull requests before merging; for a one-person repository, **do not require an external reviewer unless one is available**, or you may make all merges impossible.
4. Where available, restrict branch updates to yourself and narrowly trusted GitHub Apps. Avoid global bypasses or granting write access to unknown GitHub Apps. Ruleset options differ by plan and permissions.
5. Open [MathGate → Settings → Actions → General](https://github.com/fedefirefox/MathGate/settings/actions). Select **Read repository contents and packages permissions** as the default workflow permission. The MathGate workflow already requests only \`contents: read\`.
6. Enable GitHub account passkeys/2FA. Review active personal access tokens, authorized OAuth apps, GitHub App installations, and GitHub security alerts.

You must perform the administrator-only settings yourself; the assistant's GitHub connector does not expose branch-ruleset mutation or repository collaborator management. Once enabled, their status can be checked again. GitHub cannot guarantee that even the repository owner, an administrator, or the GitHub platform can never change a repository.

## Signed remote assets: defense in depth

MathGate **v0.6.0 and later** have a public-key signature verifier embedded in the installed Android APK.

* Algorithm: ECDSA P-256 over SHA-256 (\`SHA256withECDSA\`).
* The owner public key is in \`security/owner_public_key.pem\` and pinned in \`ContentTrust.kt\`.
* The corresponding **private signing key is never committed to GitHub**.
* The owner signs \`security/asset_manifest.json\`; the detached signature is \`security/asset_manifest.sig\`.
* The manifest contains SHA-256 checksums for the question bank, cat catalogue, and every allowed WebP illustration.
* A newer Github library is accepted **only** when the signature verifies against the app's pinned public key AND all downloaded bytes match the signed SHA-256 checksums AND the content passes its schema checks.
* Invalid or missing signatures, unsupported paths, large files, malformed JSON, and mismatching image checksums cause the update to be **rejected**. The app retains its bundled questions and cat memes.
* Once downloaded, the app re-verifies the cached files against its stored signed manifest before using them offline.
* The private key is independent of an APK release-signing keystore.

This protects already-installed copies from an unauthorized change to the hosted **question and meme data**, even if an attacker gained write access to GitHub. It does **not** protect a phone that installs an untrusted replacement APK or a device whose OS is compromised.

### How to authorize an update to the question/meme library

The bank download button remains a one-tap **installation process for signed data**. Publishing new questions has an additional owner-authorization step:

1. Edit \`app/src/main/assets/problem_bank.json\` and increment its own \`revision\`. Or edit \`app/src/main/assets/cat_memes.json\` / \`app/src/main/assets/memes/cat_NN.webp\` and increment the cat catalogue revision.
2. Keep your private ECDSA key outside the Git checkout. Never upload it to the public repository, an issue, a PR, a chat message or a screenshot.
3. From a local checkout run:
   \`\`\`bash
   python3 tools/sign_assets.py --key /secure/location/MathGate_OWNER_PRIVATE_SIGNING_KEY.pem
   python3 tools/verify_assets.py
   \`\`\`
4. Commit the changed data and BOTH \`security/asset_manifest.json\` and \`security/asset_manifest.sig\` together.
5. Let CI verify the signature and other checks. Then tap **Download new equations from GitHub** or **Download AI cat memes from GitHub** in the installed app.

The private key is essential. Losing it means new remote library revisions cannot be approved for existing installed APKs; a new trusted application version with a rotated public key would then be necessary. Keep a securely backed-up copy in a password manager or encrypted offline storage.

**Do not put the private key into an ordinary GitHub commit.** For now, signing must be done outside GitHub by the owner. Future automation could use a carefully protected signing service, but that would introduce a new trust boundary.

## Android application and APK security

- MathGate's Accessibility service watches foreground-window package events and does not request reading screen contents.
- MathGate requires Android's Usage Access permission to read Instagram foreground time and its normal Internet permission only to fetch content from the project's GitHub endpoints.
- MathML equation rendering has JavaScript disabled, network access disabled and no file/content access; math comes from restricted, escaped syntax.
- The answer parser accepts only a small set of arithmetic functions and constants. It does not run downloaded code.
- Image updates are restricted to \`cat_NN.webp\`, with strict file-size/type/image-dimension checks and signed checksum verification.
- An accessibility permission can still be revoked and an app can be force-stopped or removed in Android Settings. No normal APK can guarantee an absolutely unavoidable Instagram lock.
- The app's current GitHub Actions builds are **debug-signed with runner-specific certificates**. In-place app upgrades remain unreliable: an Android **release signing keystore** stored outside GitHub source and configured as a secret is needed for stable app updates. The **asset signing key is not an APK signing key**.
- A successful CI build is not a substitute for testing the Accessibility restriction and time accounting on the target Samsung device.

## Reporting a vulnerability

Avoid publishing secrets or private data in public issues. In particular, do not upload the private signing key, keystores, screenshots containing credentials, or API keys. Use GitHub's private vulnerability reporting feature if the repository owner has enabled it.

## In-app APK updater, v0.7+

`AppUpdater.kt` only accepts the exact `MathGate-vX.Y.Z.apk` asset from the official GitHub release path, verifies SHA-256 and package identity, checks the downloaded APK version is newer and requires the Android signing certificates to match the currently installed app. It uses a private file URI via AndroidX FileProvider to hand the APK to the system installer. Users must explicitly approve installation and may need to authorize install-from-this-source once; MathGate does not bypass Android protections.

**Permanent signing setup:** a release-signing PKCS#12 identity is held in the `MATHGATE_SIGNING_BUNDLE_BASE64` repository Actions Secret (configured by the owner; not stored in source). The publish workflow skips a release if the secret is missing. CI uses a different throwaway certificate only to verify the release build steps; the throwaway-signed APK is never published. The old v0.6 CI debug certificate is not available, so the first stable-signed APK cannot be installed over it. See [UPDATING.md](UPDATING.md).
