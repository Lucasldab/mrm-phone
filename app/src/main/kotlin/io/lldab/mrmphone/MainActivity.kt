package io.lldab.mrmphone

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import io.lldab.mrmphone.ui.AppNav
import io.lldab.mrmphone.ui.theme.MrmTheme

class MainActivity : ComponentActivity() {
    // Consumed by AppNav; a new intent (singleTask) replaces it.
    private val pendingLink = mutableStateOf<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Nothing here should be autofilled: password managers otherwise offer
        // to save the server token as a login (Bitwarden did, 2026-11-07).
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (savedInstanceState == null) pendingLink.value = DeepLink.parse(intent?.dataString)
        val app = application as MrmApp
        setContent {
            MrmTheme {
                AppNav(
                    container = app.container,
                    deepLink = pendingLink.value,
                    onDeepLinkHandled = { pendingLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLink.parse(intent.dataString)?.let { pendingLink.value = it }
    }
}
