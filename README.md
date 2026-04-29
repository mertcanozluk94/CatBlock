# CatBlock 🐱

CatBlock is an Android app that fights doom-scrolling. When you spend too long
in a chosen app, a giant cat fills the screen, blocks every touch, and refuses
to leave until your break is over.

## Features

- **49 different cats** — mix of professionally animated Lottie cats and real
  cat video clips. A different one every time.
- **15 languages** — auto-detects from system: English, Turkish, Spanish,
  Portuguese (BR), French, German, Russian, Arabic, Hindi, Indonesian,
  Japanese, Korean, Italian, Simplified Chinese, Persian.
- **107 break messages** — random one each appearance: motivational, mindful,
  funny, scolding, philosophical.
- **100 "wait" messages** — rotating prompts at the bottom of the overlay.
- **5-second Skip button** — appears after the cat has shown for 5 seconds.
- **Optional TTS voice** — the cat reads the message aloud (off by default).
- **Per-app limits** — set how long until the cat appears, and how long the break lasts.
- **5-step visual onboarding** — explains how everything works.

## How it works

1. Open CatBlock and grant the two required permissions
   (display over other apps, usage access).
2. Pick the apps you scroll too much.
3. For each app, set the trigger time and break duration.
4. Use your phone normally — CatBlock runs in the background.
5. When you go over your limit, the cat appears.

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- DataStore Preferences
- WindowManager TYPE_APPLICATION_OVERLAY for the cat
- UsageStatsManager polled by a foreground service
- Lottie + Media3 ExoPlayer for cat animations
- Android TextToSpeech for the optional voice

## Building

1. Clone the repo.
2. Open the project in Android Studio (Hedgehog or newer).
3. Plug in a phone with USB debugging on.
4. Click Run.

minSdk: 26 · targetSdk: 34 · Kotlin 1.9.24 · AGP 8.5.2

## Asset credits

The cat animations and clips bundled in `app/src/main/res/raw/` come from:

- [LottieFiles](https://lottiefiles.com/) — Free Lottie animations
- [Pexels](https://www.pexels.com/) — Pexels License
- [Pixabay](https://pixabay.com/) — Pixabay Content License
- [Mixkit](https://mixkit.co/) — Mixkit Free License

All assets used are free for commercial use under their respective licenses.

