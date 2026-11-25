#!/usr/bin/env python3
# Label for the button centred on the wager price chart once the game is over, which jumps to the
# replay vault and opens that game's replay detail.
# No {n} placeholders, so no apostrophe doubling is needed.
import os

KEYS = ['wager.chart.openReplay']

ENGLISH = ['Open replay detail']

TRANSLATIONS = {
    'ca': ['Obre el detall de la repetició'],
    'cs': ['Otevřít detail záznamu'],
    'de': ['Wiederholungsdetails öffnen'],
    'es': ['Abrir detalles de la repetición'],
    'fr': ['Ouvrir le détail de la rediffusion'],
    'he': ['פתח את פרטי השידור החוזר'],
    'it': ['Apri i dettagli del replay'],
    'ko': ['리플레이 상세 열기'],
    'nl': ['Replaydetails openen'],
    'pl': ['Otwórz szczegóły powtórki'],
    'pt': ['Abrir detalhes do replay'],
    'ru': ['Открыть сведения о повторе'],
    'tr': ['Tekrar ayrıntılarını aç'],
    'uk': ['Відкрити відомості про повтор'],
    'zh': ['打开回放详情'],
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
        f.write(b'# Wager price chart: open-replay button (shown when the game is over)\r\n')
        for key, value in zip(KEYS, values):
            f.write(('%s = %s' % (key, value)).encode('utf-8') + b'\r\n')
    print('appended:', os.path.basename(path))


append(os.path.join(here, 'messages.properties'), ENGLISH)
for locale, values in TRANSLATIONS.items():
    append(os.path.join(here, 'messages_%s.properties' % locale), values)
print('done')
