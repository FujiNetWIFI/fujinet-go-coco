package online.fujinet.go.coco.input

/**
 * Constants for driving the XRoar Color Computer core from the Compose UI.
 *
 * Keyboard values are XRoar hkbd scancodes, which are USB HID usage codes (see
 * xroar src/hkbd.h); injected via EmulatorNative.nativeInjectKey. Machine / TV
 * values mirror coco_host.h. The analog joystick axis range is 0..65535 with
 * 32767 at centre (the CoCo's two 6-bit DAC axes), so [axisValue] maps a
 * normalized -1..1 stick onto it.
 */
object Coco {
    // --- machine selection (coco_host.h) ---
    const val MACHINE_COCO3 = 0
    const val MACHINE_COCO2 = 1

    // --- TV input / artifact mode (coco_host.h, matches XRoar TV_INPUT_*) ---
    const val TV_SVIDEO = 0        // clean, no artifact colours
    const val TV_COMPOSITE_BR = 1  // composite, blue-red artifact phase
    const val TV_COMPOSITE_RB = 2  // composite, red-blue artifact phase
    const val TV_RGB = 3           // RGB (CoCo 3)

    // --- composite cross-colour (artifact) renderer (matches XRoar VO_CMP_CCR_*) ---
    const val CCR_NONE = 0
    const val CCR_SIMPLE = 1
    const val CCR_5BIT = 2
    const val CCR_PARTIAL = 3
    const val CCR_SIMULATED = 4

    // --- joystick ---
    const val PORT_RIGHT = 0
    const val PORT_LEFT = 1
    const val AXIS_X = 0
    const val AXIS_Y = 1
    const val AXIS_MIN = 0
    const val AXIS_CENTRE = 32767
    const val AXIS_MAX = 65535

    /** Map a normalized axis (-1..1) to the CoCo DAC range (0..65535, 32767=centre). */
    fun axisValue(v: Float): Int =
        ((v.coerceIn(-1f, 1f) + 1f) / 2f * AXIS_MAX).toInt().coerceIn(AXIS_MIN, AXIS_MAX)

    // --- hkbd scancodes (USB HID usage codes; src/hkbd.h) ---
    const val SCAN_A = 0x04
    const val SCAN_1 = 0x1e
    const val SCAN_0 = 0x27
    const val SCAN_RETURN = 0x28      // ENTER
    const val SCAN_ESCAPE = 0x29      // BREAK
    const val SCAN_BACKSPACE = 0x2a
    const val SCAN_SPACE = 0x2c
    const val SCAN_MINUS = 0x2d
    const val SCAN_EQUAL = 0x2e
    const val SCAN_SEMICOLON = 0x33
    const val SCAN_APOSTROPHE = 0x34
    const val SCAN_COMMA = 0x36
    const val SCAN_PERIOD = 0x37
    const val SCAN_SLASH = 0x38
    const val SCAN_HOME = 0x4a         // CLEAR
    const val SCAN_RIGHT = 0x4f
    const val SCAN_LEFT = 0x50
    const val SCAN_DOWN = 0x51
    const val SCAN_UP = 0x52
    const val SCAN_CONTROL_L = 0xe0    // CoCo 3 CTRL
    const val SCAN_SHIFT_L = 0xe1
    const val SCAN_ALT_L = 0xe2

    /** Scancode for an ASCII letter 'a'..'z'. */
    fun scanForLetter(c: Char): Int = SCAN_A + (c.lowercaseChar() - 'a')

    /** Scancode for a digit '0'..'9' (HID puts 1..9 before 0). */
    fun scanForDigit(c: Char): Int = if (c == '0') SCAN_0 else SCAN_1 + (c - '1')
}
