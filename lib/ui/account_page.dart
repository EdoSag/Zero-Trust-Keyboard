import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:zero_trust_core/zero_trust_core.dart';

import '../app/auth_providers.dart';
import '../l10n/l10n.dart';

/// The optional Zero-Trust account, opened from the home page.
///
/// Signed out: sign in or create an account. Signed in but locked (cold
/// start, auto-lock): unlock with the account password or biometrics. A new
/// recovery key is always shown, and confirmed, before the vault opens.
/// Unlocked: the shared Zero-Trust settings.
///
/// The keyboard never waits on any of this: it types without an account and
/// while the app is locked.
class AccountPage extends ConsumerStatefulWidget {
  const AccountPage({super.key});

  @override
  ConsumerState<AccountPage> createState() => _AccountPageState();
}

class _AccountPageState extends ConsumerState<AccountPage>
    with WidgetsBindingObserver {
  /// Stays on the sign-in screen until it hands back a result, even though
  /// Supabase reports the session a moment earlier.
  late bool _signingIn = ref.read(currentUserProvider) == null;
  UnlockResult? _awaitingRecoveryKey;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  /// A key still waiting behind the recovery-key screen is zeroed when the
  /// app leaves the foreground, so coming back asks for the password.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.detached) {
      final UnlockResult? pending = _awaitingRecoveryKey;
      if (pending != null) {
        pending.dek.fillRange(0, pending.dek.length, 0);
        setState(() => _awaitingRecoveryKey = null);
      }
    }
  }

  void _onUnlocked(UnlockResult result) {
    if (result.newRecoveryKey != null) {
      setState(() {
        _signingIn = false;
        _awaitingRecoveryKey = result;
      });
      return;
    }
    setState(() => _signingIn = false);
    ref
        .read(vaultControllerProvider.notifier)
        .unlock(result.dek, legacyDek: result.legacyDek);
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(currentUserProvider, (_, user) {
      if (user == null) {
        ref.read(vaultControllerProvider.notifier).lock();
        setState(() => _signingIn = true);
      }
    });

    final UnlockResult? pending = _awaitingRecoveryKey;
    if (pending != null) {
      return ZtRecoveryKeyScreen(
        recoveryKey: pending.newRecoveryKey!,
        onDone: () {
          setState(() => _awaitingRecoveryKey = null);
          ref
              .read(vaultControllerProvider.notifier)
              .unlock(pending.dek, legacyDek: pending.legacyDek);
        },
      );
    }

    final user = ref.watch(currentUserProvider);
    if (user == null || _signingIn) {
      return _WithClose(child: ZtSignInScreen(onUnlocked: _onUnlocked));
    }

    if (ref.watch(vaultControllerProvider) is VaultLocked) {
      return _WithClose(
        child: ZtUnlockScreen(
          email: user.email,
          onUnlocked: _onUnlocked,
          onSignOut: () => ref.read(accountServiceProvider).signOut(),
        ),
      );
    }

    return ZtSettingsPage(
      onSignedOut: () => Navigator.of(context).popUntil((r) => r.isFirst),
      onOpenUrl: (url) => launchUrl(url, mode: LaunchMode.externalApplication),
      privacyPolicyUrl: Uri.parse('https://zerotrust.bz/privacy'),
      termsUrl: Uri.parse('https://zerotrust.bz/terms'),
      supportUrl: Uri.parse('https://zerotrust.bz/support'),
    );
  }
}

/// The shared sign-in and unlock screens fill the page and have no way back
/// of their own (in the other apps there is nowhere to go back to). Here the
/// account is optional, so a close button sits on top of them.
class _WithClose extends StatelessWidget {
  const _WithClose({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        child,
        SafeArea(
          child: Align(
            alignment: AlignmentDirectional.topStart,
            child: Padding(
              padding: const EdgeInsets.all(4),
              child: IconButton(
                tooltip: context.l10n.accountClose,
                icon: const Icon(Icons.close),
                onPressed: () => Navigator.of(context).maybePop(),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
