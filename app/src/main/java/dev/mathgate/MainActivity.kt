package dev.mathgate

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.view.accessibility.AccessibilityManager
import android.widget.LinearLayout

class MainActivity : Activity() {
    private var feedback: String? = null
    private var releaseLink: String? = null
    private var isUpdating = false
    private var pendingApk: AppUpdater.Prepared? = null
    private var previewId: String? = null
    private var previewLevel: String? = null
    private var activeScroll: android.widget.ScrollView? = null

    /**
     * A practice preview only. It does not change the locked challenge's seed,
     * the number of earned minutes, or the daily Instagram limit.
     */
    private fun nextPreview() {
        val level = GateState.difficulty(this)
        val others = try {
            LibraryStore.current(this).problems
                .filter { it.level == level && it.id != previewId }
        } catch (_: Exception) {
            emptyList()
        }
        if (others.isNotEmpty()) {
            previewId = others.random().id
            previewLevel = level
        }
        render(preserveScroll = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GateUi.styleBars(this)
    }

    override fun onResume() {
        super.onResume()
        if (!GateState.hasUsageAccess(this) || !accessibilityEnabled()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        render()
        val waiting = pendingApk
        if (waiting != null && AppUpdater.mayInstall(this)) {
            pendingApk = null
            try {
                AppUpdater.launchInstaller(this, waiting)
            } catch (error: Exception) {
                feedback = "Android installer could not open: " + error.message
                render()
            }
        }
    }

    private fun accessibilityEnabled(): Boolean {
        val mgr = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return mgr.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name == GateService::class.java.name
            }
    }

    private fun testInstagramBlock() {
        if (!GateState.hasUsageAccess(this) || !accessibilityEnabled()) {
            feedback = "Both Android permissions are needed to test the Instagram guard."
            render(preserveScroll = true)
            return
        }
        val launch = packageManager.getLaunchIntentForPackage(GateState.INSTAGRAM)
        if (launch == null) {
            feedback = "Instagram app not found. This guard only supports com.instagram.android."
            render(preserveScroll = true)
            return
        }
        // Temporary stricter test, never a relaxation of the daily allowance.
        GateDiagnostics.startLockTest(this)
        try {
            startActivity(launch)
        } catch (error: Exception) {
            GateDiagnostics.endLockTest(this)
            feedback = "Cannot launch Instagram for the lock test: " + error.message
            render(preserveScroll = true)
        }
    }

    private fun checkQuestions() {
        if (isUpdating) return
        isUpdating = true
        feedback = "Checking GitHub question library…"
        render()
        LibraryStore.checkForUpdates(this) { result ->
            runOnUiThread {
                isUpdating = false
                feedback = result
                render()
            }
        }
    }

    private fun checkCatMemes() {
        if (isUpdating) return
        isUpdating = true
        feedback = "Checking today's cat meme pack on GitHub…"
        render()
        CatMemeStore.checkForUpdates(this) { result ->
            runOnUiThread {
                isUpdating = false
                feedback = result
                render()
            }
        }
    }

    private fun checkApplication() {
        if (isUpdating) return
        isUpdating = true
        feedback = "Checking GitHub for a compatible, signed MathGate update…"
        render(preserveScroll = true)
        AppUpdater.downloadNewest(this) { result ->
            runOnUiThread {
                isUpdating = false
                result.onSuccess { prepared ->
                    feedback = "Downloaded and verified " + prepared.tag +
                        ". Android will request your approval to update."
                    if (AppUpdater.mayInstall(this)) {
                        try {
                            AppUpdater.launchInstaller(this, prepared)
                        } catch (error: Exception) {
                            feedback = "Installer error: " + (error.message ?: "unknown")
                        }
                    } else {
                        pendingApk = prepared
                        feedback = "Please allow MathGate to install updates from this source. " +
                            "Return to MathGate; Android will then open the installer."
                        AppUpdater.openInstallPermission(this)
                    }
                }.onFailure { error ->
                    feedback = error.message ?: "Update could not be verified"
                    releaseLink = "https://github.com/fedefirefox/MathGate/releases"
                }
                render(preserveScroll = true)
            }
        }
    }

    private fun render(preserveScroll: Boolean = false) {
        val oldScrollY = if (preserveScroll) activeScroll?.scrollY ?: 0 else 0
        val root = GateUi.root(this)
        val scroll = GateUi.scroll(this, root)
        activeScroll = scroll
        setContentView(scroll)
        val usageOK = GateState.hasUsageAccess(this)
        val guardOK = accessibilityEnabled()
        val used = GateState.usedMs(this)
        val allowance = GateState.allowanceMs(this)
        val bonus = GateState.bonusCount(this)
        val level = GateState.difficulty(this)
        val problemCount = LibraryStore.count(this)
        val libraryRevision = LibraryStore.revision(this)

        GateUi.add(root, GateUi.text(this, "●   MATHGATE   /   INSTAGRAM",
            13f, GateUi.TEAL, true), bottom = 13)
        GateUi.add(root, GateUi.text(this, "LESS SCROLL.\nMORE SOLVING.",
            33f, GateUi.INK, true))
        GateUi.add(root, GateUi.text(this,
            "20 minutes included. Solve a real Analysis I/II problem to earn five more.",
            15f, GateUi.MUTED), bottom = 21)

        val summary = GateUi.panel(this, true)
        GateUi.add(summary, GateUi.text(this, "TODAY'S INSTAGRAM USAGE",
            12f, GateUi.LIME, true))
        GateUi.add(summary, GateUi.text(this,
            if (used < 0) "Permission required"
            else GateState.timeLabel(used) + " / " + GateState.timeLabel(allowance),
            if (used < 0) 22f else 30f, GateUi.WHITE, true),
            top = 10, bottom = 9)
        GateUi.add(summary, GateUi.progress(this,
            if (used < 0) 0f else used.toFloat() / allowance.toFloat()),
            bottom = 12)
        GateUi.add(summary, GateUi.text(this,
            if (used < 0) "Usage Access not granted"
            else GateState.timeLabel(GateState.remainingMs(this)) +
                " remaining · " + bonus + " equations solved",
            14f, GateUi.WHITE))
        GateUi.add(root, summary, bottom = 20)

        GateUi.add(root, GateUi.button(this,
            "View permission setup checklist", GateUi.SOFT, GateUi.INK) {
            startActivity(Intent(this, SetupActivity::class.java))
        }, bottom = 12)

        val permitted = usageOK && guardOK
        val running = GateDiagnostics.serviceResponsive(this)
        val active = permitted && running
        val status = GateUi.panel(this)
        GateUi.add(status, GateUi.text(this,
            when {
                !permitted -> "●  INSTAGRAM GUARD NOT CONFIGURED"
                !running -> "●  WARNING: SERVICE NOT RESPONDING"
                else -> "●  GUARD SERVICE RUNNING"
            },
            14f, if (active) GateUi.TEAL else GateUi.CORAL, true))
        GateUi.add(status, GateUi.text(this,
            when {
                !permitted -> "Usage Access and Accessibility permission are both required."
                !running -> "Accessibility is enabled, but its background monitoring loop has no recent heartbeat. Toggle MathGate Instagram Guard OFF and ON in Accessibility settings."
                else -> "The service is responding. Use Test lock now to verify that Instagram is actually covered on your Samsung."
            }, 14f, GateUi.MUTED), bottom = 8)
        GateUi.add(status, GateUi.text(this,
            "Service heartbeat: " + GateDiagnostics.elapsedLabel(
                GateDiagnostics.lastHeartbeatMs(this)) +
            "\nInstagram last detected: " + GateDiagnostics.elapsedLabel(
                GateDiagnostics.lastInstagramAt(this)) +
            "\nLast blocked: " + GateDiagnostics.elapsedLabel(
                GateDiagnostics.lastOverlayAt(this)) +
            "\nLast action: " + GateDiagnostics.lastAction(this),
            12f, GateUi.MUTED))
        GateUi.add(root, status, bottom = 13)

        if (!permitted) {
            GateUi.add(root, GateUi.button(this,
                "Review Samsung permission setup",
                GateUi.TEAL, GateUi.WHITE) {
                startActivity(Intent(this, SetupActivity::class.java))
            }, bottom = 12)
        } else {
            if (!running) {
                GateUi.add(root, GateUi.button(this,
                    "Restart guard in Accessibility settings",
                    GateUi.CORAL, GateUi.WHITE) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }, bottom = 10)
            }
            GateUi.add(root, GateUi.button(this,
                "TEST LOCK NOW · no need to wait 20 min",
                GateUi.LIME, GateUi.INK) {
                testInstagramBlock()
            }, bottom = 9)
            GateUi.add(root, GateUi.text(this,
                "Opens Instagram and requests a temporary 90-second block. A full-screen MathGate lock should cover it within a few seconds. Test mode never adds Instagram minutes. Return to MathGate to inspect the last blocked time.",
                13f, GateUi.MUTED), bottom = 14)
        }

        GateUi.add(root, GateUi.text(this, "CHALLENGE DIFFICULTY",
            14f, GateUi.TEAL, true), bottom = 8)
        val difficultyBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        listOf(
            "foundation" to "Foundation",
            "standard" to "Standard",
            "advanced" to "Advanced"
        ).forEach { (value, name) ->
            val selected = level == value
            val control = GateUi.button(this, name,
                if (selected) GateUi.LIME else GateUi.SOFT,
                GateUi.INK) {
                GateState.setDifficulty(this, value)
                previewId = null
                previewLevel = null
                render(preserveScroll = true)
            }
            difficultyBar.addView(control,
                LinearLayout.LayoutParams(0, GateUi.dp(this, 55), 1f).apply {
                    rightMargin = GateUi.dp(this@MainActivity, 4)
                })
        }
        GateUi.add(root, difficultyBar, bottom = 6)
        GateUi.add(root, GateUi.text(this, when (level) {
            "foundation" -> "Foundation · refresh key concepts before more difficult work."
            "advanced" -> "Advanced · nontrivial contour integrals and PDEs, often 5–10 minutes."
            else -> "Standard · core university-level Analysis I/II, around 5–10 minutes."
        }, 13f, GateUi.MUTED), bottom = 18)

        val candidatePreviews = try {
            LibraryStore.current(this).problems.filter { it.level == level }
        } catch (_: Exception) { emptyList() }
        val example = (if (previewLevel == level) {
            candidatePreviews.firstOrNull { it.id == previewId }
        } else null) ?: candidatePreviews.randomOrNull()?.also {
            previewId = it.id
            previewLevel = level
        }
        if (example != null) {
            val preview = GateUi.panel(this)
            GateUi.add(preview, GateUi.text(this,
                "EQUATION PREVIEW  /  " + example.topic.uppercase(),
                12f, GateUi.TEAL, true), bottom = 5)
            GateUi.add(preview, GateUi.text(this,
                example.prompt, 14f, GateUi.MUTED), bottom = 5)
            GateUi.add(preview, MathRenderer.create(this, example.latex), bottom = 9)
            GateUi.add(preview, GateUi.button(this,
                "↻  Show another equation", GateUi.LIME, GateUi.INK
            ) { nextPreview() }, bottom = 9)
            GateUi.add(preview, GateUi.text(this,
                "Practice preview only. Refreshing does not change or skip the real Instagram unlock challenge.",
                12f, GateUi.MUTED))
            GateUi.add(root, preview, bottom = 18)
        }

        GateUi.add(root, GateUi.text(this, "QUESTION LIBRARY",
            14f, GateUi.TEAL, true), bottom = 5)
        GateUi.add(root, GateUi.text(this,
            problemCount.toString() + " exercises · revision " +
                libraryRevision + " · offline · signed GitHub updates",
            14f, GateUi.MUTED), bottom = 11)
        GateUi.add(root, GateUi.button(this,
            "↻  Download new equations from GitHub",
            GateUi.TEAL, GateUi.WHITE) { checkQuestions() }, bottom = 8)
        GateUi.add(root, GateUi.button(this,
            "↻  Download AI cat memes from GitHub",
            GateUi.TEAL, GateUi.WHITE) { checkCatMemes() }, bottom = 8)
        GateUi.add(root, GateUi.button(this,
            "Update MathGate directly from GitHub", GateUi.SOFT, GateUi.INK) {
            checkApplication()
        }, bottom = 8)
        feedback?.let {
            GateUi.add(root, GateUi.text(this, it, 13f,
                GateUi.MUTED), bottom = 7)
        }
        releaseLink?.let { link ->
            GateUi.add(root, GateUi.button(this,
                "Open GitHub download page", GateUi.SOFT, GateUi.INK) {
                ReleaseChecker.open(this, link)
            }, bottom = 8)
        }

        GateUi.add(root, GateUi.text(this, "NO SKIP. NO IN-APP DISABLE.",
            13f, GateUi.TEAL, true), top = 16, bottom = 5)
        GateUi.add(root, GateUi.text(this,
            "Your earned minutes remain available across Instagram sessions until midnight. " +
                "Difficulty changes never reset the quota. Android can still revoke permissions " +
                "or uninstall a third-party app.",
            13f, GateUi.MUTED), bottom = 12)
        GateUi.add(root, GateUi.button(this, "Refresh usage",
            GateUi.SOFT, GateUi.INK) { render() })
        if (preserveScroll) scroll.post { scroll.scrollTo(0, oldScrollY) }
    }
}
