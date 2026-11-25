#!/usr/bin/env python3
# Appends round-6 French translations (reserved slots) to messages_fr.properties.
# Doubles apostrophes only for values whose key uses {n} placeholders (MessageFormat rule).

content = '''
# Reserved slots (appended round 6)
reservedSlots.enable = Slots réservés
reservedSlots.enable.tooltip = Garde certains slots pour des joueurs choisis. Les autres ne peuvent pas rejoindre si cela évincerait un joueur réservé.
reservedSlots.manage = Gérer...
reservedSlots.summary.empty = (aucun joueur sélectionné pour l'instant)
reservedSlots.summary.count = {0} joueur(s) réservé(s)
reservedSlots.editor.title = Joueurs réservés
reservedSlots.editor.add = Ajouter un joueur...
reservedSlots.editor.addFriends = Ajouter des amis
reservedSlots.editor.addFromLastGame = Depuis la dernière partie ({0})
reservedSlots.editor.addFromLastGame.empty = Depuis la dernière partie (aucun)
reservedSlots.editor.clear = Vider
reservedSlots.editor.capacity = {0}/{1} réservés
reservedSlots.editor.hostSuffix =  (hôte)
reservedSlots.editor.statusOffline = Hors ligne
reservedSlots.editor.statusOnline = En ligne
reservedSlots.editor.statusInGame = Dans une autre partie
reservedSlots.editor.statusInThisGame = Dans cette partie
reservedSlots.detail.title = Joueurs réservés
reservedSlots.detail.empty = Tous les joueurs réservés ont rejoint
reservedSlots.detail.edit = Modifier...
reservedSlots.detail.edit.tooltip = Modifier la liste des joueurs réservés
reservedSlots.leaveDialog.title = Quitter la partie
reservedSlots.leaveDialog.message = Cette partie a des slots réservés. Veux-tu garder ta place au cas où tu reviendrais dans les 5 minutes ?
reservedSlots.leaveDialog.justLeave = Quitter simplement
reservedSlots.leaveDialog.reserveAndLeave = Réserver ma place et partir (5 min)
reservedSlots.exitedDialog.message = Tu viens de quitter le salon d'une partie avec slots réservés. Garder ta place pendant 5 minutes ?
reservedSlots.exitedDialog.dontReserve = Ne pas réserver
reservedSlots.contextMenu.addToReserved = Réserver un slot pour ce joueur
reservedSlots.requestAccess.toastTitle = Demande de slot réservé
reservedSlots.requestAccess.toast = {0} demande à rejoindre ta partie à slots réservés
reservedSlots.requestAccess.approve = Approuver
reservedSlots.requestAccess.dismiss = Rejeter
reservedSlots.knocking.tooltip = {0} demande un slot réservé
reservedSlots.knocking.approve.tooltip = Gérer la liste et le laisser entrer
reservedSlots.knocking.dismiss.tooltip = Refuser cette demande
reservedSlots.denied.title = Impossible de rejoindre — slots réservés
reservedSlots.denied.fallback = Cette partie a des slots réservés et n'accepte pas de joueurs non réservés pour le moment.
reservedSlots.denied.requestAccess = Demander l'accès
reservedSlots.denied.acknowledge = OK
reservedSlots.denied.alreadyRequested = Tu as déjà demandé l'accès à cette partie. En attente de l'hôte.
reservedSlots.invitedToJoin = L'hôte t'a invité à rejoindre sa partie.
reservedSlots.invitedToJoin.joinNow = Rejoindre maintenant
'''

placeholder_keys = {
    'reservedSlots.summary.count',
    'reservedSlots.editor.addFromLastGame',
    'reservedSlots.editor.capacity',
    'reservedSlots.requestAccess.toast',
    'reservedSlots.knocking.tooltip',
}

out_lines = []
for line in content.split('\n'):
    if '=' in line and not line.lstrip().startswith('#'):
        key, _, value = line.partition('=')
        if key.strip() in placeholder_keys:
            value = value.replace("'", "''")
            line = key + '=' + value
    out_lines.append(line)

body = '\n'.join(out_lines).lstrip('\n')

target = r'C:\Users\Alex\IdeaProjects\downlords-faf-client\src\main\resources\i18n\messages_fr.properties'
with open(target, 'ab') as f:
    f.write(b'\r\n')  # start a fresh line; existing file ends with bare LF
    for ln in body.split('\n'):
        f.write(ln.encode('utf-8') + b'\r\n')

print('done')
