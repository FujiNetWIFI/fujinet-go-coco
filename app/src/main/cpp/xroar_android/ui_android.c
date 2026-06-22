/** \file
 *
 *  \brief Android UI + video-output module for XRoar.
 *
 *  Headless counterpart to src/sdl2/{ui_sdl2,vo_sdl2}.c: builds a vo_interface
 *  backed by a vo_render that writes RGBA8888 into an owned buffer, and on each
 *  vsync hands the completed viewport to the Android host (coco_host.c) via
 *  cocohost_emit_frame(), which forwards it to the ANativeWindow render thread.
 *
 *  XRoar is GPLv3 (see COPYING.GPL).
 */

#include "top-config.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "delegate.h"
#include "xalloc.h"

#include "hkbd.h"
#include "joystick.h"
#include "messenger.h"
#include "module.h"
#include "ui.h"
#include "vo.h"
#include "vo_render.h"

#include "../coco_host.h"

// Matches src/sdl2/vo_sdl2.c maxima (covers the 736x276 "underscan" viewport).
#define MAX_VIEWPORT_WIDTH  (800)
#define MAX_VIEWPORT_HEIGHT (300)

// Android analog joystick module (joystick_android.c).
extern struct joystick_module *android_js_modlist[];

struct vo_android_interface {
	struct vo_interface vo_interface;
	uint32_t *pixels;   // RGBA8888, MAX_VIEWPORT_WIDTH * MAX_VIEWPORT_HEIGHT
	int vp_w;
	int vp_h;
};

// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

static void vo_android_set_viewport(void *sptr, int vp_w, int vp_h) {
	struct vo_android_interface *voa = sptr;
	struct vo_interface *vo = &voa->vo_interface;
	struct vo_render *vr = vo->renderer;

	if (vp_w < 16) vp_w = 16;
	if (vp_w > MAX_VIEWPORT_WIDTH) vp_w = MAX_VIEWPORT_WIDTH;
	if (vp_h < 6) vp_h = 6;
	if (vp_h > MAX_VIEWPORT_HEIGHT) vp_h = MAX_VIEWPORT_HEIGHT;

	vo_render_set_viewport(vr, vp_w, vp_h);
	vr->buffer_pitch = vr->viewport.w;
	voa->vp_w = vr->viewport.w;
	voa->vp_h = vr->viewport.h;
	vo_set_draw_area(vo, 0, 0, vr->viewport.w, vr->viewport.h);
}

// Called once per emulated frame (vo_vsync with draw=1). The render buffer holds
// the completed viewport; ship it to the Android surface.
static void vo_android_draw(void *sptr) {
	struct vo_android_interface *voa = sptr;
	cocohost_emit_frame(voa->pixels, voa->vp_w, voa->vp_h);
}

static void vo_android_resize(void *sptr, unsigned int w, unsigned int h) {
	(void)sptr; (void)w; (void)h;  // host surface owns sizing/scaling
}

static void vo_android_notify_frame_rate(void *sptr, _Bool is_60hz) {
	(void)sptr; (void)is_60hz;  // no host-side 60Hz squash; surface scales
}

static void vo_android_free(void *sptr) {
	struct vo_android_interface *voa = sptr;
	struct vo_render *vr = voa->vo_interface.renderer;
	// xroar_shutdown invokes this delegate (vo_interface->free) directly, NOT via
	// vo_free(), so we own the full teardown: unregister the vo's messenger client
	// (otherwise a stale callback dangles into freed memory across a machine
	// switch), free the renderer + buffer, then the vo struct itself.
	messenger_client_unregister(voa->vo_interface.msgr_client_id);
	if (vr) vo_render_free(vr);
	free(voa->pixels);
	free(voa);
}

static struct vo_interface *vo_android_new(void) {
	struct vo_android_interface *voa =
		vo_interface_new(sizeof(*voa));
	struct vo_interface *vo = &voa->vo_interface;

	vo_interface_init(vo);

	// Use the explicit ABGR8 base format, NOT the VO_RENDER_FMT_RGBA32 alias:
	// that alias is gated on `#if __BYTE_ORDER == __BIG_ENDIAN`, but neither
	// macro is in scope here (no <endian.h>), so the alias wrongly resolves to
	// the big-endian branch (RGBA8 = 0xRRGGBBFF). ABGR8's map_abgr8() packs
	// 0xFFBBGGRR, i.e. little-endian memory order R,G,B,A -- exactly what
	// Android's WINDOW_FORMAT_RGBA_8888 wants, with alpha already 0xFF.
	struct vo_render *vr = vo_render_new(VO_RENDER_FMT_ABGR8);
	vo_set_renderer(vo, vr);

	voa->pixels = xmalloc(MAX_VIEWPORT_WIDTH * MAX_VIEWPORT_HEIGHT * sizeof(uint32_t));
	memset(voa->pixels, 0, MAX_VIEWPORT_WIDTH * MAX_VIEWPORT_HEIGHT * sizeof(uint32_t));
	vo_render_set_buffer(vr, voa->pixels);

	vo->free = DELEGATE_AS0(void, vo_android_free, voa);
	vo->set_viewport = DELEGATE_AS2(void, int, int, vo_android_set_viewport, voa);
	vo->draw = DELEGATE_AS0(void, vo_android_draw, voa);
	vo->resize = DELEGATE_AS2(void, unsigned, unsigned, vo_android_resize, voa);
	vr->notify_frame_rate = DELEGATE_AS1(void, bool, vo_android_notify_frame_rate, voa);

	// Sensible default viewport (640x240 "title"); the machine refines it.
	vo_android_set_viewport(voa, 640, 240);

	return vo;
}

// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

static void *ui_android_new(void *cfg);
static void ui_android_free(void *sptr);

struct ui_module ui_android_module = {
	.common = { .name = "android", .description = "Android UI", .new = ui_android_new, },
	.joystick_module_list = android_js_modlist,
};

static void *ui_android_new(void *cfg) {
	(void)cfg;
	struct ui_interface *ui = xmalloc(sizeof(*ui));
	*ui = (struct ui_interface){0};

	ui->free = DELEGATE_AS0(void, ui_android_free, ui);
	ui->vo_interface = vo_android_new();

	// Host keyboard scancode subsystem (we inject via hk_scan_press/release).
	hk_init();

	return ui;
}

static void ui_android_free(void *sptr) {
	struct ui_interface *ui = sptr;
	// Do NOT free ui->vo_interface here: xroar_shutdown already tore it down via
	// its own vo_interface->free delegate (line 1485). Calling vo_free() here as
	// well was a double free of the renderer.
	free(ui);
}
