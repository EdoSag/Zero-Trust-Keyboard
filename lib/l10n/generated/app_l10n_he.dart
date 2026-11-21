// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_l10n.dart';

// ignore_for_file: type=lint

/// The translations for Hebrew (`he`).
class AppL10nHe extends AppL10n {
  AppL10nHe([String locale = 'he']) : super(locale);

  @override
  String get appTitle => 'ZT Keyboard';

  @override
  String get setupTitle => 'הגדרת המקלדת';

  @override
  String get readyTitle => 'ZT Keyboard מוכנה';

  @override
  String get readyBody => 'היא נפתחת בכל פעם שמקישים על שדה טקסט.';

  @override
  String get stepEnableTitle => 'הפעלת ZT Keyboard';

  @override
  String get stepEnableBody =>
      'מצאו את ZT Keyboard ברשימת המקלדות של הטלפון והפעילו אותה. אנדרואיד מציג אזהרה רגילה לכל מקלדת; ZT Keyboard שומרת את מה שאתם מקלידים בטלפון הזה.';

  @override
  String get stepEnableAction => 'פתיחת רשימת המקלדות';

  @override
  String get stepSelectTitle => 'בחירת ZT Keyboard';

  @override
  String get stepSelectBody => 'בחרו ב-ZT Keyboard כמקלדת להקלדה.';

  @override
  String get stepSelectAction => 'בחירת מקלדת';

  @override
  String get stepDone => 'בוצע';

  @override
  String get tryItLabel => 'נסו את המקלדת כאן';

  @override
  String get keyboardSettings => 'הגדרות המקלדת';

  @override
  String get keyboardSettingsHint =>
      'שפות, פריסה, ערכות נושא, תיקונים, מילונים';

  @override
  String get privacyTitle => 'מה שמקלידים נשאר בטלפון הזה';

  @override
  String get privacyBody =>
      'הצעות, תיקונים ומילים שנלמדו מחושבים בטלפון. המקלדת עצמה אף פעם לא משתמשת באינטרנט.';

  @override
  String get accountHeader => 'חשבון Zero-Trust';

  @override
  String get accountOptional => 'לא חובה';

  @override
  String get accountSignedOutBody =>
      'המקלדת עובדת במלואה בלי חשבון. סנכרון המילון האישי בין טלפונים, בהצפנה מקצה לקצה, יגיע בגרסה מאוחרת יותר; אפשר להתחבר כבר עכשיו כדי להיות מוכנים.';

  @override
  String get accountSignIn => 'התחברות או יצירת חשבון';

  @override
  String get accountLocked => 'נעול. הקישו כדי לפתוח.';

  @override
  String get accountSettings => 'חשבון, אבטחה והגדרות האפליקציה';

  @override
  String get appHeader => 'האפליקציה הזאת';

  @override
  String get accountClose => 'חזרה למקלדת';

  @override
  String get syncWordsTitle => 'סנכרון המילון האישי שלי';

  @override
  String get syncWordsBody =>
      'המילים והקיצורים שהוספתם, בהצפנה מקצה לקצה, בכל טלפון שמחובר לחשבון הזה. הסנכרון פועל כשהאפליקציה פתוחה ולא נעולה.';

  @override
  String syncWordsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count רשומות סונכרנו',
      one: 'רשומה אחת סונכרנה',
      zero: 'אין עדיין מה לסנכרן',
    );
    return '$_temp0';
  }

  @override
  String get syncWordsKeyboardOff =>
      'קודם הפעילו את ZT Keyboard. אנדרואיד מציג את המילון האישי רק למקלדת פעילה.';

  @override
  String get syncWordsFailed => 'הסנכרון לא הצליח כרגע. הוא ינסה שוב.';

  @override
  String get syncClipsTitle => 'סנכרון הקטעים שנעצתי';

  @override
  String get syncClipsBody =>
      'טקסט שנעצתם בלוח ההעתקה של המקלדת, בהצפנה מקצה לקצה, בכל טלפון שמחובר לחשבון הזה. תמונות וטקסטים ארוכים מאוד נשארים בטלפון הזה. הסנכרון פועל כשהאפליקציה פתוחה ולא נעולה.';

  @override
  String syncClipsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count קטעים סונכרנו',
      one: 'קטע אחד סונכרן',
      zero: 'אין עדיין מה לסנכרן',
    );
    return '$_temp0';
  }

  @override
  String get syncClipsUnavailable =>
      'לא ניתן לפתוח את לוח ההעתקה כרגע. ננסה שוב.';

  @override
  String get syncSettingsTitle => 'סנכרון הגדרות המקלדת שלי';

  @override
  String get syncSettingsBody =>
      'ערכות נושא, סרגל כלים, שפות ואפשרויות הקלדה, בהצפנה מקצה לקצה, בכל טלפון שמחובר לחשבון הזה. גדלים, מודל הקול וקובצי פריסה מותאמים נשארים בטלפון הזה. הסנכרון פועל כשהאפליקציה פתוחה ולא נעולה.';

  @override
  String get syncSettingsDone => 'ההגדרות מסונכרנות';

  @override
  String get syncSettingsUnavailable =>
      'בהמתנה: טלפון אחר בחשבון הזה מריץ גרסה חדשה יותר של ZT Keyboard. עדכנו את האפליקציה כדי לסנכרן הגדרות.';

  @override
  String get syncLearnedTitle => 'סנכרון המילים שהמקלדת למדה';

  @override
  String get syncLearnedBody =>
      'מילים שהמקלדת למדה מההקלדה שלכם וכבר מציעה, בהצפנה מקצה לקצה, בכל טלפון שמחובר לחשבון הזה. מה שמוקלד בשדות סיסמה או במצב פרטי לא נלמד אף פעם. הסנכרון פועל כשהאפליקציה פתוחה ולא נעולה.';

  @override
  String syncLearnedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count מילים סונכרנו',
      one: 'מילה אחת סונכרנה',
      zero: 'אין עדיין מה לסנכרן',
    );
    return '$_temp0';
  }

  @override
  String get syncLearnedUnavailable =>
      'אין מה לסנכרן: הלמידה מהקלדה כבויה בהגדרות המקלדת, או שלא ניתן לקרוא את המילים כרגע.';
}
