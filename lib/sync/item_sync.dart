import 'dart:async';
import 'dart:convert';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import 'package:zero_trust_core/zero_trust_core.dart';

import '../app/auth_providers.dart';
import '../app/keyboard_host.dart';
import 'personal_word.dart';
import 'keyboard_settings.dart';
import 'learned_words.dart';
import 'pinned_clip.dart';
import 'reconcile.dart';

/// Whether the signed-in account syncs one kind of keyboard data. Off until
/// the user turns it on; remembered per account on this phone.
class SyncEnabled extends AsyncNotifier<bool> {
  SyncEnabled(this.name);

  /// Which data: part of the preference's name.
  final String name;

  String? _key;

  @override
  Future<bool> build() async {
    final userId = ref.watch(currentUserProvider)?.id;
    if (userId == null) {
      _key = null;
      return false;
    }
    final key = _key = 'kb_sync_${name}_$userId';
    return await ref.watch(ztPreferencesStoreProvider).read(key) == '1';
  }

  Future<void> set(bool on) async {
    final key = _key;
    if (key == null) return;
    await ref.read(ztPreferencesStoreProvider).write(key, on ? '1' : '0');
    state = AsyncData(on);
  }
}

/// How one kind of data's sync stands.
class ItemSyncStatus {
  const ItemSyncStatus({
    this.running = false,
    this.synced,
    this.unavailable = false,
    this.failed = false,
  });

  /// On, and the account is unlocked.
  final bool running;

  /// Entries the phone and the account both hold, once known.
  final int? synced;

  /// The phone's side could not be read, so nothing was compared. For the
  /// personal dictionary: Android only shows it to a keyboard that is
  /// turned on.
  final bool unavailable;

  /// The last try did not finish; the next one starts over.
  final bool failed;
}

/// Keeps one kind of keyboard data on this phone and in the account the
/// same, while its switch is on and the account is unlocked in this app.
/// The keyboard itself never holds the account's key, so nothing syncs
/// while only the keyboard is in use.
///
/// Runs after every finished sync with the account: then everything other
/// phones changed is here, and comparing cannot mistake "not downloaded
/// yet" for "deleted".
abstract class RoundSync extends Notifier<ItemSyncStatus> {
  /// Which data: part of the preferences' names.
  String get name;

  AsyncNotifierProvider<SyncEnabled, bool> get enabledProvider;

  /// Compares and changes both sides once. Null when the sync was switched
  /// off or the account locked meanwhile.
  Future<ItemSyncStatus?> round(String userId);

  Future<void> _turn = Future.value();
  bool _active = false;

  /// False once the sync was switched off or the account locked.
  bool get active => _active;

  @override
  ItemSyncStatus build() {
    final enabled = ref.watch(enabledProvider).value ?? false;
    final unlocked = ref.watch(
      vaultControllerProvider.select((s) => s is VaultUnlocked),
    );
    final userId = ref.watch(currentUserProvider)?.id;
    _active = false;
    if (!enabled || !unlocked || userId == null) {
      return const ItemSyncStatus();
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
    // Changes made in the keyboard while the app was in the background.
    final lifecycle = AppLifecycleListener(
      onResume: () => ref.read(ztSyncControllerProvider.notifier).syncNow(),
    );
    ref.onDispose(lifecycle.dispose);
    return const ItemSyncStatus(running: true);
  }

  void _queue(String userId) {
    _turn = _turn.then((_) async {
      if (!_active) return;
      try {
        final status = await round(userId);
        if (_active && status != null) state = status;
      } on Object catch (e) {
        debugPrint('Sync of $name failed: $e');
        if (_active) state = const ItemSyncStatus(running: true, failed: true);
      }
    });
  }
}

/// [RoundSync] for a list of entries that are added and removed: the
/// personal dictionary, the pinned clips.
abstract class ItemSync<T> extends RoundSync {
  static const _uuid = Uuid();

  ZtCollection<Synced<T>> get collection;

  /// What makes two entries the same entry.
  String keyOf(T value);

  /// False for an account item that should not exist.
  bool valid(T value) => true;

  /// The phone's entries, or null when they cannot be read now. Null must
  /// never be an empty list: that would delete everything in the account.
  Future<List<T>?> readPhone(KeyboardHost host);

  /// Changes the phone's entries. False when nothing could be changed.
  Future<bool> applyToPhone(
    KeyboardHost host, {
    required List<T> add,
    required List<T> remove,
  });

  @override
  Future<ItemSyncStatus?> round(String userId) async {
    final host = ref.read(keyboardHostProvider);
    final prefs = ref.read(ztPreferencesStoreProvider);
    final phone = await readPhone(host);
    if (!active) return null;
    if (phone == null) {
      return const ItemSyncStatus(running: true, unavailable: true);
    }
    final store = await ref.read(ztLocalStoreProvider.future);
    final account = await collection.list(store);
    final knownKey = 'kb_sync_${name}_known_$userId';
    final changes = reconcile<T>(
      phone: phone,
      account: account,
      known: _decodeKnown(await prefs.read(knownKey)),
      keyOf: keyOf,
      valid: valid,
    );
    if (!active) return null;

    if (changes.addToPhone.isNotEmpty || changes.removeFromPhone.isNotEmpty) {
      final applied = await applyToPhone(
        host,
        add: changes.addToPhone,
        remove: changes.removeFromPhone,
      );
      if (!applied) {
        return const ItemSyncStatus(running: true, unavailable: true);
      }
    }
    if (!active) return null;
    final sync = ref.read(ztSyncControllerProvider.notifier);
    for (final value in changes.addToAccount) {
      await collection.save(sync, store, Synced(id: _uuid.v4(), value: value));
    }
    for (final id in changes.removeFromAccount) {
      await collection.delete(sync, id);
    }
    await prefs.write(knownKey, jsonEncode(changes.known.toList()));
    return ItemSyncStatus(running: true, synced: changes.known.length);
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

final wordsSyncEnabledProvider = AsyncNotifierProvider<SyncEnabled, bool>(
  () => SyncEnabled('words'),
);

/// The personal dictionary: words and shortcuts the user added.
class WordsSync extends ItemSync<PersonalWord> {
  @override
  String get name => 'words';

  @override
  AsyncNotifierProvider<SyncEnabled, bool> get enabledProvider =>
      wordsSyncEnabledProvider;

  @override
  ZtCollection<Synced<PersonalWord>> get collection => syncedWordsCollection;

  @override
  String keyOf(PersonalWord value) => value.key;

  @override
  bool valid(PersonalWord value) => value.word.isNotEmpty;

  @override
  Future<List<PersonalWord>?> readPhone(KeyboardHost host) =>
      host.personalWords();

  @override
  Future<bool> applyToPhone(
    KeyboardHost host, {
    required List<PersonalWord> add,
    required List<PersonalWord> remove,
  }) => host.applyPersonalWords(add: add, remove: remove);
}

final wordsSyncProvider = NotifierProvider<WordsSync, ItemSyncStatus>(
  WordsSync.new,
);

final clipsSyncEnabledProvider = AsyncNotifierProvider<SyncEnabled, bool>(
  () => SyncEnabled('clips'),
);

/// The clips pinned in the keyboard's clipboard: text only.
class ClipsSync extends ItemSync<String> {
  @override
  String get name => 'clips';

  @override
  AsyncNotifierProvider<SyncEnabled, bool> get enabledProvider =>
      clipsSyncEnabledProvider;

  @override
  ZtCollection<Synced<String>> get collection => syncedClipsCollection;

  @override
  String keyOf(String value) => clipKey(value);

  @override
  bool valid(String value) => value.isNotEmpty;

  @override
  Future<List<String>?> readPhone(KeyboardHost host) => host.pinnedClips();

  @override
  Future<bool> applyToPhone(
    KeyboardHost host, {
    required List<String> add,
    required List<String> remove,
  }) => host.applyPinnedClips(add: add, remove: remove);
}

final clipsSyncProvider = NotifierProvider<ClipsSync, ItemSyncStatus>(
  ClipsSync.new,
);

final settingsSyncEnabledProvider = AsyncNotifierProvider<SyncEnabled, bool>(
  () => SyncEnabled('settings'),
);

/// The keyboard's own settings: one item in the account, merged setting by
/// setting.
class SettingsSync extends RoundSync {
  @override
  String get name => 'settings';

  @override
  AsyncNotifierProvider<SyncEnabled, bool> get enabledProvider =>
      settingsSyncEnabledProvider;

  @override
  Future<ItemSyncStatus?> round(String userId) async {
    final host = ref.read(keyboardHostProvider);
    final prefs = ref.read(ztPreferencesStoreProvider);
    final phone = await host.keyboardSettings();
    if (!active) return null;
    if (phone == null) {
      return const ItemSyncStatus(running: true, unavailable: true);
    }
    final store = await ref.read(ztLocalStoreProvider.future);
    final items = await syncedSettingsCollection.list(store);
    final account = items
        .where((item) => item.id == KeyboardSettings.itemId)
        .firstOrNull;
    final baseKey = 'kb_sync_settings_base_$userId';
    final merge = mergeSettings(
      phone: phone,
      account: account,
      base: decodeSettingsBase(await prefs.read(baseKey)),
    );
    if (!active) return null;
    if (merge == null) {
      // The other phone runs a newer app. Nothing is changed on either
      // side until this one is updated.
      return const ItemSyncStatus(running: true, unavailable: true);
    }
    if (merge.setOnPhone.isNotEmpty || merge.removeFromPhone.isNotEmpty) {
      final applied = await host.applyKeyboardSettings(
        set: merge.setOnPhone,
        remove: merge.removeFromPhone,
      );
      if (!applied) {
        return const ItemSyncStatus(running: true, unavailable: true);
      }
    }
    if (!active) return null;
    if (merge.upload) {
      await syncedSettingsCollection.save(
        ref.read(ztSyncControllerProvider.notifier),
        store,
        KeyboardSettings(version: phone.version, values: merge.merged),
      );
    }
    await prefs.write(baseKey, jsonEncode(merge.merged));
    return ItemSyncStatus(running: true, synced: merge.merged.length);
  }
}

final settingsSyncProvider = NotifierProvider<SettingsSync, ItemSyncStatus>(
  SettingsSync.new,
);

final learnedSyncEnabledProvider = AsyncNotifierProvider<SyncEnabled, bool>(
  () => SyncEnabled('learned'),
);

/// The words the keyboard learned from typing: one item in the account,
/// merged word by word. Words are only ever added to a phone.
class LearnedSync extends RoundSync {
  @override
  String get name => 'learned';

  @override
  AsyncNotifierProvider<SyncEnabled, bool> get enabledProvider =>
      learnedSyncEnabledProvider;

  @override
  Future<ItemSyncStatus?> round(String userId) async {
    final host = ref.read(keyboardHostProvider);
    final prefs = ref.read(ztPreferencesStoreProvider);
    final phone = await host.learnedWords();
    if (!active) return null;
    if (phone == null) {
      return const ItemSyncStatus(running: true, unavailable: true);
    }
    final store = await ref.read(ztLocalStoreProvider.future);
    final items = await learnedWordsCollection.list(store);
    final account = items
        .where((item) => item.id == LearnedWords.itemId)
        .firstOrNull;
    final baseKey = 'kb_sync_learned_base_$userId';
    final merge = mergeLearned(
      phone: phone,
      account: account?.words,
      base: decodeLearnedBase(await prefs.read(baseKey)),
    );
    if (!active) return null;
    if (merge.addToPhone.isNotEmpty &&
        !await host.addLearnedWords(merge.addToPhone)) {
      return const ItemSyncStatus(running: true, unavailable: true);
    }
    if (!active) return null;
    if (merge.upload) {
      await learnedWordsCollection.save(
        ref.read(ztSyncControllerProvider.notifier),
        store,
        LearnedWords(merge.merged),
      );
    }
    await prefs.write(
      baseKey,
      jsonEncode(encodeWordsByLanguage(merge.merged)),
    );
    return ItemSyncStatus(
      running: true,
      synced: merge.merged.values.fold<int>(0, (n, words) => n + words.length),
    );
  }
}

final learnedSyncProvider = NotifierProvider<LearnedSync, ItemSyncStatus>(
  LearnedSync.new,
);
