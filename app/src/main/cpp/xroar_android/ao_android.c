/** \file
 *
 *  \brief Android audio-output module for XRoar.
 *
 *  Counterpart to src/null/ao_null.c and src/sdl2/ao_sdl2.c: presents XRoar a
 *  44100 Hz interleaved stereo S16 sound buffer and, as each buffer completes,
 *  pushes it into the Android host ring (coco_host.c) where the JNI AudioTrack
 *  feeder drains it. Pacing comes from the 60Hz emulator loop (production) and
 *  the blocking AudioTrack write (consumption), so this module never sleeps.
 *
 *  XRoar is GPLv3 (see COPYING.GPL).
 */

#include "top-config.h"

#include <stdint.h>
#include <stdlib.h>

#include "delegate.h"
#include "xalloc.h"

#include "ao.h"
#include "module.h"
#include "sound.h"

#include "../coco_host.h"

#define AO_RATE       (44100)
#define AO_CHANNELS   (2)
#define AO_NFRAMES    (1024)

struct ao_android_interface {
	struct ao_interface public;
	int16_t *buffer;  // AO_NFRAMES * AO_CHANNELS samples
};

static void *new(void *cfg);
static void ao_android_free(void *sptr);
static void *ao_android_write_buffer(void *sptr, void *buffer);

struct module ao_android_module = {
	.name = "android", .description = "Android audio",
	.new = new,
};

static void *new(void *cfg) {
	(void)cfg;
	struct ao_android_interface *aoa = xmalloc(sizeof(*aoa));
	*aoa = (struct ao_android_interface){0};
	struct ao_interface *ao = &aoa->public;

	aoa->buffer = xmalloc(AO_NFRAMES * AO_CHANNELS * sizeof(int16_t));

	ao->free = DELEGATE_AS0(void, ao_android_free, aoa);
	ao->sound_interface = sound_interface_new(aoa->buffer, SOUND_FMT_S16_HE,
						  AO_RATE, AO_CHANNELS, AO_NFRAMES);
	if (!ao->sound_interface) {
		free(aoa->buffer);
		free(aoa);
		return NULL;
	}
	ao->sound_interface->write_buffer =
		DELEGATE_AS1(voidp, voidp, ao_android_write_buffer, aoa);

	cocohost_clear_audio();
	cocohost_audio_set_active(1);
	return aoa;
}

static void ao_android_free(void *sptr) {
	struct ao_android_interface *aoa = sptr;
	cocohost_audio_set_active(0);
	if (aoa->public.sound_interface)
		sound_interface_free(aoa->public.sound_interface);
	free(aoa->buffer);
	free(aoa);
}

// Called by sound.c when a full buffer of AO_NFRAMES stereo frames is ready.
// Push it to the host ring and reuse the same buffer for the next fill.
static void *ao_android_write_buffer(void *sptr, void *buffer) {
	(void)sptr;
	cocohost_push_audio((const int16_t *)buffer, AO_NFRAMES);
	return buffer;
}
