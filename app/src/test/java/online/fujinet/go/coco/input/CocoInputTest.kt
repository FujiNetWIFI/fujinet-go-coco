package online.fujinet.go.coco.input

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the XRoar hkbd (USB HID) scancodes the on-screen CoCo keyboard sends and
 * the analog-joystick axis mapping. If the scancodes drift from src/hkbd.h the
 * keyboard silently mistranslates, and if the axis mapping drifts the CoCo's DAC
 * reads the wrong position, so pin the values.
 */
class CocoInputTest {

    @Test
    fun hidScancodesMatchHkbd() {
        assertEquals(0x04, Coco.SCAN_A)
        assertEquals(0x1e, Coco.SCAN_1)
        assertEquals(0x27, Coco.SCAN_0)
        assertEquals(0x28, Coco.SCAN_RETURN)
        assertEquals(0x29, Coco.SCAN_ESCAPE)
        assertEquals(0x2c, Coco.SCAN_SPACE)
        assertEquals(0x4a, Coco.SCAN_HOME)
        assertEquals(0x4f, Coco.SCAN_RIGHT)
        assertEquals(0x50, Coco.SCAN_LEFT)
        assertEquals(0x51, Coco.SCAN_DOWN)
        assertEquals(0x52, Coco.SCAN_UP)
        assertEquals(0xe0, Coco.SCAN_CONTROL_L)
        assertEquals(0xe1, Coco.SCAN_SHIFT_L)
    }

    @Test
    fun letterAndDigitScancodesAreContiguous() {
        assertEquals(0x04, Coco.scanForLetter('a'))
        assertEquals(0x1d, Coco.scanForLetter('z')) // hk_scan_z
        assertEquals(0x04 + 25, Coco.scanForLetter('Z'))
        // HID orders 1..9 then 0.
        assertEquals(0x1e, Coco.scanForDigit('1'))
        assertEquals(0x26, Coco.scanForDigit('9'))
        assertEquals(0x27, Coco.scanForDigit('0'))
    }

    @Test
    fun axisMappingCentresAndSaturates() {
        assertEquals(Coco.AXIS_CENTRE, Coco.axisValue(0f))
        assertEquals(Coco.AXIS_MIN, Coco.axisValue(-1f))
        assertEquals(Coco.AXIS_MAX, Coco.axisValue(1f))
        // Clamps beyond the unit range.
        assertEquals(Coco.AXIS_MIN, Coco.axisValue(-2f))
        assertEquals(Coco.AXIS_MAX, Coco.axisValue(2f))
        // Monotonic around centre.
        assert(Coco.axisValue(0.5f) > Coco.AXIS_CENTRE)
        assert(Coco.axisValue(-0.5f) < Coco.AXIS_CENTRE)
    }
}
