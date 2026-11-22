# Zero-Trust Keyboard

A private Android keyboard for the Zero-Trust suite. Store name **Zero-Trust Keyboard**,
launcher name **ZT Keyboard**.

Everything you type stays on your phone. Word prediction, autocorrect and learning run on the
device, and the keyboard itself never uses the internet. Hebrew and English are first-class
and both are on by default, including typing both in one sentence.

Plan and roadmap (phases K0 to K6): swipe typing with our own open decoder, on-device voice
typing, on-device translation, and optional end-to-end encrypted sync of your personal
dictionary through your Zero-Trust account.

The Hebrew dictionary (181,813 words) is our own "ZT Hebrew" list: the Android Open Source
Project's Hebrew word list, widened with the words people really use (from OpenSubtitles
counts) that the Hspell speller accepts. Sources, licences and how it is built are in
[dictionaries/README.md](dictionaries/README.md).

Emoji search and emoji names work in Hebrew and English with nothing to download: type
כלב or dog in the emoji search, or just in a message, and the emoji is offered. The words
come from Unicode CLDR.

Signed in, you can turn on **Sync my personal dictionary** in the ZT Keyboard app: the words and
shortcuts you added are kept the same on every phone of the account, end-to-end encrypted. It is
off until you turn it on, and it runs while the app is open and unlocked, because the keyboard
itself never holds the account's key.

## Two editions

| | ZT Keyboard | ZT Keyboard Offline |
|---|---|---|
| App id | `dev.sagron.zerotrustkeyboard` | `dev.sagron.zerotrustkeyboard.offline` |
| The keyboard | yes | yes, the same one |
| ZT app around it (setup, optional Zero-Trust account, app lock, shared settings) | yes | no |
| `INTERNET` permission | yes, for the account only | **no** |
| Launcher icon opens | the ZT app | the keyboard's settings |

The keyboard is a native Android service (`android/`, Kotlin/Java/C++). The ZT app is Flutter
on [Zero-Trust-Core](https://github.com/EdoSag/Zero-Trust-Core) (`lib/`) and lives only in
the full edition; the Offline edition contains no Flutter code at all. The two can be
installed side by side.

## Based on HeliBoard

ZT Keyboard is a fork of [HeliBoard](https://github.com/HeliBorg/HeliBoard) 4.2
(upstream commit `0c60dffb03a4e2e7a37cb82210f5299f93c03979`), which is based on OpenBoard and
Google's AOSP keyboard. Many thanks to the HeliBoard, OpenBoard and AOSP contributors.

The first commit after the repository start is an unchanged copy of HeliBoard 4.2, so every
ZT change is visible as a diff against upstream. To take a newer HeliBoard release, apply the
diff between the two upstream tags, for example:

```
git clone https://github.com/HeliBorg/HeliBoard /tmp/heliboard
git -C /tmp/heliboard diff v4.2 v4.3 | git apply -3 --directory=android
```

Java/Kotlin package names (`helium314.keyboard...`) are kept on purpose so upstream diffs keep
applying; the app id is `dev.sagron.zerotrustkeyboard`.

Users cannot load a closed-source gesture library into the keyboard
(`BuildConfig.ALLOW_USER_LIB` is off). Swipe typing comes from our own open decoder in
`android/app/src/main/java/dev/sagron/zerotrustkeyboard/swipe/` (AGPL-3.0, pure Kotlin, runs
on the device). It compares the shape of the swipe with the ideal path of every word in the
loaded dictionaries (main, learned words, personal dictionary, contacts) and weighs in how
common the word is. It works for every language that has a dictionary and a letter layout.
It does not yet use the previous word, so look-alike swipes ("the" / "there") go to the
more common word, with the other one in the suggestion strip.

Voice typing (phase K3) is in `android/app/src/main/java/dev/sagron/zerotrustkeyboard/voice/`
with the engine in `android/whisper/`: whisper.cpp (MIT), fetched by CMake at a pinned release
and built into `libzt_whisper.so`. The microphone key records into memory, the speech is
turned into text on the phone, and the sound is never stored or sent. It needs a speech
model: a Whisper file in ggml format, added in Settings > Voice typing. The language is the
keyboard's current language.

The full edition can download a model there (the Offline edition adds one from a file). The
models are mirrored to the `voice-models` release of this repository by the "Voice models"
workflow; `voice-models/sources.tsv` names them with a pinned sha256 each, and the app checks
every download against the published list. A model whose name ends in a language code, like
`.he.bin`, is tuned for that language and is used whenever the keyboard is in it: for Hebrew
that is the ivrit.ai model.

Translating (phase K5) is in `android/app/src/main/java/dev/sagron/zerotrustkeyboard/translate/`.
The Translate key in the toolbar replaces what is written in the text field (or the selected
part) with its translation, between the two languages chosen in Settings > Translate, and
offers Undo. Hebrew and English are told apart by the letters; two languages with the same
letters follow the language the keyboard is in.

The engine is the one ZT Browser uses: Firefox's Bergamot (MPL-2.0, in
`android/app/src/full/assets/translate`), run in a hidden WebView whose every request is
answered by the keyboard itself from its own files, so it has no way to the network and the
text never leaves the phone. The language models are Mozilla's Firefox Translations models,
downloaded per language in Settings > Translate from ZT Browser's `translation-models`
release. The list there is signed (Ed25519); the keyboard checks the signature and then each
file's size and sha256 before using it. The Offline edition has no Translate key: it cannot
download a language.

Inserts from other Zero-Trust apps (phase K6) are in
`android/app/src/main/java/dev/sagron/zerotrustkeyboard/insert/`. The "Insert from ZT apps"
toolbar key (off by default, switch it on in Settings > Toolbar) opens another Zero-Trust
app's own pick screen with the intent action `bz.zerotrust.action.PICK_TEXT`. The user
unlocks that app as usual, picks a value, and the app answers with only that text in the
extra `bz.zerotrust.extra.TEXT`. The keyboard types it when it is back in the text field
that asked, within two minutes, and never anywhere else. The keyboard never reads another
app's data. Both sides check that the other app is signed with the same key
(`PackageManager.checkSignatures`), so no other app can offer itself to the keyboard or ask
a Zero-Trust app for a value. The key does nothing while the phone is locked.

HeliBoard's settings migrations are keyed to its own version codes; `UPSTREAM_VERSION_CODE`
in `android/app/build.gradle.kts` must be raised when taking a newer HeliBoard release.

## Building

Branches: work happens on `Dev`; `main` holds releases. Every push to `main` builds both
editions, signed, plus a Google Play bundle of the full edition, and publishes them as a
GitHub Release (`.github/workflows/android-apk.yml`). Pull requests get test builds, the
keyboard's unit tests and the app's widget tests. Every build first runs the suite's release
checks (`dart run zt_release_checks --app keyboard --database`): keys, sync and sign-in with
fixed values, app setup, and the database rules.

The Google Play listing (title, descriptions, icon and feature graphic, in English and Hebrew)
is in `fastlane/metadata/android/`. Screenshots are added there by hand.

Local builds need Flutter 3.47.9, the Android SDK and NDK, and a `.env` with `SUPABASE_URL`
and `SUPABASE_ANON_KEY`. The app version is the `version:` line of `pubspec.yaml`.

```
# Full edition
flutter build apk --release --flavor full

# Offline edition (run the line above, or `flutter pub get` plus
# `flutter build apk --config-only --flavor full`, once first: it writes android/local.properties)
cd android && ./gradlew -PztOffline :app:assembleOfflineRelease

# The keyboard's unit tests
cd android && ./gradlew -PztOffline :app:testOfflineRunTestsUnitTest
```

The edition is chosen by the `ztOffline` Gradle property, not by a flavor picker, because the
Flutter Gradle plugin cannot be left out of one flavor only.

## License

Copyright (C) 2026 Edo Sagron (https://zerotrust.bz)

ZT Keyboard is open source under the [GNU Affero General Public License v3.0](LICENSE)
(AGPL-3.0-only), the same licence Signal uses. The HeliBoard code it is built on is
licensed under the [GNU General Public License v3.0](LICENSE-GPL-3.0) by its authors, and
parts that come from the AOSP keyboard also under the [Apache License 2.0](LICENSE-Apache-2.0).
GPL-3.0 and AGPL-3.0 code may be combined (section 13 of both licences).

- You may use, study, change and share the code, including in your own projects.
- If you share a changed version, or run it for other people over a network, you must
  publish your full source code under the same licence.
- This program comes with no warranty; see the licence for details.

### Name and logo

The licences cover the code, not the brand. "Zero-Trust", "ZT", the app names and icons are
reserved. If you publish a fork, give it a different name and icon so nobody mistakes it
for an official Zero-Trust app.

### Contributing

Pull requests are welcome. By sending one you agree that your contribution is licensed
under the AGPL-3.0 and that the copyright holder may also license it under other terms,
including commercially. This applies to new contributions only; the HeliBoard code keeps
its own GPL-3.0 licence from its authors.
