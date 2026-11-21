import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_l10n_en.dart';
import 'app_l10n_he.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppL10n
/// returned by `AppL10n.of(context)`.
///
/// Applications need to include `AppL10n.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'generated/app_l10n.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppL10n.localizationsDelegates,
///   supportedLocales: AppL10n.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppL10n.supportedLocales
/// property.
abstract class AppL10n {
  AppL10n(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppL10n of(BuildContext context) {
    return Localizations.of<AppL10n>(context, AppL10n)!;
  }

  static const LocalizationsDelegate<AppL10n> delegate = _AppL10nDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('he'),
  ];

  /// No description provided for @appTitle.
  ///
  /// In en, this message translates to:
  /// **'ZT Keyboard'**
  String get appTitle;

  /// No description provided for @setupTitle.
  ///
  /// In en, this message translates to:
  /// **'Set up the keyboard'**
  String get setupTitle;

  /// No description provided for @readyTitle.
  ///
  /// In en, this message translates to:
  /// **'ZT Keyboard is ready'**
  String get readyTitle;

  /// No description provided for @readyBody.
  ///
  /// In en, this message translates to:
  /// **'It opens whenever you tap a text field.'**
  String get readyBody;

  /// No description provided for @stepEnableTitle.
  ///
  /// In en, this message translates to:
  /// **'Turn on ZT Keyboard'**
  String get stepEnableTitle;

  /// No description provided for @stepEnableBody.
  ///
  /// In en, this message translates to:
  /// **'Find ZT Keyboard in the phone\'s keyboard list and switch it on. Android shows a standard warning for every keyboard; ZT Keyboard keeps what you type on this phone.'**
  String get stepEnableBody;

  /// No description provided for @stepEnableAction.
  ///
  /// In en, this message translates to:
  /// **'Open keyboard list'**
  String get stepEnableAction;

  /// No description provided for @stepSelectTitle.
  ///
  /// In en, this message translates to:
  /// **'Choose ZT Keyboard'**
  String get stepSelectTitle;

  /// No description provided for @stepSelectBody.
  ///
  /// In en, this message translates to:
  /// **'Pick ZT Keyboard as the keyboard to type with.'**
  String get stepSelectBody;

  /// No description provided for @stepSelectAction.
  ///
  /// In en, this message translates to:
  /// **'Choose keyboard'**
  String get stepSelectAction;

  /// No description provided for @stepDone.
  ///
  /// In en, this message translates to:
  /// **'Done'**
  String get stepDone;

  /// No description provided for @tryItLabel.
  ///
  /// In en, this message translates to:
  /// **'Try the keyboard here'**
  String get tryItLabel;

  /// No description provided for @keyboardSettings.
  ///
  /// In en, this message translates to:
  /// **'Keyboard settings'**
  String get keyboardSettings;

  /// No description provided for @keyboardSettingsHint.
  ///
  /// In en, this message translates to:
  /// **'Languages, layout, themes, corrections, dictionaries'**
  String get keyboardSettingsHint;

  /// No description provided for @privacyTitle.
  ///
  /// In en, this message translates to:
  /// **'What you type stays on this phone'**
  String get privacyTitle;

  /// No description provided for @privacyBody.
  ///
  /// In en, this message translates to:
  /// **'Suggestions, corrections and learned words are worked out on the phone. The keyboard itself never uses the internet.'**
  String get privacyBody;

  /// No description provided for @accountHeader.
  ///
  /// In en, this message translates to:
  /// **'Zero-Trust account'**
  String get accountHeader;

  /// No description provided for @accountOptional.
  ///
  /// In en, this message translates to:
  /// **'Optional'**
  String get accountOptional;

  /// No description provided for @accountSignedOutBody.
  ///
  /// In en, this message translates to:
  /// **'The keyboard works fully without an account. Syncing your personal dictionary between phones, end-to-end encrypted, comes in a later version; you can sign in now to be ready for it.'**
  String get accountSignedOutBody;

  /// No description provided for @accountSignIn.
  ///
  /// In en, this message translates to:
  /// **'Sign in or create an account'**
  String get accountSignIn;

  /// No description provided for @accountLocked.
  ///
  /// In en, this message translates to:
  /// **'Locked. Tap to unlock.'**
  String get accountLocked;

  /// No description provided for @accountSettings.
  ///
  /// In en, this message translates to:
  /// **'Account, security and app settings'**
  String get accountSettings;

  /// No description provided for @appHeader.
  ///
  /// In en, this message translates to:
  /// **'This app'**
  String get appHeader;

  /// No description provided for @accountClose.
  ///
  /// In en, this message translates to:
  /// **'Back to the keyboard'**
  String get accountClose;

  /// No description provided for @syncWordsTitle.
  ///
  /// In en, this message translates to:
  /// **'Sync my personal dictionary'**
  String get syncWordsTitle;

  /// No description provided for @syncWordsBody.
  ///
  /// In en, this message translates to:
  /// **'The words and shortcuts you added, end-to-end encrypted, on every phone signed in to this account. Syncs while this app is open and unlocked.'**
  String get syncWordsBody;

  /// No description provided for @syncWordsCount.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{Nothing to sync yet} =1{1 entry synced} other{{count} entries synced}}'**
  String syncWordsCount(int count);

  /// No description provided for @syncWordsKeyboardOff.
  ///
  /// In en, this message translates to:
  /// **'Turn on ZT Keyboard first. Android only shows the personal dictionary to a keyboard that is on.'**
  String get syncWordsKeyboardOff;

  /// No description provided for @syncWordsFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not sync just now. It will try again.'**
  String get syncWordsFailed;

  /// No description provided for @syncClipsTitle.
  ///
  /// In en, this message translates to:
  /// **'Sync my pinned clips'**
  String get syncClipsTitle;

  /// No description provided for @syncClipsBody.
  ///
  /// In en, this message translates to:
  /// **'Text you pinned in the keyboard\'s clipboard, end-to-end encrypted, on every phone signed in to this account. Pictures and very long texts stay on this phone. Syncs while this app is open and unlocked.'**
  String get syncClipsBody;

  /// No description provided for @syncClipsCount.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{Nothing to sync yet} =1{1 clip synced} other{{count} clips synced}}'**
  String syncClipsCount(int count);

  /// No description provided for @syncClipsUnavailable.
  ///
  /// In en, this message translates to:
  /// **'The clipboard could not be opened just now. It will try again.'**
  String get syncClipsUnavailable;
}

class _AppL10nDelegate extends LocalizationsDelegate<AppL10n> {
  const _AppL10nDelegate();

  @override
  Future<AppL10n> load(Locale locale) {
    return SynchronousFuture<AppL10n>(lookupAppL10n(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'he'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppL10nDelegate old) => false;
}

AppL10n lookupAppL10n(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppL10nEn();
    case 'he':
      return AppL10nHe();
  }

  throw FlutterError(
    'AppL10n.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
