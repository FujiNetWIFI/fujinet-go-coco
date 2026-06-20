# Licensing & Compliance

FujiNet Go CoCo is a **combined work** of several components under different
licenses. This note summarises the obligations; it is not legal advice.

## Components

| Component | License | Role |
|---|---|---|
| FujiNet Go CoCo (this repo: app, host, build glue) | GPLv3 | Android app + native integration |
| XRoar 1.11 | GPLv3-or-later | Emulator core (`libcococore.so`) |
| fujinet-pc (COCO target) | GPLv3 | In-process FujiNet runtime (`libfujinet.so`) |
| Mbed TLS | Apache-2.0 / GPL-2.0 | TLS for the FujiNet runtime |
| HDB-DOS (Becker/DriveWire) | Freely redistributable | CoCo disk DOS over the Becker port |
| Tandy/Microware Color BASIC, Extended/Super Extended BASIC ROMs | **Copyright Tandy/Microware** | CoCo system ROMs |

## Effective license of the combined work

XRoar and fujinet-pc are both **GPLv3**, so the combined application is governed
by the **GNU General Public License v3**. Source for all GPL components is the
local checkouts named in [README.md](./README.md); this repository's own sources
are GPLv3.

## ROM copyright caveat

The app bundles the Tandy/Microware Color Computer **system ROMs** (Color BASIC
`bas13`, Extended Color BASIC `extbas11`, and the CoCo 3 Super Extended Color
BASIC `coco3`) from `~/Workspace/CoCoPi-roms` so the emulator boots out of the
box. These ROMs are **copyrighted by Tandy/Microware** and are *not* covered by
the GPL. Bundling them is fine for personal/development use, but **redistributing
a combined binary that includes them may infringe those copyrights** — the same
caveat the Apple II target documents for the Apple system ROMs. Anyone
redistributing this app should either obtain the right to include the ROMs or
ship without them and provide a user-import path.

The **HDB-DOS** Becker ROMs (`hdbdw3bck`, `hdbdw3bc3`) are freely redistributable
and carry no such restriction.

## Notes

- XRoar's `becker.c` is replaced with a lazy-connect variant
  (`tools/xroar/support/becker.c`); it remains GPLv3 and the change is documented
  in that file's header.
- See [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md) for upstream attributions.
