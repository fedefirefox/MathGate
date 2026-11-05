# Changelog

## v0.8.0 — Guided Samsung setup + iOS source preview
- On first Android launch, automatically guide users through Usage Access, the separate App info / Allow restricted settings step, and Accessibility.
- Verify actual permission states; include a repair path from the dashboard.
- Add illustrated promotional mockup to GitHub README, explicitly labeled as a mockup.
- Add initial native SwiftUI iPhone project using Apple Family Controls, Device Activity and Managed Settings, with Family Controls entitlements and device monitor extension.
- iOS installation is **not yet available**: Apple distribution approval/signing/TestFlight are required.
- Preserve Android's 20-minute allowance, +5-minute calculus reward, offline questions/memes, and app update controls.

## v0.7.0 — Victory UI layout + in-app updater
- Full aspect-ratio-preserving cat-meme display (no forced 255dp height or clipped caption).
- Bottom Claim +5:00 and Leave actions pinned above Android 15/16 system navigation insets.
- Official GitHub Release download from inside the app, including size/SHA-256 and APK signing identity validation.
- Android System installer handoff with explicit user confirmation.
- Permanent release signing workflow for a one-time owner-provided GitHub Actions Secret; no unsigned or throwaway-signed public releases.
- CI smoke-tests the release signing configuration with a throwaway certificate.
- In-place upgrades require a permanently stable APK signing identity; the initial transition from v0.6 needs one reinstall.
- Preserves 20 minutes free, 5 minutes per solved question, the cat reward, and signed equation/meme updates.

## v0.6.0 — Signed data and security review
- Owner-signed ECDSA P-256 manifest required for GitHub math and cat-meme updates.
- SHA-256 integrity checks for all downloaded JSON and WebP assets.
- Cached downloads are revalidated before use; unsigned changes fail closed to bundled data.
- Added local signing/verification tools and CI signature enforcement.
- Added detailed repository protection steps in SECURITY.md.
- No change to Instagram's base 20 minutes, +5 reward, or cat congratulations flow.

## v0.5.0
- Added a **Congrats!** reward Activity between a correct answer and the +5-minute Instagram credit.
- Illustrated daily AI-cat meme and 16 playful captions, rotating by local day.
- No award is credited until **Claim +5:00 · Open Instagram** is tapped. Pending rewards persist until the day changes, and are single-use.
- Added versioned GitHub-updatable cat meme catalogue and image assets, with offline fallback and size-checked downloads.
- Added **Download AI cat memes from GitHub** button.
- Added cat-meme catalogue validation in CI.
- Preserved the 20-minute Instagram allowance, difficulty selector, 54-problem bank, and GitHub equation updates.
- Cat images are generated in advance; **daily automated live image generation is not enabled**.

## v0.4.0
- Added practice-preview **Show another equation** refresh. Each tap shows a different exercise from the selected difficulty without revealing the answer.
- The preview refresh does not replace or bypass a live Instagram unlock challenge.
- Increased the public GitHub question library to **54 problems**: 18 Foundation, 18 Standard and 18 Advanced, including more conventional definite and improper integrals.
- Bundled question-bank revision 2 while keeping the in-app GitHub library update path.
- Preserved the 20-minute daily Instagram allowance and five-minute reward per correct response.

## v0.3.0
- 36 curated problems, 12 each at Foundation, Standard and Advanced difficulty.
- Added residue-theorem and PDE questions.
- Offline MathML equation display and safe typed arithmetic expressions.
- Difficulty selector without changing time restrictions.
- GitHub-synchronized problem-bank download.
- Check GitHub Releases for application updates.
- Added detailed Samsung Accessibility/restricted-settings instructions.
- Structural CI validation for the question bank.

## v0.2.0
- 20 minutes daily included, plus 5 minutes for each solved problem.
- Foreground Instagram usage tracking.
- Graphite/lime/teal design.

## v0.1.0
- Initial Android proof of concept.
