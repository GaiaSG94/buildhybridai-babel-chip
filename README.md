# Babel Chip

Live conversation translation where you can see the handoff.

Babel Chip translates a short sentence **on the device first** (Gemini Nano
through the ML Kit Prompt API, wired through Firebase AI Logic) and shows the
confidence it got. When the device is unsure — or the sentence carries idiom,
tone or culture that a literal translation would flatten — the **cloud model
takes over**, and the message says so.

Every bubble shows where the inference ran (`⚡ LOCAL` / `☁ CLOUD`), how long
it took, and the confidence that triggered the escalation.

## Why it matters

Travel, field work, customer calls: a translator app that streams everything
to a server is slow, expensive and leaks the conversation. Babel Chip keeps
the common case (short, literal sentences) on the phone and pays for the cloud
only when a human would actually notice the difference.

## How Firebase AI Logic is used

- `FirebaseAI.getInstance(GenerativeBackend.googleAI())` — Firebase AI Logic
  client SDK (`com.google.firebase:firebase-ai`), Gemini Developer API backend
  (free tier, no billing account needed).
- Two model instances (`src/main/java/com/gaia/babelchip/engine/TranslationEngine.kt`):
  - `OnDeviceConfig(mode = InferenceMode.ONLY_ON_DEVICE)` — the on-device pass;
  - `OnDeviceConfig(mode = InferenceMode.ONLY_IN_CLOUD)` — the nuance pass.
- `firebase-ai-ondevice` brings the Prompt API from ML Kit (AICore), which
  downloads and keeps the on-device model up to date.
- App Check protects every cloud call (debug provider in debug builds).

## Build and run

```bash
# Firebase config (never committed):
cp <your project>/google-services.json app/google-services.json

./gradlew assembleDebug     # APK in app/build/outputs/apk/debug/
./gradlew installDebug       # on a connected device or emulator
./gradlew testDebugUnitTest  # unit tests
```

`google-services.json` is git-ignored: the Google Services Gradle plugin is
only applied when the file is present, so the repository builds and the unit
tests run without a Firebase project.

## On-device requirements

- Android 8.0 (API 26) or newer, **foreground only**.
- The device must be in the
  [supported list for the Prompt API](https://developers.google.com/ml-kit/genai#prompt-device);
  AICore downloads the model on demand (check `FirebaseAIOnDevice.checkStatus()`).
- On-device requests are limited to single-turn text (≤ 4000 tokens, short
  outputs); only English and Korean have been validated for on-device
  inference, so the app shows a clear status when the device model is not
  available and falls back to the cloud.

## Architecture

```
app/src/main/java/com/gaia/babelchip/
  MainActivity.kt                 UI (views), languages, Firebase boot, bubbles
  engine/Prompts.kt               prompts for the two passes
  engine/Parsing.kt               pure parsing + escalation rule (unit tested)
  engine/TranslationEngine.kt     Firebase AI Logic hybrid calls
app/src/test/java/.../ParsingTest.kt
```

## Demo script (3 minutes)

1. Launch the app: the status line shows whether the on-device model is ready.
2. Type a short literal sentence ("Where is the station?") → the bubble shows
   `⚡ LOCAL` with the latency and the confidence.
3. Type a sentence with idiom ("Let's break a leg before the pitch") → the
   bubble shows `☁ CLOUD` and mentions that it escalated from the device pass.
4. Show the device model status line and the on-device download behaviour.

## Limitations

- On-device inference is experimental in the Firebase AI Logic SDK (no SLA).
- The confidence comes from the model itself: it is an estimate, shown as such.
- The cloud fallback needs a network; the device pass needs a downloaded model.

## Built with

Kotlin, Android (Views, Material 3), Firebase AI Logic (on-device + cloud),
ML Kit Prompt API, Gradle, JUnit.
