#!/usr/bin/env python3
# Marker shown in place of the rating (team cards, manual teams, chat user info, leaderboard) for a player who has too few
# rated games for mean-3*deviation to mean anything (a fresh 1500/500 rating displays as 0, which
# reads as "terrible" rather than "not yet known").
# No {n} placeholders in this value, so apostrophes stay single (MessageFormat doubling not needed).
# LENGTH BUDGET: the team-card rank label is Consolas 12px (monospace, 7.0 px/char) drawn ON TOP of
# the player name, so every extra character hides one more character of the name. A rating reads
# "(1616)" = 42 px; keep every translation <= ~70 px (10 chars). Abbreviate rather than translate
# literally -- "(sense classificar)" was 133 px and swallowed two thirds of a 200 px card.
# The base messages.properties already carries the key (added next to the other tooltipFormat
# suffixes), so the idempotence guard skips it.
import os

KEYS = ['userInfo.tooltipFormat.placementSuffix']

ENGLISH = ['(unrated)']

TRANSLATIONS = {
    'ca': ['(no clas.)'],
    'cs': ['(bez hod.)'],
    'de': ['(unbew.)'],
    'es': ['(sin cl.)'],
    'fr': ['(non cl.)'],
    'he': ['(ללא דירוג)'],
    'it': ['(non val.)'],
    'ko': ['(미배치)'],
    'nl': ['(ongew.)'],
    'pl': ['(nieskl.)'],
    'pt': ['(s/class.)'],
    'ru': ['(без р.)'],
    'tr': ['(puansız)'],
    'uk': ['(без р.)'],
    'zh': ['(未定级)'],
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
        f.write(b'# Team card: marker shown instead of a rating while a player is still in placement\r\n')
        for key, value in zip(KEYS, values):
            f.write(('%s = %s' % (key, value)).encode('utf-8') + b'\r\n')
    print('appended:', os.path.basename(path))


append(os.path.join(here, 'messages.properties'), ENGLISH)
for locale, values in TRANSLATIONS.items():
    append(os.path.join(here, 'messages_%s.properties' % locale), values)
print('done')
