# Rewrite browserChoice.text in all 16 files (see .claude/skills/i18n-add-keys, "Changing an
# EXISTING key's value"). The prompt used to open with "Some users see Chrome crash while
# watching in the 3D viewer, while Firefox is unaffected." — dropped: a modal that appears
# before you have watched anything should ask the question, not warn about a browser.
# The settings-page description is a separate key and is deliberately left alone.
#
# No {n} placeholders in this value, so apostrophes stay single.
import io, os, re

KEY = "browserChoice.text"

VALUES = {
 "":   "Pick a browser for this replay. You can change this at any time in Settings.",
 "ca": "Trieu un navegador per a aquesta repetició. Podeu canviar-ho quan vulgueu a la configuració.",
 "cs": "Vyberte prohlížeč pro tento záznam. Kdykoli to můžete změnit v nastavení.",
 "de": "Wähle einen Browser für dieses Replay. Du kannst dies jederzeit in den Einstellungen ändern.",
 "es": "Elige un navegador para esta repetición. Puedes cambiarlo cuando quieras en Ajustes.",
 "fr": "Choisissez un navigateur pour cette partie. Vous pouvez changer cela à tout moment dans les paramètres.",
 "he": "בחרו דפדפן להקלטה זו. אפשר לשנות זאת בכל עת בהגדרות.",
 "it": "Scegli un browser per questo replay. Puoi cambiarlo in qualsiasi momento nelle impostazioni.",
 "ko": "이 리플레이에 사용할 브라우저를 선택하세요. 설정에서 언제든지 변경할 수 있습니다.",
 "nl": "Kies een browser voor deze replay. Je kunt dit altijd wijzigen in de instellingen.",
 "pl": "Wybierz przeglądarkę dla tej powtórki. Możesz to zmienić w każdej chwili w ustawieniach.",
 "pt": "Escolha um navegador para este replay. Você pode alterar isso quando quiser nas configurações.",
 "ru": "Выберите браузер для этой записи. Изменить это можно в любой момент в настройках.",
 "tr": "Bu tekrar için bir tarayıcı seçin. Bunu istediğiniz zaman Ayarlar'dan değiştirebilirsiniz.",
 "uk": "Виберіть браузер для цього запису. Змінити це можна будь-коли в налаштуваннях.",
 "zh": "请为这个录像选择一个浏览器。你随时可以在设置中更改。",
}

here = os.path.dirname(os.path.abspath(__file__))
pattern = re.compile(r'^' + re.escape(KEY) + r'\s*=.*$', re.MULTILINE)

for loc, value in VALUES.items():
    name = "messages.properties" if loc == "" else "messages_%s.properties" % loc
    path = os.path.join(here, name)
    if not os.path.exists(path):
        print("MISSING", name)
        continue
    text = io.open(path, "rb").read().decode("utf-8")
    if KEY not in text:
        print("key absent, skipping:", name)
        continue
    new_text, n = pattern.subn(KEY + " = " + value, text)
    if n != 1:
        print("expected 1 match, got %d in %s" % (n, name))
        continue
    io.open(path, "wb").write(new_text.encode("utf-8"))
    print("updated:", name)
