# Mirrors replay.stillSealed into all 15 locale files.
#
# Shown when a local demo is still encrypted and the server declined to release the key -
# usually because the game has not ended yet, or the recovery delay has not elapsed.
#
# NOTE the value contains {0}, so per the skill's MessageFormat rule every literal
# apostrophe in a translation must be DOUBLED ('' ) or MessageFormat will eat it.
import io
import os

KEY = 'replay.stillSealed'

VALUES = {
    'ca': "Aquesta repeticio encara esta xifrada i no s''ha pogut recuperar: {0}",
    'cs': "Tento zaznam je stale zasifrovany a nepodarilo se jej obnovit: {0}",
    'de': "Diese Aufzeichnung ist noch verschluesselt und konnte nicht wiederhergestellt werden: {0}",
    'es': "Esta repeticion sigue cifrada y no se ha podido recuperar: {0}",
    'fr': "Cette partie enregistree est encore chiffree et n''a pas pu etre recuperee : {0}",
    'he': "הקלטה עדיין מוצפנת ולא ניתן היה לשחזר אותה: {0}",
    'it': "Questo replay e ancora criptato e non e stato possibile recuperarlo: {0}",
    'ko': "이 리플레이는 아직 암호화되어 있으며 복구할 수 없습니다: {0}",
    'nl': "Deze replay is nog versleuteld en kon niet worden hersteld: {0}",
    'pl': "Ta powtorka jest wciaz zaszyfrowana i nie udalo sie jej odzyskac: {0}",
    'pt': "Esta repeticao ainda esta encriptada e nao foi possivel recupera-la: {0}",
    'ru': "Эта запись всё ещё зашифрована, восстановить её не удалось: {0}",
    'tr': "Bu tekrar hala sifreli ve kurtarilamadi: {0}",
    'uk': "Цей запис ще зашифровано, відновити його не вдалося: {0}",
    'zh': "此回放仍已加密，无法恢复：{0}",
}

here = os.path.dirname(os.path.abspath(__file__))

for loc, value in VALUES.items():
    path = os.path.join(here, 'messages_%s.properties' % loc)
    if not os.path.exists(path):
        print('SKIP (missing) %s' % path)
        continue
    data = io.open(path, 'rb').read()
    if KEY.encode('utf-8') in data:
        print('SKIP (already present) %s' % loc)
        continue
    out = b''
    if data and not data.endswith(b'\n'):
        out += b'\r\n'
    out += b'# demo seal recovery\r\n'
    out += (u'%s = %s\r\n' % (KEY, value)).encode('utf-8')
    with io.open(path, 'ab') as fh:
        fh.write(out)
    print('appended %s' % loc)
