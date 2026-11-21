# Word lists the bundled dictionaries are built from

| File | Becomes | Source | Licence |
|---|---|---|---|
| `iw_wordlist.combined.gz` | `android/app/src/main/assets/dicts/main_he.dict` (Hebrew) | Android Open Source Project, LatinIME, `dictionaries/iw_wordlist.combined.gz` | Apache-2.0 |

The Hebrew list holds 94,799 words with how common each one is, and no word pairs.
sha256 of the list: `0fbbaa614e8512fdf2dfe7ce091c0fd011074eae654041a2ccd78459ca24c0fc`.

## How a dictionary file is built

With the builder that is part of the keyboard's own code (`helium314.keyboard.latin.makedict`,
from the Android Open Source Project), not with an outside tool:

```kotlin
DictIO.writeBinaryDictionary("main_he.dict", DictIO.readCompressedCombinedDictionary("iw_wordlist.combined.gz"))
```

`BundledHebrewDictionaryTest` does exactly this on every build and fails when the result is
not, byte for byte, the file in the app. So the dictionary in the app always comes from the
list here, and a change to either one without the other is caught.

The other bundled dictionaries (`main_en-US.dict` and so on) come ready-made from HeliBoard.
