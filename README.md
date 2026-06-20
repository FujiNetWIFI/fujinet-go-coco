# FujiNet Go CoCo

Android Tandy Color Computer emulation with integrated FujiNet, in the spirit of
[FujiNet Go 800](https://github.com/mozzwald/fujinet-go-800) (Atari 8-bit),
FujiNet Go Adam (Coleco ADAM) and FujiNet Go Apple2.

This repository fuses two desktop programs into one cohesive mobile app:

- **XRoar** — Ciaran Anscomb's Dragon/Tandy Color Computer emulator. A headless
  subset is compiled into a native library and driven frame-by-frame into an
  Android `Surface`.
- **fujinet-pc (COCO target)** — the FujiNet firmware/PC port built as
  `libfujinet.so` and run in-process as a background runtime.

The two halves talk over the **Becker port** (DriveWire-over-TCP) on loopback
**TCP 65504**: FujiNet's DriveWire bus `BeckerSocket` *listens* (its default), and
XRoar's becker client connects in. This is the CoCo analogue of FujiNet Go 800's
NetSIO, Adam's AdamNet "Bus over IP", and Apple2's SmartPort-over-SLIP. To the
user it is transparent — boot the CoCo, `DOS`, and FujiNet's HDB-DOS world is
just there.

## Architecture

| Concern | Component |
|---|---|
| Emulator core | XRoar 1.11, driven one frame per `xroar_run()` on a worker thread |
| App native lib | `libcococore.so` (headless XRoar + Android host modules + session + JNI) |
| Android host | `app/src/main/cpp/coco_host.c` + `xroar_android/{ui_android,ao_android,joystick_android}.c` (video→Surface, audio→AudioTrack, input from Kotlin) |
| FujiNet runtime | `libfujinet.so` (fujinet-pc COCO/DRIVEWIRE target), `dlopen`'d in-process |
| Transport | Becker port (DriveWire-over-TCP), loopback TCP 65504 (FujiNet listens, XRoar connects) |
| WebUI | fujinet-pc web admin on **port 8002**; WebView loads `http://127.0.0.1:8002` |
| UI | Jetpack Compose (emulator surface, on-screen CoCo keyboard, analog joystick, FujiNet WebUI) |

The app boots a **CoCo 3** by default and can switch to a **CoCo 2** at runtime
(this restarts XRoar with the matching HDB-DOS Becker ROM — `hdbdw3bc3` for the
CoCo 3, `hdbdw3bck` for the CoCo 2 — while FujiNet keeps listening). On the
CoCo 3 the display output toggles live between **Composite** (default) and
**RGB** (XRoar's `-tv-input cmp` / `rgb`).

The joystick is **analog** (like the Apple II target's paddle): the on-screen pad
or a physical gamepad's left stick drives the CoCo's two 6-bit DAC axes
proportionally; the face buttons map to the CoCo joystick button(s).

The application id / package is `online.fujinet.go.coco`.

## Sources

The native components are built from local checkouts (not pinned GitHub
tarballs), so unpushed changes are used as-is:

- XRoar: `~/Workspace/xroar-1.11` (override with `XROAR_SRC=`)
- FujiNet: `~/Workspace/fujinet-pc-coco` (override with `FUJINET_SRC=`)
- CoCo ROMs: `~/Workspace/CoCoPi-roms` (override with `COCO_ROMS_SRC=`)

## Build requirements

- JDK 21 (the Gradle daemon is pinned to JDK 21)
- Android SDK (compile SDK 36) + an installed NDK
- `bash`, `git`, `python3`, `cmake`, `ninja`, `rsync`
- The FujiNet build also clones and cross-compiles Mbed TLS.

`local.properties` records `sdk.dir` and `ndk.dir`.

## Build

```bash
# Full (all four ABIs):
./gradlew assembleDebug

# Fast single-ABI dev build:
./gradlew assembleDebug -PcocoAbi=arm64-v8a

# Unit tests:
./gradlew testDebugUnitTest
```

The Gradle build invokes the staging/cross-compile scripts:

- `bash tools/xroar/build-xroar-core.sh` — stages XRoar's `src/`+`portalib/` into
  the generated tree, generates the headless Android `config.h`, registers the
  Android UI/audio modules, swaps in the lazy-connect `becker.c`, and stages the
  CoCo ROM assets.
- `bash tools/fujinet/build-fujinet.sh --all-abis` — builds `libfujinet.so` and
  the runtime assets (forced to `[BOIP] enabled=1 host=127.0.0.1 port=65504`).

## Generated (uncommitted) directories

- `app/src/main/cpp-generated/xroar/` — staged XRoar sources + `config.h`
- `app/src/main/assets-generated/{xroar,fujinet}/` — ROMs + FujiNet runtime assets
- `app/src/main/jniLibs-generated/` — `libfujinet.so` per ABI
- `tools/xroar/`, `tools/fujinet/work/`

## Licensing

This is a mixed-license project — see [COMPLIANCE.md](./COMPLIANCE.md) and
[THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md). XRoar is GPLv3 and FujiNet is
GPLv3 (so the combined work is GPLv3). Note that the **Tandy/Microware Color
Computer system ROMs are copyright** and bundling them constrains redistribution
of any combined binary; HDB-DOS is freely redistributable.
