# Changelog

## v0.9.3 — Correct midnight rollover and daily usage window
- Removed pre-aggregated Android daily usage statistics that can include expanded intervals outside the requested midnight boundary.
- Derive today's Instagram foreground time from timestamped events clipped to local midnight, with previous-local-day lookback for sessions spanning midnight.
- Account for 23- or 25-hour local days and foreground Instagram activity begun before midnight.
- Automatically refresh the dashboard at midnight and display the local calendar day.
- Reset daily earned minutes and pending rewards on date change, retaining completed-problem history.
- Added ten unit tests for midnight rollover, screen-off, resumed sessions, and DST transitions.
- Preserved 20 minutes free, +5 minutes per solved challenge, and Accessibility overlay.

## v0.9.2 — Instagram enforcement and diagnostics
- Replaced background Activity launch with a touch-intercepting TYPE_ACCESSIBILITY_OVERLAY on Instagram when the allowance expires.
- Added Android usage-event foreground recovery rather than trusting a single volatile Accessibility event flag.
- Daily usage now considers both timestamped usage events and Android's aggregate foreground-duration records to avoid missed intervals.
- Fail-closed behavior when Usage Access is missing; attempt HOME if overlay creation fails.
- Added Accessibility service heartbeat, last Instagram detection, last successful block and error/action telemetry on the dashboard (local only).
- Added a **TEST LOCK NOW** button to simulate a temporary block for 90 seconds, without consuming time or granting a bonus.
- Added threshold, extension, missing-permission, test-mode and lock-screen unit tests.
- Preserved 20-minute base, +5-minute math unlocks, non-repeating problem IDs and cat reward.

## v0.9.1 — Samsung Accessibility setup order
- First-time wizard now follows Usage Access → Accessibility FIRST (trigger restricted-setting denial) → App info / ⋮ Allow restricted settings → Accessibility AGAIN (enable).
- Treat opening Settings screens as guidance only. Setup only reports success after actual Usage Access and Accessibility are granted.
- Explain why the three-dot App info menu may be hidden until the initial denied attempt; skip steps 3–4 if Accessibility can be enabled directly.
- Update README instructions to match Samsung's actual flow. No change to daily limits or math problem selection.

## v0.9.0 — Non-repeating math challenge selection
- Remember successfully completed question IDs per difficulty in persistent app settings.
- Do not repeat a completed question until all available questions at that difficulty have been solved.
- Prefer changing the subject family between consecutive unlocks (e.g. calculus integral → PDE/residue problem), if unseen candidates permit.
- Keep the challenge unchanged after failed answers, task switching or app restarts.
- Move to the next question only after the user actually claims the +5 minute reward.
- Preserve the last question across a fresh cycle to avoid immediate repeats.
- Automated selection tests run in Android GitHub Actions CI; unchanged 20 min daily baseline and +5 min reward.
- Current Android bank: 54 questions, 18 at each difficulty. This update changes app code and therefore needs a new APK.

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
