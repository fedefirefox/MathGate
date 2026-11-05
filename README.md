# MathGate 🧠🔒

**20 minutes of Instagram, then earn +5 minutes per calculus problem.**

A native, free, open-source Android tool designed for personal friction against excessive scrolling. Vivid graphite, lime, teal and coral interface. No cloud, accounts, subscriptions, advertising or analytics.

## Behavior

- **Base allowance: 20 minutes of Instagram each calendar day.**
- When the allowance is used, opening Instagram sends you to a **MathGate full-screen challenge**.
- A **correct typed numerical result** for an engineering/physics **Analysis I/II** problem earns **5 additional minutes of Instagram foreground usage**.
- Every further successful challenge earns another **+5 minutes**. No arbitrary hard cap on successfully earned bonuses.
- Remaining bonus minutes persist across multiple Instagram launches that day. Bonuses reset at local midnight.
- There is **no in-app pause, skip, or disable switch**.
- Challenges are generated from parameterized, locally checked vector calculus, double integrals, optimization, ODEs, improper integrals and series. Incorrect answers show a hint without unlocking access.

## Samsung screen-time integration

MathGate reads Android's actual foreground usage via `UsageStatsManager.queryEvents`, with local-midnight boundaries. That incorporates Instagram time used before installing MathGate (subject to the OS's available history). MathGate does **not** have direct access to Samsung Digital Wellbeing's private time counter; small differences can occur. The math screen is a foreground Android activity, **not an overlay**, so time spent solving problems is not intentionally counted as Instagram foreground time.

## Install on Samsung Android

Download the APK from the latest successful **Build Android APK** run in [GitHub Actions](https://github.com/fedefirefox/MathGate/actions) under **Artifacts → MathGate-debug-apk**. Extract `app-debug.apk`, then open it. This is a debug-signed development build.

If updating an already installed MathGate signed by this same GitHub Actions build, it should update in place; if Android reports a **signature conflict**, uninstall the old version first (which loses local bonus state).

After installing, enable both Android permissions:

1. **Settings → Apps → MathGate → ⋮ → Allow restricted settings** (necessary on many Samsung devices for sideloaded apps).
2. **Settings → Accessibility → Installed apps → MathGate Instagram Guard → On**.
3. Open MathGate and select **Grant system Usage Access**; enable MathGate under usage access.

MathGate must have these permissions to intercept Instagram and read its usage time.

## Important security and platform limits

- Accessibility service only observes foreground window transitions; it does **not** request accessibility window-content access or inspect messages/passwords.
- Android can still let the phone owner **disable Accessibility permissions, revoke Usage Access, force-stop, or uninstall** the app. A normal third-party APK cannot make itself un-disableable. There is no bypass control **inside MathGate**.
- Redirecting from Instagram relies on Android allowing the AccessibilityService to open the MathGate activity. Some Samsung builds might block background activity launches; if so, the app attempts to return to Home instead. This requires physical device testing.
- Using a modified Instagram package or browser version of Instagram is not covered.
- Numeric answers are validated with a small tolerance; arbitrary analytic derivations/proofs are not checked.
- This is a personal prototype and not a security product.

## Build

Requires JDK 17, Android SDK 35 and Gradle 8.9. CI builds an APK automatically for every push to main.

```bash
gradle assembleDebug
```

MIT licensed. Contributions welcome.
