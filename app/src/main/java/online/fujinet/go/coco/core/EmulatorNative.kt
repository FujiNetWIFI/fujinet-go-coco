package online.fujinet.go.coco.core

import android.view.Surface

/**
 * JNI bridge to libcococore.so (the headless XRoar Color Computer core + the
 * Android host + the session runtime, plus the in-process FujiNet runtime which
 * is dlopen'd on demand).
 *
 * The native side drives XRoar one frame per `xroar_run()` on a worker thread;
 * the FujiNet runtime runs in-process and the two meet over the Becker port
 * (DriveWire-over-TCP) on loopback TCP 65504 (FujiNet listens, XRoar's becker
 * client connects out).
 */
object EmulatorNative {
    init {
        // libfujinet.so is dlopen'd by the native layer on demand; we only load
        // our own core, which statically links the XRoar emulator.
        System.loadLibrary("cococore")
    }

    /**
     * Starts the session. [machine] is [Coco.MACHINE_COCO3]/[Coco.MACHINE_COCO2];
     * [tvInput] is [Coco.TV_COMPOSITE]/[Coco.TV_RGB]. [romPath] is the writable
     * directory the CoCo ROMs were extracted to.
     */
    external fun nativeStartSession(
        runtimeRoot: String,
        configPath: String,
        sdPath: String,
        dataPath: String,
        romPath: String,
        machine: Int,
        tvInput: Int,
    )

    external fun nativeStopSession()
    external fun nativeIsRunning(): Boolean

    /** Switch CoCo 2 <-> CoCo 3 (restarts the emulator; FujiNet stays up). */
    external fun nativeSwitchMachine(machine: Int, tvInput: Int)

    /** Live RGB/composite switch (CoCo 3 only). */
    external fun nativeSetTvInput(tvInput: Int)

    external fun nativeCurrentMachine(): Int
    external fun nativeCurrentTvInput(): Int

    external fun nativeAttachSurface(surface: Surface)
    external fun nativeDetachSurface()
    external fun nativeRequestReset()

    /** Injects a key by XRoar hkbd (USB HID) scancode; see [Coco]. */
    external fun nativeInjectKey(down: Boolean, scancode: Int)

    /** Analog axis for [port] (0=right,1=left), [axis] (0=X,1=Y), value 0..65535 (32767=centre). */
    external fun nativeSetJoystickAxis(port: Int, axis: Int, value: Int)

    /** Joystick button for [port], [button] (0/1). */
    external fun nativeSetJoystickButton(port: Int, button: Int, pressed: Boolean)

    /**
     * Blocks (bounded) until [out] can be filled with a full block of interleaved
     * stereo signed-16 samples (44100 Hz), silence-padding on underrun.
     */
    external fun nativeFillAudio(out: ShortArray): Int

    /** Toggle audio drain; pass false on shutdown to unblock a waiting fill. */
    external fun nativeAudioSetActive(active: Boolean)
}
