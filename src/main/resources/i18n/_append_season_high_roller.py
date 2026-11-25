#!/usr/bin/env python3
# Season High Roller medal (season_high_roller): the season's best wager P&L on a board,
# awarded by the season-close sweep. Adds the medal name/desc plus a new cabinet tier
# ("Season Wagering") that holds it.
#
# Unlike the plain _append_* scripts these keys are INSERTED after their sibling anchors
# rather than appended at EOF, so the medal block stays together in every file:
#   medal.season_high_roller.name/.desc  ->  after medal.season_top33.desc
#   medal.class.wager                    ->  after medal.class.percentile
# Each inserted line copies the anchor line's own EOL, so CRLF/LF files stay as they are.
#
# No {n} placeholders in any value, so the MessageFormat apostrophe-doubling rule does not
# apply (the Catalan/French apostrophes below are literal and must stay single).
import os
import re

MEDAL_ANCHOR = 'medal.season_top33.desc'
CLASS_ANCHOR = 'medal.class.percentile'

NAME_KEY = 'medal.season_high_roller.name'
DESC_KEY = 'medal.season_high_roller.desc'
CLASS_KEY = 'medal.class.wager'

# locale -> (name, desc, class label); phrasing mirrors each file's existing season_top33.desc.
VALUES = {
    None: ('Season High Roller',
           'Finish a season with the biggest wagering profit on a busy ladder.',
           'Season Wagering'),
    'ca': ('Gran apostador de la temporada',
           "Acaba una temporada amb el major benefici d'apostes d'un rànquing concorregut.",
           'Apostes de temporada'),
    'cs': ('Sázkař sezóny',
           'Zakonči sezónu s největším ziskem ze sázek na rušném žebříčku.',
           'Sázení v sezóně'),
    'de': ('High Roller der Saison',
           'Beende eine Saison mit dem größten Wettgewinn einer gut besuchten Rangliste.',
           'Saisonwetten'),
    'es': ('Gran apostador de la temporada',
           'Termina una temporada con la mayor ganancia en apuestas de un ladder concurrido.',
           'Apuestas de temporada'),
    'fr': ('Gros parieur de la saison',
           "Terminez une saison avec le plus gros gain de paris d'un ladder bien rempli.",
           'Paris de saison'),
    'he': ('מהמר העונה',
           'סיים עונה עם רווח ההימורים הגדול ביותר בסולם עמוס.',
           'הימורי העונה'),
    'it': ('Grande scommettitore della stagione',
           'Concludi una stagione con il maggior profitto da scommesse di un ladder affollato.',
           'Scommesse di stagione'),
    'ko': ('시즌 큰손',
           '붐비는 래더에서 시즌 최대 베팅 수익으로 마치세요.',
           '시즌 베팅'),
    'nl': ('Grootgokker van het seizoen',
           'Eindig een seizoen met de grootste gokwinst van een drukke ladder.',
           'Seizoensweddenschappen'),
    'pl': ('Hazardzista sezonu',
           'Zakończ sezon z największym zyskiem z zakładów na ruchliwej drabince.',
           'Zakłady sezonu'),
    'pt': ('Grande apostador da temporada',
           'Termine uma temporada com o maior lucro em apostas de um ladder movimentado.',
           'Apostas da temporada'),
    'ru': ('Хайроллер сезона',
           'Завершите сезон с наибольшей прибылью от ставок в активном ладдере.',
           'Ставки сезона'),
    'tr': ('Sezonun yüksek bahisçisi',
           'Bir sezonu yoğun bir merdivende en yüksek bahis kârıyla bitir.',
           'Sezon Bahisleri'),
    'uk': ('Хайролер сезону',
           'Завершити сезон із найбільшим прибутком від ставок в активному ладдері.',
           'Ставки сезону'),
    'zh': ('赛季豪赌客',
           '在热门天梯中以最高的下注盈利结束一个赛季。',
           '赛季下注'),
}

here = os.path.dirname(os.path.abspath(__file__))


def insert_after(text, anchor_key, lines):
    """Insert `lines` (list of 'key = value') right after the anchor key's line, reusing that
    line's EOL. Returns the new text, or None when the anchor is missing."""
    m = re.search(r'^' + re.escape(anchor_key) + r'[ \t]*=.*?(\r?\n)', text, re.MULTILINE)
    if not m:
        return None
    eol = m.group(1)
    block = ''.join(line + eol for line in lines)
    return text[:m.end()] + block + text[m.end():]


def process(path, values):
    name, desc, cls = values
    with open(path, 'rb') as f:
        data = f.read()
    if NAME_KEY.encode('utf-8') in data:
        print('skip (already present):', os.path.basename(path))
        return
    text = data.decode('utf-8')

    updated = insert_after(text, MEDAL_ANCHOR,
                           ['%s = %s' % (NAME_KEY, name), '%s = %s' % (DESC_KEY, desc)])
    if updated is None:
        raise SystemExit('anchor %s missing in %s' % (MEDAL_ANCHOR, path))
    text = updated

    updated = insert_after(text, CLASS_ANCHOR, ['%s = %s' % (CLASS_KEY, cls)])
    if updated is None:
        raise SystemExit('anchor %s missing in %s' % (CLASS_ANCHOR, path))
    text = updated

    with open(path, 'wb') as f:
        f.write(text.encode('utf-8'))
    print('inserted:', os.path.basename(path))


process(os.path.join(here, 'messages.properties'), VALUES[None])
for locale, values in VALUES.items():
    if locale is None:
        continue
    process(os.path.join(here, 'messages_%s.properties' % locale), values)
print('done')
