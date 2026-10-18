package io.netbird.wear.ui

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import io.netbird.wear.data.StateStore
import io.netbird.wear.model.Command
import androidx.wear.compose.material3.MaterialTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StateStore.init(applicationContext)

        val initialCommand = intent
            ?.getStringExtra(EXTRA_COMMAND)
            ?.let { Command.fromWire(it) }
        intent?.removeExtra(EXTRA_COMMAND)

        val demoMode = intent?.getBooleanExtra(EXTRA_DEMO, false) ?: false
        val demoScreen = intent?.getStringExtra(EXTRA_DEMO_SCREEN) ?: "HOME"
        val demoLocale = intent?.getStringExtra(EXTRA_DEMO_LOCALE)

        setContent {
            if (!demoLocale.isNullOrBlank()) {
                val baseConfig = LocalConfiguration.current
                val customConfig = Configuration(baseConfig).apply {
                    setLocale(Locale(demoLocale))
                }
                val localizedContext = LocalContext.current.createConfigurationContext(customConfig)

                CompositionLocalProvider(
                    LocalConfiguration provides customConfig,
                    LocalContext provides localizedContext,
                ) {
                    MaterialTheme {
                        NetBirdWearApp(
                            initialCommand = initialCommand,
                            demoMode = demoMode,
                            demoScreen = demoScreen,
                        )
                    }
                }
            } else {
                MaterialTheme {
                    NetBirdWearApp(
                        initialCommand = initialCommand,
                        demoMode = demoMode,
                        demoScreen = demoScreen,
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_COMMAND = "io.netbird.wear.EXTRA_COMMAND"
        const val EXTRA_DEMO = "io.netbird.wear.EXTRA_DEMO"
        const val EXTRA_DEMO_SCREEN = "io.netbird.wear.EXTRA_DEMO_SCREEN"
        const val EXTRA_DEMO_LOCALE = "io.netbird.wear.EXTRA_DEMO_LOCALE"
    }
}
