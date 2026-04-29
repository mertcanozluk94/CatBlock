# CatBlock

CatBlock is an Android app that fights doom-scrolling. When you spend too long in a
chosen app, a giant cat fills the screen, blocks every touch, and refuses to leave
until your break is over.

## What's in this build

- **5-step visual onboarding guide** — explains permissions and how the app works in
  plain language, with one of the cat characters illustrating each step.
- **15 languages** — English, Turkish, Spanish, Portuguese (BR), French, German,
  Russian, Arabic, Hindi, Indonesian, Japanese, Korean, Italian, Simplified Chinese,
  Persian. The app picks the language from the system settings automatically.
- **107 different break messages** — motivational, mindful, funny, scolding, and
  philosophical. A random one is picked every time the cat appears.
- **50 unique cat characters** — every appearance shows a different cat: orange
  tabbies, calicos, tuxedos, Russian blues, Siamese, sleepy ones, smug ones, grumpy
  ones, ones in tiny hats. All vector art, all original.
- **Live animations** — every cat breathes, sways, pulses, and pops onto the screen
  with a bounce. The speech bubble floats too.
- **Optional voice (off by default)** — if you turn it on, the cat reads its message
  aloud using your phone's built-in text-to-speech voice. No copyrighted sounds.

## Project layout

```
app/src/main/java/com/catblock/app/
├── MainActivity.kt                  ← onboarding gate + bottom nav
├── data/
│   ├── AppCatalog.kt                ← lists installed apps
│   └── SettingsRepository.kt        ← DataStore: rules, voice, onboarding state
├── overlay/
│   └── CatOverlayManager.kt         ← full-screen blocking overlay + animations + TTS
├── permissions/
│   └── PermissionUtils.kt           ← overlay + usage stats permission helpers
├── service/
│   ├── UsageMonitorService.kt       ← foreground service polling usage stats
│   └── BootReceiver.kt              ← restarts service after reboot
└── ui/
    ├── CatBlockViewModel.kt
    ├── theme/Theme.kt
    └── screens/
        ├── OnboardingScreen.kt      ← 5-step guide + permission grants
        ├── AppListScreen.kt         ← app picker with steppers
        └── SettingsScreen.kt        ← voice toggle, replay guide

app/src/main/res/
├── drawable/cat_00.xml … cat_49.xml ← the 50 cats
├── values/strings.xml               ← English (master, with 107 messages)
└── values-{tr,es,pt-rBR,fr,de,ru,ar,hi,in,ja,ko,it,zh-rCN,fa}/strings.xml
```

## Running it

1. Open the project in Android Studio (Hedgehog or newer).
2. Plug in a phone with USB debugging on.
3. Click Run. First launch will walk through the 5-step guide and ask for the two
   special permissions (overlay + usage access). Both have to be granted in the
   system settings page CatBlock opens for you.
4. On the main screen, toggle on the apps you want guarded. Set how long until the
   cat appears and how long the break lasts. Done.
5. To hear the cat speak, open the Settings tab and turn on **Cat voice**.

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- DataStore Preferences for persistence
- WindowManager `TYPE_APPLICATION_OVERLAY` for the cat
- UsageStatsManager polled every 2s by a foreground service
- Android TextToSpeech for the optional voice
- Vector drawables for all 50 cats — no images, no copyright concerns

## Building a release

`Build → Generate Signed Bundle / APK` in Android Studio. See the Play Store
deployment notes you already have for the .aab + signing flow.
