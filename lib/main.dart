import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:zero_trust_core/zero_trust_core.dart';

import 'app/keyboard_config.dart';
import 'l10n/l10n.dart';
import 'ui/home_page.dart';

/// The ZT app around the keyboard (full edition). The keyboard itself is a
/// native Android service and runs without this app being open, without an
/// account and without the network.
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await dotenv.load(fileName: '.env');
  await Supabase.initialize(
    url: dotenv.env['SUPABASE_URL']!,
    publishableKey: dotenv.env['SUPABASE_ANON_KEY']!,
  );
  // English or Hebrew, as picked in the app, from the first frame.
  await ZtLanguageController.preload();
  runApp(
    ProviderScope(
      overrides: [ztAppConfigProvider.overrideWithValue(keyboardAppConfig)],
      child: const ZeroTrustKeyboardApp(),
    ),
  );
}

class ZeroTrustKeyboardApp extends ConsumerWidget {
  const ZeroTrustKeyboardApp({super.key, this.home = const HomePage()});

  final Widget home;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ZtThemeBuilder(
      builder: (context, theme, darkTheme, mode) => MaterialApp(
        // On-device task switcher label; the store name is Zero-Trust Keyboard.
        title: 'ZT Keyboard',
        theme: theme,
        darkTheme: darkTheme,
        themeMode: mode,
        locale: ref.watch(ztLanguageProvider),
        supportedLocales: ZtLanguages.supported,
        localizationsDelegates: const [
          AppL10n.delegate,
          ...ZtLanguages.delegates,
        ],
        home: home,
        // Any touch on any screen counts as activity for auto-lock.
        builder: (BuildContext context, Widget? child) => Listener(
          onPointerDown: (_) =>
              ref.read(vaultControllerProvider.notifier).touch(),
          child: child,
        ),
      ),
    );
  }
}
