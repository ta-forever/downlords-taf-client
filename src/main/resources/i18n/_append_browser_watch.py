#!/usr/bin/env python3
# Appends "watch in browser" keys (browser live viewer context menu) to all locale files.
# No {n} placeholders in any of these keys, so no apostrophe doubling is needed.
import os

KEYS = ['game.watch.inGame', 'game.watch.inBrowser', 'game.watch.browserFailed',
        'chat.userContext.viewLiveReplayBrowser', 'wager.watchLiveBrowser']

TRANSLATIONS = {
    'ca': ['Veure al joc', 'Veure al navegador',
           "No s'ha pogut obrir el visor del navegador",
           'Veure la partida al navegador',
           'Veure la repetició en directe al navegador'],
    'cs': ['Sledovat ve hře', 'Sledovat v prohlížeči',
           'Prohlížečový přehrávač se nepodařilo otevřít',
           'Sledovat hru v prohlížeči',
           'Sledovat živý záznam v prohlížeči'],
    'de': ['Im Spiel ansehen', 'Im Browser ansehen',
           'Der Browser-Viewer konnte nicht geöffnet werden',
           'Spiel im Browser ansehen',
           'Live-Wiederholung im Browser ansehen'],
    'es': ['Ver en el juego', 'Ver en el navegador',
           'No se pudo abrir el visor del navegador',
           'Ver la partida en el navegador',
           'Ver la repetición en vivo en el navegador'],
    'fr': ['Regarder dans le jeu', 'Regarder dans le navigateur',
           "Impossible d'ouvrir le visualiseur dans le navigateur",
           'Regarder la partie dans le navigateur',
           'Regarder la rediffusion en direct dans le navigateur'],
    'he': ['צפה בתוך המשחק', 'צפה בדפדפן',
           'לא ניתן לפתוח את הצופה בדפדפן',
           'צפה במשחק בדפדפן',
           'צפה בשידור חוזר חי בדפדפן'],
    'it': ['Guarda nel gioco', 'Guarda nel browser',
           'Impossibile aprire il visualizzatore nel browser',
           'Guarda la partita nel browser',
           'Guarda il replay dal vivo nel browser'],
    'ko': ['게임에서 관전', '브라우저에서 관전',
           '브라우저 뷰어를 열 수 없습니다',
           '브라우저에서 게임 관전',
           '브라우저에서 실시간 리플레이 관전'],
    'nl': ['Bekijken in het spel', 'Bekijken in de browser',
           'De browserviewer kon niet worden geopend',
           'Spel bekijken in de browser',
           'Live replay bekijken in de browser'],
    'pl': ['Oglądaj w grze', 'Oglądaj w przeglądarce',
           'Nie można otworzyć przeglądarkowego podglądu',
           'Oglądaj grę w przeglądarce',
           'Oglądaj powtórkę na żywo w przeglądarce'],
    'pt': ['Assistir no jogo', 'Assistir no navegador',
           'Não foi possível abrir o visualizador no navegador',
           'Assistir à partida no navegador',
           'Assistir ao replay ao vivo no navegador'],
    'ru': ['Смотреть в игре', 'Смотреть в браузере',
           'Не удалось открыть браузерный просмотрщик',
           'Смотреть игру в браузере',
           'Смотреть живой повтор в браузере'],
    'tr': ['Oyunda izle', 'Tarayıcıda izle',
           'Tarayıcı görüntüleyici açılamadı',
           'Oyunu tarayıcıda izle',
           'Canlı tekrarı tarayıcıda izle'],
    'uk': ['Дивитися в грі', 'Дивитися в браузері',
           'Не вдалося відкрити браузерний переглядач',
           'Дивитися гру в браузері',
           'Дивитися живий повтор у браузері'],
    'zh': ['在游戏中观看', '在浏览器中观看',
           '无法打开浏览器查看器',
           '在浏览器中观看比赛',
           '在浏览器中观看实时回放'],
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
        f.write(b'# Watch-in-browser live viewer\r\n')
        for key, value in zip(KEYS, values):
            f.write(('%s = %s' % (key, value)).encode('utf-8') + b'\r\n')
    print('appended:', locale)
print('done')
