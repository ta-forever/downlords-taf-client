#!/usr/bin/env python3
# Wager tab "the game you are watching has ended" indication: market lifecycle in the header
# status label, the won/lost suffix on the outcome rows, and the spectator game-over line.
# MessageFormat apostrophe rule: only wager.market.settled / wager.gameOverResult carry {n};
# any apostrophe in those two values is doubled below.
import os

KEYS = ['wager.market.closed', 'wager.market.settled', 'wager.market.voided',
        'wager.market.unknownWinner', 'wager.outcome.won', 'wager.outcome.lost',
        'wager.gameOverResult']

ENGLISH = ['Game over — settling…', 'Settled — {0} won', 'Voided — stakes refunded',
           'the winner', '(winner)', '(lost)',
           'Game over — {0} won. Trading is closed.']

TRANSLATIONS = {
    'ca': ['Partida acabada — liquidant…', 'Liquidat — ha guanyat {0}',
           'Anul·lat — apostes retornades', 'el guanyador', '(guanyador)', '(perdedor)',
           'Partida acabada — ha guanyat {0}. La negociació està tancada.'],
    'cs': ['Hra skončila — vypořádání…', 'Vypořádáno — vyhrál {0}',
           'Zrušeno — sázky vráceny', 'vítěz', '(vítěz)', '(prohrál)',
           'Hra skončila — vyhrál {0}. Obchodování je uzavřeno.'],
    'de': ['Spiel beendet — wird abgerechnet…', 'Abgerechnet — {0} hat gewonnen',
           'Annulliert — Einsätze erstattet', 'der Gewinner', '(Sieger)', '(verloren)',
           'Spiel beendet — {0} hat gewonnen. Der Handel ist geschlossen.'],
    'es': ['Partida terminada — liquidando…', 'Liquidado — ganó {0}',
           'Anulado — apuestas reembolsadas', 'el ganador', '(ganador)', '(perdedor)',
           'Partida terminada — ganó {0}. La negociación está cerrada.'],
    'fr': ['Partie terminée — règlement…', 'Réglé — {0} a gagné',
           'Annulé — mises remboursées', 'le vainqueur', '(vainqueur)', '(perdant)',
           'Partie terminée — {0} a gagné. Les échanges sont clos.'],
    'he': ['המשחק הסתיים — מסלקים…', 'סולק — {0} ניצח',
           'בוטל — ההימורים הוחזרו', 'המנצח', '(מנצח)', '(הפסיד)',
           'המשחק הסתיים — {0} ניצח. המסחר סגור.'],
    'it': ['Partita finita — liquidazione…', 'Liquidato — ha vinto {0}',
           'Annullato — puntate rimborsate', 'il vincitore', '(vincitore)', '(perdente)',
           'Partita finita — ha vinto {0}. Gli scambi sono chiusi.'],
    'ko': ['경기 종료 — 정산 중…', '정산 완료 — {0} 승리',
           '무효 — 베팅 환불', '승자', '(승리)', '(패배)',
           '경기 종료 — {0} 승리. 거래가 마감되었습니다.'],
    'nl': ['Spel afgelopen — afwikkelen…', 'Afgewikkeld — {0} heeft gewonnen',
           'Geannuleerd — inzetten terugbetaald', 'de winnaar', '(winnaar)', '(verloren)',
           'Spel afgelopen — {0} heeft gewonnen. De handel is gesloten.'],
    'pl': ['Gra zakończona — rozliczanie…', 'Rozliczone — wygrał {0}',
           'Unieważnione — stawki zwrócone', 'zwycięzca', '(zwycięzca)', '(przegrany)',
           'Gra zakończona — wygrał {0}. Handel jest zamknięty.'],
    'pt': ['Partida encerrada — liquidando…', 'Liquidado — {0} venceu',
           'Anulado — apostas reembolsadas', 'o vencedor', '(vencedor)', '(perdedor)',
           'Partida encerrada — {0} venceu. As negociações estão encerradas.'],
    'ru': ['Игра окончена — расчёт…', 'Рассчитано — победил {0}',
           'Аннулировано — ставки возвращены', 'победитель', '(победитель)', '(проигравший)',
           'Игра окончена — победил {0}. Торги закрыты.'],
    'tr': ['Oyun bitti — hesaplanıyor…', 'Sonuçlandı — {0} kazandı',
           'İptal edildi — bahisler iade edildi', 'kazanan', '(kazanan)', '(kaybeden)',
           'Oyun bitti — {0} kazandı. İşlemler kapandı.'],
    'uk': ['Гру завершено — розрахунок…', 'Розраховано — переміг {0}',
           'Анульовано — ставки повернено', 'переможець', '(переможець)', '(програв)',
           'Гру завершено — переміг {0}. Торги закрито.'],
    'zh': ['比赛结束 — 结算中…', '已结算 — {0} 获胜',
           '已作废 — 赌注已退还', '获胜方', '（获胜）', '（失败）',
           '比赛结束 — {0} 获胜。交易已关闭。'],
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
        f.write(b'# Wager tab: market lifecycle / game-over indication\r\n')
        for key, value in zip(KEYS, values):
            f.write(('%s = %s' % (key, value)).encode('utf-8') + b'\r\n')
    print('appended:', os.path.basename(path))


append(os.path.join(here, 'messages.properties'), ENGLISH)
for locale, values in TRANSLATIONS.items():
    append(os.path.join(here, 'messages_%s.properties' % locale), values)
print('done')
