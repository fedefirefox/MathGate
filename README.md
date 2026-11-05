# MathGate 🧠🔒

**Instagram: 20 minutes daily, then +5 minutes for every solved university-level calculus problem.**

MathGate is a free, public-source Android app. It uses a light graphite, electric lime, and teal interface, and an offline mathematical equation display.

## What's in version 0.7.0?

**Real Android layout repair:** the Victory screen now fits the cat meme at its full original aspect ratio, without a 255dp crop. Claim +5:00 and Leave are in a fixed bottom area above the Samsung navigation bar, including Android 15/16 system-bar insets.

**In-app application updater:** press **Update MathGate directly from GitHub**. The app checks the official latest Release, downloads its APK over HTTPS, validates size and GitHub's SHA-256 digest, confirms the package name and that the signing certificate matches your currently installed version, then launches the Android system installer. You still approve installation and may need to allow MathGate as an installation source once. No downloaded update can execute code without Android's installation approval.

**One-time prerequisite:** v0.6.0 uses a temporary CI debug certificate. Android cannot update it in place using a different signing certificate. The new permanent release identity requires one initial uninstall/install transition from v0.6.0 to the first stable-signed v0.7.0 release. After that, future versions signed by the same owner-controlled identity can install without uninstalling MathGate. See [UPDATING.md](UPDATING.md).



**Security hardening:** GitHub-hosted equations and cat memes now require an **owner-signed ECDSA P-256 manifest and matching SHA-256 hashes**. An unsigned GitHub edit is rejected by the app. This protects installed users from unauthorized content edits after they have installed v0.6. See [SECURITY.md](SECURITY.md) for the private-key owner workflow and GitHub branch-protection setup.


**New reward flow:** after submitting the correct answer, MathGate shows a *Congrats!* celebration, an AI-created cat illustration, a silly daily caption, and a **Claim +5:00 · Open Instagram** button. The five-minute allowance is awarded **only when that button is tapped**. Leaving the reward screen without claiming leaves a pending reward for the same day; it cannot be claimed twice. The daily Instagram time rule is unchanged.

The APK bundles **two compact AI-generated cat illustrations**, paired with **16 different AI-written meme captions**. A deterministic daily selection keeps the same joke for one local calendar day; the joke changes the next day. The cat-meme catalogue is a separate versioned file on GitHub and the in-app **Download AI cat memes from GitHub** button can update captions and download additional WebP pictures without reinstalling the app. The bundled offline memes are always available.

**AI-generation limitation:** the app does not run an image generator on the phone and does not generate a new image from scratch each morning. Daily generation of entirely new images would require an external image-generation provider and configured credentials; this has **not** been enabled. The current artwork was AI-generated in advance and rotates by day.



- **54 curated problems:** 18 Foundation, 18 Standard, 18 Advanced. The previous eight coefficient-randomized templates remain as emergency fallback.
- Topics include **Cauchy's formula and residue theorem** (simple, repeated and higher-order poles), **PDEs** (heat, wave, transport, Laplace, Poisson, inviscid Burgers), vector calculus, ODEs, integration, Fourier methods, optimization and series.
- **Good-looking math:** common TeX formulas are converted to offline MathML Core and displayed by Android WebView. No CDN or JavaScript is needed.
- **Difficulty selector** inside MathGate. Changing difficulty never resets Instagram usage.
- **Show another equation** in the practice-preview card displays a different randomly selected question at your chosen difficulty. The view includes a short prompt and a typeset equation, not the answer. It stays at your current scroll position.
- Preview refresh does **not** alter, skip, or regenerate the genuine challenge blocking Instagram. That challenge remains fixed until solved.
- **Typed numeric expressions:** 2*pi, sqrt(3)/2, exp(-1), 1/3, etc. MathGate does not evaluate full symbolic proofs.
- **Download new equations from GitHub** in the app; new questions do not require a new APK.
- **Check for MathGate app updates** through GitHub Releases. Android must still approve APK installation.

## Meme catalogue and GitHub updates

The editable [cat meme catalogue](app/src/main/assets/cat_memes.json) contains entries with `id`, `image`, `title`, and `message`. To add a new picture, commit a WebP file named `cat_NN.webp` under [app/src/main/assets/memes](app/src/main/assets/memes) and reference its exact filename in the catalogue. Increment top-level `revision` and sign all affected assets using your private key before committing (see [SECURITY.md](SECURITY.md)). In the app, select **Download AI cat memes from GitHub** to retrieve the new data and images. Only the project's fixed GitHub path is accepted. Images are type-, dimensions-, and size-checked; no remote code is executed.

A meme is selected based on the local day and remains stable for that day, even if the catalogue is refreshed. The library currently contains **16 daily captions and 2 images**. To add a genuinely new AI-generated image every day automatically, a scheduled generation workflow and private provider credentials would be needed.

## Instagram rule

1. Get **20 minutes** of Instagram foreground usage per local calendar day.
2. Once exhausted, opening Instagram triggers a full-screen MathGate challenge.
3. One correct typed result brings up a celebratory reward screen. **Only tapping Claim +5:00 grants the five-minute extension**; the cat meme itself cannot be skipped to grant time.
4. Repeat whenever your earned minutes expire; there is no arbitrary maximum number of correct unlocks.
5. Bonus minutes persist across Instagram sessions today and reset at local midnight.
6. There are **no in-app skip, pause, quota-reset or disable controls**.

Time comes from Android's usage-event records (UsageStatsManager), not an app-specific stopwatch. Samsung Digital Wellbeing does not expose a public API to its exact displayed counter; small differences may occur.

## Samsung permission denied: how to enable the Accessibility guard

If the screen says **"App was denied access. Access to this permission can put your personal and financial info at risk"**, Samsung has restricted Accessibility because you installed the app from an APK outside Google Play.

**Fix on Samsung:**

1. Tap Close and **leave the Accessibility screen** entirely.
2. Open **Settings → Apps → MathGate**. This is the main MathGate *App info* page.
3. Tap the **three-dot menu (⋮)** at the top right.
4. Choose **Allow restricted settings**, then confirm with your PIN or fingerprint.
5. Return to **Settings → Accessibility → Installed apps → MathGate Instagram Guard**.
6. Turn **MathGate Instagram Guard ON**.
7. Open MathGate and grant **Usage Access** through its setup button, if not granted already.
8. Return to MathGate and look for **Instagram Guard Active**.

If step 4 is missing, verify that you are on the main *App info* page. Samsung One UI and managed-device policies may vary.

Accessibility is a sensitive Android permission. MathGate requests only foreground window events and does not request screen content. Review the source before granting it. Android always lets you revoke permission.

## Installing / updating the APK

Open [Build Android APK workflows](https://github.com/fedefirefox/MathGate/actions). Select the latest **successful** run, download **MathGate-debug-apk** from Artifacts, extract app-debug.apk, and install it.

When stable APK releases are published, they will appear under [GitHub Releases](https://github.com/fedefirefox/MathGate/releases). The in-app **Check for MathGate app update** button checks this endpoint.

**Important:** Versions through v0.6.0 were signed using runner-specific GitHub Actions debug keys. A future APK may have a different certificate, and Android will then refuse an in-place update. It may require uninstalling the previous version. The v0.7.0 release workflow is prepared to use a permanent private signing identity from GitHub Actions secret `MATHGATE_SIGNING_BUNDLE_BASE64`. **The owner must add that secret once; until then the workflow deliberately publishes no official v0.7 release.** Reinstalling can clear locally earned minutes and settings, although system usage history persists.

## Get more problems from GitHub (no APK reinstall)

The source file is:

[app/src/main/assets/problem_bank.json](app/src/main/assets/problem_bank.json)

It contains a **versioned, editable JSON array** of exercises. Each item specifies an ID, difficulty level, topic, explanatory prompt, mathematical latex, numeric answer, tolerance and hint.

To publish new questions:

1. Add a mathematically checked question to the JSON file and increment the top-level `revision`.
2. **Sign** the changed library using your private owner key outside GitHub: `python3 tools/sign_assets.py --key /secure/location/MathGate_OWNER_PRIVATE_SIGNING_KEY.pem`.
3. Commit the data and both signed manifest files from `security/` to GitHub. CI checks the signature and data.
4. In MathGate select **Download new equations from GitHub**. No APK reinstall is needed.

**An unsigned data change will intentionally fail to download.** The private key must remain offline and is never part of the public repository. See [SECURITY.md](SECURITY.md).

MathGate retrieves only the expected HTTPS files, validates the **owner's digital signature, signed SHA-256 hashes** and schemas, and stores the data locally. **No remote code executes.** The offline library is always retained as fallback. App releases and question-library revisions are independent.

Every commit triggers [question-bank structural validation](tools/validate_problem_bank.py). Structural validation is **not** mathematical verification: new questions need their answer checked independently.

Math renderer supports common TeX fractions, radicals, superscripts/subscripts, integrals, sums, vectors, Greek letters and derivatives. It is deliberately not a full LaTeX package, so avoid custom macros or TeX environments in question fields.

## Android and security limitations

- A normal APK cannot make itself impossible to disable. Android Settings can revoke Accessibility, revoke Usage Access, force-stop or uninstall it; there is no in-app bypass.
- Redirecting from Instagram relies on the Accessibility service and Android permitting a foreground Activity to open. Firmware differences require Samsung device testing.
- Instagram Lite, modified Instagram and browser Instagram are not covered.
- The numerical answer checker is a small arithmetic evaluator, not a symbolic algebra system.
- Screen-time totals can differ slightly from Samsung Digital Wellbeing. The math challenge screen should not count toward Instagram foreground time.
- MathML quality depends on the phone's Android System WebView version.
- No ads, tracking SDKs or remote code execution.

## Local build

Requires Java 17, Android SDK 35, Gradle 8.9. Before building, run the question-bank validator, then Gradle assembleDebug.

App version **0.7.0**. Bundled question library revision **2**, with 54 problems. Problem-library schema version **1**. Licensed MIT.

## Repository protection status

The public source is readable and forkable, not publicly writable. However, the repository had **no branch rulesets** at the last security inspection. The owner must enable protection in [Settings → Rules](https://github.com/fedefirefox/MathGate/settings/rules) and review collaborators in [Settings → Access](https://github.com/fedefirefox/MathGate/settings/access). The connected GitHub tool cannot manage those administrator-only settings. See [SECURITY.md](SECURITY.md) for exact steps.

## App updates: one-tap workflow and stable signing

Read [UPDATING.md](UPDATING.md) for the exact GitHub Secret setup and the unavoidable one-time Android signing transition. The app never performs a silent installation. A mismatch in the installed and downloaded APK signing certificates causes a clear error and **prevents an unsafe update attempt**.
