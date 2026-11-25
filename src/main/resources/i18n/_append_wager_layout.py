#!/usr/bin/env python3
# Wager tab layout rework:
#  * buy is now entered as a SHARE count (like sell), so wager.buyPreview flips from
#    "= N shares" to "= N LP" -- an in-place value update, same as wager.sellPreview.
#  * new keys: buy/sell row labels + the anonymised (mine / other players') trade markers
#    and legend on the LIVE price chart.
# MessageFormat apostrophe rule: none of the {n}-bearing values below contain an apostrophe,
# so no doubling is needed; wager.legend.otherTrades has no placeholder and keeps a plain one.
import os
import re

# --- 1. in-place value update -------------------------------------------------
UPDATE_KEY = 'wager.buyPreview'
UPDATE_VALUE = '≈ {0} LP'          # identical in every locale, same as wager.sellPreview

# --- 2. appended keys ---------------------------------------------------------
KEYS = ['wager.buyLabel', 'wager.sharesUnit',
        'wager.marker.youBought', 'wager.marker.youSold',
        'wager.marker.otherBought', 'wager.marker.otherSold',
        'wager.legend.myTrades', 'wager.legend.otherTrades']

ENGLISH = ['Buy', 'shares',
           'You bought {0} shares → {1}', 'You sold {0} shares → {1}',
           'Another player bought {0} shares → {1}',
           'Another player sold {0} shares → {1}',
           'My trades', "Other players' trades"]

TRANSLATIONS = {
    'ca': ['Compra', 'accions',
           'Has comprat {0} accions → {1}', 'Has venut {0} accions → {1}',
           'Un altre jugador ha comprat {0} accions → {1}',
           'Un altre jugador ha venut {0} accions → {1}',
           'Les meves operacions', "Operacions d'altres jugadors"],
    'cs': ['Koupit', 'podílů',
           'Koupil jsi {0} podílů → {1}', 'Prodal jsi {0} podílů → {1}',
           'Jiný hráč koupil {0} podílů → {1}',
           'Jiný hráč prodal {0} podílů → {1}',
           'Mé obchody', 'Obchody ostatních hráčů'],
    'de': ['Kaufen', 'Anteile',
           'Du hast {0} Anteile gekauft → {1}', 'Du hast {0} Anteile verkauft → {1}',
           'Ein anderer Spieler hat {0} Anteile gekauft → {1}',
           'Ein anderer Spieler hat {0} Anteile verkauft → {1}',
           'Meine Trades', 'Trades anderer Spieler'],
    'es': ['Comprar', 'acciones',
           'Has comprado {0} acciones → {1}', 'Has vendido {0} acciones → {1}',
           'Otro jugador compró {0} acciones → {1}',
           'Otro jugador vendió {0} acciones → {1}',
           'Mis operaciones', 'Operaciones de otros jugadores'],
    'fr': ['Acheter', 'parts',
           'Vous avez acheté {0} parts → {1}', 'Vous avez vendu {0} parts → {1}',
           'Un autre joueur a acheté {0} parts → {1}',
           'Un autre joueur a vendu {0} parts → {1}',
           'Mes transactions', 'Transactions des autres joueurs'],
    'he': ['קנה', 'מניות',
           'קנית {0} מניות → {1}',
           'מכרת {0} מניות → {1}',
           'שחקן אחר קנה {0} מניות → {1}',
           'שחקן אחר מכר {0} מניות → {1}',
           'העסקאות שלי',
           'עסקאות של שחקנים אחרים'],
    'it': ['Compra', 'quote',
           'Hai comprato {0} quote → {1}', 'Hai venduto {0} quote → {1}',
           'Un altro giocatore ha comprato {0} quote → {1}',
           'Un altro giocatore ha venduto {0} quote → {1}',
           'Le mie operazioni', 'Operazioni di altri giocatori'],
    'ko': ['매수', '주',
           '내가 {0}주 매수 → {1}', '내가 {0}주 매도 → {1}',
           '다른 플레이어가 {0}주 매수 → {1}',
           '다른 플레이어가 {0}주 매도 → {1}',
           '내 거래', '다른 플레이어의 거래'],
    'nl': ['Kopen', 'aandelen',
           'Jij kocht {0} aandelen → {1}', 'Jij verkocht {0} aandelen → {1}',
           'Een andere speler kocht {0} aandelen → {1}',
           'Een andere speler verkocht {0} aandelen → {1}',
           'Mijn transacties', 'Transacties van andere spelers'],
    'pl': ['Kup', 'udziałów',
           'Kupiłeś {0} udziałów → {1}',
           'Sprzedałeś {0} udziałów → {1}',
           'Inny gracz kupił {0} udziałów → {1}',
           'Inny gracz sprzedał {0} udziałów → {1}',
           'Moje transakcje', 'Transakcje innych graczy'],
    'pt': ['Comprar', 'cotas',
           'Você comprou {0} cotas → {1}', 'Você vendeu {0} cotas → {1}',
           'Outro jogador comprou {0} cotas → {1}',
           'Outro jogador vendeu {0} cotas → {1}',
           'Minhas operações', 'Operações de outros jogadores'],
    'ru': ['Купить', 'долей',
           'Вы купили {0} долей → {1}',
           'Вы продали {0} долей → {1}',
           'Другой игрок купил {0} долей → {1}',
           'Другой игрок продал {0} долей → {1}',
           'Мои сделки',
           'Сделки других игроков'],
    'tr': ['Al', 'pay',
           '{0} pay aldınız → {1}', '{0} pay sattınız → {1}',
           'Başka bir oyuncu {0} pay aldı → {1}',
           'Başka bir oyuncu {0} pay sattı → {1}',
           'İşlemlerim', 'Diğer oyuncuların işlemleri'],
    'uk': ['Купити', 'часток',
           'Ви купили {0} часток → {1}',
           'Ви продали {0} часток → {1}',
           'Інший гравець купив {0} часток → {1}',
           'Інший гравець продав {0} часток → {1}',
           'Мої угоди',
           'Угоди інших гравців'],
    'zh': ['买入', '份',
           '你买入 {0} 份 → {1}', '你卖出 {0} 份 → {1}',
           '其他玩家买入 {0} 份 → {1}',
           '其他玩家卖出 {0} 份 → {1}',
           '我的交易', '其他玩家的交易'],
}

here = os.path.dirname(os.path.abspath(__file__))


def process(path, values, header):
    with open(path, 'rb') as f:
        data = f.read()

    # 1. in-place update of the flipped key (idempotent: writing the same value twice is a no-op)
    text = data.decode('utf-8')
    pattern = re.compile(r'^' + re.escape(UPDATE_KEY) + r'\s*=.*$', re.MULTILINE)
    if pattern.search(text):
        text = pattern.sub(UPDATE_KEY + ' = ' + UPDATE_VALUE, text)
        data = text.encode('utf-8')
        with open(path, 'wb') as f:
            f.write(data)
        print('updated %s in %s' % (UPDATE_KEY, os.path.basename(path)))

    # 2. append the new keys
    if KEYS[0].encode('utf-8') in data:
        print('skip append (already present):', os.path.basename(path))
        return
    with open(path, 'ab') as f:
        if not data.endswith(b'\n'):
            f.write(b'\r\n')
        f.write(('# %s\r\n' % header).encode('utf-8'))
        for key, value in zip(KEYS, values):
            f.write(('%s = %s' % (key, value)).encode('utf-8') + b'\r\n')
    print('appended:', os.path.basename(path))


process(os.path.join(here, 'messages.properties'), ENGLISH, 'Wager tab: buy/sell row + anonymised live trade markers')
for locale, values in TRANSLATIONS.items():
    process(os.path.join(here, 'messages_%s.properties' % locale), values,
            'Wager tab: buy/sell row + anonymised live trade markers')
print('done')
