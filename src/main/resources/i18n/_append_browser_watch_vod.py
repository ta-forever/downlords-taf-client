#!/usr/bin/env python3
# Appends the TADA watch-choice key (browser VOD watch) to all locale files.
# No {n} placeholders, so no apostrophe doubling.
import os

KEY = 'tada.watchChoice.text'

TRANSLATIONS = {
    'ca': "Vols veure aquesta repetició dins el joc o al visor 3D del navegador?",
    'cs': "Sledovat tento záznam ve hře, nebo v prohlížečovém 3D přehrávači?",
    'de': "Diese Wiederholung im Spiel ansehen oder im browserbasierten 3D-Viewer?",
    'es': "¿Ver esta repetición en el juego o en el visor 3D del navegador?",
    'fr': "Regarder cette rediffusion dans le jeu ou dans le visualiseur 3D du navigateur ?",
    'he': "לצפות בשידור חוזר זה בתוך המשחק או בצופה התלת-ממדי בדפדפן?",
    'it': "Guardare questo replay nel gioco o nel visualizzatore 3D del browser?",
    'ko': "이 리플레이를 게임에서 볼까요, 브라우저 3D 뷰어에서 볼까요?",
    'nl': "Deze replay in het spel bekijken of in de 3D-browserviewer?",
    'pl': "Obejrzeć tę powtórkę w grze czy w przeglądarkowym podglądzie 3D?",
    'pt': "Assistir a este replay no jogo ou no visualizador 3D do navegador?",
    'ru': "Смотреть этот повтор в игре или в браузерном 3D-просмотрщике?",
    'tr': "Bu tekrarı oyunda mı yoksa tarayıcı tabanlı 3B görüntüleyicide mi izlemek istersiniz?",
    'uk': "Дивитися цей повтор у грі чи в браузерному 3D-переглядачі?",
    'zh': "在游戏中观看此回放，还是在浏览器 3D 查看器中观看？",
}

here = os.path.dirname(os.path.abspath(__file__))
for locale, value in TRANSLATIONS.items():
    target = os.path.join(here, 'messages_%s.properties' % locale)
    with open(target, 'rb') as f:
        existing = f.read()
    if KEY.encode('utf-8') in existing:
        print('skip (already present):', locale)
        continue
    with open(target, 'ab') as f:
        if not existing.endswith(b'\n'):
            f.write(b'\r\n')
        f.write(('%s = %s' % (KEY, value)).encode('utf-8') + b'\r\n')
    print('appended:', locale)
print('done')
