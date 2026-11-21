import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:zero_trust_core/zero_trust_core.dart';
import 'package:zerotrust_keyboard/app/auth_providers.dart';
import 'package:zerotrust_keyboard/app/keyboard_config.dart';
import 'package:zerotrust_keyboard/app/keyboard_host.dart';
import 'package:zerotrust_keyboard/l10n/l10n.dart';
import 'package:zerotrust_keyboard/ui/home_page.dart';

/// Stands in for the native side: the phone's keyboard settings.
class FakeHost extends KeyboardHost {
  FakeHost({this.enabled = false, this.selected = false});

  bool enabled;
  bool selected;
  final calls = <String>[];

  @override
  Future<ImeStatus> status() async =>
      ImeStatus(enabled: enabled, selected: selected);

  @override
  Future<void> openImeSettings() async => calls.add('openImeSettings');

  @override
  Future<void> showImePicker() async => calls.add('showImePicker');

  @override
  Future<void> openKeyboardSettings() async =>
      calls.add('openKeyboardSettings');
}

Widget app(FakeHost host, {Locale locale = ZtLanguages.english}) {
  return ProviderScope(
    overrides: [
      ztAppConfigProvider.overrideWithValue(keyboardAppConfig),
      ztPreferencesStoreProvider.overrideWithValue(InMemoryPreferencesStore()),
      keyboardHostProvider.overrideWithValue(host),
      // No account: the keyboard's normal state.
      currentUserProvider.overrideWithValue(null),
      // No Supabase in tests: no auth events either.
      authStateChangesProvider.overrideWith((ref) => const Stream.empty()),
    ],
    child: MaterialApp(
      // The suite's real theme: its full-width buttons break rows that a
      // plain theme lets through.
      theme: ZtTheme.light(accent: keyboardAppConfig.accent),
      locale: locale,
      supportedLocales: ZtLanguages.supported,
      localizationsDelegates: const [
        AppL10n.delegate,
        ...ZtLanguages.delegates,
      ],
      home: const HomePage(),
    ),
  );
}

FilledButton buttonIn(WidgetTester tester, Key step) => tester.widget(
  find.descendant(of: find.byKey(step), matching: find.byType(FilledButton)),
);

void main() {
  const enable = Key('step-enable');
  const select = Key('step-select');

  testWidgets('setup walks through turning on, then choosing, the keyboard', (
    tester,
  ) async {
    final host = FakeHost();
    await tester.pumpWidget(app(host));
    await tester.pumpAndSettle();

    // Step 1 first and usable; step 2 under it, waiting.
    expect(find.text('Set up the keyboard'), findsOneWidget);
    final first = tester.getRect(find.byKey(enable));
    final second = tester.getRect(find.byKey(select));
    expect(second.top, greaterThanOrEqualTo(first.bottom));
    expect(buttonIn(tester, enable).onPressed, isNotNull);
    expect(buttonIn(tester, select).onPressed, isNull);
    // The card sits above the field to try the keyboard in.
    final card = tester.getRect(find.byKey(const Key('setup-card')));
    final field = tester.getRect(find.byKey(const Key('try-field')));
    expect(field.top, greaterThanOrEqualTo(card.bottom));
    // Nothing runs off the screen.
    final screen = tester.getRect(find.byType(Scaffold));
    expect(card.left, greaterThanOrEqualTo(screen.left));
    expect(card.right, lessThanOrEqualTo(screen.right));

    await tester.tap(find.text('Open keyboard list'));
    expect(host.calls, ['openImeSettings']);

    // Turned on in the phone's settings: the page notices by itself.
    host.enabled = true;
    await tester.pump(const Duration(seconds: 1));
    await tester.pumpAndSettle();
    expect(
      find.descendant(
        of: find.byKey(enable),
        matching: find.byType(FilledButton),
      ),
      findsNothing,
    );
    expect(buttonIn(tester, select).onPressed, isNotNull);
    await tester.tap(find.text('Choose keyboard'));
    expect(host.calls, ['openImeSettings', 'showImePicker']);

    // Chosen: setup is replaced by the short "ready" card.
    host.selected = true;
    await tester.pump(const Duration(seconds: 1));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('setup-card')), findsNothing);
    expect(find.text('ZT Keyboard is ready'), findsOneWidget);
    final ready = tester.getRect(find.byKey(const Key('ready-card')));
    expect(
      tester.getRect(find.byKey(const Key('try-field'))).top,
      greaterThanOrEqualTo(ready.bottom),
    );
  });

  testWidgets(
    'keyboard settings open the native screens; account is optional',
    (tester) async {
      final host = FakeHost(enabled: true, selected: true);
      await tester.pumpWidget(app(host));
      await tester.pumpAndSettle();

      // The page's own list (the text field scrolls too).
      final list = find.byType(Scrollable).first;
      final settings = find.byKey(const Key('keyboard-settings'));
      await tester.tap(settings);
      expect(host.calls, ['openKeyboardSettings']);

      // The account comes after the keyboard's own things, marked optional.
      final signIn = find.byKey(const Key('account-sign-in'));
      await tester.scrollUntilVisible(signIn, 200, scrollable: list);
      expect(find.text('Optional'), findsOneWidget);
      expect(find.text('Sign in or create an account'), findsOneWidget);
      final header = tester.getRect(find.text('Zero-Trust account'));
      expect(tester.getRect(signIn).top, greaterThan(header.bottom));

      // Language and theme can be set without an account.
      final language = find.byKey(const Key('app-language'));
      await tester.scrollUntilVisible(language, 200, scrollable: list);
      expect(
        tester.getRect(language).top,
        greaterThan(tester.getRect(signIn).bottom),
      );
      await tester.tap(language);
      await tester.pumpAndSettle();
      await tester.tap(find.text('עברית'));
      await tester.pumpAndSettle();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(HomePage)),
      );
      expect(container.read(ztLanguageProvider), ZtLanguages.hebrew);
    },
  );

  testWidgets('Hebrew: right to left, step number on the right of its text', (
    tester,
  ) async {
    await tester.pumpWidget(app(FakeHost(), locale: ZtLanguages.hebrew));
    await tester.pumpAndSettle();

    expect(find.text('הגדרת המקלדת'), findsOneWidget);
    final number = tester.getRect(find.text('1'));
    final title = tester.getRect(find.text('הפעלת ZT Keyboard'));
    expect(number.left, greaterThanOrEqualTo(title.right));
    expect(buttonIn(tester, const Key('step-enable')).onPressed, isNotNull);
  });
}
