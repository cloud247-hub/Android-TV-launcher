package no.cloud247.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Får HOME-knappen til å åpne cloud247 – også på Philips/Google TV der HOME er låst til
 * standard-launcheren. Standard-launcheren beholdes (må IKKE deaktiveres), vi legger oss bare over.
 *
 * Gjør kun to ting, og ingenting i bakgrunnen ellers:
 *  1. Fanger HOME-tasten og åpner vår startskjerm.
 *  2. Hvis standard-launcheren likevel dukker opp (f.eks. etter oppstart), åpnes vår i stedet.
 */
class HomeButtonService : AccessibilityService() {

    private var lastLaunch = 0L

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openHome()
        return true // svelg både ned og opp, slik at systemet ikke også åpner standard-launcheren
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in STOCK_LAUNCHERS) openHome()
    }

    override fun onInterrupt() = Unit

    private fun openHome() {
        val now = SystemClock.uptimeMillis()
        if (now - lastLaunch < DEBOUNCE_MS) return
        lastLaunch = now
        try {
            startActivity(
                Intent(this, LauncherActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 400L

        /** Må samsvare med packageNames i res/xml/home_button_service.xml. */
        val STOCK_LAUNCHERS = setOf(
            "com.google.android.tvlauncher",          // Android TV-startskjerm (Philips)
            "com.google.android.leanbacklauncher",    // eldre Android TV
            "com.google.android.apps.tv.launcherx",   // Google TV
        )
    }
}
