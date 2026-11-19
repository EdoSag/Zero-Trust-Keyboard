import 'package:flutter/material.dart';
import 'package:zero_trust_core/zero_trust_core.dart';

/// The keyboard's accent in the suite (violet, as in the launcher icon). The brand seed (indigo) is
/// shared by every Zero-Trust app.
const Color keyboardAccent = Color(0xFF8B5CF6);

/// Keyboard in the suite. Not in the core's `ZtSuite` list yet, so the other
/// apps don't link to it before it's released.
const ZtSuiteApp keyboardSuiteApp = ZtSuiteApp(
  appId: 'keyboard',
  name: 'Keyboard',
  tagline: 'A private keyboard for Hebrew and English',
  icon: Icons.keyboard_outlined,
  accent: keyboardAccent,
  androidPackage: 'dev.sagron.zerotrustkeyboard',
);

const ZtAppConfig keyboardAppConfig = ZtAppConfig(
  appId: 'keyboard',
  displayName: 'Zero-Trust Keyboard',
  accent: keyboardAccent,
);
