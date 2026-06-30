package online.fujinet.go.coco

import android.content.Context
import android.util.Log
import android.view.Surface
import online.fujinet.go.coco.core.EmulatorNative
import online.fujinet.go.coco.input.Coco
import kotlin.concurrent.thread

/**
 * Owns the lifetime of one CoCo session: stages the FujiNet runtime + XRoar ROM
 * assets, starts the native emulator (headless XRoar) and the in-process FujiNet
 * runtime (joined over the Becker port / DriveWire-over-TCP on loopback TCP
 * 65504), streams audio, and forwards input.
 *
 * A process-wide singleton so the activity and the foreground service share one
 * running session. The selected machine (CoCo 2/3) and CoCo 3 display mode
 * (composite/RGB) are persisted so they survive relaunch.
 */
class SessionController private constructor(private val context: Context) {

    @Volatile private var started = false
    @Volatile private var paths: RuntimeInstaller.Paths? = null
    private val audio = AudioOutput()
    private val lock = Any()

    private val prefs = context.getSharedPreferences("coco", Context.MODE_PRIVATE)

    @Volatile var machine: Int = prefs.getInt(KEY_MACHINE, Coco.MACHINE_COCO3)
        private set

    @Volatile var tvInput: Int = prefs.getInt(KEY_TV, Coco.TV_COMPOSITE_BR)
        private set

    @Volatile var ccr: Int = prefs.getInt(KEY_CCR, Coco.CCR_5BIT)
        private set

    /** Live haptic-feedback toggles (persisted; no session restart). */
    var keyboardHapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_KEYBOARD_HAPTICS, true)
        set(value) { prefs.edit().putBoolean(KEY_KEYBOARD_HAPTICS, value).apply() }

    var joystickHapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_JOYSTICK_HAPTICS, true)
        set(value) { prefs.edit().putBoolean(KEY_JOYSTICK_HAPTICS, value).apply() }

    /** The FujiNet SD directory (where imported media lands), once staged. */
    val sdPath: String? get() = paths?.sdPath

    fun startIfNeeded() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        thread(name = "coco-bootstrap") { launch() }
    }

    private fun launch() {
        try {
            val p = paths ?: RuntimeInstaller(context.applicationContext).install().also { paths = it }
            EmulatorNative.nativeStartSession(
                p.runtimeRoot, p.configPath, p.sdPath, p.dataPath, p.romPath, machine, tvInput, ccr,
            )
            audio.start()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start session", t)
            synchronized(lock) { started = false }
        }
    }

    fun stop() {
        synchronized(lock) { if (!started) return }
        audio.stop()
        EmulatorNative.nativeStopSession()
        synchronized(lock) { started = false }
    }

    fun attachSurface(surface: Surface) = EmulatorNative.nativeAttachSurface(surface)
    fun detachSurface() = EmulatorNative.nativeDetachSurface()

    // --- machine / artifact settings -----------------------------------------

    /** Switch CoCo 2 <-> CoCo 3 in place (matching HDB-DOS ROM; FujiNet stays up). */
    fun setMachine(newMachine: Int) {
        if (machine == newMachine) return
        machine = newMachine
        // CoCo 2 has no RGB output; fall back to composite there.
        if (machine == Coco.MACHINE_COCO2 && tvInput == Coco.TV_RGB) tvInput = Coco.TV_COMPOSITE_BR
        prefs.edit().putInt(KEY_MACHINE, machine).putInt(KEY_TV, tvInput).apply()
        EmulatorNative.nativeSwitchMachine(machine, tvInput)
    }

    /** Set the TV input / artifact mode (Coco.TV_*); applied live. RGB is CoCo 3 only. */
    fun setTvInput(newTv: Int) {
        var tv = newTv
        if (machine == Coco.MACHINE_COCO2 && tv == Coco.TV_RGB) tv = Coco.TV_COMPOSITE_BR
        if (tvInput == tv) return
        tvInput = tv
        prefs.edit().putInt(KEY_TV, tvInput).apply()
        EmulatorNative.nativeSetTvInput(tvInput)
    }

    /** Set the composite cross-colour (artifact) renderer (Coco.CCR_*); applied live. */
    fun setCcr(newCcr: Int) {
        if (ccr == newCcr) return
        ccr = newCcr
        prefs.edit().putInt(KEY_CCR, ccr).apply()
        EmulatorNative.nativeSetCcr(ccr)
    }

    // --- keyboard ------------------------------------------------------------

    /** Tap a key (press then release) by hkbd scancode. */
    fun tapKey(scancode: Int) {
        EmulatorNative.nativeInjectKey(true, scancode)
        EmulatorNative.nativeInjectKey(false, scancode)
    }

    fun keyDown(scancode: Int) = EmulatorNative.nativeInjectKey(true, scancode)
    fun keyUp(scancode: Int) = EmulatorNative.nativeInjectKey(false, scancode)

    /** Hard reset (reboots the CoCo). */
    fun reset() = EmulatorNative.nativeRequestReset()

    // --- joystick ------------------------------------------------------------

    /**
     * Set the analog joystick position from a normalized stick (-1..1 each axis).
     * Defaults to the right port (port 0), which is what most CoCo games read.
     */
    fun joystick(x: Float, y: Float, port: Int = Coco.PORT_RIGHT) {
        EmulatorNative.nativeSetJoystickAxis(port, Coco.AXIS_X, Coco.axisValue(x))
        EmulatorNative.nativeSetJoystickAxis(port, Coco.AXIS_Y, Coco.axisValue(y))
    }

    /** Joystick button: 0 = primary fire, 1 = secondary (CoCo 3 only). */
    fun joystickButton(index: Int, pressed: Boolean, port: Int = Coco.PORT_RIGHT) {
        EmulatorNative.nativeSetJoystickButton(port, index, pressed)
    }

    companion object {
        @Volatile private var instance: SessionController? = null

        fun get(context: Context): SessionController =
            instance ?: synchronized(this) {
                instance ?: SessionController(context.applicationContext).also { instance = it }
            }

        private const val TAG = "FujiCoCo"
        private const val KEY_KEYBOARD_HAPTICS = "keyboardHaptics"
        private const val KEY_JOYSTICK_HAPTICS = "joystickHaptics"
        private const val KEY_MACHINE = "machine"
        // "tv_mode" (not the old "tv_input"): the enum's meaning changed (now 0..3
        // = S-Video / composite-BR / composite-RB / RGB), so reset to the default.
        private const val KEY_TV = "tv_mode"
        private const val KEY_CCR = "ccr"
    }
}
