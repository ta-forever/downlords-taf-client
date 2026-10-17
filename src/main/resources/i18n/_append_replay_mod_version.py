#!/usr/bin/env python3
# Appends the replay mod-version tooltip key to all locale files.
# The label itself shows the raw version display string ("10.1"), so only the tooltip needs
# translating. No {n} placeholders, so no apostrophe doubling is needed.
import os

KEYS = ['replay.modVersionTooltip']

TRANSLATIONS = {
    'ca': ['Versió del mod amb què es va jugar aquesta repetició'],
    'cs': ['Verze modu, na které byl tento záznam odehrán'],
    'de': ['Mod-Version, mit der diese Wiederholung gespielt wurde'],
    'es': ['Versión del mod con la que se jugó esta repetición'],
    'fr': ['Version du mod avec laquelle cette partie a été jouée'],
    'he': ['גרסת המוד שבה שוחק שידור חוזר זה'],
    'it': ['Versione della mod con cui è stato giocato questo replay'],
    'ko': ['이 리플레이가 플레이된 모드 버전'],
    'nl': ['Modversie waarmee deze replay is gespeeld'],
    'pl': ['Wersja moda, na której rozegrano tę powtórkę'],
    'pt': ['Versão do mod em que esta repetição foi jogada'],
    'ru': ['Версия мода, на которой был сыгран этот повтор'],
    'tr': ['Bu tekrarın oynandığı mod sürümü'],
    'uk': ['Версія моду, на якій було зіграно цей повтор'],
    'zh': ['此回放所使用的模组版本'],
}

here = os.path.dirname(os.path.abspath(__file__))
for locale, values in TRANSLATIONS.items():
    target = os.path.join(here, 'messages_%s.properties' % locale)
    with open(target, 'rb') as f:
        existing = f.read()
    if KEYS[0].encode('utf-8') in existing:
        print('skip (already present):', locale)
        continue
    with open(target, 'ab') as f:
        if not existing.endswith(b'\n'):
            f.write(b'\r\n')
        f.write(b'# Replay mod version\r\n')
        for key, value in zip(KEYS, values):
            f.write(('%s = %s' % (key, value)).encode('utf-8') + b'\r\n')
    print('appended:', locale)
print('done')
