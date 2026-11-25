# Append the wager trade-unit pill keys (shares <-> LP) to every locale mirror.
# Binary append, UTF-8, CRLF, idempotent (skips a locale that already has the first key).
# MessageFormat rule: only wager.previewShares carries a {0}, and no translation of it needs an
# apostrophe, so nothing here has to double one.
import io
import os

FIRST_KEY = "wager.previewShares"

TRANSLATIONS = {
    "ca": {
        "wager.previewShares": "≈ {0} accions",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "Introdueix l'operació com a nombre d'accions o com a import en LP (comissió inclosa)",
        "wager.enterLpReceive": "Introdueix l'import en LP que vols rebre.",
        "wager.lpUnavailable": "Aquest import en LP no es pot negociar a aquest preu.",
    },
    "cs": {
        "wager.previewShares": "≈ {0} podílů",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "Zadejte obchod jako počet podílů, nebo jako částku v LP (včetně poplatku)",
        "wager.enterLpReceive": "Zadejte částku v LP, kterou chcete získat.",
        "wager.lpUnavailable": "Tuto částku v LP nelze za tuto cenu obchodovat.",
    },
    "de": {
        "wager.previewShares": "≈ {0} Anteile",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "Handel als Anzahl Anteile oder als LP-Betrag (inkl. Gebühr) eingeben",
        "wager.enterLpReceive": "Gib den zu erhaltenden LP-Betrag ein.",
        "wager.lpUnavailable": "Dieser LP-Betrag kann zu diesem Preis nicht gehandelt werden.",
    },
    "es": {
        "wager.previewShares": "≈ {0} acciones",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "Introduce la operación como número de acciones o como importe en LP (comisión incluida)",
        "wager.enterLpReceive": "Introduce el importe en LP que quieres recibir.",
        "wager.lpUnavailable": "Ese importe en LP no se puede negociar a este precio.",
    },
    "fr": {
        "wager.previewShares": "≈ {0} parts",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "Saisissez l'opération en nombre de parts ou en montant de LP (frais inclus)",
        "wager.enterLpReceive": "Saisissez le montant de LP à recevoir.",
        "wager.lpUnavailable": "Ce montant de LP ne peut pas être échangé à ce prix.",
    },
    "he": {
        "wager.previewShares": "≈ {0} מניות",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "הזן את העסקה כמספר מניות או כסכום LP (כולל עמלה)",
        "wager.enterLpReceive": "הזן את סכום ה-LP שברצונך לקבל.",
        "wager.lpUnavailable": "לא ניתן לסחור בסכום LP זה במחיר הזה.",
    },
    "it": {
        "wager.previewShares": "≈ {0} quote",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "Inserisci l'operazione come numero di quote o come importo in LP (commissione inclusa)",
        "wager.enterLpReceive": "Inserisci l'importo in LP da ricevere.",
        "wager.lpUnavailable": "Questo importo in LP non è negoziabile a questo prezzo.",
    },
    "ko": {
        "wager.previewShares": "≈ {0} 지분",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "거래를 지분 수 또는 LP 금액(수수료 포함)으로 입력합니다",
        "wager.enterLpReceive": "받을 LP 금액을 입력하세요.",
        "wager.lpUnavailable": "이 가격에서는 해당 LP 금액을 거래할 수 없습니다.",
    },
    "nl": {
        "wager.previewShares": "≈ {0} aandelen",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "Voer de transactie in als aantal aandelen of als LP-bedrag (inclusief kosten)",
        "wager.enterLpReceive": "Voer het te ontvangen LP-bedrag in.",
        "wager.lpUnavailable": "Dit LP-bedrag kan niet tegen deze prijs worden verhandeld.",
    },
    "pl": {
        "wager.previewShares": "≈ {0} udziałów",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "Wprowadź transakcję jako liczbę udziałów albo jako kwotę LP (z opłatą)",
        "wager.enterLpReceive": "Podaj kwotę LP, którą chcesz otrzymać.",
        "wager.lpUnavailable": "Tej kwoty LP nie można handlować po tej cenie.",
    },
    "pt": {
        "wager.previewShares": "≈ {0} cotas",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "Introduza a operação como número de cotas ou como valor em LP (taxa incluída)",
        "wager.enterLpReceive": "Introduza o valor em LP a receber.",
        "wager.lpUnavailable": "Esse valor em LP não pode ser negociado a este preço.",
    },
    "ru": {
        "wager.previewShares": "≈ {0} долей",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "Укажите сделку в долях или в LP (с учётом комиссии)",
        "wager.enterLpReceive": "Укажите сумму LP, которую хотите получить.",
        "wager.lpUnavailable": "Эту сумму LP нельзя выторговать по текущей цене.",
    },
    "tr": {
        "wager.previewShares": "≈ {0} pay",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "İşlemi pay sayısı veya LP tutarı (ücret dahil) olarak girin",
        "wager.enterLpReceive": "Alınacak LP tutarını girin.",
        "wager.lpUnavailable": "Bu LP tutarı bu fiyattan işlem göremez.",
    },
    "uk": {
        "wager.previewShares": "≈ {0} часток",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "Вкажіть угоду в частках або в LP (з урахуванням комісії)",
        "wager.enterLpReceive": "Вкажіть суму LP, яку хочете отримати.",
        "wager.lpUnavailable": "Цю суму LP не можна виторгувати за цією ціною.",
    },
    "zh": {
        "wager.previewShares": "≈ {0} 份额",
        "wager.lpUnit": "LP",
        "wager.mode.tooltip": "按份额数量或 LP 金额（含手续费）输入交易",
        "wager.enterLpReceive": "输入要获得的 LP 金额。",
        "wager.lpUnavailable": "该 LP 金额无法按此价格成交。",
    },
}

ORDER = ["wager.previewShares", "wager.lpUnit", "wager.mode.tooltip",
         "wager.enterLpReceive", "wager.lpUnavailable"]

here = os.path.dirname(os.path.abspath(__file__))
for locale, keys in sorted(TRANSLATIONS.items()):
    path = os.path.join(here, "messages_%s.properties" % locale)
    with io.open(path, "rb") as handle:
        data = handle.read()
    if FIRST_KEY.encode("utf-8") in data:
        print("skip %s (already has %s)" % (locale, FIRST_KEY))
        continue
    out = b""
    if not data.endswith(b"\n"):
        out += b"\r\n"
    out += "# wager trade-unit pill (shares <-> LP)\r\n".encode("utf-8")
    for key in ORDER:
        out += ("%s = %s\r\n" % (key, keys[key])).encode("utf-8")
    with io.open(path, "ab") as handle:
        handle.write(out)
    print("appended %d keys to %s" % (len(ORDER), locale))
