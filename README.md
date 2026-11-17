# Zero-Trust Keyboard

A private Android keyboard for the Zero-Trust suite. Store name **Zero-Trust Keyboard**,
launcher name **ZT Keyboard**.

Everything you type stays on your phone. Word prediction, autocorrect and learning run on the
device, and the keyboard has no internet permission. Hebrew and English are first-class,
including typing both in one sentence.

Plan and roadmap (phases K0 to K6): swipe typing with our own open decoder, on-device voice
typing, on-device translation, and optional end-to-end encrypted sync of your personal
dictionary through your Zero-Trust account.

## Based on HeliBoard

ZT Keyboard is a fork of [HeliBoard](https://github.com/HeliBorg/HeliBoard) 4.2
(upstream commit `0c60dffb03a4e2e7a37cb82210f5299f93c03979`), which is based on OpenBoard and
Google's AOSP keyboard. Many thanks to the HeliBoard, OpenBoard and AOSP contributors.

The first commit after the repository start is an unchanged copy of HeliBoard 4.2, so every
ZT change is visible as a diff against upstream. To take a newer HeliBoard release, apply the
diff between the two upstream tags, for example:

```
git clone https://github.com/HeliBorg/HeliBoard /tmp/heliboard
git -C /tmp/heliboard diff v4.2 v4.3 | git apply -3
```

Java/Kotlin package names (`helium314.keyboard...`) are kept on purpose so upstream diffs keep
applying; the app id is `dev.sagron.zerotrustkeyboard`.

Release builds use the `nouserlib` build type: users cannot load a closed-source gesture
library into the keyboard. Swipe typing will come from our own open decoder (phase K2).

## Building

Branches: work happens on `Dev`; `main` holds releases. Every push to `main` builds a signed
APK and a Google Play bundle and publishes them as a GitHub Release
(`.github/workflows/android-apk.yml`). Pull requests get a test build.

Local build: `./gradlew assembleNouserlib` (needs the Android SDK and NDK).

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
