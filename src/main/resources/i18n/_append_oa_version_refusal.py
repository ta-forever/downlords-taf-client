# Mirror the OA "version not allowed" refusal keys into all 15 locales (see .claude/skills/i18n-add-keys).
# Values with {n} placeholders: no apostrophes in the translations (typographic ones only), so no doubling.
# The English base value needs a doubled apostrophe (MessageFormat).
import os

KEYS = ["oa.versionNotAllowed.openAnnihilation", "oa.versionNotAllowed.own", "oa.versionNotAllowed.unknownVersion",
        "oa.versionNotAllowed.range.any", "oa.versionNotAllowed.range.between",
        "oa.versionNotAllowed.range.min", "oa.versionNotAllowed.range.max"]
BETWEEN = "{0}–{1}"

# order: openAnnihilation, own, unknownVersion, range.any, range.min, range.max
T = {
 "ca": ["Open Annihilation {0} no està permès per a aquest mod (cal {1}). Actualitzeu-lo des de Configuració > Actualitzacions d’Open Annihilation.",
        "Open Annihilation {0} no està permès per a aquest mod (cal {1}). Actualitzeu o substituïu la vostra instal·lació d’Open Annihilation.",
        "desconeguda", "una versió aprovada", "{0} o més nova", "{0} o més antiga"],
 "cs": ["Open Annihilation {0} není pro tento mod povolen (je potřeba {1}). Aktualizujte jej v Open Annihilation v Nastavení > Aktualizace.",
        "Open Annihilation {0} není pro tento mod povolen (je potřeba {1}). Aktualizujte nebo nahraďte svou instalaci Open Annihilation.",
        "neznámá", "schválená verze", "{0} nebo novější", "{0} nebo starší"],
 "de": ["Open Annihilation {0} ist für diesen Mod nicht erlaubt (benötigt {1}). Aktualisiere es in Open Annihilation unter Einstellungen > Updates.",
        "Open Annihilation {0} ist für diesen Mod nicht erlaubt (benötigt {1}). Aktualisiere oder ersetze deine Open-Annihilation-Installation.",
        "unbekannt", "eine freigegebene Version", "{0} oder neuer", "{0} oder älter"],
 "es": ["Open Annihilation {0} no está permitido para este mod (se necesita {1}). Actualízalo desde Ajustes > Actualizaciones de Open Annihilation.",
        "Open Annihilation {0} no está permitido para este mod (se necesita {1}). Actualiza o sustituye tu instalación de Open Annihilation.",
        "desconocida", "una versión aprobada", "{0} o posterior", "{0} o anterior"],
 "fr": ["Open Annihilation {0} n’est pas autorisé pour ce mod (il faut {1}). Mettez-le à jour depuis Paramètres > Mises à jour d’Open Annihilation.",
        "Open Annihilation {0} n’est pas autorisé pour ce mod (il faut {1}). Mettez à jour ou remplacez votre installation d’Open Annihilation.",
        "inconnue", "une version approuvée", "{0} ou plus récente", "{0} ou plus ancienne"],
 "he": ["Open Annihilation {0} אינה מותרת עבור מוד זה (נדרשת {1}). עדכנו אותה מתוך ההגדרות > עדכונים של Open Annihilation.",
        "Open Annihilation {0} אינה מותרת עבור מוד זה (נדרשת {1}). עדכנו או החליפו את ההתקנה של Open Annihilation.",
        "לא ידועה", "גרסה מאושרת", "{0} או חדשה יותר", "{0} או ישנה יותר"],
 "it": ["Open Annihilation {0} non è consentito per questo mod (serve {1}). Aggiornalo da Impostazioni > Aggiornamenti di Open Annihilation.",
        "Open Annihilation {0} non è consentito per questo mod (serve {1}). Aggiorna o sostituisci la tua installazione di Open Annihilation.",
        "sconosciuta", "una versione approvata", "{0} o successiva", "{0} o precedente"],
 "ko": ["Open Annihilation {0} 버전은 이 모드에서 허용되지 않습니다({1} 필요). Open Annihilation의 설정 > 업데이트에서 업데이트하세요.",
        "Open Annihilation {0} 버전은 이 모드에서 허용되지 않습니다({1} 필요). Open Annihilation 설치를 업데이트하거나 교체하세요.",
        "알 수 없음", "승인된 버전", "{0} 이상", "{0} 이하"],
 "nl": ["Open Annihilation {0} is niet toegestaan voor deze mod ({1} nodig). Werk het bij via Instellingen > Updates van Open Annihilation.",
        "Open Annihilation {0} is niet toegestaan voor deze mod ({1} nodig). Werk je Open Annihilation-installatie bij of vervang die.",
        "onbekend", "een goedgekeurde versie", "{0} of nieuwer", "{0} of ouder"],
 "pl": ["Open Annihilation {0} nie jest dozwolone dla tego moda (wymagane {1}). Zaktualizuj je w Ustawienia > Aktualizacje w Open Annihilation.",
        "Open Annihilation {0} nie jest dozwolone dla tego moda (wymagane {1}). Zaktualizuj lub zastąp swoją instalację Open Annihilation.",
        "nieznana", "zatwierdzona wersja", "{0} lub nowsza", "{0} lub starsza"],
 "pt": ["O Open Annihilation {0} não é permitido para este mod (é necessário {1}). Atualize-o em Definições > Atualizações do Open Annihilation.",
        "O Open Annihilation {0} não é permitido para este mod (é necessário {1}). Atualize ou substitua a sua instalação do Open Annihilation.",
        "desconhecida", "uma versão aprovada", "{0} ou mais recente", "{0} ou mais antiga"],
 "ru": ["Open Annihilation {0} не разрешён для этого мода (нужно {1}). Обновите его в Настройки > Обновления Open Annihilation.",
        "Open Annihilation {0} не разрешён для этого мода (нужно {1}). Обновите или замените вашу установку Open Annihilation.",
        "неизвестна", "одобренная версия", "{0} или новее", "{0} или старее"],
 "tr": ["Open Annihilation {0} bu mod için izin verilmiyor ({1} gerekli). Open Annihilation Ayarlar > Güncellemeler bölümünden güncelleyin.",
        "Open Annihilation {0} bu mod için izin verilmiyor ({1} gerekli). Open Annihilation kurulumunuzu güncelleyin veya değiştirin.",
        "bilinmiyor", "onaylı bir sürüm", "{0} veya daha yeni", "{0} veya daha eski"],
 "uk": ["Open Annihilation {0} не дозволено для цього мода (потрібно {1}). Оновіть його в Налаштування > Оновлення Open Annihilation.",
        "Open Annihilation {0} не дозволено для цього мода (потрібно {1}). Оновіть або замініть вашу інсталяцію Open Annihilation.",
        "невідома", "схвалена версія", "{0} або новіша", "{0} або старіша"],
 "zh": ["此 Mod 不允许使用 Open Annihilation {0}（需要 {1}）。请在 Open Annihilation 的设置 > 更新中进行更新。",
        "此 Mod 不允许使用 Open Annihilation {0}（需要 {1}）。请更新或替换您的 Open Annihilation 安装。",
        "未知", "已批准的版本", "{0} 或更高", "{0} 或更低"],
}
EN = [
 "Open Annihilation {0} is not allowed for this mod (needs {1}). Update it from Open Annihilation''s Settings > Updates.",
 "Open Annihilation {0} is not allowed for this mod (needs {1}). Update or replace your Open Annihilation install.",
 "unknown", "an approved version", "{0} or newer", "{0} or older"]
COMMENTS = ["Refusal shown when the Engine updates setting is Open Annihilation; {0}=player's OA version, {1}=allowed range",
            "Refusal shown when the Engine updates setting is own install; {0}=player's OA version, {1}=allowed range",
            "Shown in place of the version when the server did not know the player's OA version",
            "Allowed range when the server gave neither a minimum nor a maximum",
            "Allowed range {0}-{1} (min and max)", "Allowed range with only a minimum", "Allowed range with only a maximum"]


def full(vals):
    return vals[:4] + [BETWEEN] + vals[4:]


def write(path, vals, with_comments):
    data = open(path, "rb").read()
    if KEYS[0].encode() in data:
        return
    out = b""
    if not data.endswith(b"\n"):
        out += b"\r\n"
    out += b"\r\n# OA version-not-allowed refusal text (by Engine updates setting)\r\n"
    for k, v, c in zip(KEYS, vals, COMMENTS):
        if with_comments:
            out += ("# " + c + "\r\n").encode("utf-8")
        out += (k + " = " + v + "\r\n").encode("utf-8")
    with open(path, "ab") as f:
        f.write(out)


here = os.path.dirname(os.path.abspath(__file__))
write(os.path.join(here, "messages.properties"), full(EN), True)
for loc, vals in T.items():
    write(os.path.join(here, "messages_%s.properties" % loc), full(vals), False)
