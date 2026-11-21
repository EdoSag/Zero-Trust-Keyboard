import 'package:flutter/foundation.dart';

/// Credits for the bundled Hebrew word list, shown on the Open source licences
/// page. The list and how it is built are in /dictionaries of the source code.
void registerWordListCredits() {
  LicenseRegistry.addLicense(() async* {
    yield const LicenseEntryWithLineBreaks(
      ['ZT Hebrew word list'],
      'The Hebrew dictionary of ZT Keyboard is built from three sources.\n'
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
      'The combined list is shared under Creative Commons '
      'Attribution-ShareAlike 4.0 and the GNU Affero General Public License v3.',
    );
  });
}
