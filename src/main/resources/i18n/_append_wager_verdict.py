#!/usr/bin/env python3
# Tooltips for the settlement stamp drawn at the end of the wager price chart (green tick if the
# charted outcome won, red X if it lost).
# MessageFormat apostrophe rule: both values carry {0}, so any apostrophe must be doubled — none
# of the translations below contain one.
import os

KEYS = ['wager.chart.won', 'wager.chart.lost']

ENGLISH = ['{0} won', '{0} lost']

TRANSLATIONS = {
    'ca': ['{0} ha guanyat', '{0} ha perdut'],
    'cs': ['{0} vyhrál', '{0} prohrál'],
    'de': ['{0} hat gewonnen', '{0} hat verloren'],
    'es': ['{0} ganó', '{0} perdió'],
    'fr': ['{0} a gagné', '{0} a perdu'],
    'he': ['{0} ניצח', '{0} הפסיד'],
    'it': ['{0} ha vinto', '{0} ha perso'],
    'ko': ['{0} 승리', '{0} 패배'],
    'nl': ['{0} heeft gewonnen', '{0} heeft verloren'],
    'pl': ['{0} wygrał', '{0} przegrał'],
    'pt': ['{0} venceu', '{0} perdeu'],
    'ru': ['{0} победил', '{0} проиграл'],
    'tr': ['{0} kazandı', '{0} kaybetti'],
    'uk': ['{0} переміг', '{0} програв'],
    'zh': ['{0} 获胜', '{0} 失败'],
}

here = os.path.dirname(os.path.abspath(__file__))


def append(path, values):
    with open(path, 'rb') as f:
        data = f.read()
    if KEYS[0].encode('utf-8') in data:
        print('skip (already present):', os.path.basename(path))
        return
    with open(path, 'ab') as f:
        if not data.endswith(b'\n'):
            f.write(b'\r\n')
        f.write(b'# Wager price chart: settlement stamp (tick / X) tooltips\r\n')
        for key, value in zip(KEYS, values):
            f.write(('%s = %s' % (key, value)).encode('utf-8') + b'\r\n')
    print('appended:', os.path.basename(path))


append(os.path.join(here, 'messages.properties'), ENGLISH)
for locale, values in TRANSLATIONS.items():
    append(os.path.join(here, 'messages_%s.properties' % locale), values)
print('done')
