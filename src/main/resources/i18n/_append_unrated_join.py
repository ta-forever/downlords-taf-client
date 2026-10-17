#!/usr/bin/env python3
# "Rating out of bounds" join dialog, variant for a player who has not yet played enough games for a
# meaningful rating (quoting their mean-3*deviation would show a negative number once the prior is
# 1000/500).
# MessageFormat apostrophe rule: this value carries {0}/{1}, so every apostrophe in a translation
# must be DOUBLED. Affected below: fr ("n'avez" -> "n''avez") and ca ("unir-t'hi" -> "unir-t''hi").
# TRAP: the doubled '' must sit inside a DOUBLE-quoted python literal. Inside a single-quoted one,
# '' is implicit string concatenation and silently DELETES the apostrophe ("Vous navez").
# The base messages.properties already carries the key (added next to the sibling .text key), so the
# idempotence guard skips it.
import os

KEYS = ['game.joinGameRatingConfirmation.textUnrated']

ENGLISH = ['This game is for players with a rating between {0,number,#} and {1,number,#}. '
           'You have not played enough games yet for a rating.\\n\\nAre you sure you want to join?']

TRANSLATIONS = {
    'ca': ['Aquesta partida és per a jugadors amb una puntuació entre {0,number,#} i {1,number,#}. '
           "Encara no has jugat prou partides per tenir una puntuació.\\n\\nSegur que vols unir-t''hi?"],
    'cs': ['Tato hra je pro hráče s hodnocením mezi {0,number,#} a {1,number,#}. '
           'Zatím jsi neodehrál dost her na získání hodnocení.\\n\\nOpravdu se chceš připojit?'],
    'de': ['Dieses Spiel ist für Spieler mit einer Wertung zwischen {0,number,#} und {1,number,#}. '
           'Du hast noch nicht genug Spiele für eine Wertung gespielt.\\n\\nMöchtest du wirklich beitreten?'],
    'es': ['Esta partida es para jugadores con una puntuación entre {0,number,#} y {1,number,#}. '
           'Aún no has jugado suficientes partidas para tener una puntuación.\\n\\n¿Seguro que quieres unirte?'],
    'fr': ['Cette partie est réservée aux joueurs ayant un classement entre {0,number,#} et {1,number,#}. '
           "Vous n''avez pas encore joué assez de parties pour avoir un classement.\\n\\nVoulez-vous vraiment rejoindre ?"],
    'he': ['משחק זה מיועד לשחקנים עם דירוג בין {0,number,#} ל-{1,number,#}. '
           'עדיין לא שיחקת מספיק משחקים כדי לקבל דירוג.\\n\\nבטוח שברצונך להצטרף?'],
    'it': ['Questa partita è per giocatori con un punteggio tra {0,number,#} e {1,number,#}. '
           'Non hai ancora giocato abbastanza partite per avere un punteggio.\\n\\nVuoi davvero unirti?'],
    'ko': ['이 게임은 레이팅 {0,number,#} ~ {1,number,#} 사이의 플레이어를 위한 것입니다. '
           '아직 레이팅을 받을 만큼 충분히 플레이하지 않았습니다.\\n\\n정말 참가하시겠습니까?'],
    'nl': ['Dit spel is voor spelers met een rating tussen {0,number,#} en {1,number,#}. '
           'Je hebt nog niet genoeg spellen gespeeld voor een rating.\\n\\nWeet je zeker dat je wilt deelnemen?'],
    'pl': ['Ta gra jest dla graczy z rankingiem między {0,number,#} a {1,number,#}. '
           'Nie rozegrałeś jeszcze wystarczająco wielu gier, aby uzyskać ranking.\\n\\nNa pewno chcesz dołączyć?'],
    'pt': ['Este jogo é para jogadores com uma pontuação entre {0,number,#} e {1,number,#}. '
           'Ainda não jogaste partidas suficientes para teres uma pontuação.\\n\\nTens a certeza de que queres entrar?'],
    'ru': ['Эта игра для игроков с рейтингом от {0,number,#} до {1,number,#}. '
           'Вы ещё не сыграли достаточно игр для получения рейтинга.\\n\\nВы уверены, что хотите присоединиться?'],
    'tr': ['Bu oyun {0,number,#} ile {1,number,#} arası puana sahip oyuncular içindir. '
           'Henüz bir puan almak için yeterli oyun oynamadınız.\\n\\nKatılmak istediğinize emin misiniz?'],
    'uk': ['Ця гра для гравців з рейтингом від {0,number,#} до {1,number,#}. '
           'Ви ще не зіграли достатньо ігор для отримання рейтингу.\\n\\nВи впевнені, що хочете приєднатися?'],
    'zh': ['此游戏面向评分在 {0,number,#} 到 {1,number,#} 之间的玩家。'
           '您尚未进行足够的对局来获得评分。\\n\\n确定要加入吗？'],
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
        f.write(b'# Join dialog: rating bounds, variant for a player still in placement\r\n')
        for key, value in zip(KEYS, values):
            f.write(('%s = %s' % (key, value)).encode('utf-8') + b'\r\n')
    print('appended:', os.path.basename(path))


append(os.path.join(here, 'messages.properties'), ENGLISH)
for locale, values in TRANSLATIONS.items():
    append(os.path.join(here, 'messages_%s.properties' % locale), values)
print('done')
