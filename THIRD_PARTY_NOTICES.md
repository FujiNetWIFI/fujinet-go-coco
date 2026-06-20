# Third-Party Notices

FujiNet Go CoCo incorporates the following third-party software. Each remains
under its own license; see [COMPLIANCE.md](./COMPLIANCE.md) for how they combine.

## XRoar

Dragon and Tandy Color Computer emulator.
Copyright © 2003–2026 Ciaran Anscomb <xroar@6809.org.uk>
<https://www.6809.org.uk/xroar/>
Licensed under the GNU General Public License, version 3 or later.

A headless subset of XRoar's `src/` and `portalib/` is compiled into
`libcococore.so`. The only source modification is `src/becker.c`, replaced with a
lazy-connect/reconnect variant (`tools/xroar/support/becker.c`) so the becker
client tolerates startup ordering and reconnects across machine switches.

## fujinet-pc (FujiNet firmware / PC port)

Copyright © the FujiNet project and contributors.
<https://github.com/FujiNetWIFI/fujinet-firmware>
Licensed under the GNU General Public License, version 3.

Built for the `COCO` (DRIVEWIRE) target as `libfujinet.so` and run in-process.

## Mbed TLS

Copyright © The Mbed TLS Contributors.
<https://github.com/Mbed-TLS/mbedtls>
Dual-licensed Apache-2.0 / GPL-2.0; used here under Apache-2.0.

## HDB-DOS

HDB-DOS (DriveWire/Becker) by Cloud-9 / contributors. Freely redistributable.
Provides the CoCo's disk DOS over the Becker port.

## Tandy / Microware Color Computer ROMs

Color BASIC, Extended Color BASIC and Super Extended Color BASIC are copyright
Tandy Corporation / Microware. Bundled for emulator functionality only; see the
ROM copyright caveat in [COMPLIANCE.md](./COMPLIANCE.md).
