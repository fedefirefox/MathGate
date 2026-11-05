# MathGate for iPhone — SwiftUI / Apple Screen Time prototype

**Status: source-code preview, not a downloadable iOS app.**

The native iPhone implementation is included in this public repository under \`ios/\`. It uses Apple's privacy-preserving APIs:

- \`FamilyControls.AuthorizationCenter.requestAuthorization(for: .individual)\` for user consent.
- \`FamilyActivityPicker\` so the user explicitly selects Instagram.
- \`DeviceActivityCenter.startMonitoring\` for a 20-minute daily event threshold.
- \`DeviceActivityMonitor\` application extension to show Apple's system shield at the threshold.
- \`ManagedSettingsStore\` to remove the shield after a correctly solved numerical problem grants +5 minutes.

**Differences from Android:** iOS does not let MathGate grab foreground Instagram activity or automatically redirect an app into MathGate's question screen. The system displays an Apple Screen Time shield; the user must manually open MathGate to solve and claim their extension. Apple can also let individuals revoke permission or remove the blocker. The current iOS exercise view shows TeX source and a numerical input; polished mathematical typesetting, signed data sync, cat images and complete device-specific parity are future work.

## Project structure

- \`project.yml\`: XcodeGen project definition.
- \`App/\`: SwiftUI UI, app entry point and Screen Time controller.
- \`Monitor/\`: Device Activity monitor extension and Info.plist.
- \`Shared/\`: shared application-group state and opaque Family Controls app selection.
- \`Resources/problem_bank.json\`: bundled copy of 54 curated Analysis I/II exercises.

## Native setup (requires macOS/Xcode)

1. Install Xcode and XcodeGen on a Mac.
2. Run \`cd ios && xcodegen generate\`. Open \`MathGateIOS.xcodeproj\`.
3. Set unique iOS app and extension bundle identifiers, your Apple Developer Team, and an App Group that both targets share. Update \`MathGateShared.group\` and the two entitlements if you change it.
4. Add Family Controls capability for the app **and** its Device Activity Monitor extension.
5. Request Apple Family Controls **distribution entitlement** for both targets before submitting to TestFlight or the App Store: https://developer.apple.com/documentation/FamilyControls/requesting-the-family-controls-entitlement
6. Test on a real iPhone with a signed development provisioning profile. Family Controls cannot be reliably tested with simulator-only behavior.
7. Archive in Xcode, upload to App Store Connect, and distribute through TestFlight once approved. Apple must provision and sign the app. A GitHub APK/IPA file alone is **not** an installable consumer iPhone release.

## Current status / known limitations

- The project is set up for unsigned **iOS Simulator compilation** in GitHub Actions, *not* for iOS distribution. CI success would only prove compile correctness; it does not establish entitlement approval or actual shield behavior on iPhone.
- A screen-time threshold may be notified asynchronously, and the monitor callback requires device testing.
- Individual authorizations can be revoked; the app is not unbreakable.
- Only one user-selected app is supported in this prototype.
- The 54-problem bank is bundled and the answer field accepts decimal numbers only. Full Android-style expression parsing, signed live question/meme downloads, and image rewards are not yet implemented on iOS.
- Two Apple-specific blockers remain before a downloadable link: Family Controls distribution entitlement and Apple Developer signing/TestFlight distribution. These require the app owner's Apple Developer account and approval.

Authoritative docs: https://developer.apple.com/documentation/FamilyControls/requesting-the-family-controls-entitlement and https://developer.apple.com/documentation/deviceactivity/ and https://developer.apple.com/testflight/
