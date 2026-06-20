#pragma once

#include <jni.h>
#include <android/native_window.h>

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

// Orchestrates one CoCo session: the headless XRoar core (driven one frame per
// xroar_run() on a worker thread) plus the in-process FujiNet runtime, joined
// over the Becker port (DriveWire-over-TCP) on loopback TCP 65504 (FujiNet
// listens, XRoar's becker client connects out).
class SessionRuntime {
public:
    static SessionRuntime& Get();

    // runtime_root/config/SD/data: FujiNet paths. rom_path: XRoar ROM dir.
    // machine: COCO_MACHINE_*; tv_input: COCO_TV_*.
    void StartSession(const std::string& runtime_root,
                      const std::string& config_path,
                      const std::string& sd_path,
                      const std::string& data_path,
                      const std::string& rom_path,
                      int machine,
                      int tv_input);
    void StopSession();
    bool IsRunning() const { return running_.load(); }

    // Switch CoCo 2 <-> CoCo 3: restarts just the emulator thread with new argv
    // (and the matching HDB-DOS Becker ROM); FujiNet keeps listening so XRoar's
    // becker reconnects. Live RGB/composite is applied without a restart.
    void SwitchMachine(int machine, int tv_input);
    void SetTvInput(int tv_input);
    int  CurrentMachine() const { return machine_.load(); }
    int  CurrentTvInput() const { return tv_input_.load(); }

    void AttachSurface(JNIEnv* env, jobject surface);
    void DetachSurface(JNIEnv* env);

    void RequestReset();

    // Called (on the emulator thread) by the host's video frame sink.
    void OnFrame(const uint32_t* rgba8888, int width, int height);

private:
    SessionRuntime() = default;
    SessionRuntime(const SessionRuntime&) = delete;
    SessionRuntime& operator=(const SessionRuntime&) = delete;

    void StartEmulatorThread();
    void StopEmulatorThread();
    void EmulatorThreadMain();
    void RenderThreadMain();
    void PresentTo(ANativeWindow* w, const uint32_t* rgba8888, int width, int height);
    void SignalRepaint();

    // Becker port (DriveWire-over-TCP) loopback endpoint; FujiNet listens here.
    static constexpr int kBeckerPort = 65504;

    // On a cold start the just-launched FujiNet runtime can't answer HDB-DOS's
    // first auto-DOS DriveWire transaction in time, so the CoCo drops to BASIC
    // instead of booting CONFIG. Once FujiNet is warm, a single hard reset
    // re-runs auto-DOS and boots CONFIG -- so we issue one automatically this many
    // frames (~60Hz) into a cold start. Disarmed for machine switches (FujiNet is
    // already warm then).
    static constexpr int kAutoBootResetFrames = 200;  // ~3.3s
    std::atomic<bool> arm_auto_reset_{false};

    mutable std::mutex surface_mutex_;
    ANativeWindow* window_ = nullptr;

    std::mutex frame_mutex_;
    std::condition_variable frame_cv_;
    bool frame_dirty_ = false;
    std::vector<uint32_t> last_frame_;
    int last_frame_w_ = 0;
    int last_frame_h_ = 0;
    std::thread render_thread_;
    std::atomic<bool> render_running_{false};

    std::mutex lifecycle_mutex_;
    std::thread emulator_thread_;
    std::atomic<bool> running_{false};
    std::atomic<bool> emu_should_run_{false};

    std::atomic<int> machine_{0};   // COCO_MACHINE_*
    std::atomic<int> tv_input_{0};  // COCO_TV_*

    std::string runtime_root_;
    std::string config_path_;
    std::string sd_path_;
    std::string data_path_;
    std::string rom_path_;
};
