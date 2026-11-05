# MathGate in-app APK updates (v0.7+)

## What the user does after permanent signing is configured

Open **MathGate → Update MathGate directly from GitHub**. The app checks the latest official public GitHub release, downloads the candidate Android APK, verifies its digest and package signature, and opens the Android package installer. Approve **Update**. On a Samsung you may need to enable **Allow from this source** for MathGate once. Android confirmation is mandatory; silent installation is not possible for this ordinary third-party APK.

The app also continues to download **owner-signed question and cat-meme data** without an APK reinstall. These are separate workflows from APK/code updates.

## Why the installed v0.6.0 cannot be upgraded in place

The existing v0.6.0 APK was signed by an ephemeral GitHub runner's debug certificate. Android will reject any APK whose signing certificate does not match the installed app. The original runner's private key cannot be reconstructed from the APK. Therefore there is **one unavoidable transition**: uninstall the old debug-signed v0.6.0, then install the first stable-signed release. Re-enable MathGate's Usage Access and Accessibility permissions. System Instagram usage history is still maintained by Android.

After that transition, keep the same stable keystore for every newer release. The in-app updater will reject a candidate with a different certificate instead of silently failing the installation.

## One-time owner setup of stable APK signing

The publish workflow is present but deliberately will **not** publish an APK until the owner adds its private signing bundle as a GitHub encrypted Actions secret.

1. Back up the private PKCS#12 signing bundle in encrypted/offline storage. It must NOT be committed to GitHub, posted in an issue, uploaded to a pull request, or shared publicly.
2. Open [MathGate repository Secrets → Actions](https://github.com/fedefirefox/MathGate/settings/secrets/actions).
3. Add a **repository secret** named exactly `MATHGATE_SIGNING_BUNDLE_BASE64`.
4. Paste the complete Base64 text of the signing bundle ZIP into its value, then save. The bundle ZIP contains `mathgate-release.p12` and `credentials.json`, which holds the keystore alias and passwords.
5. Open [Publish stable-signed MathGate APK](https://github.com/fedefirefox/MathGate/actions/workflows/publish-apk.yml), choose **Run workflow** on `main`. The workflow validates owner-signed questions and memes, builds a release APK, signs it with the permanent private key, verifies the Android signature and publishes the APK to **GitHub Releases**.
6. Install that published stable-signed APK once, after uninstalling the older debug-signed v0.6.0. Future releases made with the same secret should update over the existing installation by tapping the button in MathGate.

If the secret is missing or malformed, the release is **not published**. The normal CI still produces a development-only APK for testing, but those ephemeral debug APKs cannot guarantee in-place updates. Do not publish the private key or the secret ZIP in the public repo.

**Security:** the release-signing key is distinct from the ECDSA private key that authorizes downloaded math/meme data. Both must remain private. Losing the release key means future APK upgrades are blocked unless Android's signing-key migration support is correctly configured; for this small app, keep a backed-up copy rather than assuming it can be recovered.

## Testing status

The build workflow checks Kotlin compilation, the math and meme catalogues, owner asset signature and release-signing configuration. It does not simulate every Samsung One UI version or prove in-app updates succeed on your individual phone. The Android installer can still reject installation for storage, permissions, or security-policy reasons.
