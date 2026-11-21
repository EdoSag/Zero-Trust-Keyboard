import 'dart:async';
import 'dart:convert';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import 'package:zero_trust_core/zero_trust_core.dart';

import '../app/auth_providers.dart';
import '../app/keyboard_host.dart';
import 'personal_word.dart';
import 'reconcile_words.dart';

/// Whether the signed-in account syncs the personal dictionary. Off until
/// the user turns it on; remembered per account on this phone.
class WordsSyncEnabled extends AsyncNotifier<bool> {
  String? _key;

  @override
  Future<bool> build() async {
    final userId = ref.watch(currentUserProvider)?.id;
    if (userId == null) {
      _key = null;
      return false;
    }
    final key = _key = 'kb_sync_words_$userId';
    return await ref.watch(ztPreferencesStoreProvider).read(key) == '1';
  }

  Future<void> set(bool on) async {
    final key = _key;
    if (key == null) return;
    await ref.read(ztPreferencesStoreProvider).write(key, on ? '1' : '0');
    state = AsyncData(on);
  }
}

final wordsSyncEnabledProvider =
    AsyncNotifierProvider<WordsSyncEnabled, bool>(WordsSyncEnabled.new);

/// How the personal dictionary's sync stands.
class WordsSyncStatus {
  const WordsSyncStatus({
    this.running = false,
    this.synced,
    this.keyboardOff = false,
    this.failed = false,
  });

  /// On, and the account is unlocked.
  final bool running;

  /// Entries the phone and the account both hold, once known.
  final int? synced;

  /// Android only shows the personal dictionary to a keyboard that is
  /// turned on, so there is nothing to sync until ZT Keyboard is.
  final bool keyboardOff;

  /// The last try did not finish; the next one starts over.
  final bool failed;
}

/// Keeps the personal dictionary on this phone and the one in the account
/// the same, while the switch is on and the account is unlocked in this
/// app. The keyboard itself never holds the account's key, so nothing syncs
/// while only the keyboard is in use.
///
/// Runs after every finished sync with the account: then everything other
/// phones changed is here, and comparing cannot mistake "not downloaded
/// yet" for "deleted".
class WordsSync extends Notifier<WordsSyncStatus> {
  static const _uuid = Uuid();

  Future<void> _turn = Future.value();
  bool _active = false;

  @override
  WordsSyncStatus build() {
    final enabled = ref.watch(wordsSyncEnabledProvider).value ?? false;
    final unlocked = ref.watch(
      vaultControllerProvider.select((s) => s is VaultUnlocked),
    );
    final userId = ref.watch(currentUserProvider)?.id;
    _active = false;
    if (!enabled || !unlocked || userId == null) {
      return const WordsSyncStatus();
    }
    _active = true;
    ref.onDispose(() => _active = false);
    // Starts the account's sync and tells when a round of it has finished.
    ref.listen(ztSyncControllerProvider, (previous, next) {
      final finished = next.lastSyncedAt;
      if (finished == null || next.isSyncing || next.error != null) return;
      if (finished == previous?.lastSyncedAt && previous?.isSyncing != true) {
        return;
      }
      _queue(userId);
    });
    // Turned on after the account already synced: ask for another round, so
    // the first comparison does not wait for the next change.
    scheduleMicrotask(() {
      if (_active) ref.read(ztSyncControllerProvider.notifier).syncNow();
    });
    // Words added in the keyboard while the app was in the background.
    final lifecycle = AppLifecycleListener(
      onResume: () => ref.read(ztSyncControllerProvider.notifier).syncNow(),
    );
    ref.onDispose(lifecycle.dispose);
    return const WordsSyncStatus(running: true);
  }

  void _queue(String userId) {
    _turn = _turn.then((_) async {
      if (!_active) return;
      try {
        await _run(userId);
      } on Object catch (e) {
        debugPrint('Personal dictionary sync failed: $e');
        if (_active) state = const WordsSyncStatus(running: true, failed: true);
      }
    });
  }

  Future<void> _run(String userId) async {
    final host = ref.read(keyboardHostProvider);
    final prefs = ref.read(ztPreferencesStoreProvider);
    final phone = await host.personalWords();
    if (!_active) return;
    if (phone == null) {
      state = const WordsSyncStatus(running: true, keyboardOff: true);
      return;
    }
    final store = await ref.read(ztLocalStoreProvider.future);
    final account = await syncedWordsCollection.list(store);
    final knownKey = 'kb_sync_words_known_$userId';
    final changes = reconcileWords(
      phone: phone,
      account: account,
      known: _decodeKnown(await prefs.read(knownKey)),
    );
    if (!_active) return;

    if (changes.addToPhone.isNotEmpty || changes.removeFromPhone.isNotEmpty) {
      final applied = await host.applyPersonalWords(
        add: changes.addToPhone,
        remove: changes.removeFromPhone,
      );
      if (!applied) {
        if (_active) {
          state = const WordsSyncStatus(running: true, keyboardOff: true);
        }
        return;
      }
    }
    if (!_active) return;
    final sync = ref.read(ztSyncControllerProvider.notifier);
    for (final word in changes.addToAccount) {
      await syncedWordsCollection.save(
        sync,
        store,
        SyncedWord(id: _uuid.v4(), word: word),
      );
    }
    for (final id in changes.removeFromAccount) {
      await syncedWordsCollection.delete(sync, id);
    }
    await prefs.write(knownKey, jsonEncode(changes.known.toList()));
    if (_active) {
      state = WordsSyncStatus(running: true, synced: changes.known.length);
    }
  }

  static Set<String> _decodeKnown(String? saved) {
    if (saved == null || saved.isEmpty) return {};
    try {
      return {for (final key in jsonDecode(saved) as List) key as String};
    } on Object {
      // Unreadable: start as on a first sync, which joins and deletes nothing.
      return {};
    }
  }
}

final wordsSyncProvider = NotifierProvider<WordsSync, WordsSyncStatus>(
  WordsSync.new,
);
