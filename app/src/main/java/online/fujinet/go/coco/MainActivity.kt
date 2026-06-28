package online.fujinet.go.coco

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import online.fujinet.go.coco.fujinet.FujiNetWebViewActivity
import online.fujinet.go.coco.input.GameControllerMapper
import online.fujinet.go.coco.input.HardwareKeyboard
import online.fujinet.go.coco.ui.EmulatorScreen
import online.fujinet.go.coco.ui.theme.FujiNetGoCoCoTheme

/**
 * FujiNet Go CoCo main screen: the Color Computer display plus the on-screen
 * keyboard / analog joystick and a control bar. The native layer (headless XRoar
 * core + the in-process FujiNet runtime over the Becker port) is owned by
 * [EmulatorSessionService] (a foreground service) so it keeps running across
 * activity changes (e.g. the FujiNet web admin) and while backgrounded. The
 * session itself is a process singleton; the Power button stops both.
 */
class MainActivity : ComponentActivity() {

    private lateinit var session: SessionController

    // Routes a Bluetooth/USB game controller to the CoCo analog joystick + buttons.
    private val gamepad by lazy {
        GameControllerMapper(
            onAxis = { x, y -> session.joystick(x, y) },
            onButton = { index, pressed -> session.joystickButton(index, pressed) },
        )
    }

    // Routes an attached hardware keyboard to the CoCo keyboard.
    private val keyboard by lazy {
        HardwareKeyboard(
            onDown = { scancode -> session.keyDown(scancode) },
            onUp = { scancode -> session.keyUp(scancode) },
        )
    }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == EmulatorSessionService.ACTION_SHUTDOWN) {
            shutdown()
            return
        }
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Hold clocks steady over a long emulation session (thermals permitting)
        // rather than letting DVFS oscillate the 60Hz loop off schedule.
        window.setSustainedPerformanceMode(true)
        session = SessionController.get(applicationContext)

        maybeRequestNotificationPermission()
        EmulatorSessionService.start(this)

        setContent {
            FujiNetGoCoCoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    EmulatorScreen(
                        session = session,
                        onOpenFujiNet = ::openFujiNet,
                        onShutdown = ::shutdown,
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == EmulatorSessionService.ACTION_SHUTDOWN) {
            shutdown()
        }
    }

    private fun openFujiNet() {
        startActivity(Intent(this, FujiNetWebViewActivity::class.java))
    }

    /** Stop the emulator + FujiNet and close the app. */
    private fun shutdown() {
        EmulatorSessionService.shutdown(this)
        finishAndRemoveTask()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (::session.isInitialized && gamepad.onMotion(event)) return true
        return super.onGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::session.isInitialized) {
            // Game controller first, then a hardware keyboard. A TV remote's D-pad is
            // claimed by neither, so it falls through to Compose focus navigation.
            if (gamepad.onKey(event)) return true
            if (keyboard.onKey(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    // No session.stop() here: the foreground service owns the session's lifetime
    // so it survives this activity being finished. Stopping is explicit, via the
    // Power button -> shutdown().
}
