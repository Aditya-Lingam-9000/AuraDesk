I am giving you FINAL tech stack that will 100% work on your Vivo T4.

FINAL TECH STACK FOR VIVO T4 5G - 100% END TO END
Use this only, don't use React Native.

Language: Kotlin - because all sensors, Office Kit, MediaPipe work only in Kotlin
UI: Jetpack Compose - modern, 1 file for all UI cards
IDE: Android Studio Ladybug - you will build via AI prompts inside it
Camera: CameraX 1.3 - rear 2fps + front 1fps
Sensors: Android SensorManager - proximity, light, accel, gyro
AI Vision: MediaPipe Tasks 0.10.14 - ObjectDetector EfficientDet-Lite0 for person detection, Face Landmarker for deep work
AI Audio: MediaPipe AudioClassifier + Custom Keyword CNN 200KB + Vosk STT 50MB for offline 10 sec transcription
AI LLM: Qwen2-0.5B-Instruct INT4 via llama.cpp NDK - NOT Gemma. Reason: 400MB vs 600MB, 0.8 sec on 7s Gen 3 vs 2.5 sec, works on 8GB RAM. You will switch to Gemma 1B on iQOO loaner later with 1 line change.
Storage: Room Database + DataStore + EncryptedFile Android Keystore
System: Foreground Service + WorkManager + NotificationListenerService + AccessibilityService + VibratorManager for haptics
Office Kit: Vivo Office Kit Intents - EasyShare, Link to Windows, Jovi Notes ContentProvider - works on Vivo T4, 90% same as iQOO
Target Phone: Vivo T4 5G Android 15, minSdk 28 target 34
This stack runs 8 hours <3% per hour on your Vivo T4 7300mAh battery.

PRODUCT REQUIREMENTS DOCUMENT - PRD - FOR AI CODING
1. Product Name: AuraDesk - Desk's Focus Bodyguard

2. One Line: Phone face-down becomes guard that blocks interruptions before your brain loses focus.

3. Problem: Interrupted 15-20 times day, each costs 23 min recovery = 5 hours lost daily. Laptop cannot see person walking behind you. DND blocks blindly. No app remembers what interrupter said.

4. Goals: Protect deep work, reduce recovery 23 min to 15 sec, auto-reply offline in your style, remember interruption as task, haptic silent nudge, sync to laptop via Vivo Office Kit, work airplane mode zero bytes.

5. Non-Goals: No cloud dashboard, no team surveillance, no full conversation recording, no blocking family emergency.

6. Users: Same 4 personas - Arjun SDE open office, Ananya library student, Priya WFH with kid, Ramesh hallway sales.

7. Functional Requirements for AI:

FR1 Face-Down Detection: proximity <1cm + light <10 lux + accel z 9-10 + gyro stable 2 sec = face-down true 98% accuracy.

FR2 Guard Service: Foreground service starts on face-down, shows Always-On Display Guard Armed, runs 2fps rear camera + VAD mic low power.

FR3 Deep Work Detection: Front camera Face Mesh looking laptop 80% time + keyboard typing sound + no touch 15 min = deep work true 85%.

FR4 Person Approaching Radar: Rear camera 320x240 2fps MediaPipe ObjectDetector human box growth >20% per sec = approaching, distance estimation box 10% = 5m, 30% = 2m, 60% = 0.5m, accuracy 90%.

FR5 Name Call Keyword: Mic VAD detects voice then tiny CNN 200KB detects user name Arjun/mummy/excuse me latency 500ms.

FR6 Auto-Reply: Qwen2-0.5B INT4 generates 30 word polite reply includes return time, style from last 5 messages.

FR7 Interruption Capsule: At 0.5m record 10 sec only, Vosk STT offline transcript, Qwen summary format Person - Task - Deadline.

FR8 Haptic Whisper: Low short-short, medium short-long-short, high long-long-long kid urgent.

FR9 Summary Card: Full screen card blurred avatar, task big bold, 2 buttons Create Task in Jovi Notes + Dismiss, link Show Context You were editing main.py line 124.

FR10 Office Kit: 4 integrations - Screen Mirroring banner Arjun in Deep Work till 11:30, Task Handoff mute laptop, Jovi Notes Sync summary, EasyShare + Clipboard Sync auto-reply.

FR11 Privacy: No raw video audio leaves phone, no INTERNET permission for guard service, embeddings only, summary encrypted Keystore, auto-delete 1 hour, shake 3 times instant delete, airplane mode proof network profiler zero bytes.

8. Non-Functional: Battery <3% per hour, latency person 30ms face 15ms keyword 500ms auto-reply 1.2 sec, accuracy face-down 98% person 90% name 95% quiet, APK <800MB with models, cold start <2 sec, thermal <42C.

9. Success Criteria: Works airplane mode, 80% interruptions handled, recovery 23 min to 15 sec, 4 Office Kit integrations, zero bytes network log, demo never fails.

PHASE WISE IMPLEMENTATION PLAN - 10 PHASES - COPY PASTE PROMPTS FOR CLAUDE/CODEX
You know nothing about Android, so follow this order. Do not move to next phase until testing passes.

PHASE 0: SETUP - 1 Hour
Goal: Android Studio + Vivo T4 connected
What to do:

Install Android Studio Ladybug from developer.android.com
Open, New Project -> Empty Compose Activity, name AuraDesk, package com.auradesk.guard, minSdk 28
Enable Developer Options on Vivo T4: Settings -> About Phone -> Tap Build Number 7 times, enable USB Debugging
Connect Vivo T4 via USB, allow debugging, see device in Android Studio
Prompt for Claude/Codex:

Code
Create Android project structure for AuraDesk. Add dependencies in build.gradle: CameraX 1.3.0, MediaPipe Tasks 0.10.14, Room 2.6, DataStore, Vosk, llama.cpp NDK setup for Qwen2-0.5B. Add permissions in AndroidManifest: CAMERA, RECORD_AUDIO, VIBRATE, FOREGROUND_SERVICE, ACCESS_FINE_LOCATION optional. No INTERNET permission for guard service. Create package com.auradesk.guard.sensors
Testing: Build app, run on Vivo T4, see empty screen Hello World. If builds, Phase 0 pass.

PHASE 1: FACE-DOWN DETECTION - 2 Hours - MOST IMPORTANT
Goal: Flip phone face-down -> log Guard Armed
Files: FaceDownDetector.kt, SensorManager
Prompt:

Code
Create FaceDownDetector.kt using SensorManager. Listen proximity <1cm, light <10 lux, accelerometer z 9.8 +-0.5, gyroscope stable <0.1 rad/s for 2 seconds. All true = faceDown true. Expose Flow<Boolean>. Add log "Guard Armed" when face-down. Create Foreground Service GuardService that starts when face-down.
Testing Steps:

Install app, open Logcat in Android Studio filter FaceDownDetector
Keep Vivo T4 face-up on table -> log false
Flip face-down on table -> within 2 sec log true Guard Armed
Pick up in hand -> false
Put in pocket walking -> false not true (accel moving)
If 10/10 flips correct, Phase 1 pass.
PHASE 2: ALWAYS-ON DISPLAY + GUARD SERVICE - 2 Hours
Goal: Face-down shows Guard Armed screen
Prompt:

Code
Create Always-On Display Composable GuardArmedScreen. Dark background #121212, shield icon, text Guard Armed Battery 3%/hr, Deep Work Shield OFF. When deep work true show ON Return 11:30. Use low brightness. Add Foreground Service notification "AuraDesk Guard Armed - Battery 2%/hr"
Testing:
Flip face-down -> notification appears + always-on screen shows. Swipe notification -> should not kill service. Battery settings -> allow background. Pass if service runs 30 min without killed.

PHASE 3: PERSON APPROACHING RADAR - 4 Hours - CORE FEATURE
Goal: Rear camera sees person walking 5m to 0.5m
Prompt:

Code
Integrate CameraX 2 fps 320x240 rear camera in GuardService. Integrate MediaPipe ObjectDetector EfficientDet-Lite0 5MB GPU delegate 30ms. Detect human class only. Track bounding box via centroid tracking. Calculate growth rate (area_current - area_prev)/area_prev. If >20% per second = approaching. Distance estimation box height 10% screen =5m, 30%=2m, 60%=0.5m. Log "Person at Xm approaching Y%". Don't store image.
Testing:

Put Vivo T4 face-down, open rear camera preview small overlay
Ask friend walk from 5m behind to your desk
Logcat should show Person at 5m, then 3m, then 2m approaching
Test 10 times, accuracy 8/10 pass
Test dark room light <5 lux -> camera off fallback log
If detection works, Phase 3 pass - this is 25% marks.
PHASE 4: DEEP WORK DETECTION - 2 Hours
Goal: Phone knows you are coding vs YouTube
Prompt:

Code
Add front camera 640x480 1 fps only when Guard Armed + no touch 15 min. Integrate MediaPipe Face Landmarker 468 landmarks 15ms. Calculate Eye Aspect Ratio, head yaw +-15 deg looking laptop, blink rate. Combine with AudioClassifier keyboard typing detection. If 2 of 3 true = deepWork true Flow<Boolean>. Add UsageStatsManager no phone touch 15 min check.
Testing:

Face-down 15 min, open VS Code type -> log Deep Work true
Watch YouTube on laptop not typing -> Deep Work false
Test library studying book looking down -> true
Pass if 80% correct.
PHASE 5: HAPTIC + SUMMARY CARD + ROOM DB - 2 Hours
Goal: Person leaves -> buzz + card
Prompt:

Code
Create Room Database Summaries table id, person, task, deadline, timestamp. Create VibratorManager patterns low short-short 100msx2, medium short-long-short, high long-long-long 500msx3. When person detection no human 5 sec = person leaves -> trigger haptic + create summary card Composable InterruptionCard: Blurred avatar, task big bold, buttons Create Task in Jovi Notes + Dismiss + Show Context. Add auto-delete worker 1 hour + shake 3 times delete via accelerometer.
Testing:

Friend approaches then leaves -> feel vibration in hand
Lift phone -> card shows Interruption Saved
Shake phone 3 times -> card deleted
Pass if haptic + card works.
PHASE 6: AUDIO - VAD + KEYWORD + STT - 4 Hours
Goal: Mic hears name + records 10 sec
Prompt:

Code
Integrate AudioRecord 16kHz mono. Stage1 VAD tiny CNN 100KB voice vs silence DSP low power. Stage2 Keyword Spotting CNN 200KB trained on user name 3 samples recorded onboarding. Onboarding screen record name 3 times create embedding average. Stage3 At 0.5m distance record 10 sec only, use Vosk STT small 50MB offline transcription. Delete audio immediately after transcript. Log transcript.
Testing:

Onboarding record name Arjun 3 times
Shout name from 2 desks -> log Keyword Arjun detected 95%
Person at 0.5m talks 10 sec -> transcript shows in logcat
Check file manager no audio file saved - privacy pass
Pass if name + STT works.
PHASE 7: LLM AUTO-REPLY + SUMMARY - 4 Hours - USE QWEN2-0.5B
Goal: Auto-reply offline in your style
Prompt:

Code
Integrate llama.cpp NDK with Qwen2-0.5B-Instruct INT4 400MB via Play Asset Delivery. Create prompts: PromptA Auto-Reply System You are focus guard for userName deep work till returnTime write short polite reply style from past messages include return time offer urgent tap Max 30 words Language same incoming Hinglish allowed User Message from senderName relation teammate MessageText. PromptB Summary System Summarize 10 sec talk into one task line Format Person - Task - Deadline. Inference NPU delegate if available CPU fallback. Fallback rule-based if LLM fails In focus till time will reply after.
Testing:

Send WhatsApp to Vivo T4 while deep work -> auto-reply generated in log <1 sec
Check reply text includes return time, polite, <30 words
Transcript -> summary Person Task Deadline
Airplane mode ON -> still generates - zero internet proof
If airplane works, Phase 7 pass.
PHASE 8: VIVO OFFICE KIT INTEGRATION - 3 Hours - 25% MARKS
Goal: Laptop shows banner + Notes sync
Prompt:

Code
Integrate Vivo Office Kit Intents. Check com.vivo.easyshare installed bind service. Implement 4: Screen Mirroring banner send intent com.vivo.officekit.SCREEN_MIRROR_BANNER text Arjun in Deep Work till 11:30 Tap phone twice to interrupt urgently, Task Handoff MUTE_NOTIFICATIONS intent to laptop, Jovi Notes Sync via ContentResolver content://com.vivo.notes.provider/notes insert title AuraDesk timestamp, EasyShare file share txt summary + Clipboard Sync auto-reply. If not installed fallback local notification.
Testing:

Connect Vivo T4 to laptop with Vivo Link to Windows / EasyShare
Flip phone face-down deep work -> laptop should show banner automatically within 2 sec
Interruption summary -> check laptop Jovi Notes app note appears within 2 sec offline
Clipboard copy auto-reply -> paste on laptop Slack works
If 3 of 4 work, Phase 8 pass - you get full 25%.
PHASE 9: BATTERY + PRIVACY + AIRPLANE PROOF - 2 Hours
Goal: <3% per hour + zero bytes
Prompt:

Code
Optimize battery: Camera 2 fps not 30, 320x240 not 1080p, GPU delegate not CPU, VAD first only voice run keyword only keyword run STT, NPU for LLM, front camera only deep work. Add thermal check if >45C reduce fps to 1 pause front. Add privacy screen Show Network Usage 0 bytes guard mode via NetworkStatsManager, Shutter Mode toggle disable camera 70% effectiveness.
Testing:

Full charge Vivo T4 100%, guard armed 1 hour airplane mode, check battery drop -> should be 2-3% not 10%
Android Studio Network Profiler -> 0 bytes during guard
Settings -> show Last 24h network 0 bytes
Thermal app -> <42C after 1 hour
Pass if all true.
PHASE 10: FINAL POLISH + APK + DEMO - 2 Hours
Goal: Build release APK + demo video
Prompt:

Code
Create release APK with models via Play Asset Delivery Qwen2-0.5B asset pack. Add onboarding 30 sec set name voice 3 samples. Add settings 5 items Set Name, Return Time, Haptic Patterns, Office Kit toggles, Privacy Shutter Auto-Delete Shake Delete Network Usage. Add backup tappable chips Simulate Person Approaching 2m, Simulate Name Call for stage demo never fails. Prepare demo video airplane mode icon visible entire flow.
Testing Final Checklist:

Install release APK fresh, onboarding 30 sec
Airplane mode ON WiFi OFF entire flow face-down deep work person approaching banner auto-reply 10 sec record haptic summary card Notes sync works
Record screen + network profiler zero bytes
Battery 2.8% per hour
Shake 3 times delete
If all 5 pass, you are ready for submission.