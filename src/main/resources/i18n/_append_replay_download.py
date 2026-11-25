#!/usr/bin/env python3
# Appends the "download replay (canonically named)" keys to every locale mirror.
# Binary append, UTF-8 + CRLF, per the i18n-add-keys skill.
# MessageFormat rule: keys whose value contains {n} must have their apostrophes doubled.
import io
import os

FIRST_KEY = "replay.download"

TRANSLATIONS = {
    "ca": {
        "replay.download": "Baixa",
        "replay.download.tooltip": "Desa aquesta repetició al teu ordinador, amb el mateix nom que té a TADA.",
        "replay.download.chooseFile": "Anomena i desa la repetició...",
        "replay.download.fileType": "Arxiu de repetició",
        "replay.download.finished": "Repetició desada com a {0}",
        "replay.download.showInFolder": "Mostra a la carpeta",
        "replay.download.failed": "No s''ha pogut baixar la repetició {0,number,#}. Consulta els detalls de l''error a sota.",
    },
    "cs": {
        "replay.download": "Stáhnout",
        "replay.download.tooltip": "Uložte si tento záznam do počítače, pojmenovaný stejně jako na TADA.",
        "replay.download.chooseFile": "Uložit záznam jako...",
        "replay.download.fileType": "Archiv se záznamem",
        "replay.download.finished": "Záznam uložen jako {0}",
        "replay.download.showInFolder": "Zobrazit ve složce",
        "replay.download.failed": "Záznam {0,number,#} se nepodařilo stáhnout. Podrobnosti o chybě najdete níže.",
    },
    "de": {
        "replay.download": "Herunterladen",
        "replay.download.tooltip": "Speichere diese Aufzeichnung auf deinem Computer – mit dem gleichen Namen, den sie auf TADA hat.",
        "replay.download.chooseFile": "Aufzeichnung speichern unter...",
        "replay.download.fileType": "Aufzeichnungsarchiv",
        "replay.download.finished": "Aufzeichnung gespeichert als {0}",
        "replay.download.showInFolder": "Im Ordner anzeigen",
        "replay.download.failed": "Aufzeichnung {0,number,#} konnte nicht heruntergeladen werden. Fehlerdetails siehe unten.",
    },
    "es": {
        "replay.download": "Descargar",
        "replay.download.tooltip": "Guarda esta repetición en tu ordenador, con el mismo nombre que tiene en TADA.",
        "replay.download.chooseFile": "Guardar repetición como...",
        "replay.download.fileType": "Archivo de repetición",
        "replay.download.finished": "Repetición guardada como {0}",
        "replay.download.showInFolder": "Mostrar en la carpeta",
        "replay.download.failed": "No se pudo descargar la repetición {0,number,#}. Consulta los detalles del error más abajo.",
    },
    "fr": {
        "replay.download": "Télécharger",
        "replay.download.tooltip": "Enregistrez ce replay sur votre ordinateur, avec le même nom que sur TADA.",
        "replay.download.chooseFile": "Enregistrer le replay sous...",
        "replay.download.fileType": "Archive de replay",
        "replay.download.finished": "Replay enregistré sous {0}",
        "replay.download.showInFolder": "Afficher dans le dossier",
        "replay.download.failed": "Le replay {0,number,#} n''a pas pu être téléchargé. Voir les détails de l''erreur ci-dessous.",
    },
    "he": {
        "replay.download": "הורד",
        "replay.download.tooltip": "שמור הקלטה זו למחשב שלך, באותו שם שיש לה ב-TADA.",
        "replay.download.chooseFile": "שמור הקלטה בשם...",
        "replay.download.fileType": "ארכיון הקלטה",
        "replay.download.finished": "ההקלטה נשמרה כ-{0}",
        "replay.download.showInFolder": "הצג בתיקייה",
        "replay.download.failed": "לא ניתן היה להוריד את הקלטה {0,number,#}. פרטי השגיאה מופיעים להלן.",
    },
    "it": {
        "replay.download": "Scarica",
        "replay.download.tooltip": "Salva questo replay sul tuo computer, con lo stesso nome che ha su TADA.",
        "replay.download.chooseFile": "Salva replay come...",
        "replay.download.fileType": "Archivio replay",
        "replay.download.finished": "Replay salvato come {0}",
        "replay.download.showInFolder": "Mostra nella cartella",
        "replay.download.failed": "Impossibile scaricare il replay {0,number,#}. Vedi i dettagli dell''errore qui sotto.",
    },
    "ko": {
        "replay.download": "다운로드",
        "replay.download.tooltip": "이 리플레이를 TADA에서와 같은 이름으로 내 컴퓨터에 저장합니다.",
        "replay.download.chooseFile": "리플레이 다른 이름으로 저장...",
        "replay.download.fileType": "리플레이 압축 파일",
        "replay.download.finished": "리플레이를 {0}(으)로 저장했습니다",
        "replay.download.showInFolder": "폴더에서 보기",
        "replay.download.failed": "리플레이 {0,number,#}을(를) 다운로드하지 못했습니다. 아래 오류 세부 정보를 확인하세요.",
    },
    "nl": {
        "replay.download": "Downloaden",
        "replay.download.tooltip": "Sla deze replay op je computer op, met dezelfde naam als op TADA.",
        "replay.download.chooseFile": "Replay opslaan als...",
        "replay.download.fileType": "Replay-archief",
        "replay.download.finished": "Replay opgeslagen als {0}",
        "replay.download.showInFolder": "Toon in map",
        "replay.download.failed": "Replay {0,number,#} kon niet worden gedownload. Zie de foutdetails hieronder.",
    },
    "pl": {
        "replay.download": "Pobierz",
        "replay.download.tooltip": "Zapisz tę powtórkę na swoim komputerze, pod tą samą nazwą co na TADA.",
        "replay.download.chooseFile": "Zapisz powtórkę jako...",
        "replay.download.fileType": "Archiwum powtórki",
        "replay.download.finished": "Powtórka zapisana jako {0}",
        "replay.download.showInFolder": "Pokaż w folderze",
        "replay.download.failed": "Nie udało się pobrać powtórki {0,number,#}. Szczegóły błędu poniżej.",
    },
    "pt": {
        "replay.download": "Baixar",
        "replay.download.tooltip": "Salve este replay no seu computador, com o mesmo nome que ele tem no TADA.",
        "replay.download.chooseFile": "Salvar replay como...",
        "replay.download.fileType": "Arquivo de replay",
        "replay.download.finished": "Replay salvo como {0}",
        "replay.download.showInFolder": "Mostrar na pasta",
        "replay.download.failed": "Não foi possível baixar o replay {0,number,#}. Veja os detalhes do erro abaixo.",
    },
    "ru": {
        "replay.download": "Скачать",
        "replay.download.tooltip": "Сохраните эту запись на свой компьютер — под тем же именем, что и на TADA.",
        "replay.download.chooseFile": "Сохранить запись как...",
        "replay.download.fileType": "Архив записи",
        "replay.download.finished": "Запись сохранена как {0}",
        "replay.download.showInFolder": "Показать в папке",
        "replay.download.failed": "Не удалось скачать запись {0,number,#}. Подробности ошибки см. ниже.",
    },
    "tr": {
        "replay.download": "İndir",
        "replay.download.tooltip": "Bu tekrarı TADA'daki adıyla aynı adla bilgisayarınıza kaydedin.",
        "replay.download.chooseFile": "Tekrarı farklı kaydet...",
        "replay.download.fileType": "Tekrar arşivi",
        "replay.download.finished": "Tekrar {0} olarak kaydedildi",
        "replay.download.showInFolder": "Klasörde göster",
        "replay.download.failed": "{0,number,#} numaralı tekrar indirilemedi. Hata ayrıntıları aşağıdadır.",
    },
    "uk": {
        "replay.download": "Завантажити",
        "replay.download.tooltip": "Збережіть цей запис на свій комп'ютер — під тією самою назвою, що й на TADA.",
        "replay.download.chooseFile": "Зберегти запис як...",
        "replay.download.fileType": "Архів запису",
        "replay.download.finished": "Запис збережено як {0}",
        "replay.download.showInFolder": "Показати в теці",
        "replay.download.failed": "Не вдалося завантажити запис {0,number,#}. Деталі помилки нижче.",
    },
    "zh": {
        "replay.download": "下载",
        "replay.download.tooltip": "将此录像保存到你的电脑，文件名与 TADA 上的相同。",
        "replay.download.chooseFile": "录像另存为...",
        "replay.download.fileType": "录像压缩包",
        "replay.download.finished": "录像已保存为 {0}",
        "replay.download.showInFolder": "在文件夹中显示",
        "replay.download.failed": "无法下载录像 {0,number,#}。错误详情见下方。",
    },
}

ORDER = [
    "replay.download",
    "replay.download.tooltip",
    "replay.download.chooseFile",
    "replay.download.fileType",
    "replay.download.finished",
    "replay.download.showInFolder",
    "replay.download.failed",
]

here = os.path.dirname(os.path.abspath(__file__))

for locale, keys in sorted(TRANSLATIONS.items()):
    path = os.path.join(here, "messages_%s.properties" % locale)
    if not os.path.exists(path):
        print("SKIP (missing): %s" % path)
        continue
    with io.open(path, "rb") as f:
        data = f.read()
    if FIRST_KEY.encode("utf-8") in data:
        print("SKIP (already present): %s" % os.path.basename(path))
        continue
    out = b""
    if data and not data.endswith(b"\n"):
        out += b"\r\n"
    out += b"# Download a replay to disk, named the way TADA names it\r\n"
    for key in ORDER:
        out += ("%s = %s\r\n" % (key, keys[key])).encode("utf-8")
    with io.open(path, "ab") as f:
        f.write(out)
    print("appended: %s" % os.path.basename(path))
