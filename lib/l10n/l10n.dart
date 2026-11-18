import 'package:flutter/widgets.dart';

import 'generated/app_l10n.dart';

export 'generated/app_l10n.dart';

/// ZT Keyboard's own strings, in English and Hebrew (`app_en.arb`,
/// `app_he.arb`). The language is the one picked in the app (the core's
/// `ztLanguageProvider`). The keyboard's typing languages are separate and
/// set in the keyboard settings.
extension AppL10nContext on BuildContext {
  AppL10n get l10n => AppL10n.of(this);
}
