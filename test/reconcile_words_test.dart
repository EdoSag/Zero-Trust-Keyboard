import 'package:flutter_test/flutter_test.dart';
import 'package:zerotrust_keyboard/sync/personal_word.dart';
import 'package:zerotrust_keyboard/sync/reconcile_words.dart';

PersonalWord w(String word, {String shortcut = '', String locale = ''}) =>
    PersonalWord(word: word, shortcut: shortcut, locale: locale);

SyncedWord s(String id, PersonalWord word) => SyncedWord(id: id, word: word);

List<String> words(List<PersonalWord> list) =>
    [for (final w in list) w.word]..sort();

void main() {
  test('first sync joins both sides and deletes nothing', () {
    final changes = reconcileWords(
      phone: [w('Edo'), w('zerotrust')],
      account: [s('a', w('zerotrust')), s('b', w('Sagron'))],
      known: {},
    );
    expect(words(changes.addToAccount), ['Edo']);
    expect(words(changes.addToPhone), ['Sagron']);
    expect(changes.removeFromPhone, isEmpty);
    expect(changes.removeFromAccount, isEmpty);
    expect(changes.known, {
      w('Edo').key,
      w('zerotrust').key,
      w('Sagron').key,
    });
  });

  test('a word deleted on this phone is deleted in the account', () {
    final changes = reconcileWords(
      phone: [w('Edo')],
      account: [s('a', w('Edo')), s('b', w('old'))],
      known: {w('Edo').key, w('old').key},
    );
    expect(changes.removeFromAccount, ['b']);
    expect(changes.addToPhone, isEmpty);
    expect(changes.known, {w('Edo').key});
  });

  test('a word deleted on another phone is deleted here', () {
    final changes = reconcileWords(
      phone: [w('Edo'), w('old')],
      account: [s('a', w('Edo'))],
      known: {w('Edo').key, w('old').key},
    );
    expect(words(changes.removeFromPhone), ['old']);
    expect(changes.addToAccount, isEmpty);
    expect(changes.known, {w('Edo').key});
  });

  test('new words on each side travel to the other', () {
    final changes = reconcileWords(
      phone: [w('Edo'), w('here')],
      account: [s('a', w('Edo')), s('b', w('there'))],
      known: {w('Edo').key},
    );
    expect(words(changes.addToAccount), ['here']);
    expect(words(changes.addToPhone), ['there']);
    expect(changes.removeFromPhone, isEmpty);
    expect(changes.removeFromAccount, isEmpty);
  });

  test('in step: nothing to do', () {
    final changes = reconcileWords(
      phone: [w('Edo'), w('brb', shortcut: 'be right back')],
      account: [
        s('a', w('Edo')),
        s('b', w('brb', shortcut: 'be right back')),
      ],
      known: {w('Edo').key, w('brb', shortcut: 'be right back').key},
    );
    expect(changes.isEmpty, isTrue);
    expect(changes.known.length, 2);
  });

  test('the same word with another shortcut or language is another entry', () {
    final changes = reconcileWords(
      phone: [w('שלום', locale: 'he'), w('omw', shortcut: 'on my way')],
      account: [s('a', w('שלום')), s('b', w('omw'))],
      known: {},
    );
    expect(changes.addToAccount.length, 2);
    expect(changes.addToPhone.length, 2);
    expect(changes.known.length, 4);
  });

  test('a word two phones both added is kept once in the account', () {
    final changes = reconcileWords(
      phone: [w('Edo')],
      account: [s('b', w('Edo')), s('a', w('Edo')), s('c', w(''))],
      known: {},
    );
    expect(changes.removeFromAccount..sort(), ['b', 'c']);
    expect(changes.addToAccount, isEmpty);
    expect(changes.addToPhone, isEmpty);
    expect(changes.known, {w('Edo').key});
  });

  test('a changed weight alone is not a change', () {
    final changes = reconcileWords(
      phone: [const PersonalWord(word: 'Edo', weight: 100)],
      account: [s('a', const PersonalWord(word: 'Edo', weight: 250))],
      known: {w('Edo').key},
    );
    expect(changes.isEmpty, isTrue);
  });

  test('entries survive the trip through the account', () {
    const word = PersonalWord(
      word: 'מה נשמע',
      shortcut: 'מנ',
      locale: 'he',
      weight: 200,
    );
    final item = SyncedWord(id: 'x', word: word);
    final json = syncedWordsCollection.encode(item);
    expect(json['kind'], 'word');
    final back = syncedWordsCollection.fromJson('x', json, DateTime(2026));
    expect(back.word.key, word.key);
    expect(back.word.weight, 200);
  });
}
