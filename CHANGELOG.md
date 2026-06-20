# Changelog

## 0.1.0 (initial)

First cut of **FujiNet Go CoCo** — Android Tandy Color Computer emulation with an
integrated, in-process FujiNet, following the FujiNet Go 800 / Adam / Apple2
pattern.

### Added
- **XRoar 1.11 core, headless on Android.** A subset of XRoar's C sources is
  compiled into `libcococore.so` with a generated Android `config.h` (no
  SDL/GTK/X11/WASM/OpenGL) and custom Android UI/audio/joystick modules
  (`xroar_android/`). The core is driven one frame per `xroar_run()` on a worker
  thread; frames (`VO_RENDER_FMT_RGBA32`) blit straight to an `ANativeWindow`.
- **In-process FujiNet (COCO target)** built as `libfujinet.so` and `dlopen`'d,
  joined to XRoar over the **Becker port (DriveWire-over-TCP)** on loopback TCP
  65504. XRoar's `becker.c` is replaced with a lazy-connect variant so ordering
  and machine-switch reconnects are robust.
- **CoCo 2 ↔ CoCo 3 switching** at runtime (restarts XRoar with the matching
  HDB-DOS Becker ROM; FujiNet stays up).
- **RGB ↔ Composite** display switching on the CoCo 3 (live `-tv-input`).
- **Analog joystick** (on-screen pad + physical gamepad) mapped to the CoCo's two
  6-bit DAC axes, plus fire button(s); on-screen **CoCo keyboard** emitting XRoar
  hkbd scancodes.
- **FujiNet WebUI** served on port 8002, opened from the in-app FujiNet button.
- Packaged as an Android **game** (`appCategory`/`isGame`, ADPF performance hints
  so vendor game governors like MediaTek GameTime engage); portrait & landscape
  layouts.
- Bundled CoCo system ROMs (`bas13`, `extbas11`, `coco3`) and HDB-DOS Becker ROMs
  (`hdbdw3bck`, `hdbdw3bc3`); launcher icon + UI accent in CoCo green (`#8BC34A`).
