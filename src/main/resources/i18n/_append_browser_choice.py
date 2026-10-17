# Mirror the browser-choice keys into all 15 locale files (see .claude/skills/i18n-add-keys).
# No {n} placeholders in any of these values, so apostrophes stay single.
import io, os

KEYS = ["settings.general.browser", "settings.general.browser.description",
        "settings.browser.ask", "settings.browser.systemDefault", "settings.browser.chrome",
        "settings.browser.firefox", "settings.browser.edge",
        "browserChoice.title", "browserChoice.text", "browserChoice.remember"]

T = {
 "ca": ["Navegador per mirar",
        "Amb quin navegador s'obre el visor 3D de repeticions. Alguns usuaris veuen que el Chrome peta mentre miren (buidar la memòria cau ajuda, però no gaire estona) mentre que el Firefox no es veu afectat. Trieu «Pregunta-ho sempre» per decidir cada vegada, amb l'opció de recordar la resposta.",
        "Pregunta-ho sempre", "Navegador per defecte del sistema", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "Amb quin navegador voleu obrir el visor de repeticions?",
        "Alguns usuaris veuen que el Chrome peta mentre miren al visor 3D, mentre que el Firefox no es veu afectat. Trieu un navegador per a aquesta repetició. Podeu canviar-ho quan vulgueu a la configuració.",
        "Recorda la meva tria i no ho tornis a preguntar"],
 "cs": ["Prohlížeč pro sledování",
        "Ve kterém prohlížeči se otevře 3D prohlížeč záznamů. Některým uživatelům při sledování padá Chrome (vymazání mezipaměti pomůže, ale ne nadlouho), zatímco Firefox je bez problémů. Zvolte „Vždy se zeptat“ pro dotaz pokaždé, s možností zapamatovat si odpověď.",
        "Vždy se zeptat", "Výchozí prohlížeč systému", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "Ve kterém prohlížeči otevřít prohlížeč záznamů?",
        "Některým uživatelům při sledování ve 3D prohlížeči padá Chrome, zatímco Firefox je bez problémů. Vyberte prohlížeč pro tento záznam. Kdykoli to můžete změnit v nastavení.",
        "Zapamatovat si volbu a příště se neptat"],
 "de": ["Browser zum Zuschauen",
        "In welchem Browser der 3D-Replay-Betrachter geöffnet wird. Bei einigen Nutzern stürzt Chrome beim Zuschauen ab (das Leeren des Caches hilft, aber nicht lange), während Firefox nicht betroffen ist. Wähle „Jedes Mal fragen“, um gefragt zu werden – mit der Möglichkeit, die Antwort zu speichern.",
        "Jedes Mal fragen", "Standardbrowser des Systems", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "In welchem Browser soll der Replay-Betrachter geöffnet werden?",
        "Bei einigen Nutzern stürzt Chrome beim Zuschauen im 3D-Betrachter ab, während Firefox nicht betroffen ist. Wähle einen Browser für dieses Replay. Du kannst dies jederzeit in den Einstellungen ändern.",
        "Meine Wahl merken und nicht mehr fragen"],
 "es": ["Navegador para ver repeticiones",
        "En qué navegador se abre el visor 3D de repeticiones. Algunos usuarios ven que Chrome se bloquea mientras miran (vaciar la caché ayuda, pero no por mucho tiempo), mientras que Firefox no se ve afectado. Elige «Preguntar siempre» para que se te pregunte, con la opción de recordar tu respuesta.",
        "Preguntar siempre", "Navegador predeterminado del sistema", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "¿En qué navegador quieres abrir el visor de repeticiones?",
        "Algunos usuarios ven que Chrome se bloquea al mirar en el visor 3D, mientras que Firefox no se ve afectado. Elige un navegador para esta repetición. Puedes cambiarlo cuando quieras en Ajustes.",
        "Recordar mi elección y dejar de preguntar"],
 "fr": ["Navigateur pour regarder",
        "Dans quel navigateur ouvrir la visionneuse 3D des parties enregistrées. Chez certains utilisateurs, Chrome plante pendant le visionnage (vider le cache aide, mais pas longtemps) alors que Firefox n'est pas touché. Choisissez « Demander à chaque fois » pour être consulté, avec la possibilité de mémoriser votre réponse.",
        "Demander à chaque fois", "Navigateur par défaut du système", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "Dans quel navigateur ouvrir la visionneuse ?",
        "Chez certains utilisateurs, Chrome plante pendant le visionnage dans la visionneuse 3D, alors que Firefox n'est pas touché. Choisissez un navigateur pour cette partie. Vous pouvez changer cela à tout moment dans les paramètres.",
        "Mémoriser mon choix et ne plus demander"],
 "he": ["דפדפן לצפייה",
        "באיזה דפדפן ייפתח מציג ההקלטות התלת-ממדי. אצל חלק מהמשתמשים Chrome קורס בזמן צפייה (ניקוי המטמון עוזר, אך לא לאורך זמן) ואילו Firefox אינו מושפע. בחרו ב\"לשאול בכל פעם\" כדי להישאל, עם אפשרות לזכור את התשובה.",
        "לשאול בכל פעם", "דפדפן ברירת המחדל של המערכת", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "באיזה דפדפן לפתוח את מציג ההקלטות?",
        "אצל חלק מהמשתמשים Chrome קורס בזמן צפייה במציג התלת-ממדי, ואילו Firefox אינו מושפע. בחרו דפדפן להקלטה זו. אפשר לשנות זאת בכל עת בהגדרות.",
        "לזכור את בחירתי ולהפסיק לשאול"],
 "it": ["Browser per guardare",
        "In quale browser aprire il visualizzatore 3D dei replay. Alcuni utenti vedono Chrome andare in crash durante la visione (svuotare la cache aiuta, ma non a lungo) mentre Firefox non è interessato. Scegli «Chiedi ogni volta» per essere interpellato, con la possibilità di ricordare la risposta.",
        "Chiedi ogni volta", "Browser predefinito di sistema", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "In quale browser aprire il visualizzatore dei replay?",
        "Alcuni utenti vedono Chrome andare in crash durante la visione nel visualizzatore 3D, mentre Firefox non è interessato. Scegli un browser per questo replay. Puoi cambiarlo in qualsiasi momento nelle impostazioni.",
        "Ricorda la mia scelta e non chiedere più"],
 "ko": ["관전용 브라우저",
        "3D 리플레이 뷰어를 어느 브라우저에서 열지 정합니다. 일부 사용자는 관전 중 Chrome이 다운되는 현상을 겪지만(캐시를 지우면 잠시 나아집니다) Firefox는 영향을 받지 않습니다. \"항상 묻기\"를 선택하면 매번 묻고, 답을 기억하도록 할 수 있습니다.",
        "항상 묻기", "시스템 기본 브라우저", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "리플레이 뷰어를 어느 브라우저에서 열까요?",
        "일부 사용자는 3D 뷰어에서 관전 중 Chrome이 다운되는 현상을 겪지만 Firefox는 영향을 받지 않습니다. 이 리플레이에 사용할 브라우저를 선택하세요. 설정에서 언제든지 변경할 수 있습니다.",
        "내 선택을 기억하고 다시 묻지 않기"],
 "nl": ["Browser om te kijken",
        "In welke browser de 3D-replayviewer wordt geopend. Bij sommige gebruikers crasht Chrome tijdens het kijken (cache legen helpt, maar niet lang) terwijl Firefox geen last heeft. Kies \"Altijd vragen\" om gevraagd te worden, met de mogelijkheid je antwoord te onthouden.",
        "Altijd vragen", "Standaardbrowser van het systeem", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "In welke browser wil je de replayviewer openen?",
        "Bij sommige gebruikers crasht Chrome tijdens het kijken in de 3D-viewer, terwijl Firefox geen last heeft. Kies een browser voor deze replay. Je kunt dit altijd wijzigen in de instellingen.",
        "Mijn keuze onthouden en niet meer vragen"],
 "pl": ["Przeglądarka do oglądania",
        "W której przeglądarce otwierać przeglądarkę powtórek 3D. U niektórych użytkowników Chrome zawiesza się podczas oglądania (wyczyszczenie pamięci podręcznej pomaga, ale nie na długo), a Firefox działa bez problemu. Wybierz „Pytaj za każdym razem”, aby otrzymać pytanie, z możliwością zapamiętania odpowiedzi.",
        "Pytaj za każdym razem", "Domyślna przeglądarka systemu", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "W której przeglądarce otworzyć podgląd powtórki?",
        "U niektórych użytkowników Chrome zawiesza się podczas oglądania w przeglądarce 3D, a Firefox działa bez problemu. Wybierz przeglądarkę dla tej powtórki. Możesz to zmienić w każdej chwili w ustawieniach.",
        "Zapamiętaj mój wybór i nie pytaj więcej"],
 "pt": ["Navegador para assistir",
        "Em qual navegador abrir o visualizador 3D de replays. Alguns usuários veem o Chrome travar durante a exibição (limpar o cache ajuda, mas não por muito tempo), enquanto o Firefox não é afetado. Escolha «Perguntar sempre» para ser consultado, com a opção de lembrar sua resposta.",
        "Perguntar sempre", "Navegador padrão do sistema", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "Em qual navegador abrir o visualizador de replays?",
        "Alguns usuários veem o Chrome travar ao assistir no visualizador 3D, enquanto o Firefox não é afetado. Escolha um navegador para este replay. Você pode alterar isso quando quiser nas configurações.",
        "Lembrar minha escolha e parar de perguntar"],
 "ru": ["Браузер для просмотра",
        "В каком браузере открывать 3D-просмотрщик записей. У части пользователей Chrome аварийно завершается во время просмотра (очистка кэша помогает, но ненадолго), тогда как Firefox работает нормально. Выберите «Спрашивать каждый раз», чтобы получать запрос, с возможностью запомнить ответ.",
        "Спрашивать каждый раз", "Браузер системы по умолчанию", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "В каком браузере открыть просмотрщик записей?",
        "У части пользователей Chrome аварийно завершается при просмотре в 3D-просмотрщике, тогда как Firefox работает нормально. Выберите браузер для этой записи. Изменить это можно в любой момент в настройках.",
        "Запомнить выбор и больше не спрашивать"],
 "tr": ["İzleme tarayıcısı",
        "3B tekrar görüntüleyicisinin hangi tarayıcıda açılacağı. Bazı kullanıcılarda izleme sırasında Chrome çöküyor (önbelleği temizlemek yardımcı oluyor ama uzun sürmüyor), Firefox ise etkilenmiyor. Her seferinde sorulması için «Her zaman sor» seçeneğini seçin; yanıtınızı hatırlatabilirsiniz.",
        "Her zaman sor", "Sistemin varsayılan tarayıcısı", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "Tekrar görüntüleyicisi hangi tarayıcıda açılsın?",
        "Bazı kullanıcılarda 3B görüntüleyicide izlerken Chrome çöküyor, Firefox ise etkilenmiyor. Bu tekrar için bir tarayıcı seçin. Bunu istediğiniz zaman Ayarlar'dan değiştirebilirsiniz.",
        "Seçimimi hatırla ve bir daha sorma"],
 "uk": ["Браузер для перегляду",
        "У якому браузері відкривати 3D-переглядач записів. У частини користувачів Chrome аварійно завершується під час перегляду (очищення кешу допомагає, але ненадовго), тоді як Firefox працює нормально. Виберіть «Запитувати щоразу», щоб отримувати запит, з можливістю запам'ятати відповідь.",
        "Запитувати щоразу", "Типовий браузер системи", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "У якому браузері відкрити переглядач записів?",
        "У частини користувачів Chrome аварійно завершується під час перегляду в 3D-переглядачі, тоді як Firefox працює нормально. Виберіть браузер для цього запису. Змінити це можна будь-коли в налаштуваннях.",
        "Запам'ятати вибір і більше не запитувати"],
 "zh": ["观战使用的浏览器",
        "用哪个浏览器打开 3D 录像观看器。部分用户在观看时会遇到 Chrome 崩溃（清除缓存有帮助，但维持不久），而 Firefox 不受影响。选择“每次询问”即可在每次观看时选择，并可记住你的答案。",
        "每次询问", "系统默认浏览器", "Google Chrome", "Mozilla Firefox", "Microsoft Edge",
        "用哪个浏览器打开录像观看器？",
        "部分用户在 3D 观看器中观看时会遇到 Chrome 崩溃，而 Firefox 不受影响。请为这个录像选择一个浏览器。你随时可以在设置中更改。",
        "记住我的选择，不再询问"],
}

here = os.path.dirname(os.path.abspath(__file__))
for loc, values in T.items():
    path = os.path.join(here, "messages_%s.properties" % loc)
    if not os.path.exists(path):
        print("MISSING", path)
        continue
    data = io.open(path, "rb").read()
    if KEYS[0].encode("utf-8") in data:
        print("skip (already present):", loc)
        continue
    out = b"" if data.endswith(b"\n") else b"\r\n"
    out += b"# browser choice for the 3D web viewer\r\n"
    for key, value in zip(KEYS, values):
        out += ("%s = %s\r\n" % (key, value)).encode("utf-8")
    with io.open(path, "ab") as f:
        f.write(out)
    print("appended:", loc)
