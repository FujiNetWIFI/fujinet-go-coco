/** \file
 *
 *  \brief Android analog joystick module for XRoar.
 *
 *  Provides the "android" joystick submodule whose axis/button controls read the
 *  per-port analog state set from Kotlin (via coco_host.c). coco_host registers
 *  two profiles -- android0 (right port) and android1 (left port) -- whose specs
 *  are "android:PORT"; configure_axis/configure_button bind a control to
 *  (PORT, index) and read it through cocohost_get_joystick_*.
 *
 *  The CoCo joystick is two 6-bit analog axes (read here as 0..65535, 32767 =
 *  centre) plus one (CoCo 1/2) or two (CoCo 3) buttons per port.
 *
 *  XRoar is GPLv3 (see COPYING.GPL).
 */

#include "top-config.h"

#include <stdlib.h>

#include "delegate.h"
#include "xalloc.h"

#include "joystick.h"
#include "module.h"

#include "../coco_host.h"

struct android_js_control {
	struct joystick_control joystick_control;
	int port;
	int index;
};

static int android_axis_read(void *sptr) {
	struct android_js_control *c = sptr;
	return cocohost_get_joystick_axis(c->port, c->index);
}

static int android_button_read(void *sptr) {
	struct android_js_control *c = sptr;
	return cocohost_get_joystick_button(c->port, c->index);
}

static void android_control_free(void *sptr) {
	free(sptr);
}

static struct android_js_control *new_control(char *spec, unsigned index) {
	struct android_js_control *c = xmalloc(sizeof(*c));
	*c = (struct android_js_control){0};
	c->port = spec ? atoi(spec) : 0;
	if (c->port < 0 || c->port >= JOYSTICK_NUM_PORTS) c->port = 0;
	c->index = (int)index;
	c->joystick_control.free = DELEGATE_AS0(void, android_control_free, c);
	return c;
}

static struct joystick_control *configure_axis(char *spec, unsigned jaxis) {
	struct android_js_control *c = new_control(spec, jaxis);
	c->joystick_control.read = DELEGATE_AS0(int, android_axis_read, c);
	return &c->joystick_control;
}

static struct joystick_control *configure_button(char *spec, unsigned jbutton) {
	struct android_js_control *c = new_control(spec, jbutton);
	c->joystick_control.read = DELEGATE_AS0(int, android_button_read, c);
	return &c->joystick_control;
}

static struct joystick_submodule android_js_submod = {
	.name = "android",
	.configure_axis = configure_axis,
	.configure_button = configure_button,
};

static struct joystick_submodule *android_js_submodlist[] = {
	&android_js_submod,
	NULL,
};

static struct joystick_module android_js_mod = {
	.common = { .name = "android", .description = "Android analog joystick" },
	.submodule_list = android_js_submodlist,
};

struct joystick_module *android_js_modlist[] = {
	&android_js_mod,
	NULL,
};
