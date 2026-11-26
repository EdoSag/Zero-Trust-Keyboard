# Zero-Trust Keyboard

A private Android keyboard for the Zero-Trust suite. Everything you type
stays on your phone: word prediction, autocorrect and learning run on the
device, and the keyboard itself never uses the internet. On the phone it is
called **ZT Keyboard**; in stores and on release pages, **Zero-Trust
Keyboard**. Part of the [Zero-Trust suite](https://zerotrust.bz).

## What it does

- **Hebrew and English first-class**, both on by default, including typing
  both in one sentence. Layouts for many other languages come from
  HeliBoard.
- **Swipe typing** with our own open decoder, on the device.
- **Voice typing** on the phone (whisper.cpp): the sound is never stored or
  sent.
- **Translate** what you wrote, on the phone (Firefox's Bergamot engine and
  Mozilla's models).
- **Writing help** (off by default): Proofread, and "Finish my sentence",
  with a small language model that runs on the phone. With ZT Assist on the
  phone, Proofread uses its larger model (better in Hebrew) and a Rewrite
  key rewords what you wrote.
- **Emoji search and emoji names** in Hebrew and English, with nothing to
  download.
- **Codes typed for you:** one-time codes from text messages, and codes
  from ZT Authenticator, offered as a chip above the keyboard.
- **Insert from ZT apps** (off by default): pick a value in another
  Zero-Trust app and the keyboard types it.
- **Sync my personal dictionary** (optional, signed in): your added words
  and shortcuts on every phone of the account, end-to-end encrypted.
- **Offline edition** with no internet permission at all.

How each of these works is under [Based on HeliBoard](#based-on-heliboard).

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

**Codes from text messages**: when a one-time code arrives by SMS ("Your code is 482913",
"קוד האימות שלך: 482913"), the keyboard offers it as a chip in the suggestion strip, in a
number field or an empty one-line field, for 10 minutes. One tap types it. Turn it on under
Settings > Text correction > Suggest codes from text messages; the keyboard then asks for
permission to read text messages. The messages are read on the phone only and nothing is
kept. Android 15 hides these codes from notification readers, so reading the messages is the
only way. Google Play allows that permission only for SMS apps, so this is in the GitHub
builds only; the Play build leaves the permission and the setting out.

## Privacy

ZT Keyboard works without an account. When you sign in, it is zero-knowledge like every Zero-Trust app: your data is encrypted on
your device, with keys made from your password, before anything is sent.
The server stores only ciphertext and your password itself is never
sent, so neither we nor anyone who breaks into the server can read your
data. The recovery key shown at sign-up is the only way back in if you
forget the password. How the keys work:
[Zero-Trust-Core](https://github.com/EdoSag/Zero-Trust-Core#key-hierarchy) and
[zerotrust.bz/security](https://zerotrust.bz/security/).

- The keyboard itself never uses the internet. In the full edition the
  `INTERNET` permission is for the account and for downloading models and
  languages you ask for; the Offline edition has no such permission.
- The keyboard never holds the account's key. Dictionary sync runs in the
  ZT app, while it is open and unlocked.
- Voice typing, translation and writing help run on the phone. Every
  downloaded model is checked against a published size and sha256.
- Text messages are read on the phone only, for the code chip, and nothing
  is kept.
- Values from other Zero-Trust apps are handed over only between apps
  signed with the same key.

## One account, one core

Signing in is optional. The account is the one Zero-Trust account that opens every app in the suite. Sign-in, unlock
(fingerprint or face, auto-lock), the recovery key, settings, the look,
sync and languages come from
[Zero-Trust-Core](https://github.com/EdoSag/Zero-Trust-Core), so they work
the same in every app. Accounts made before the core still sign in and
see their data.

## Install

Android: get the latest signed APK from this repository's
[Releases](https://github.com/EdoSag/Zero-Trust-Keyboard/releases/latest),
or from [zerotrust.bz/install](https://zerotrust.bz/install/#keyboard),
which also shows each file's SHA-256, the signing fingerprint and an
Obtainium link. GitHub Releases are the main way the apps are published.

Builds from GitHub check for a newer release themselves (Settings > About,
one anonymous request a day, can be turned off). Every Zero-Trust app is signed
with the same key, and Android only installs an update signed with it.

Each release has two APKs: ZT Keyboard, and ZT Keyboard Offline (no internet
permission, no account, no ZT app around it). They can be installed side by
side; see [Two editions](#two-editions).

## Languages

The ZT app around the keyboard is in English and Hebrew, with right-to-left
layout. The keyboard itself has layouts and dictionaries for many
languages; Hebrew and English are on by default.

## Two editions

| | ZT Keyboard | ZT Keyboard Offline |
|---|---|---|
| App id | `dev.sagron.zerotrustkeyboard` | `dev.sagron.zerotrustkeyboard.offline` |
| The keyboard | yes | yes, the same one |
| ZT app around it (setup, optional Zero-Trust account, app lock, shared settings) | yes | no |
| `INTERNET` permission | yes, for the account only | **no** |
| `READ_SMS` permission (codes from text messages, off until turned on) | yes, GitHub build only | yes |
| Writing help language model | downloaded in settings | added from a file |
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

One-time codes from ZT Authenticator come the same way. On a field that asks for a code (a
numeric password field, or a hint or label like "code" or "verification"), the strip shows
"Code from ZT Authenticator" when that app is on the phone and signed with our key. A tap
opens it with the category `bz.zerotrust.category.ONE_TIME_CODE` and the extra
`bz.zerotrust.extra.FOR_PACKAGE` (the app of the text field), so it can pick the account.
The code it hands back is typed in that field only, without being learned or put on the
clipboard, and is dropped once `bz.zerotrust.extra.VALID_UNTIL` has passed. The chip stays
away when the field already shows an autofill suggestion.

Writing help (phase K6) is in `android/app/src/main/java/dev/sagron/zerotrustkeyboard/lm/`
with the engine in `android/llm/`: llama.cpp (MIT), fetched by CMake at a pinned release tag
and sha256 and built into `libzt_llm.so`. It needs a small language model in GGUF format,
downloaded once in Settings > Writing help (or added from a file in the Offline edition):
Qwen2.5 0.5B Instruct (Apache-2.0, about 400 MB), mirrored to the `language-models` release
of this repository by the "Language models" workflow from the pinned source in
`language-models/sources.tsv`. The app checks every download against the published size and
sha256. The model is used for two things, both on the phone only: the Proofread key (off by
default, switch it on in Settings > Toolbar) fixes spelling and grammar of the text in the
field, or the selected part, and offers Undo; "Finish my sentence" (off by default) offers
the next few words above the keyboard after a short pause in typing. Neither runs in password
fields, in incognito mode or while the phone is locked.

Where ZT Assist (`dev.sagron.zerotrustassist`) is on the phone, the keyboard can hand a text
to it instead (`android/app/src/main/java/dev/sagron/zerotrustkeyboard/assist/`): the
Proofread key then uses ZT Assist's larger model (Settings > Writing help > Proofread with
ZT Assist, on by default), and falls back to the keyboard's own model when ZT Assist has no
model yet. The Rewrite key (off by default, Settings > Toolbar) has ZT Assist reword the text
so it reads better; it needs ZT Assist. `AssistClient.kt` is the keyboard's own client of ZT
Assist's answering service, the same protocol as `packages/zt_assist` in Zero-Trust-Core:
messages over a bound service, protocol 1. It sends nothing unless the app under that name is
signed with the keyboard's key, and ZT Assist checks the keyboard the same way. The text
stays on the phone and is not kept; in incognito mode it is not handed over at all. An answer
that was cut short, or written for a part of the text only, never replaces the text.

HeliBoard's settings migrations are keyed to its own version codes; `UPSTREAM_VERSION_CODE`
in `android/app/build.gradle.kts` must be raised when taking a newer HeliBoard release.

## Build from source

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

Never add `cryptography_flutter`: it breaks the core's key derivation and
sign-in fails.

## Branches and releases

Work happens on `Dev`: pull requests target `Dev`, never `main`. A release is
one `Dev` → `main` pull request. Every push to `main` builds both
editions, signed, plus a Google Play bundle of the full edition, and publishes them as a
GitHub Release (`.github/workflows/android-apk.yml`). Pull requests get test builds, the
keyboard's unit tests and the app's widget tests. Every build first runs the suite's release
checks (`dart run zt_release_checks --app keyboard --database`): keys, sync and sign-in with
fixed values, app setup, and the database rules.

The Google Play listing (title, descriptions, icon and feature graphic, in English and Hebrew)
is in `fastlane/metadata/android/`. Screenshots are added there by hand.

## Support and security

- Help centre: [zerotrust.bz/help](https://zerotrust.bz/help/). Questions and
  account deletion: support@zerotrust.bz.
- Security problems: please report them privately to cyber@zerotrust.bz. What
  the encryption does and doesn't protect against is on
  [zerotrust.bz/security](https://zerotrust.bz/security/).
- [Privacy policy](https://zerotrust.bz/privacy/) and
  [terms](https://zerotrust.bz/terms/).

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

### Servers

The full edition uses the Zero-Trust servers for the optional account. A fork
must use its own backend (for example its own Supabase project) and must not
connect to the official servers.

### Contributing

Pull requests are welcome. By sending one you agree that your contribution is licensed
under the AGPL-3.0 and that the copyright holder may also license it under other terms,
including commercially. This applies to new contributions only; the HeliBoard code keeps
its own GPL-3.0 licence from its authors.
