import 'package:flutter/foundation.dart';

/// Credits for the bundled Hebrew word list, emoji words and engines, shown on the Open source licences
/// page. The lists and how they are built are in /dictionaries of the source code.
void registerWordListCredits() {
  LicenseRegistry.addLicense(() async* {
    yield const LicenseEntryWithLineBreaks(
      ['ZT Hebrew word list'],
      'The Hebrew dictionary of ZT Keyboard is built from four sources.\n'
      '\n'
      'Android Open Source Project, LatinIME Hebrew word list. '
      'Copyright The Android Open Source Project. Apache License 2.0.\n'
      '\n'
      'Hspell, the free Hebrew speller, by Nadav Har\'El and Dan Kenigsberg, '
      'as packaged for LibreOffice. GNU Affero General Public License v3. '
      'http://hspell.ivrix.org.il/\n'
      '\n'
      'FrequencyWords by Hermit Dave: word counts from the OpenSubtitles 2018 '
      'collection (opensubtitles.org, OPUS). Creative Commons '
      'Attribution-ShareAlike 4.0. Changes: only the counts of words that '
      'Hspell accepts were used, turned into a 1 to 150 scale. '
      'https://github.com/hermitdave/FrequencyWords\n'
      '\n'
      'Hebrew Wikipedia, by its contributors, from the Wikimedia "wikipedia" '
      'dataset (20231101.he). Creative Commons Attribution-ShareAlike 4.0. '
      'Changes: only how often one word follows another was counted, for the '
      'next-word suggestions; no text is included. https://he.wikipedia.org/\n'
      '\n'
      'The combined list is shared under Creative Commons '
      'Attribution-ShareAlike 4.0 and the GNU Affero General Public License v3.',
    );
    yield const LicenseEntryWithLineBreaks(
      ['Emoji names and search words (Unicode CLDR)'],
      'Emoji search and emoji names in Hebrew and English are built from the '
      'Unicode Common Locale Data Repository (CLDR) annotations.\n'
      '\n'
      'Copyright © 2004-2025 Unicode, Inc. Unicode and the Unicode Logo are '
      'registered trademarks of Unicode, Inc. in the United States and other '
      'countries. Used under the Unicode License v3. '
      'https://www.unicode.org/license.txt',
    );
    yield const LicenseEntryWithLineBreaks(
      ['Translation engine and language models (Mozilla)'],
      'Translating on the phone uses Bergamot Translator '
      '(@browsermt/bergamot-translator 0.4.9), the engine of Firefox '
      'Translations, unchanged. Mozilla Public License 2.0. '
      'https://github.com/browsermt/bergamot-translator\n'
      '\n'
      'The languages you download are Mozilla\'s Firefox Translations models. '
      'Mozilla Public License 2.0. https://github.com/mozilla/translations\n'
      '\n'
      'The source code of both is at the addresses above. '
      'https://mozilla.org/MPL/2.0/',
    );
    yield const LicenseEntryWithLineBreaks(
      ['whisper.cpp and llama.cpp (voice typing and writing help)'],
      'Voice typing uses whisper.cpp and writing help uses llama.cpp, both '
      'with the ggml library. https://github.com/ggml-org/whisper.cpp '
      'https://github.com/ggml-org/llama.cpp\n'
      '\n'
      'MIT License. Copyright (c) 2023-2026 The ggml authors.\n'
      '\n'
      'Permission is hereby granted, free of charge, to any person obtaining a '
      'copy of this software and associated documentation files (the '
      '"Software"), to deal in the Software without restriction, including '
      'without limitation the rights to use, copy, modify, merge, publish, '
      'distribute, sublicense, and/or sell copies of the Software, and to '
      'permit persons to whom the Software is furnished to do so, subject to '
      'the following conditions:\n'
      '\n'
      'The above copyright notice and this permission notice shall be included '
      'in all copies or substantial portions of the Software.\n'
      '\n'
      'THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS '
      'OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF '
      'MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. '
      'IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY '
      'CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, '
      'TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE '
      'SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.\n'
      '\n'
      'The language model you can download for writing help is Qwen2.5 0.5B '
      'Instruct by the Qwen team, Alibaba Cloud, in GGUF format. Apache '
      'License 2.0. https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF',
    );
  });
}
