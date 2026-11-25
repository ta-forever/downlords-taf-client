#!/usr/bin/env python3
"""Append translations for the new tournament check-in feature keys to
every non-English locale file. Idempotent: if a given key already exists
(anywhere) in the target file, it is skipped.

The `EN` block is the English reference — never written anywhere (the
base messages.properties already has them). Every other block lists the
exact same keys; missing translations are copied from English at the
end of this script so we can't accidentally leave a key out of a file.

Run from the i18n directory:
    python _append_round5.py
"""
from pathlib import Path

KEYS = [
    "tournament.status.checkIn",
    "tournament.checkIn",
    "tournament.checkIn.open",
    "tournament.checkIn.title",
    "tournament.checkIn.text",
    "tournament.create.checkInMinutes",
    "tournament.create.checkInMinutes.hint",
    "tournament.participant.checkedIn",
    "tournament.participant.notCheckedIn",
    "tournament.participant.reputation",
    "tournament.detail.checkInOpensAt",
    "tournament.detail.checkInOpensAt.tooltip",
]

EN = {
    "tournament.status.checkIn": "Check-in open",
    "tournament.checkIn": "Check in",
    "tournament.checkIn.open": "Open tournament",
    "tournament.checkIn.title": "Tournament check-in",
    "tournament.checkIn.text": "A tournament you signed up for is open for check-in. Click \"Check in\" now to confirm you'll play, or open the tournament to see details.",
    "tournament.create.checkInMinutes": "Check-in window",
    "tournament.create.checkInMinutes.hint": "minutes (0 = no check-in)",
    "tournament.participant.checkedIn": "Checked in",
    "tournament.participant.notCheckedIn": "Not yet checked in",
    "tournament.participant.reputation": "Showed up to {0} of {1} signed-up tournaments ({2} no-check-ins, {3} match forfeits)",
    "tournament.detail.checkInOpensAt": "Check-in opens at",
    "tournament.detail.checkInOpensAt.tooltip": "Check in before the tournament starts or be dropped from the bracket.",
}

DE = {
    "tournament.status.checkIn": "Check-in offen",
    "tournament.checkIn": "Einchecken",
    "tournament.checkIn.open": "Turnier öffnen",
    "tournament.checkIn.title": "Turnier-Check-in",
    "tournament.checkIn.text": "Ein Turnier, für das du angemeldet bist, ist zum Check-in bereit. Klicke jetzt auf „Einchecken“, um deine Teilnahme zu bestätigen, oder öffne das Turnier für Details.",
    "tournament.create.checkInMinutes": "Check-in-Fenster",
    "tournament.create.checkInMinutes.hint": "Minuten (0 = kein Check-in)",
    "tournament.participant.checkedIn": "Eingecheckt",
    "tournament.participant.notCheckedIn": "Noch nicht eingecheckt",
    "tournament.participant.reputation": "Bei {0} von {1} angemeldeten Turnieren erschienen ({2} fehlende Check-ins, {3} Match-Aufgaben)",
    "tournament.detail.checkInOpensAt": "Check-in beginnt um",
    "tournament.detail.checkInOpensAt.tooltip": "Vor Turnierbeginn einchecken, sonst wirst du aus dem Bracket entfernt.",
}

ES = {
    "tournament.status.checkIn": "Registro abierto",
    "tournament.checkIn": "Registrarse",
    "tournament.checkIn.open": "Abrir torneo",
    "tournament.checkIn.title": "Registro del torneo",
    "tournament.checkIn.text": "El registro está abierto para un torneo en el que te inscribiste. Haz clic en «Registrarse» ahora para confirmar tu participación, o abre el torneo para ver más detalles.",
    "tournament.create.checkInMinutes": "Ventana de registro",
    "tournament.create.checkInMinutes.hint": "minutos (0 = sin registro)",
    "tournament.participant.checkedIn": "Registrado",
    "tournament.participant.notCheckedIn": "Aún no registrado",
    "tournament.participant.reputation": "Asistió a {0} de {1} torneos inscritos ({2} sin registro, {3} abandonos de partido)",
    "tournament.detail.checkInOpensAt": "El registro abre a las",
    "tournament.detail.checkInOpensAt.tooltip": "Regístrate antes del inicio del torneo o serás eliminado del cuadro.",
}

FR = {
    "tournament.status.checkIn": "Enregistrement ouvert",
    "tournament.checkIn": "S'enregistrer",
    "tournament.checkIn.open": "Ouvrir le tournoi",
    "tournament.checkIn.title": "Enregistrement au tournoi",
    "tournament.checkIn.text": "L'enregistrement est ouvert pour un tournoi auquel tu t'es inscrit. Clique sur « S'enregistrer » maintenant pour confirmer ta participation, ou ouvre le tournoi pour plus de détails.",
    "tournament.create.checkInMinutes": "Fenêtre d'enregistrement",
    "tournament.create.checkInMinutes.hint": "minutes (0 = pas d'enregistrement)",
    "tournament.participant.checkedIn": "Enregistré",
    "tournament.participant.notCheckedIn": "Pas encore enregistré",
    "tournament.participant.reputation": "S'est présenté à {0} sur {1} tournois inscrits ({2} sans enregistrement, {3} forfaits de match)",
    "tournament.detail.checkInOpensAt": "L'enregistrement ouvre à",
    "tournament.detail.checkInOpensAt.tooltip": "Enregistre-toi avant le début du tournoi ou tu seras exclu du tableau.",
}

IT = {
    "tournament.status.checkIn": "Check-in aperto",
    "tournament.checkIn": "Check-in",
    "tournament.checkIn.open": "Apri torneo",
    "tournament.checkIn.title": "Check-in al torneo",
    "tournament.checkIn.text": "È aperto il check-in per un torneo a cui ti sei iscritto. Fai clic su «Check-in» ora per confermare la partecipazione, o apri il torneo per i dettagli.",
    "tournament.create.checkInMinutes": "Finestra di check-in",
    "tournament.create.checkInMinutes.hint": "minuti (0 = nessun check-in)",
    "tournament.participant.checkedIn": "Check-in effettuato",
    "tournament.participant.notCheckedIn": "Check-in non ancora effettuato",
    "tournament.participant.reputation": "Presente a {0} di {1} tornei iscritti ({2} mancati check-in, {3} ritiri da partita)",
    "tournament.detail.checkInOpensAt": "Il check-in apre alle",
    "tournament.detail.checkInOpensAt.tooltip": "Esegui il check-in prima dell'inizio del torneo o sarai escluso dal tabellone.",
}

NL = {
    "tournament.status.checkIn": "Check-in open",
    "tournament.checkIn": "Inchecken",
    "tournament.checkIn.open": "Toernooi openen",
    "tournament.checkIn.title": "Toernooi-check-in",
    "tournament.checkIn.text": "Voor een toernooi waarvoor je je hebt aangemeld, is de check-in geopend. Klik nu op 'Inchecken' om je deelname te bevestigen, of open het toernooi voor details.",
    "tournament.create.checkInMinutes": "Check-in-venster",
    "tournament.create.checkInMinutes.hint": "minuten (0 = geen check-in)",
    "tournament.participant.checkedIn": "Ingecheckt",
    "tournament.participant.notCheckedIn": "Nog niet ingecheckt",
    "tournament.participant.reputation": "Verschenen bij {0} van {1} aangemelde toernooien ({2} geen check-in, {3} match-forfaits)",
    "tournament.detail.checkInOpensAt": "Check-in opent om",
    "tournament.detail.checkInOpensAt.tooltip": "Check in vóór het toernooi begint, anders word je uit de bracket verwijderd.",
}

PT = {
    "tournament.status.checkIn": "Check-in aberto",
    "tournament.checkIn": "Fazer check-in",
    "tournament.checkIn.open": "Abrir torneio",
    "tournament.checkIn.title": "Check-in do torneio",
    "tournament.checkIn.text": "O check-in está aberto para um torneio em que você se inscreveu. Clique em «Fazer check-in» agora para confirmar sua participação ou abra o torneio para mais detalhes.",
    "tournament.create.checkInMinutes": "Janela de check-in",
    "tournament.create.checkInMinutes.hint": "minutos (0 = sem check-in)",
    "tournament.participant.checkedIn": "Check-in feito",
    "tournament.participant.notCheckedIn": "Sem check-in",
    "tournament.participant.reputation": "Presente em {0} de {1} torneios inscritos ({2} sem check-in, {3} desistências de partida)",
    "tournament.detail.checkInOpensAt": "Check-in abre às",
    "tournament.detail.checkInOpensAt.tooltip": "Faça check-in antes do início do torneio ou será removido do chaveamento.",
}

CA = {
    "tournament.status.checkIn": "Registre obert",
    "tournament.checkIn": "Registrar-se",
    "tournament.checkIn.open": "Obre el torneig",
    "tournament.checkIn.title": "Registre del torneig",
    "tournament.checkIn.text": "El registre està obert per a un torneig en què t'has inscrit. Fes clic a «Registrar-se» ara per confirmar la teva participació, o obre el torneig per veure'n els detalls.",
    "tournament.create.checkInMinutes": "Finestra de registre",
    "tournament.create.checkInMinutes.hint": "minuts (0 = sense registre)",
    "tournament.participant.checkedIn": "Registrat",
    "tournament.participant.notCheckedIn": "Encara no registrat",
    "tournament.participant.reputation": "Es va presentar a {0} de {1} torneigs inscrits ({2} sense registre, {3} abandonaments de partida)",
    "tournament.detail.checkInOpensAt": "El registre obre a les",
    "tournament.detail.checkInOpensAt.tooltip": "Registra't abans que comenci el torneig o seràs eliminat del quadre.",
}

CS = {
    "tournament.status.checkIn": "Check-in otevřen",
    "tournament.checkIn": "Přihlásit se",
    "tournament.checkIn.open": "Otevřít turnaj",
    "tournament.checkIn.title": "Check-in turnaje",
    "tournament.checkIn.text": "Check-in je otevřen pro turnaj, do kterého ses přihlásil. Klikni na „Přihlásit se“ nyní, abys potvrdil účast, nebo otevři turnaj pro detaily.",
    "tournament.create.checkInMinutes": "Okno check-inu",
    "tournament.create.checkInMinutes.hint": "minut (0 = bez check-inu)",
    "tournament.participant.checkedIn": "Přihlášen",
    "tournament.participant.notCheckedIn": "Ještě nepřihlášen",
    "tournament.participant.reputation": "Zúčastnil se {0} z {1} přihlášených turnajů ({2}× nepřihlášen, {3} odhlášených zápasů)",
    "tournament.detail.checkInOpensAt": "Check-in se otevře v",
    "tournament.detail.checkInOpensAt.tooltip": "Přihlas se před začátkem turnaje, jinak budeš ze schématu vyřazen.",
}

PL = {
    "tournament.status.checkIn": "Rejestracja otwarta",
    "tournament.checkIn": "Zarejestruj się",
    "tournament.checkIn.open": "Otwórz turniej",
    "tournament.checkIn.title": "Rejestracja turnieju",
    "tournament.checkIn.text": "Rejestracja jest otwarta dla turnieju, na który się zapisałeś. Kliknij „Zarejestruj się“ teraz, aby potwierdzić udział, lub otwórz turniej, aby zobaczyć szczegóły.",
    "tournament.create.checkInMinutes": "Okno rejestracji",
    "tournament.create.checkInMinutes.hint": "minut (0 = bez rejestracji)",
    "tournament.participant.checkedIn": "Zarejestrowany",
    "tournament.participant.notCheckedIn": "Jeszcze nie zarejestrowany",
    "tournament.participant.reputation": "Pojawił się na {0} z {1} zapisanych turniejów ({2} brak rejestracji, {3} oddanych meczów)",
    "tournament.detail.checkInOpensAt": "Rejestracja otwiera się o",
    "tournament.detail.checkInOpensAt.tooltip": "Zarejestruj się przed startem turnieju, albo zostaniesz usunięty z drabinki.",
}

RU = {
    "tournament.status.checkIn": "Регистрация открыта",
    "tournament.checkIn": "Зарегистрироваться",
    "tournament.checkIn.open": "Открыть турнир",
    "tournament.checkIn.title": "Регистрация на турнир",
    "tournament.checkIn.text": "Регистрация открыта для турнира, на который вы записались. Нажмите «Зарегистрироваться» сейчас, чтобы подтвердить участие, или откройте турнир для просмотра деталей.",
    "tournament.create.checkInMinutes": "Окно регистрации",
    "tournament.create.checkInMinutes.hint": "минут (0 = без регистрации)",
    "tournament.participant.checkedIn": "Зарегистрирован",
    "tournament.participant.notCheckedIn": "Ещё не зарегистрирован",
    "tournament.participant.reputation": "Явился на {0} из {1} записанных турниров ({2} без регистрации, {3} отказов от матча)",
    "tournament.detail.checkInOpensAt": "Регистрация открывается в",
    "tournament.detail.checkInOpensAt.tooltip": "Зарегистрируйтесь до начала турнира, иначе вас исключат из сетки.",
}

UK = {
    "tournament.status.checkIn": "Реєстрація відкрита",
    "tournament.checkIn": "Зареєструватися",
    "tournament.checkIn.open": "Відкрити турнір",
    "tournament.checkIn.title": "Реєстрація на турнір",
    "tournament.checkIn.text": "Реєстрація відкрита для турніру, на який ти записався. Натисни «Зареєструватися» зараз, щоб підтвердити участь, або відкрий турнір для подробиць.",
    "tournament.create.checkInMinutes": "Вікно реєстрації",
    "tournament.create.checkInMinutes.hint": "хвилин (0 = без реєстрації)",
    "tournament.participant.checkedIn": "Зареєстрований",
    "tournament.participant.notCheckedIn": "Ще не зареєстрований",
    "tournament.participant.reputation": "З'явився на {0} з {1} записаних турнірів ({2} без реєстрації, {3} відмов від матчу)",
    "tournament.detail.checkInOpensAt": "Реєстрація відкривається о",
    "tournament.detail.checkInOpensAt.tooltip": "Зареєструйся до початку турніру, інакше тебе виключать із сітки.",
}

TR = {
    "tournament.status.checkIn": "Check-in açık",
    "tournament.checkIn": "Check-in yap",
    "tournament.checkIn.open": "Turnuvayı aç",
    "tournament.checkIn.title": "Turnuva check-in",
    "tournament.checkIn.text": "Kayıt olduğun bir turnuvanın check-in'i açıldı. Katılımı onaylamak için «Check-in yap»a tıkla veya ayrıntıları görmek için turnuvayı aç.",
    "tournament.create.checkInMinutes": "Check-in penceresi",
    "tournament.create.checkInMinutes.hint": "dakika (0 = check-in yok)",
    "tournament.participant.checkedIn": "Check-in yapıldı",
    "tournament.participant.notCheckedIn": "Henüz check-in yapılmadı",
    "tournament.participant.reputation": "Kayıtlı {1} turnuvanın {0}'inde göründü ({2} check-in yapılmamış, {3} maç hükmen kayıp)",
    "tournament.detail.checkInOpensAt": "Check-in başlangıcı",
    "tournament.detail.checkInOpensAt.tooltip": "Turnuva başlamadan check-in yap, yoksa eşleşme tablosundan çıkarılırsın.",
}

KO = {
    "tournament.status.checkIn": "체크인 열림",
    "tournament.checkIn": "체크인",
    "tournament.checkIn.open": "토너먼트 열기",
    "tournament.checkIn.title": "토너먼트 체크인",
    "tournament.checkIn.text": "신청한 토너먼트의 체크인이 열렸습니다. 지금 「체크인」을 클릭해 참가를 확정하거나 토너먼트를 열어 세부 정보를 확인하세요.",
    "tournament.create.checkInMinutes": "체크인 시간",
    "tournament.create.checkInMinutes.hint": "분 (0 = 체크인 없음)",
    "tournament.participant.checkedIn": "체크인 완료",
    "tournament.participant.notCheckedIn": "체크인 미완료",
    "tournament.participant.reputation": "신청한 {1}개 토너먼트 중 {0}개에 참가 (체크인 누락 {2}, 경기 기권 {3})",
    "tournament.detail.checkInOpensAt": "체크인 시작",
    "tournament.detail.checkInOpensAt.tooltip": "토너먼트 시작 전에 체크인하지 않으면 대진표에서 제외됩니다.",
}

ZH = {
    "tournament.status.checkIn": "签到开启",
    "tournament.checkIn": "签到",
    "tournament.checkIn.open": "打开锦标赛",
    "tournament.checkIn.title": "锦标赛签到",
    "tournament.checkIn.text": "你报名的锦标赛已开启签到。立即点击「签到」以确认参赛，或打开锦标赛查看详情。",
    "tournament.create.checkInMinutes": "签到时段",
    "tournament.create.checkInMinutes.hint": "分钟（0 = 无签到）",
    "tournament.participant.checkedIn": "已签到",
    "tournament.participant.notCheckedIn": "尚未签到",
    "tournament.participant.reputation": "已报名 {1} 场锦标赛中出席 {0} 场（{2} 次未签到，{3} 次比赛弃权）",
    "tournament.detail.checkInOpensAt": "签到开放时间",
    "tournament.detail.checkInOpensAt.tooltip": "在锦标赛开始前签到，否则将从对阵表中移除。",
}

HE = {
    "tournament.status.checkIn": "Check-in פתוח",
    "tournament.checkIn": "Check-in",
    "tournament.checkIn.open": "פתח טורניר",
    "tournament.checkIn.title": "Check-in לטורניר",
    "tournament.checkIn.text": "Check-in פתוח לטורניר שנרשמת אליו. לחץ על «Check-in» עכשיו כדי לאשר השתתפות, או פתח את הטורניר לפרטים.",
    "tournament.create.checkInMinutes": "חלון Check-in",
    "tournament.create.checkInMinutes.hint": "דקות (0 = ללא Check-in)",
    "tournament.participant.checkedIn": "ב-Check-in",
    "tournament.participant.notCheckedIn": "ללא Check-in",
    "tournament.participant.reputation": "הגיע ל-{0} מתוך {1} טורנירים שנרשם אליהם ({2} ללא Check-in, {3} פרישות ממשחק)",
    "tournament.detail.checkInOpensAt": "Check-in נפתח ב-",
    "tournament.detail.checkInOpensAt.tooltip": "בצע Check-in לפני תחילת הטורניר אחרת תוסר מהבראקט.",
}

LOCALES = {
    "de": DE, "es": ES, "fr": FR, "it": IT, "nl": NL, "pt": PT,
    "ca": CA, "cs": CS, "pl": PL, "ru": RU, "uk": UK, "tr": TR,
    "ko": KO, "zh": ZH, "he": HE,
}

HEADER = "\n\n# === Tournament check-in (appended round 5) ===\n"


def escape_value(v: str, encoding: str) -> str:
    """Java .properties files loaded without an explicit charset default
    to ISO-8859-1, and a couple of files (pt, tr) are stored as latin-1.
    Any non-latin-1 code point has to be emitted as a `\\uXXXX` escape
    to survive that load. UTF-8 files in this repo already carry the
    characters verbatim, so we only escape when writing latin-1."""
    if encoding == "latin-1":
        out = []
        for ch in v:
            if ord(ch) < 0x100:
                out.append(ch)
            else:
                out.append("\\u%04X" % ord(ch))
        return "".join(out)
    return v


def main():
    here = Path(__file__).resolve().parent
    for lang, table in LOCALES.items():
        path = here / f"messages_{lang}.properties"
        if not path.exists():
            print(f"skip {lang} — file missing")
            continue
        try:
            existing = path.read_text(encoding="utf-8")
            encoding = "utf-8"
        except UnicodeDecodeError:
            existing = path.read_text(encoding="latin-1")
            encoding = "latin-1"
        new_lines = []
        for key in KEYS:
            if f"\n{key}=" in existing or existing.startswith(f"{key}="):
                continue
            value = table.get(key) or EN[key]
            new_lines.append(f"{key}={escape_value(value, encoding)}")
        if not new_lines:
            print(f"{lang}: nothing to add")
            continue
        if not existing.endswith("\n"):
            existing += "\n"
        existing += HEADER + "\n".join(new_lines) + "\n"
        path.write_text(existing, encoding=encoding)
        print(f"{lang}: appended {len(new_lines)} keys")


if __name__ == "__main__":
    main()
