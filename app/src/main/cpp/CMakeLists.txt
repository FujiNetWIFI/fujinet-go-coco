cmake_minimum_required(VERSION 3.22.1)

project("cococore")

# ---------------------------------------------------------------------------
# Staged XRoar 1.11 core (Tandy Color Computer / Dragon emulator).
#
# tools/xroar/build-xroar-core.sh stages XRoar's src/ + portalib/ into
# ../cpp-generated/xroar, generates a headless Android config.h, and registers
# the Android UI/audio modules in src/ui.c and src/ao.c. XRoar normally builds
# with autotools; here we compile an explicit (headless) subset of its C sources
# into libcococore.so. No SDL/GTK/X11/WASM/OpenGL frontends are listed, so they
# are never compiled. The Android host (coco_host.cpp) drives the core via
# xroar_init()/xroar_run(), and the custom ui_android/ao_android modules feed
# video to an ANativeWindow and audio to an AudioTrack feeder.
# ---------------------------------------------------------------------------

set(XR "${CMAKE_CURRENT_SOURCE_DIR}/../cpp-generated/xroar")

if(NOT EXISTS "${XR}/src/xroar.c")
    message(FATAL_ERROR
        "Staged XRoar source tree is missing. "
        "Run tools/xroar/build-xroar-core.sh before configuring CMake.")
endif()

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# XRoar reads config.h via top-config.h.
add_compile_definitions(HAVE_CONFIG_H=1)
# Macros the autotools Makefile normally supplies on the compile line. We pass an
# explicit -rompath at runtime, so the defaults are just placeholders.
add_compile_definitions(ROMPATH=".")
add_compile_definitions(CONFPATH=".")
add_compile_definitions(AUTOSAVE_PREFIX=".")
# XRoar's C is permissive about these; silence the noise on the NDK clang.
add_compile_options(-Wno-unused-parameter -Wno-sign-compare -Wno-multichar)

# ARM tuning (no-ops on x86/x86_64).
if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_ANDROID_ARM_MODE ON)
    set(CMAKE_ANDROID_ARM_NEON ON)
endif()

# 16K page-size support for newer NDKs on ARM (lld expects -z,max-page-size).
if((ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "armeabi-v7a")
        AND (NOT DEFINED CMAKE_ANDROID_NDK_VERSION OR CMAKE_ANDROID_NDK_VERSION VERSION_GREATER_EQUAL 26))
    add_link_options(-Wl,-z,max-page-size=16384)
endif()

# --- XRoar core sources (headless CoCo/Dragon subset) -----------------------
# Derived from src/Makefile.am with the CoCo/Dragon feature set. The frontends
# and the template .c files that are #included by other sources (mc6809_common.c,
# mc680x_ops.c, vo_render_tmpl.c, hkbd_lang_tables.c) and main_unix.c are omitted.
set(XROAR_CORE_SOURCES
    src/ao.c
    src/auto_kbd.c
    src/ay891x.c
    src/becker.c
    src/blockdev.c
    src/breakpoint.c
    src/cart.c
    src/clock.c
    src/colourspace.c
    src/crc16.c
    src/crc32.c
    src/crclist.c
    src/dkbd.c
    src/events.c
    src/filter.c
    src/fs.c
    src/hexs19.c
    src/hkbd.c
    src/hkbd_joystick.c
    src/joystick.c
    src/keyboard.c
    src/logging.c
    src/machine.c
    src/mc10_cart.c
    src/messenger.c
    src/module.c
    src/mos6551.c
    src/ntsc.c
    src/null/ui_null.c
    src/null/ao_null.c
    src/part.c
    src/path.c
    src/printer.c
    src/ram.c
    src/rombank.c
    src/romlist.c
    src/screenshot.c
    src/serialise.c
    src/snapshot.c
    src/sndfile_compat.c
    src/sound.c
    src/tape.c
    src/tape_cas.c
    src/tape_sndfile.c
    src/ui.c
    src/vdg_pal.c
    src/vdg_palette.c
    src/vdisk.c
    src/vdrive.c
    src/vo.c
    src/vo_render.c
    src/xconfig.c
    src/xroar.c
    # MACHINE_ARCH_DRAGON
    src/dragon/coco.c
    src/dragon/immunity.c
    src/dragon/deluxecoco.c
    src/dragon/dragon.c
    src/dragon/dragon32.c
    src/dragon/dragon64.c
    src/dragon/dragonpro.c
    # MACHINE_ARCH_COCO3
    src/coco3.c
    # PART_MC6801 / PART_MC6809
    src/mc6801/mc6801.c
    src/mc6809/hd6309.c
    src/mc6809/mc6809.c
    # PART_MC6821
    src/mc6821.c
    # PART_MC6847
    src/mc6847/font-6847.c
    src/mc6847/font-6847t1.c
    src/mc6847/mc6847.c
    # PART_MC6883
    src/mc6883.c
    src/samx8.c
    # PART_TCC1014 (CoCo 3 GIME)
    src/tcc1014/font-gime.c
    src/tcc1014/tcc1014.c
    # CART_ARCH_DRAGON
    src/deltados.c
    src/dragondos.c
    src/gmc.c
    src/ide.c
    src/idecart.c
    src/ikon.c
    src/mooh.c
    src/mpi.c
    src/nx32.c
    src/orch90.c
    src/rsdos.c
    src/sn76489.c
    src/spi65.c
    src/spi_sdcard.c
    src/wd279x.c
)
list(TRANSFORM XROAR_CORE_SOURCES PREPEND "${XR}/")

set(PORTALIB_SOURCES
    portalib/c-ctype.c
    portalib/c-strcasecmp.c
    portalib/c-strncasecmp.c
    portalib/delegate.c
    portalib/intfuncs.c
    portalib/pl-estrcpy.c
    portalib/sds.c
    portalib/sdsx.c
    portalib/slist.c
    portalib/strnlen.c
    portalib/strsep.c
    portalib/xmalloc.c
)
list(TRANSFORM PORTALIB_SOURCES PREPEND "${XR}/")

# --- The app's native library -----------------------------------------------
add_library(
        cococore
        SHARED
        ${XROAR_CORE_SOURCES}
        ${PORTALIB_SOURCES}
        # Custom XRoar Android modules (registered in the staged ui.c / ao.c).
        xroar_android/ui_android.c
        xroar_android/ao_android.c
        xroar_android/joystick_android.c
        # Android host + session + JNI + FujiNet dlopen wrapper.
        coco_host.c
        session_runtime.cpp
        coco_core.cpp
        fujinet_android.cpp
)

target_include_directories(cococore PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        "${XR}"
        "${XR}/src"
        "${XR}/portalib"
)

target_link_libraries(cococore
        m
        log
        android
        dl       # fujinet_android.cpp dlopen's libfujinet.so
)
