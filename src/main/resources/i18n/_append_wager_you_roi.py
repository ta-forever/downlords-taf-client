#!/usr/bin/env python3
# "You vs the model" line on the House Model-Maker card, now carrying the player's own ROI next to
# the model's so "you're ahead" has a magnitude.
#
# MessageFormat apostrophe rule (see the i18n-add-keys skill):
#   * youVsModel / youOnly carry {n} -> any apostrophe MUST be doubled (English "model''s").
#   * youNoTrades has NO placeholder -> its apostrophe stays single (French "l'instant").
# Values containing apostrophes use DOUBLE-quoted python strings, so '' survives as two characters
# instead of being folded into an empty-string concatenation.
import os

KEYS = ['wager.bot.youVsModel', 'wager.bot.youOnly', 'wager.bot.youNoTrades']

ENGLISH = ["You vs the model: {0} LP on {1} staked — your ROI {2} vs the model''s {3}.",
           'You: {0} LP on {1} staked — ROI {2}.',
           'You vs the model: no settled wagers yet — place one to see how you compare.']

TRANSLATIONS = {
    'ca': ['Tu contra el model: {0} LP sobre {1} apostats — el teu ROI {2} contra el {3} del model.',
           'Tu: {0} LP sobre {1} apostats — ROI {2}.',
           'Tu contra el model: encara no tens apostes liquidades; fes-ne una per comparar-te.'],
    'cs': ['Ty vs. model: {0} LP z {1} vsazených — tvé ROI {2} proti {3} modelu.',
           'Ty: {0} LP z {1} vsazených — ROI {2}.',
           'Ty vs. model: zatím žádné vypořádané sázky — vsaď si a porovnej se.'],
    'de': ['Du gegen das Modell: {0} LP bei {1} Einsatz — dein ROI {2} gegen {3} des Modells.',
           'Du: {0} LP bei {1} Einsatz — ROI {2}.',
           'Du gegen das Modell: noch keine abgerechneten Wetten — setze eine und vergleiche dich.'],
    'es': ['Tú contra el modelo: {0} LP sobre {1} apostados — tu ROI {2} frente al {3} del modelo.',
           'Tú: {0} LP sobre {1} apostados — ROI {2}.',
           'Tú contra el modelo: aún no hay apuestas liquidadas; haz una para compararte.'],
    'fr': ['Vous contre le modèle : {0} LP sur {1} misés — votre ROI {2} contre {3} pour le modèle.',
           'Vous : {0} LP sur {1} misés — ROI {2}.',
           "Vous contre le modèle : aucun pari réglé pour l'instant — placez-en un pour vous comparer."],
    'he': ['אתה מול המודל: {0} LP מתוך {1} שהושקעו — ה-ROI שלך {2} מול {3} של המודל.',
           'אתה: {0} LP מתוך {1} שהושקעו — ROI {2}.',
           'אתה מול המודל: אין עדיין הימורים שסולקו — הצב אחד כדי להשוות.'],
    'it': ['Tu contro il modello: {0} LP su {1} puntati — il tuo ROI {2} contro il {3} del modello.',
           'Tu: {0} LP su {1} puntati — ROI {2}.',
           'Tu contro il modello: nessuna scommessa liquidata finora; piazzane una per confrontarti.'],
    'ko': ['나 vs 모델: {1} 투입 대비 {0} LP — 내 ROI {2}, 모델 {3}.',
           '나: {1} 투입 대비 {0} LP — ROI {2}.',
           '나 vs 모델: 아직 정산된 베팅이 없습니다. 한 번 베팅해 비교해 보세요.'],
    'nl': ['Jij tegen het model: {0} LP op {1} ingezet — jouw ROI {2} tegen {3} van het model.',
           'Jij: {0} LP op {1} ingezet — ROI {2}.',
           'Jij tegen het model: nog geen afgewikkelde weddenschappen — plaats er een om te vergelijken.'],
    'pl': ['Ty kontra model: {0} LP z {1} postawionych — twoje ROI {2} wobec {3} modelu.',
           'Ty: {0} LP z {1} postawionych — ROI {2}.',
           'Ty kontra model: brak rozliczonych zakładów — postaw jeden i się porównaj.'],
    'pt': ['Você contra o modelo: {0} LP sobre {1} apostados — seu ROI {2} contra {3} do modelo.',
           'Você: {0} LP sobre {1} apostados — ROI {2}.',
           'Você contra o modelo: ainda não há apostas liquidadas — faça uma para se comparar.'],
    'ru': ['Вы против модели: {0} LP при ставках {1} — ваш ROI {2} против {3} у модели.',
           'Вы: {0} LP при ставках {1} — ROI {2}.',
           'Вы против модели: рассчитанных ставок пока нет — сделайте ставку, чтобы сравнить себя.'],
    'tr': ['Sen modele karşı: {1} yatırıma karşılık {0} LP — senin ROI {2}, modelin {3}.',
           'Sen: {1} yatırıma karşılık {0} LP — ROI {2}.',
           'Sen modele karşı: henüz sonuçlanmış bahis yok — bir tane oyna ve karşılaştır.'],
    'uk': ['Ви проти моделі: {0} LP зі {1} поставлених — ваш ROI {2} проти {3} у моделі.',
           'Ви: {0} LP зі {1} поставлених — ROI {2}.',
           'Ви проти моделі: розрахованих ставок ще немає — зробіть ставку, щоб порівняти себе.'],
    'zh': ['你 vs 模型：投入 {1}，盈亏 {0} LP — 你的投资回报率 {2}，模型 {3}。',
           '你：投入 {1}，盈亏 {0} LP — 投资回报率 {2}。',
           '你 vs 模型：尚无已结算的下注 — 下一注即可比较。'],
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
        f.write(b'# House Model-Maker card: "you vs the model" with both ROIs\r\n')
        for key, value in zip(KEYS, values):
            f.write(('%s = %s' % (key, value)).encode('utf-8') + b'\r\n')
    print('appended:', os.path.basename(path))


append(os.path.join(here, 'messages.properties'), ENGLISH)
for locale, values in TRANSLATIONS.items():
    append(os.path.join(here, 'messages_%s.properties' % locale), values)
print('done')
