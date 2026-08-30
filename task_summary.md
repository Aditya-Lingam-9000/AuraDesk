### 1. What is AuraDesk?

  AuraDesk is a zero-touch, 100% offline, privacy-first focus bodyguard for your desk.

  When you place your Vivo T4 5G face-down on your desk, the phone automatically arms its guard service:

  1. Physical Radar: Detects approaching colleagues/distractions from 5m down to 0.5m using the rear camera.
  2. Deep Work Detection: Detects if you are locked into coding/studying using face-mesh gaze & typing audio.
  3. Audio Capture & STT: Listens for your name and captures short 10-second interruption capsules when someone stands at your desk.
  4. On-Device LLM: Generates smart, polite auto-replies and summarizes interruptions into actionable tasks (Person – Task – Deadline).
  5. Vivo Office Kit Sync: Pushes "In Deep Work" banners to your laptop screen, mutes laptop pings, and syncs tasks directly into Jovi Notes & clipboard over local WiFi Direct.
  6. Zero Internet / Privacy: Runs entirely in airplane mode with 0 network bytes and draws <3% battery per hour.
  ──────
  ### 2. System & AI/ML Architecture Overview

  Since you have a strong AI/ML background, here is how the ML pipelines and Android hardware layers integrate on your Snapdragon 7s Gen 3:

    flowchart TD
        A[Phone Flipped Face-Down] --> B[Sensor Fusion: Proximity + Light + Accel + Gyro]
        B --> C[Foreground Guard Service Armed]

        C --> D1[Rear Camera 2fps: MediaPipe EfficientDet-Lite0]
        D1 --> D2[Centroid & Bounding Box Area Growth >20%/s]
        D2 --> D3[Distance Estimation: 5m -> 2m -> 0.5m]

        C --> E1[Audio Pipeline: VAD -> Keyword Spotting CNN]
        E1 --> E2[Name / Voice Triggered at 0.5m]
        E2 --> E3[Vosk STT: 10s Offline Audio to Text]

        E3 --> F1[Qwen2-0.5B INT4 via llama.cpp NDK]
        F1 --> F2[Structured Task Summary: Person - Task - Deadline]
        F1 --> F3[Context-Aware Auto-Reply Generator]

        F2 --> G1[Haptic Whisper: VibratorManager]
        F2 --> G2[Room Database / Encrypted Store]
        F2 --> G3[Vivo Office Kit: Screen Banner + Jovi Notes Sync]
  ──────
  ### 3. Implementation Roadmap (10 Phases)

  We will build and test AuraDesk iteratively in 10 sequential phases so nothing breaks:

   Phase                                   │ Module                                  │ What We Will Build                                                                                                                  │ Verification on Vivo T4 5G
  ─────────────────────────────────────────┼─────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────
   Phase 0                                 │ Project Setup                           │ Android Studio project, Kotlin, Jetpack Compose, build scripts, NDK, CameraX, MediaPipe, Vosk dependencies, zero-internet manifest. │ Clean build & "Hello World" on Vivo T4.
   Phase 1                                 │ Face-Down Detector                      │ SensorManager fusion (proximity < 1cm, light < 10 lux, accel z ~ 9.8, gyro stable 2s) emitting a reactive State Flow.               │ 10/10 flip tests in Logcat logging Guard Armed.
   Phase 2                                 │ Guard Service & AOD UI                  │ Foreground Service + minimal dark Always-On Display UI (#121212, status shield, battery indicator).                                 │ Persistent notification & AOD screen running smoothly.
   Phase 3                                 │ Person Approaching Radar                │ CameraX rear feed (2 fps, 320x240) + MediaPipe EfficientDet-Lite0 GPU delegate tracking bounding box growth.                        │ Walking towards desk logs Person at 5m -> 2m -> 0.5m.
   Phase 4                                 │ Deep Work Detector                      │ Front camera FaceLandmarker (eye aspect ratio + head yaw) + typing sound classifier + 15 min touch-idle state.                      │ Detects coding vs away/idle states.
   Phase 5                                 │ Haptic & Interruption Card              │ Room SQLite DB, custom vibration patterns (VibratorManager), and Jetpack Compose interruption card with shake-to-delete.            │ Lifting phone after interruption shows actionable card.
   Phase 6                                 │ Audio Pipeline (VAD + STT)              │ 16kHz audio recorder, VAD filter, user name keyword CNN, and Vosk 50MB offline speech-to-text (10-second limit).                    │ Accurately transcribes speech at 0.5m with no audio files leaked.
   Phase 7                                 │ On-Device LLM (Qwen2-0.5B)              │ llama.cpp NDK bindings running quantized Qwen2-0.5B INT4 for auto-replies and task extraction.                                      │ Airplane mode test: generates summaries and replies in <1.2s.
   Phase 8                                 │ Vivo Office Kit Integration             │ Vivo EasyShare & Jovi Notes ContentProvider intents, screen mirroring focus banner, clipboard sync.                                 │ Laptop automatically receives "In Deep Work" banner & Notes sync.
   Phase 9                                 │ Battery & Privacy Hardening             │ Low-power throttling, thermal safeguards (                                                                                          │

       ∘
    <42  C

  ), privacy shutter mode, and Network Profiler verification (0 bytes). | 1-hour continuous guard run tests <3% battery drain. |
  | Phase 10 | Release Build & Demo Mode | Release APK packaging, 30-second voice onboarding, settings screen, and stage simulation chips for demos. | Flawless end-to-end demo under airplane mode. |
  ──────
  ### 4. How We Will Collaborate

  • No Android experience needed: I will handle all Android-specific code (Kotlin syntax, Compose UI trees, Android lifecycle, Services, Coroutines, Gradle setups, and NDK JNI bridges) and explain each step in plain English.
  • Your AI/ML knowledge will shine: You can guide prompt engineering, model quantization tradeoffs, and audio/vision thresholds.
  • Strict Phase-by-Phase Discipline: We will not touch future phases until the current phase is built, deployed, and tested on your Vivo T4 5G.

  Whenever you are ready to kick off Phase 0 (Project Setup), just let me know and we will begin!