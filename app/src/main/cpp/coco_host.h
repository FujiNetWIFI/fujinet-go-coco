#ifndef COCO_HOST_H
#define COCO_HOST_H

#include <stdint.h>

// Android host for the XRoar Tandy Color Computer core.
//
// libcococore.so statically links a headless XRoar build. This host drives the
// core via xroar_init()/xroar_run() on the emulator thread and owns the shared
// state the custom XRoar modules talk to:
//
//   * ui_android / vo (xroar_android/ui_android.c) calls cocohost_emit_frame()
//     once per emulated frame with the rendered RGBA8888 viewport.
//   * ao_android (xroar_android/ao_android.c) calls cocohost_push_audio() with
//     completed stereo S16 buffers; the JNI audio feeder drains them with
//     cocohost_fill_audio().
//   * joystick_android (xroar_android/joystick_android.c) reads the per-port
//     analog axis / button state set from Kotlin via cocohost_set_joystick_*.
//
// Keyboard, reset and TV-input changes are queued and applied on the emulator
// thread at the top of cocohost_core_run_frame() (XRoar is single-threaded).

#ifdef __cplusplus
extern "C" {
#endif

// Machine selection (also picks the matching HDB-DOS Becker ROM).
enum {
    COCO_MACHINE_COCO3 = 0,  // xroar -machine coco3, becker ROM hdbdw3bc3
    COCO_MACHINE_COCO2 = 1,  // xroar -machine coco2bus, becker ROM hdbdw3bck
};

// CoCo 3 display output (ignored on CoCo 2).
enum {
    COCO_TV_COMPOSITE = 0,  // xroar -tv-input cmp-br (NTSC composite, artifact colours)
    COCO_TV_RGB = 1,        // xroar -tv-input rgb    (TV_INPUT_RGB)
};

// --- frame sink (set by the session; called on the emulator thread) ---------
typedef void (*CocoFrameSink)(const uint32_t* rgba8888, int width, int height, void* user);
void cocohost_set_frame_sink(CocoFrameSink sink, void* user);

// Where XRoar should look for ROMs (the runtime-extracted roms dir). Must be set
// before cocohost_core_start(). Copied internally.
void cocohost_set_rompath(const char* path);

// Becker port (DriveWire-over-TCP) endpoint XRoar connects out to. FujiNet
// listens here. Defaults to 127.0.0.1:65504.
void cocohost_set_becker_endpoint(const char* ip, int port);

// --- core lifecycle (call on the emulator thread) ---------------------------
// Builds argv and runs xroar_init()/xroar_init_finish(). machine is COCO_MACHINE_*,
// tvInput is COCO_TV_*. Returns 0 on failure.
int  cocohost_core_start(int machine, int tvInput);
void cocohost_core_run_frame(void);   // advances one ~60Hz frame of emulation
void cocohost_core_stop(void);
void cocohost_core_reset(void);        // queued hard reset

// Live RGB/composite switch (CoCo 3); applied on the emulator thread.
void cocohost_set_tv_input(int tvInput);

// In-place CoCo 2 <-> CoCo 3 switch: no XRoar re-init (that crashes), just
// ui_update_state(machine/cartridge) + xroar_hard_reset on the emulator thread,
// with the matching HDB-DOS Becker ROM. FujiNet stays up; becker reconnects.
void cocohost_set_machine(int machine, int tvInput);

// --- audio ------------------------------------------------------------------
// ao_android pushes completed interleaved stereo S16 frames (44100 Hz).
void cocohost_push_audio(const int16_t* interleaved, int nframes);
// JNI feeder: blocks (bounded) until maxSamples interleaved stereo S16 samples
// are available, silence-padding on underrun. Returns maxSamples.
int  cocohost_fill_audio(int16_t* out, int maxSamples);
void cocohost_audio_set_active(int active);   // 0 on shutdown unblocks fill
void cocohost_clear_audio(void);

// --- video emit (from vo_android, emulator thread) --------------------------
void cocohost_emit_frame(const uint32_t* rgba8888, int width, int height);

// --- input ------------------------------------------------------------------
// Keyboard: XRoar hkbd scancode (see src/hkbd.h). Queued, applied per frame.
void cocohost_inject_key(int down, int scancode);
// Analog joystick: port 0=right 1=left; axis 0=X 1=Y; value 0..65535 (32767=centre).
void cocohost_set_joystick_axis(int port, int axis, int value);
void cocohost_set_joystick_button(int port, int button, int pressed);
// Read sides used by joystick_android's control delegates (emulator thread).
int  cocohost_get_joystick_axis(int port, int axis);
int  cocohost_get_joystick_button(int port, int button);

#ifdef __cplusplus
}
#endif

#endif  // COCO_HOST_H
