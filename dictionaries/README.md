# Word lists the bundled dictionaries are built from

| File | What it is | Licence |
|---|---|---|
| `he_zt_wordlist.combined.gz` | The "ZT Hebrew" list. Becomes `android/app/src/main/assets/dicts/main_he.dict` | CC BY-SA 4.0 and AGPL-3.0, see below |
| `iw_wordlist.combined.gz` | The Android Hebrew list, one of the inputs. Android Open Source Project, LatinIME, `dictionaries/iw_wordlist.combined.gz` | Apache-2.0 |
| `build_zt_hebrew.py` | Builds the first file from the inputs | AGPL-3.0 |
| `emoji_he_wordlist.combined.gz`, `emoji_en_wordlist.combined.gz` | Emoji search words and emoji names. Become `android/app/src/main/assets/dicts/emoji_he.dict` and `emoji_en.dict` | Unicode License v3, see below |
| `build_emoji_wordlist.py` | Builds the emoji lists from Unicode CLDR | AGPL-3.0 |

## The ZT Hebrew list

181,813 words with how common each one is, and no word pairs yet.

Hebrew glues "and", "the", "that", "when", "in", "to" onto the front of a word (ואני,
כשהגעתי, והילדים). The Android list holds 94,799 words and few of those forms, so a
keyboard built on it alone keeps "correcting" ordinary words. The ZT list is:

1. every word of the Android list, with the frequency Android gave it, and
2. 87,014 more words: those seen at least 10 times in film and TV subtitles that the
   Hspell speller accepts, either as a word or as a word with a prefix Hspell allows for
   it. Their frequency comes from the subtitle count, on Android's scale, capped at 150.

Names and slang that Hspell does not know are not added; the keyboard learns those from
your own typing. On the subtitle counts themselves, the Android list misses about 66
words in every 1,000 typed and the ZT list about 28. The words were chosen from those
same counts, so on other text the gain will be smaller.

### Inputs

| Input | Source | Licence | sha256 |
|---|---|---|---|
| `iw_wordlist.combined.gz` | in this folder | Apache-2.0 | `0fbbaa614e8512fdf2dfe7ce091c0fd011074eae654041a2ccd78459ca24c0fc` |
| `he_IL.dic`, `he_IL.aff` | [Hspell](http://hspell.ivrix.org.il/) 1.4 by Nadav Har'El and Dan Kenigsberg, as packaged in [LibreOffice dictionaries](https://github.com/LibreOffice/dictionaries/tree/master/he_IL) | AGPL-3.0 | `5f5331f90ed775bd527f6fb7ad1ead9a1b7d8ce46ad640c2387d9d1dc91d3058`, `6caf86b3a545be5614f135d33a48baa244a59aca43f051dda6173d5d9cbc7700` |
| `he_full.txt` | [FrequencyWords](https://github.com/hermitdave/FrequencyWords) by Hermit Dave, `content/2018/he/he_full.txt`: counts from the OpenSubtitles 2018 collection ([opensubtitles.org](http://www.opensubtitles.org/), via OPUS) | CC BY-SA 4.0 | `a69a3f390eb53183bf191c4eac18282592992aef9ff184c5dcf8919f5ea0f773` |

The Hspell and FrequencyWords files are not copied into this repository; fetch them from
the links above and check the sha256.

### Building it

```sh
python3 build_zt_hebrew.py iw_wordlist.combined.gz he_IL.dic he_IL.aff he_full.txt he_zt_wordlist.combined.gz
```

The same inputs always give the same file, sha256
`d3a9a094c0f670dcb4cda1b9bbbe09e2f7fa9799d583ba9126dfdcb0a243da4f`. The script only reads
the inputs; it runs nothing from them.

### Licence of the list

The list is built from CC BY-SA 4.0 counts and checked against AGPL-3.0 Hspell data, so
it is shared under both: Creative Commons Attribution-ShareAlike 4.0 and the GNU Affero
General Public License v3. The Android words in it remain available under Apache-2.0 in
`iw_wordlist.combined.gz`. The app shows these credits under Open source licences.

## The emoji lists

Emoji search, emoji suggestions while typing and the emoji name shown on a long press all
read an "emoji dictionary". HeliBoard leaves it to the user to download one; ZT Keyboard
comes with Hebrew and English. A dictionary the user adds for a language still wins.

Each list holds 1,914 emoji and about 3,900 search words, from the
[Unicode CLDR](https://github.com/unicode-org/cldr) annotations, release 48
(`common/annotations/<language>.xml` and `common/annotationsDerived/<language>.xml`).
Copyright © 2004-2025 Unicode, Inc., [Unicode License v3](https://www.unicode.org/license.txt).
Only emoji the keyboard can show (`android/app/src/main/assets/emoji`) are in, each in its
neutral form.

- A search word is a word of an emoji's name or of one of its keywords. It carries up to
  18 emoji, best first: a word of the name beats a keyword, a handful of everyday
  favourites win a tie, then a short name, then an early keyword.
- An emoji carries its name.

| Input | sha256 |
|---|---|
| `annotations/he.xml` | `aeaa82174a473a00ba1bd314e77f8f88d56281f04a7013a9a282c2c43e91a7e1` |
| `annotationsDerived/he.xml` | `110013198799f8bdb5d7140bb04e07d9c382f30b9e9c9c09fd7342902a1c2318` |
| `annotations/en.xml` | `8511aadd046fdba2f0ffe590266ced8bbf48175ad139b2675d85d7141057b235` |
| `annotationsDerived/en.xml` | `d76bd041c8c9e7b00b716aff8b7d9dbf509877010e191d5efd068be2553e066e` |

```sh
python3 build_emoji_wordlist.py he annotations/he.xml annotationsDerived/he.xml ../android/app/src/main/assets/emoji emoji_he_wordlist.combined.gz
python3 build_emoji_wordlist.py en annotations/en.xml annotationsDerived/en.xml ../android/app/src/main/assets/emoji emoji_en_wordlist.combined.gz
```

The same inputs always give the same files, sha256
`fbb0e2d5cbf4e5d4113323f69b9ae87c3c5b1c691ff79038fb06b5df0439c7aa` (Hebrew) and
`1109bfab2a2b576ddb9e3410fc107c9758351837e8abc99316e7d82993ded854` (English).
`BundledEmojiDictionaryTest` builds both dictionaries again on every build and compares.

## How a dictionary file is built

With the builder that is part of the keyboard's own code (`helium314.keyboard.latin.makedict`,
from the Android Open Source Project), not with an outside tool:

```kotlin
DictIO.writeBinaryDictionary("main_he.dict", DictIO.readCompressedCombinedDictionary("he_zt_wordlist.combined.gz"))
```

`BundledHebrewDictionaryTest` does exactly this on every build and fails when the result is
not, byte for byte, the file in the app. So the dictionary in the app always comes from the
list here, and a change to either one without the other is caught. The same test checks
that no Android word was lost or re-weighted.

The other bundled dictionaries (`main_en-US.dict` and so on) come ready-made from HeliBoard.
