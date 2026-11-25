#!/usr/bin/env python3
# The download-button tooltip claimed the file is "named the same way it is named on TADA".
# That stopped being true once the mod name + version segment went into the name, so reword it
# in place in the base file and all 15 locale mirrors. Re-running is a no-op.
# NOTE (skill i18n-add-keys): `.*$` in MULTILINE eats the trailing \r, so a rewritten CRLF line
# comes out LF. Harmless -- .gitattributes is `* text=auto eol=lf`.
import io
import os
import re

KEY = "replay.download.tooltip"

VALUES = {
    "": "Save this replay to your computer, named with its date, mod, map and players.",
    "ca": "Desa aquesta repetició al teu ordinador, amb la data, el mod, el mapa i els jugadors al nom.",
    "cs": "Uložte si tento záznam do počítače; název obsahuje datum, mod, mapu a hráče.",
    "de": "Speichere diese Aufzeichnung auf deinem Computer – der Name enthält Datum, Mod, Karte und Spieler.",
    "es": "Guarda esta repetición en tu ordenador, con la fecha, el mod, el mapa y los jugadores en el nombre.",
    "fr": "Enregistrez ce replay sur votre ordinateur ; le nom contient la date, le mod, la carte et les joueurs.",
    "he": "שמור הקלטה זו למחשב שלך, כששם הקובץ כולל תאריך, מוד, מפה ושחקנים.",
    "it": "Salva questo replay sul tuo computer, con data, mod, mappa e giocatori nel nome.",
    "ko": "이 리플레이를 날짜, 모드, 맵, 플레이어가 들어간 이름으로 내 컴퓨터에 저장합니다.",
    "nl": "Sla deze replay op je computer op, met de datum, mod, map en spelers in de naam.",
    "pl": "Zapisz tę powtórkę na swoim komputerze; nazwa zawiera datę, mod, mapę i graczy.",
    "pt": "Salve este replay no seu computador, com a data, o mod, o mapa e os jogadores no nome.",
    "ru": "Сохраните эту запись на свой компьютер — в имени будут дата, мод, карта и игроки.",
    "tr": "Bu tekrarı tarih, mod, harita ve oyuncuları içeren bir adla bilgisayarınıza kaydedin.",
    "uk": "Збережіть цей запис на свій комп'ютер — у назві будуть дата, мод, карта та гравці.",
    "zh": "将此录像保存到你的电脑，文件名包含日期、模组、地图和玩家。",
}

here = os.path.dirname(os.path.abspath(__file__))
pattern = re.compile(r'^' + re.escape(KEY) + r'\s*=.*$', re.MULTILINE)

for locale, value in sorted(VALUES.items()):
    suffix = "" if locale == "" else "_" + locale
    path = os.path.join(here, "messages%s.properties" % suffix)
    if not os.path.exists(path):
        print("SKIP (missing): %s" % path)
        continue
    with io.open(path, "rb") as f:
        text = f.read().decode("utf-8")
    if not pattern.search(text):
        print("SKIP (key absent): %s" % os.path.basename(path))
        continue
    updated = pattern.sub(KEY + " = " + value, text)
    if updated == text:
        print("unchanged: %s" % os.path.basename(path))
        continue
    with io.open(path, "wb") as f:
        f.write(updated.encode("utf-8"))
    print("updated: %s" % os.path.basename(path))
