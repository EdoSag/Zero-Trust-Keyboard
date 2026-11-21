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
}
