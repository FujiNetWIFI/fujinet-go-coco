#include "session_runtime.h"

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <dlfcn.h>
#include <pthread.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <unistd.h>

#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstring>

#include "coco_host.h"

// FujiNet runtime dlopen wrapper (fujinet_android.cpp).
extern "C" {
bool        FujiNetAndroid_StartRuntime(const char* runtimeRootPath,
                                        const char* configPath,
                                        const char* sdPath,
                                        const char* dataPath,
                                        int listenPort);
void        FujiNetAndroid_StopRuntime();
const char* FujiNetAndroid_LastErrorMessage();
bool        FujiNetAndroid_IsRuntimeRunning();
}

#define LOG_TAG "CoCoSession"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr auto kFramePeriod = std::chrono::nanoseconds(1'000'000'000 / 60);
constexpr int64_t kFrameTargetNs = 1'000'000'000LL / 60;

void frame_sink_trampoline(const uint32_t* rgba, int w, int h, void* ud) {
    static_cast<SessionRuntime*>(ud)->OnFrame(rgba, w, h);
}

// ADPF (Android Dynamic Performance Framework) "performance hint" session. This
// tells the SoC scheduler/governor the emulator thread's per-frame CPU work and
// its frame deadline, so it keeps clocks up for the 60Hz loop instead of letting
// DVFS race-to-idle drop the thread off schedule (the root cause of audio
// underruns / video judder). It is what vendor "game" governors (e.g. MediaTek
// GameTime, Qualcomm) consume. API 33+; dlsym'd so the app still runs below 33.
class PerfHint {
public:
    void start(int64_t targetNs) {
        auto getManager = reinterpret_cast<void* (*)()>(
            dlsym(RTLD_DEFAULT, "APerformanceHint_getManager"));
        create_ = reinterpret_cast<void* (*)(void*, const int32_t*, size_t, int64_t)>(
            dlsym(RTLD_DEFAULT, "APerformanceHint_createSession"));
        report_ = reinterpret_cast<void (*)(void*, int64_t)>(
            dlsym(RTLD_DEFAULT, "APerformanceHint_reportActualWorkDuration"));
        close_ = reinterpret_cast<void (*)(void*)>(
            dlsym(RTLD_DEFAULT, "APerformanceHint_closeSession"));
        if (!getManager || !create_ || !report_) {
            LOGW("ADPF unavailable (APerformanceHint symbols missing; pre-API-33)");
            return;
        }
        void* mgr = getManager();
        if (!mgr) {
            LOGW("ADPF unavailable (no performance hint manager on this device)");
            return;
        }
        const int32_t tid = static_cast<int32_t>(syscall(SYS_gettid));
        session_ = create_(mgr, &tid, 1, targetNs);
        if (session_) {
            LOGI("ADPF performance hint session active (target %lldns)",
                 static_cast<long long>(targetNs));
        } else {
            LOGW("ADPF createSession returned null");
        }
    }
    void report(int64_t actualNs) const {
        if (session_ && report_) report_(session_, actualNs);
    }
    void stop() {
        if (session_ && close_) close_(session_);
        session_ = nullptr;
    }
private:
    void* session_ = nullptr;
    void* (*create_)(void*, const int32_t*, size_t, int64_t) = nullptr;
    void (*report_)(void*, int64_t) = nullptr;
    void (*close_)(void*) = nullptr;
};
}  // namespace

SessionRuntime& SessionRuntime::Get() {
    static SessionRuntime instance;
    return instance;
}

void SessionRuntime::StartSession(const std::string& runtime_root,
                                  const std::string& config_path,
                                  const std::string& sd_path,
                                  const std::string& data_path,
                                  const std::string& rom_path,
                                  int machine,
                                  int tv_input) {
    std::lock_guard<std::mutex> lock(lifecycle_mutex_);
    if (running_.load()) {
        LOGW("StartSession ignored; session already running");
        return;
    }

    runtime_root_ = runtime_root;
    config_path_ = config_path;
    sd_path_ = sd_path;
    data_path_ = data_path;
    rom_path_ = rom_path;
    machine_.store(machine);
    tv_input_.store(tv_input);

    // XRoar resolves ~ paths from $HOME; Android doesn't set it. Point it at the
    // app's writable runtime root so any XRoar autosave/config lands there.
    if (!runtime_root_.empty()) {
        setenv("HOME", runtime_root_.c_str(), 1);
    }

    cocohost_set_frame_sink(&frame_sink_trampoline, this);
    cocohost_set_rompath(rom_path_.c_str());
    cocohost_set_becker_endpoint("127.0.0.1", kBeckerPort);

    running_.store(true);
    render_running_.store(true);
    render_thread_ = std::thread(&SessionRuntime::RenderThreadMain, this);

    // Start the in-process FujiNet runtime FIRST: its DriveWire bus sets up the
    // Becker listener synchronously in main_setup(), so by the time StartRuntime
    // returns the listener is up for XRoar's becker client to connect to. (The
    // becker client is also lazy/retrying, so exact ordering is forgiving.)
    if (!runtime_root_.empty() && !config_path_.empty() && !sd_path_.empty()) {
        if (!FujiNetAndroid_StartRuntime(runtime_root_.c_str(), config_path_.c_str(),
                                         sd_path_.c_str(), data_path_.c_str(), kBeckerPort)) {
            const char* err = FujiNetAndroid_LastErrorMessage();
            LOGE("FujiNet runtime failed to start: %s", err ? err : "(unknown)");
            // Continue: the CoCo still boots, just without the FujiNet drive.
        }
    } else {
        LOGW("FujiNet paths not provided; starting emulator without FujiNet");
    }

    arm_auto_reset_.store(true);  // cold start: auto-reset once FujiNet is warm
    StartEmulatorThread();
    LOGI("Session started (Becker %d, machine=%d tv=%d)", kBeckerPort, machine, tv_input);
}

void SessionRuntime::StartEmulatorThread() {
    emu_should_run_.store(true);
    emulator_thread_ = std::thread(&SessionRuntime::EmulatorThreadMain, this);
}

void SessionRuntime::StopEmulatorThread() {
    emu_should_run_.store(false);
    cocohost_audio_set_active(0);  // unblock any waiting audio fill
    if (emulator_thread_.joinable()) {
        emulator_thread_.join();
    }
}

void SessionRuntime::EmulatorThreadMain() {
    pthread_setname_np(pthread_self(), "coco-emu");
    // Raise priority so the 60Hz frame schedule isn't preempted by UI work
    // (THREAD_PRIORITY_URGENT_DISPLAY = -8), but stay below the audio feeder.
    setpriority(PRIO_PROCESS, 0, -8);

    if (!cocohost_core_start(machine_.load(), tv_input_.load())) {
        LOGE("XRoar core failed to start");
        running_.store(false);
        return;
    }

    PerfHint perf;
    perf.start(kFrameTargetNs);

    const bool auto_reset = arm_auto_reset_.load();
    bool did_auto_reset = false;
    long frame = 0;

    auto next = std::chrono::steady_clock::now();
    while (emu_should_run_.load()) {
        const auto work_start = std::chrono::steady_clock::now();
        cocohost_core_run_frame();

        // Cold-start safety: once FujiNet has had time to warm up, issue one hard
        // reset so HDB-DOS's auto-DOS re-runs against a responsive DriveWire
        // server and boots into the FujiNet CONFIG.
        if (auto_reset && !did_auto_reset && ++frame >= kAutoBootResetFrames) {
            LOGI("Auto-boot reset (cold start; FujiNet warm) -> booting CONFIG");
            cocohost_core_reset();
            did_auto_reset = true;
        }
        // Report the CPU work this frame actually took (excludes the sleep), so
        // the governor sizes clocks to the real per-frame load.
        const auto work_end = std::chrono::steady_clock::now();
        perf.report(std::chrono::duration_cast<std::chrono::nanoseconds>(
            work_end - work_start).count());

        next += kFramePeriod;
        const auto now = std::chrono::steady_clock::now();
        if (next > now) {
            std::this_thread::sleep_for(next - now);
        } else if (now - next > std::chrono::milliseconds(100)) {
            // Fell far behind (e.g. after a stall); resync rather than spin.
            next = now;
        }
    }

    perf.stop();
    cocohost_core_stop();
    LOGI("Emulator thread exited");
}

void SessionRuntime::SwitchMachine(int machine, int tv_input) {
    std::lock_guard<std::mutex> lock(lifecycle_mutex_);
    if (!running_.load()) {
        // Not running yet: just record the desired state for the next start.
        machine_.store(machine);
        tv_input_.store(tv_input);
        return;
    }
    if (machine_.load() == machine && tv_input_.load() == tv_input) {
        return;
    }
    LOGI("SwitchMachine -> machine=%d tv=%d (restarting emulator, FujiNet stays up)",
         machine, tv_input);
    StopEmulatorThread();
    machine_.store(machine);
    tv_input_.store(tv_input);
    cocohost_clear_audio();
    arm_auto_reset_.store(false);  // FujiNet already warm; no cold-boot reset needed
    StartEmulatorThread();
}

void SessionRuntime::SetTvInput(int tv_input) {
    tv_input_.store(tv_input);
    cocohost_set_tv_input(tv_input);  // live, applied on the emulator thread
}

void SessionRuntime::StopSession() {
    std::lock_guard<std::mutex> lock(lifecycle_mutex_);
    if (!running_.load() && !emulator_thread_.joinable()) {
        return;
    }

    StopEmulatorThread();

    render_running_.store(false);
    SignalRepaint();
    if (render_thread_.joinable()) {
        render_thread_.join();
    }

    FujiNetAndroid_StopRuntime();

    running_.store(false);
    cocohost_set_frame_sink(nullptr, nullptr);
    LOGI("Session stopped");
}

void SessionRuntime::AttachSurface(JNIEnv* env, jobject surface) {
    std::lock_guard<std::mutex> lock(surface_mutex_);
    if (window_) {
        ANativeWindow_release(window_);
        window_ = nullptr;
    }
    if (surface) {
        window_ = ANativeWindow_fromSurface(env, surface);
        LOGI("AttachSurface: window=%p", static_cast<void*>(window_));
    }
    SignalRepaint();
}

void SessionRuntime::DetachSurface(JNIEnv* /*env*/) {
    std::lock_guard<std::mutex> lock(surface_mutex_);
    if (window_) {
        ANativeWindow_release(window_);
        window_ = nullptr;
    }
}

void SessionRuntime::RequestReset() { cocohost_core_reset(); }

void SessionRuntime::OnFrame(const uint32_t* rgba8888, int width, int height) {
    if (!rgba8888 || width <= 0 || height <= 0) return;
    const size_t pixels = static_cast<size_t>(width) * height;
    {
        std::lock_guard<std::mutex> lock(frame_mutex_);
        if (last_frame_.size() != pixels) last_frame_.resize(pixels);
        std::memcpy(last_frame_.data(), rgba8888, pixels * sizeof(uint32_t));
        last_frame_w_ = width;
        last_frame_h_ = height;
        frame_dirty_ = true;
    }
    frame_cv_.notify_one();
}

void SessionRuntime::SignalRepaint() {
    {
        std::lock_guard<std::mutex> lock(frame_mutex_);
        frame_dirty_ = true;
    }
    frame_cv_.notify_one();
}

void SessionRuntime::RenderThreadMain() {
    pthread_setname_np(pthread_self(), "coco-render");
    std::vector<uint32_t> scratch;
    int w = 0, h = 0;
    while (render_running_.load()) {
        {
            std::unique_lock<std::mutex> lock(frame_mutex_);
            frame_cv_.wait(lock, [this] { return frame_dirty_ || !render_running_.load(); });
            if (!render_running_.load()) break;
            frame_dirty_ = false;
            if (last_frame_.empty()) continue;
            scratch = last_frame_;
            w = last_frame_w_;
            h = last_frame_h_;
        }
        ANativeWindow* w_local = nullptr;
        {
            std::lock_guard<std::mutex> lock(surface_mutex_);
            if (window_) {
                w_local = window_;
                ANativeWindow_acquire(w_local);
            }
        }
        if (w_local) {
            PresentTo(w_local, scratch.data(), w, h);
            ANativeWindow_release(w_local);
        }
    }
}

void SessionRuntime::PresentTo(ANativeWindow* w, const uint32_t* rgba8888, int width, int height) {
    if (!w || !rgba8888) return;

    ANativeWindow_setBuffersGeometry(w, width, height, WINDOW_FORMAT_RGBA_8888);

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(w, &buffer, nullptr) != 0) {
        return;
    }
    const int copy_w = buffer.width < width ? buffer.width : width;
    const int copy_h = buffer.height < height ? buffer.height : height;
    auto* dst = static_cast<uint32_t*>(buffer.bits);
    for (int y = 0; y < copy_h; ++y) {
        const uint32_t* src_row = rgba8888 + static_cast<size_t>(y) * width;
        uint32_t* dst_row = dst + static_cast<size_t>(y) * buffer.stride;
        for (int x = 0; x < copy_w; ++x) {
            // The vo_android renderer uses VO_RENDER_FMT_ABGR8 (map_abgr8 packs
            // 0xFFBBGGRR), i.e. little-endian memory order R,G,B,A == Android
            // WINDOW_FORMAT_RGBA_8888. Straight copy; force opaque alpha for safety.
            dst_row[x] = src_row[x] | 0xFF000000u;
        }
    }
    ANativeWindow_unlockAndPost(w);
}
