# -*- coding: utf-8 -*-
import os

path = r"C:\Users\Alex\IdeaProjects\downlords-faf-client\src\main\resources\i18n\messages_cs.properties"

lines = [
    "",
    "# Reserved slots feature",
    "reservedSlots.enable = Rezervovaná místa",
    "reservedSlots.enable.tooltip = Drží konkrétní místa pro vybrané hráče. Ostatní se nemohou připojit, pokud by tím rezervovaní hráči byli vytlačeni.",
    "reservedSlots.manage = Spravovat...",
    "reservedSlots.summary.empty = (zatím nejsou vybráni žádní hráči)",
    "reservedSlots.summary.count = Rezervováno hráčů: {0}",
    "reservedSlots.editor.title = Rezervovaní hráči",
    "reservedSlots.editor.add = Přidat hráče...",
    "reservedSlots.editor.addFriends = Přidat přátele",
    "reservedSlots.editor.addFromLastGame = Z poslední hry ({0})",
    "reservedSlots.editor.addFromLastGame.empty = Z poslední hry (žádní)",
    "reservedSlots.editor.clear = Vymazat",
    "reservedSlots.editor.capacity = {0}/{1} rezervováno",
    "reservedSlots.editor.hostSuffix =  (hostitel)",
    "reservedSlots.editor.statusOffline = Offline",
    "reservedSlots.editor.statusOnline = Online",
    "reservedSlots.editor.statusInGame = V jiné hře",
    "reservedSlots.editor.statusInThisGame = V této hře",
    "reservedSlots.detail.title = Rezervovaní hráči",
    "reservedSlots.detail.empty = Všichni rezervovaní hráči se připojili",
    "reservedSlots.detail.edit = Upravit...",
    "reservedSlots.detail.edit.tooltip = Upravit seznam rezervovaných hráčů",
    "reservedSlots.leaveDialog.title = Opustit hru",
    "reservedSlots.leaveDialog.message = Tato hra má rezervovaná místa. Chcete si podržet své místo pro případ, že se vrátíte do 5 minut?",
    "reservedSlots.leaveDialog.justLeave = Pouze odejít",
    "reservedSlots.leaveDialog.reserveAndLeave = Rezervovat mé místo a odejít (5 min)",
    "reservedSlots.exitedDialog.message = Právě jsi opustil bojovou místnost hry s rezervovanými místy. Podržet si místo na 5 minut?",
    "reservedSlots.exitedDialog.dontReserve = Nerezervovat",
    "reservedSlots.contextMenu.addToReserved = Rezervovat místo pro tohoto hráče",
    "reservedSlots.requestAccess.toastTitle = Žádost o rezervované místo",
    "reservedSlots.requestAccess.toast = {0} žádá o připojení do tvé hry s rezervovanými místy",
    "reservedSlots.requestAccess.approve = Schválit",
    "reservedSlots.requestAccess.dismiss = Zamítnout",
    "reservedSlots.knocking.tooltip = {0} žádá o rezervované místo",
    "reservedSlots.knocking.approve.tooltip = Spravovat seznam a vpustit hráče",
    "reservedSlots.knocking.dismiss.tooltip = Odmítnout tuto žádost",
    "reservedSlots.denied.title = Nelze se připojit — rezervovaná místa",
    "reservedSlots.denied.fallback = Tato hra má rezervovaná místa a momentálně nepřijímá nerezervované hráče.",
    "reservedSlots.denied.requestAccess = Požádat o přístup",
    "reservedSlots.denied.acknowledge = OK",
    "reservedSlots.denied.alreadyRequested = Pro tuto hru jsi již požádal o přístup. Čeká se na hostitele.",
    "reservedSlots.invitedToJoin = Hostitel tě pozval, aby ses připojil do jeho hry.",
    "reservedSlots.invitedToJoin.joinNow = Připojit nyní",
]

# Read existing content as bytes to preserve original line endings
with open(path, "rb") as f:
    data = f.read()

# Ensure file ends with a newline; normalize trailing LF-only to CRLF for clean append
if not data.endswith(b"\n"):
    data += b"\r\n"
elif data.endswith(b"\n") and not data.endswith(b"\r\n"):
    # Replace lone trailing LF with CRLF to be consistent
    data = data[:-1] + b"\r\n"

# Build appended block with CRLF
appended = "\r\n".join(lines) + "\r\n"
data += appended.encode("utf-8")

with open(path, "wb") as f:
    f.write(data)

print("Appended", len(lines), "lines")
