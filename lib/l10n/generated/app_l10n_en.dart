// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_l10n.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppL10nEn extends AppL10n {
  AppL10nEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'ZT Keyboard';

  @override
  String get setupTitle => 'Set up the keyboard';

  @override
  String get readyTitle => 'ZT Keyboard is ready';

  @override
  String get readyBody => 'It opens whenever you tap a text field.';

  @override
  String get stepEnableTitle => 'Turn on ZT Keyboard';

  @override
  String get stepEnableBody =>
      'Find ZT Keyboard in the phone\'s keyboard list and switch it on. Android shows a standard warning for every keyboard; ZT Keyboard keeps what you type on this phone.';

  @override
  String get stepEnableAction => 'Open keyboard list';

  @override
  String get stepSelectTitle => 'Choose ZT Keyboard';

  @override
  String get stepSelectBody => 'Pick ZT Keyboard as the keyboard to type with.';

  @override
  String get stepSelectAction => 'Choose keyboard';

  @override
  String get stepDone => 'Done';

  @override
  String get tryItLabel => 'Try the keyboard here';

  @override
  String get keyboardSettings => 'Keyboard settings';

  @override
  String get keyboardSettingsHint =>
      'Languages, layout, themes, corrections, dictionaries';

  @override
  String get privacyTitle => 'What you type stays on this phone';

  @override
  String get privacyBody =>
      'Suggestions, corrections and learned words are worked out on the phone. The keyboard itself never uses the internet.';

  @override
  String get accountHeader => 'Zero-Trust account';

  @override
  String get accountOptional => 'Optional';

  @override
  String get accountSignedOutBody =>
      'The keyboard works fully without an account. Syncing your personal dictionary between phones, end-to-end encrypted, comes in a later version; you can sign in now to be ready for it.';

  @override
  String get accountSignIn => 'Sign in or create an account';

  @override
  String get accountLocked => 'Locked. Tap to unlock.';

  @override
  String get accountSettings => 'Account, security and app settings';

  @override
  String get appHeader => 'This app';

  @override
  String get accountClose => 'Back to the keyboard';

  @override
  String get syncWordsTitle => 'Sync my personal dictionary';

  @override
  String get syncWordsBody =>
      'The words and shortcuts you added, end-to-end encrypted, on every phone signed in to this account. Syncs while this app is open and unlocked.';

  @override
  String syncWordsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count entries synced',
      one: '1 entry synced',
      zero: 'Nothing to sync yet',
    );
    return '$_temp0';
  }

  @override
  String get syncWordsKeyboardOff =>
      'Turn on ZT Keyboard first. Android only shows the personal dictionary to a keyboard that is on.';

  @override
  String get syncWordsFailed => 'Could not sync just now. It will try again.';

  @override
  String get syncClipsTitle => 'Sync my pinned clips';

  @override
  String get syncClipsBody =>
      'Text you pinned in the keyboard\'s clipboard, end-to-end encrypted, on every phone signed in to this account. Pictures and very long texts stay on this phone. Syncs while this app is open and unlocked.';

  @override
  String syncClipsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count clips synced',
      one: '1 clip synced',
      zero: 'Nothing to sync yet',
    );
    return '$_temp0';
  }

  @override
  String get syncClipsUnavailable =>
      'The clipboard could not be opened just now. It will try again.';

  @override
  String get syncSettingsTitle => 'Sync my keyboard settings';

  @override
  String get syncSettingsBody =>
      'Themes, toolbar, languages and typing options, end-to-end encrypted, on every phone signed in to this account. Sizes, the voice model and custom layout files stay on this phone. Syncs while this app is open and unlocked.';

  @override
  String get syncSettingsDone => 'Settings are in sync';

  @override
  String get syncSettingsUnavailable =>
      'Waiting: another phone on this account runs a newer ZT Keyboard. Update this app to sync settings.';

  @override
  String get syncLearnedTitle => 'Sync the words my keyboard learned';

  @override
  String get syncLearnedBody =>
      'Words the keyboard picked up from your typing and now suggests, end-to-end encrypted, on every phone signed in to this account. Nothing typed in password fields or incognito is ever learned. Syncs while this app is open and unlocked.';

  @override
  String syncLearnedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count words synced',
      one: '1 word synced',
      zero: 'Nothing to sync yet',
    );
    return '$_temp0';
  }

  @override
  String get syncLearnedUnavailable =>
      'Nothing to sync: learning from typing is off in the keyboard\'s settings, or the words could not be read just now.';
}
