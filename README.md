# MathGate 🧠🔒

**Instagram: 20 minutes daily, then +5 minutes for every solved university-level calculus problem.**

MathGate is a free, public-source Android app. It uses a light graphite, electric lime, and teal interface, and an offline mathematical equation display.

## What's in version 0.4.0?

- **54 curated problems:** 18 Foundation, 18 Standard, 18 Advanced. The previous eight coefficient-randomized templates remain as emergency fallback.
- Topics include **Cauchy's formula and residue theorem** (simple, repeated and higher-order poles), **PDEs** (heat, wave, transport, Laplace, Poisson, inviscid Burgers), vector calculus, ODEs, integration, Fourier methods, optimization and series.
- **Good-looking math:** common TeX formulas are converted to offline MathML Core and displayed by Android WebView. No CDN or JavaScript is needed.
- **Difficulty selector** inside MathGate. Changing difficulty never resets Instagram usage.
- **Show another equation** in the practice-preview card displays a different randomly selected question at your chosen difficulty. The view includes a short prompt and a typeset equation, not the answer. It stays at your current scroll position.
- Preview refresh does **not** alter, skip, or regenerate the genuine challenge blocking Instagram. That challenge remains fixed until solved.
- **Typed numeric expressions:** 2*pi, sqrt(3)/2, exp(-1), 1/3, etc. MathGate does not evaluate full symbolic proofs.
- **Download new equations from GitHub** in the app; new questions do not require a new APK.
- **Check for MathGate app updates** through GitHub Releases. Android must still approve APK installation.

## Instagram rule

1. Get **20 minutes** of Instagram foreground usage per local calendar day.
2. Once exhausted, opening Instagram triggers a full-screen MathGate challenge.
3. One correct typed result earns **five more minutes** of actual Instagram time.
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

**Important:** The present debug APKs are signed using runner-specific GitHub Actions debug keys. A future APK may have a different certificate, and Android will then refuse an in-place update. It may require uninstalling the previous version. For reliable in-place APK upgrades, the maintainer must configure a consistent, PRIVATE release-signing keystore through encrypted GitHub Actions secrets and publish release APKs. **This signing infrastructure has not yet been configured.** Reinstalling can clear locally earned minutes and settings, although system usage history persists.

## Get more problems from GitHub (no APK reinstall)

The source file is:

[app/src/main/assets/problem_bank.json](app/src/main/assets/problem_bank.json)

It contains a **versioned, editable JSON array** of exercises. Each item specifies an ID, difficulty level, topic, explanatory prompt, mathematical latex, numeric answer, tolerance and hint.

To publish new questions:

1. Add a validated question to the JSON file on GitHub.
2. Increment the top-level **revision** number.
3. Commit the file to the main branch.
4. Open MathGate and select **Download new equations from GitHub**.

MathGate retrieves only that HTTPS file, validates it strictly (schema, size, IDs, difficulty levels, numerical answers, tolerances), and stores the data locally. **No remote code executes.** The offline library is always retained as fallback. App releases and question-library revisions are independent.

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

App version **0.4.0**. Bundled question library revision **2**, with 54 problems. Problem-library schema version **1**. Licensed MIT.
