#include <jni.h>

#include <string>

#include "coco_host.h"
#include "session_runtime.h"

namespace {
std::string JStr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}
}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeStartSession(
        JNIEnv* env, jobject /*thiz*/,
        jstring runtimeRoot, jstring configPath, jstring sdPath, jstring dataPath,
        jstring romPath, jint machine, jint tvInput, jint ccr) {
    SessionRuntime::Get().StartSession(
            JStr(env, runtimeRoot), JStr(env, configPath),
            JStr(env, sdPath), JStr(env, dataPath), JStr(env, romPath),
            static_cast<int>(machine), static_cast<int>(tvInput), static_cast<int>(ccr));
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeStopSession(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    SessionRuntime::Get().StopSession();
}

JNIEXPORT jboolean JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeIsRunning(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return SessionRuntime::Get().IsRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeSwitchMachine(
        JNIEnv* /*env*/, jobject /*thiz*/, jint machine, jint tvInput) {
    SessionRuntime::Get().SwitchMachine(static_cast<int>(machine), static_cast<int>(tvInput));
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeSetTvInput(
        JNIEnv* /*env*/, jobject /*thiz*/, jint tvInput) {
    SessionRuntime::Get().SetTvInput(static_cast<int>(tvInput));
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeSetCcr(
        JNIEnv* /*env*/, jobject /*thiz*/, jint ccr) {
    SessionRuntime::Get().SetCcr(static_cast<int>(ccr));
}

JNIEXPORT jint JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeCurrentCcr(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jint>(SessionRuntime::Get().CurrentCcr());
}

JNIEXPORT jint JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeCurrentMachine(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jint>(SessionRuntime::Get().CurrentMachine());
}

JNIEXPORT jint JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeCurrentTvInput(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jint>(SessionRuntime::Get().CurrentTvInput());
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeAttachSurface(
        JNIEnv* env, jobject /*thiz*/, jobject surface) {
    SessionRuntime::Get().AttachSurface(env, surface);
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeDetachSurface(
        JNIEnv* env, jobject /*thiz*/) {
    SessionRuntime::Get().DetachSurface(env);
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeRequestReset(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    SessionRuntime::Get().RequestReset();
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeInjectKey(
        JNIEnv* /*env*/, jobject /*thiz*/, jboolean down, jint scancode) {
    cocohost_inject_key(down ? 1 : 0, static_cast<int>(scancode));
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeSetJoystickAxis(
        JNIEnv* /*env*/, jobject /*thiz*/, jint port, jint axis, jint value) {
    cocohost_set_joystick_axis(static_cast<int>(port), static_cast<int>(axis),
                               static_cast<int>(value));
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeSetJoystickButton(
        JNIEnv* /*env*/, jobject /*thiz*/, jint port, jint button, jboolean pressed) {
    cocohost_set_joystick_button(static_cast<int>(port), static_cast<int>(button),
                                 pressed ? 1 : 0);
}

// Blocks (bounded) until out.length interleaved stereo signed-16 samples
// (44100 Hz) are ready, silence-padding on underrun. Returns the count.
JNIEXPORT jint JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeFillAudio(
        JNIEnv* env, jobject /*thiz*/, jshortArray out) {
    if (out == nullptr) return 0;
    const jsize n = env->GetArrayLength(out);
    if (n <= 0) return 0;
    jshort* buf = env->GetShortArrayElements(out, nullptr);
    if (buf == nullptr) return 0;
    const int written = cocohost_fill_audio(reinterpret_cast<int16_t*>(buf),
                                            static_cast<int>(n));
    env->ReleaseShortArrayElements(out, buf, 0);
    return written;
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_coco_core_EmulatorNative_nativeAudioSetActive(
        JNIEnv* /*env*/, jobject /*thiz*/, jboolean active) {
    cocohost_audio_set_active(active ? 1 : 0);
}

}  // extern "C"
