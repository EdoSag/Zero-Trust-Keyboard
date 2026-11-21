import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:zero_trust_core/zero_trust_core.dart';

import '../app/auth_providers.dart';
import '../app/keyboard_config.dart';
import '../app/keyboard_host.dart';
import '../sync/item_sync.dart';
import '../l10n/l10n.dart';
import 'account_page.dart';

/// The app's one page: set the keyboard up, open its settings, and the
/// optional Zero-Trust account. Always reachable, with or without an account
/// and whether or not the account is locked.
class HomePage extends ConsumerStatefulWidget {
  const HomePage({super.key});

  @override
  ConsumerState<HomePage> createState() => _HomePageState();
}

class _HomePageState extends ConsumerState<HomePage>
    with WidgetsBindingObserver {
  ImeStatus? _status;
  Timer? _poll;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
    // The phone's keyboard picker is a dialog over this page, so nothing
    // tells the app when it closes. Look again every second until set up.
    _poll = Timer.periodic(const Duration(seconds: 1), (_) {
      if (_status?.ready != true) _refresh();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _poll?.cancel();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    final ImeStatus status;
    try {
      status = await ref.read(keyboardHostProvider).status();
    } on Object {
      return; // Asked again on the next tick or resume.
    }
    if (mounted && status != _status) setState(() => _status = status);
  }

  static const _accountRoute = 'account';
  bool _accountOpen = false;

  Future<void> _openAccount() async {
    if (_accountOpen) return;
    _accountOpen = true;
    await Navigator.of(
      context,
    ).push(
      MaterialPageRoute<void>(
        settings: const RouteSettings(name: _accountRoute),
        builder: (_) => const AccountPage(),
      ),
    );
    _accountOpen = false;
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(vaultControllerProvider, (_, VaultState next) {
      // Don't leave the account's settings open over a locked vault.
      if (next is VaultLocked) {
        Navigator.of(context).popUntil((Route<dynamic> r) => r.isFirst);
      }
    });

    // A password-reset link from an email opened the app: the new password
    // is chosen on the account page.
    ref.listen(passwordRecoveryProvider, (_, bool recovering) {
      if (!recovering) return;
      // Above the account page there may be the Forgot password screen.
      Navigator.of(context).popUntil(
        (Route<dynamic> r) => r.isFirst || (_accountOpen && r.settings.name == _accountRoute),
      );
      _openAccount();
    });

    final l = context.l10n;
    final host = ref.read(keyboardHostProvider);
    final status = _status;
    final user = ref.watch(currentUserProvider);
    final locked = ref.watch(vaultControllerProvider) is VaultLocked;

    return Scaffold(
      appBar: AppBar(
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const ZtBrandMark(app: keyboardSuiteApp, size: 32),
            const SizedBox(width: ZtSpacing.sm),
            Text(l.appTitle),
          ],
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.only(bottom: ZtSpacing.lg),
        children: [
          if (status != null)
            Padding(
              padding: const EdgeInsets.all(ZtSpacing.md),
              child: status.ready
                  ? const _ReadyCard()
                  : _SetupCard(status: status, host: host),
            ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: ZtSpacing.md),
            child: TextField(
              key: const Key('try-field'),
              minLines: 1,
              maxLines: 4,
              decoration: InputDecoration(
                labelText: l.tryItLabel,
                border: const OutlineInputBorder(),
              ),
            ),
          ),
          const SizedBox(height: ZtSpacing.sm),
          ListTile(
            key: const Key('keyboard-settings'),
            leading: const Icon(Icons.tune),
            title: Text(l.keyboardSettings),
            subtitle: Text(l.keyboardSettingsHint),
            trailing: const Icon(Icons.chevron_right),
            onTap: host.openKeyboardSettings,
          ),
          ListTile(
            leading: const Icon(Icons.verified_user_outlined),
            title: Text(l.privacyTitle),
            subtitle: Text(l.privacyBody),
          ),
          _Header(
            l.accountHeader,
            trailing: user == null ? l.accountOptional : null,
          ),
          if (user == null) ...[
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: ZtSpacing.md),
              child: Text(l.accountSignedOutBody),
            ),
            Padding(
              padding: const EdgeInsets.all(ZtSpacing.md),
              child: Align(
                alignment: AlignmentDirectional.centerStart,
                child: OutlinedButton.icon(
                  key: const Key('account-sign-in'),
                  icon: const Icon(Icons.login),
                  label: Text(l.accountSignIn),
                  onPressed: _openAccount,
                ),
              ),
            ),
          ] else
            ListTile(
              key: const Key('account-open'),
              leading: Icon(
                locked ? Icons.lock_outline : Icons.account_circle_outlined,
              ),
              title: Text(user.email ?? l.accountHeader),
              subtitle: Text(locked ? l.accountLocked : l.accountSettings),
              trailing: const Icon(Icons.chevron_right),
              onTap: _openAccount,
            ),
          if (user != null && !locked) const _SyncWordsTile(),
          if (user != null && !locked) const _SyncClipsTile(),
          if (user != null && !locked) const _SyncSettingsTile(),
          _Header(l.appHeader),
          const _LanguageTile(),
          const _ThemeTile(),
          ListTile(
            leading: const Icon(Icons.policy_outlined),
            title: Text(ZtL10n.of(context).privacyPolicy),
            onTap: () => launchUrl(
              Uri.parse('https://zerotrust.bz/privacy'),
              mode: LaunchMode.externalApplication,
            ),
          ),
          ListTile(
            leading: const Icon(Icons.description_outlined),
            title: Text(ZtL10n.of(context).openSourceLicences),
            onTap: () => showLicensePage(
              context: context,
              applicationName: keyboardAppConfig.displayName,
            ),
          ),
        ],
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header(this.title, {this.trailing});

  final String title;
  final String? trailing;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        ZtSpacing.md,
        ZtSpacing.lg,
        ZtSpacing.md,
        ZtSpacing.sm,
      ),
      child: Row(
        children: [
          Expanded(
            child: Semantics(
              header: true,
              child: Text(
                title,
                style: theme.textTheme.titleSmall?.copyWith(
                  color: theme.colorScheme.primary,
                ),
              ),
            ),
          ),
          if (trailing != null)
            Text(trailing!, style: theme.textTheme.labelMedium),
        ],
      ),
    );
  }
}

/// The two things Android needs before a keyboard can be used.
class _SetupCard extends StatelessWidget {
  const _SetupCard({required this.status, required this.host});

  final ImeStatus status;
  final KeyboardHost host;

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return Card(
      key: const Key('setup-card'),
      child: Padding(
        padding: const EdgeInsets.all(ZtSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Semantics(
              header: true,
              child: Text(
                l.setupTitle,
                style: Theme.of(context).textTheme.titleLarge,
              ),
            ),
            const SizedBox(height: ZtSpacing.md),
            _Step(
              key: const Key('step-enable'),
              number: 1,
              title: l.stepEnableTitle,
              body: l.stepEnableBody,
              action: l.stepEnableAction,
              done: status.enabled,
              active: !status.enabled,
              onPressed: host.openImeSettings,
            ),
            const SizedBox(height: ZtSpacing.md),
            _Step(
              key: const Key('step-select'),
              number: 2,
              title: l.stepSelectTitle,
              body: l.stepSelectBody,
              action: l.stepSelectAction,
              done: status.selected,
              active: status.enabled && !status.selected,
              onPressed: host.showImePicker,
            ),
          ],
        ),
      ),
    );
  }
}

class _Step extends StatelessWidget {
  const _Step({
    super.key,
    required this.number,
    required this.title,
    required this.body,
    required this.action,
    required this.done,
    required this.active,
    required this.onPressed,
  });

  final int number;
  final String title;
  final String body;
  final String action;
  final bool done;

  /// The step to do now. A later step waits, with its button off.
  final bool active;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        CircleAvatar(
          radius: 14,
          backgroundColor: done
              ? scheme.primary
              : scheme.surfaceContainerHighest,
          foregroundColor: done ? scheme.onPrimary : scheme.onSurfaceVariant,
          child: done
              ? Icon(
                  Icons.check,
                  size: 18,
                  semanticLabel: context.l10n.stepDone,
                )
              : Text('$number'),
        ),
        const SizedBox(width: ZtSpacing.md),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: theme.textTheme.titleMedium),
              if (!done) ...[
                const SizedBox(height: ZtSpacing.xs),
                Text(body),
                const SizedBox(height: ZtSpacing.sm),
                FilledButton(
                  onPressed: active ? onPressed : null,
                  child: Text(action),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class _ReadyCard extends StatelessWidget {
  const _ReadyCard();

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final scheme = Theme.of(context).colorScheme;
    return Card(
      key: const Key('ready-card'),
      color: scheme.primaryContainer,
      child: ListTile(
        leading: Icon(Icons.check_circle, color: scheme.onPrimaryContainer),
        title: Text(l.readyTitle),
        subtitle: Text(l.readyBody),
        textColor: scheme.onPrimaryContainer,
      ),
    );
  }
}

/// The app's language (English or Hebrew), the same choice as in the shared
/// settings, so it can be set without an account.
class _LanguageTile extends ConsumerWidget {
  const _LanguageTile();

  static const String _phone = 'phone';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final z = ZtL10n.of(context);
    final language = ref.watch(ztLanguageProvider);
    return ListTile(
      key: const Key('app-language'),
      leading: const Icon(Icons.language),
      title: Text(z.language),
      subtitle: Text(
        language == null ? z.languagePhone : ZtLanguages.nativeName(language),
      ),
      onTap: () async {
        final picked = await showDialog<String>(
          context: context,
          builder: (context) => SimpleDialog(
            title: Text(z.language),
            children: [
              RadioGroup<String>(
                groupValue: language?.languageCode ?? _phone,
                onChanged: (code) => Navigator.pop(context, code),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    for (final locale in ZtLanguages.supported)
                      RadioListTile<String>(
                        value: locale.languageCode,
                        title: Text(ZtLanguages.nativeName(locale)),
                      ),
                    RadioListTile<String>(
                      value: _phone,
                      title: Text(z.languagePhone),
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
        if (picked == null) return;
        await ref
            .read(ztLanguageProvider.notifier)
            .set(picked == _phone ? null : Locale(picked));
      },
    );
  }
}

class _ThemeTile extends ConsumerWidget {
  const _ThemeTile();

  static String _label(ZtLocalizations z, ThemeMode mode) => switch (mode) {
    ThemeMode.system => z.systemDefault,
    ThemeMode.light => z.light,
    ThemeMode.dark => z.dark,
  };

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final z = ZtL10n.of(context);
    final mode = ref.watch(themeModeProvider);
    return ListTile(
      key: const Key('app-theme'),
      leading: const Icon(Icons.brightness_6_outlined),
      title: Text(z.appearance),
      subtitle: Text(_label(z, mode)),
      onTap: () async {
        final picked = await showDialog<ThemeMode>(
          context: context,
          builder: (context) => SimpleDialog(
            title: Text(z.appearance),
            children: [
              RadioGroup<ThemeMode>(
                groupValue: mode,
                onChanged: (m) => Navigator.pop(context, m),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    for (final m in ThemeMode.values)
                      RadioListTile<ThemeMode>(
                        value: m,
                        title: Text(_label(z, m)),
                      ),
                  ],
                ),
              ),
            ],
          ),
        );
        if (picked != null) {
          await ref.read(themeModeProvider.notifier).set(picked);
        }
      },
    );
  }
}

/// The switch for syncing the personal dictionary, shown while the account
/// is unlocked. Off until the user turns it on.
class _SyncWordsTile extends ConsumerWidget {
  const _SyncWordsTile();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = context.l10n;
    return _SyncTile(
      key: const Key('sync-words'),
      icon: Icons.sync_lock_outlined,
      title: l.syncWordsTitle,
      body: l.syncWordsBody,
      unavailable: l.syncWordsKeyboardOff,
      count: l.syncWordsCount,
      enabled: ref.watch(wordsSyncEnabledProvider).value ?? false,
      status: ref.watch(wordsSyncProvider),
      onChanged: (on) => ref.read(wordsSyncEnabledProvider.notifier).set(on),
    );
  }
}

/// The switch for syncing the clips pinned in the keyboard's clipboard.
class _SyncClipsTile extends ConsumerWidget {
  const _SyncClipsTile();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = context.l10n;
    return _SyncTile(
      key: const Key('sync-clips'),
      icon: Icons.push_pin_outlined,
      title: l.syncClipsTitle,
      body: l.syncClipsBody,
      unavailable: l.syncClipsUnavailable,
      count: l.syncClipsCount,
      enabled: ref.watch(clipsSyncEnabledProvider).value ?? false,
      status: ref.watch(clipsSyncProvider),
      onChanged: (on) => ref.read(clipsSyncEnabledProvider.notifier).set(on),
    );
  }
}

/// The switch for syncing the keyboard's own settings.
class _SyncSettingsTile extends ConsumerWidget {
  const _SyncSettingsTile();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = context.l10n;
    return _SyncTile(
      key: const Key('sync-settings'),
      icon: Icons.tune,
      title: l.syncSettingsTitle,
      body: l.syncSettingsBody,
      unavailable: l.syncSettingsUnavailable,
      count: (_) => l.syncSettingsDone,
      enabled: ref.watch(settingsSyncEnabledProvider).value ?? false,
      status: ref.watch(settingsSyncProvider),
      onChanged: (on) =>
          ref.read(settingsSyncEnabledProvider.notifier).set(on),
    );
  }
}

class _SyncTile extends StatelessWidget {
  const _SyncTile({
    super.key,
    required this.icon,
    required this.title,
    required this.body,
    required this.unavailable,
    required this.count,
    required this.enabled,
    required this.status,
    required this.onChanged,
  });

  final IconData icon;
  final String title;
  final String body;
  final String unavailable;
  final String Function(int) count;
  final bool enabled;
  final ItemSyncStatus status;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final String note;
    if (!enabled) {
      note = body;
    } else if (status.unavailable) {
      note = unavailable;
    } else if (status.failed) {
      note = context.l10n.syncWordsFailed;
    } else if (status.synced case final int synced) {
      note = count(synced);
    } else {
      note = body;
    }
    return SwitchListTile(
      secondary: Icon(icon),
      title: Text(title),
      subtitle: Text(note),
      value: enabled,
      onChanged: onChanged,
    );
  }
}
