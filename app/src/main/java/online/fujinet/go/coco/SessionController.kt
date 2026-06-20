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

    @Volatile var tvInput: Int = prefs.getInt(KEY_TV, Coco.TV_COMPOSITE)
        private set

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
                p.runtimeRoot, p.configPath, p.sdPath, p.dataPath, p.romPath, machine, tvInput,
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

    // --- machine / display ---------------------------------------------------

    /** Switch CoCo 2 <-> CoCo 3 (restarts XRoar with the matching HDB-DOS ROM). */
    fun setMachine(newMachine: Int) {
        if (machine == newMachine) return
        machine = newMachine
        // CoCo 2 has no RGB output; force composite there.
        if (machine == Coco.MACHINE_COCO2) tvInput = Coco.TV_COMPOSITE
        prefs.edit().putInt(KEY_MACHINE, machine).putInt(KEY_TV, tvInput).apply()
        EmulatorNative.nativeSwitchMachine(machine, tvInput)
    }

    /** Set the CoCo 3 display output (composite/RGB); applied live. */
    fun setTvInput(newTv: Int) {
        if (machine == Coco.MACHINE_COCO2) return  // composite only
        if (tvInput == newTv) return
        tvInput = newTv
        prefs.edit().putInt(KEY_TV, tvInput).apply()
        EmulatorNative.nativeSetTvInput(tvInput)
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
        private const val KEY_MACHINE = "machine"
        private const val KEY_TV = "tv_input"
    }
}
