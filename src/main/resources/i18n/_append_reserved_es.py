#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Append reserved-slots Spanish translations to messages_es.properties with CRLF."""
import os

target = r"C:\Users\Alex\IdeaProjects\downlords-faf-client\src\main\resources\i18n\messages_es.properties"

lines = [
    "",
    "# Reserved slots (appended)",
    "reservedSlots.enable = Plazas reservadas",
    "reservedSlots.enable.tooltip = Reserva plazas concretas para jugadores elegidos. Otros no podrán unirse si eso expulsara a los reservados.",
    "reservedSlots.manage = Gestionar...",
    "reservedSlots.summary.empty = (aún no se han seleccionado jugadores)",
    "reservedSlots.summary.count = {0} jugador(es) reservado(s)",
    "reservedSlots.editor.title = Jugadores reservados",
    "reservedSlots.editor.add = Añadir jugador...",
    "reservedSlots.editor.addFriends = Añadir amigos",
    "reservedSlots.editor.addFromLastGame = De la última partida ({0})",
    "reservedSlots.editor.addFromLastGame.empty = De la última partida (ninguno)",
    "reservedSlots.editor.clear = Limpiar",
    "reservedSlots.editor.capacity = {0}/{1} reservados",
    "reservedSlots.editor.hostSuffix =  (anfitrión)",
    "reservedSlots.editor.statusOffline = Desconectado",
    "reservedSlots.editor.statusOnline = En línea",
    "reservedSlots.editor.statusInGame = En otra partida",
    "reservedSlots.editor.statusInThisGame = En esta partida",
    "reservedSlots.detail.title = Jugadores reservados",
    "reservedSlots.detail.empty = Todos los jugadores reservados se han unido",
    "reservedSlots.detail.edit = Editar...",
    "reservedSlots.detail.edit.tooltip = Editar la lista de jugadores reservados",
    "reservedSlots.leaveDialog.title = Abandonar partida",
    "reservedSlots.leaveDialog.message = Esta partida tiene plazas reservadas. ¿Quieres guardar tu sitio por si vuelves en los próximos 5 minutos?",
    "reservedSlots.leaveDialog.justLeave = Solo abandonar",
    "reservedSlots.leaveDialog.reserveAndLeave = Reservar mi plaza y abandonar (5 min)",
    "reservedSlots.exitedDialog.message = Acabas de salir de la sala de una partida con plazas reservadas. ¿Guardar tu sitio durante 5 minutos?",
    "reservedSlots.exitedDialog.dontReserve = No reservar",
    "reservedSlots.contextMenu.addToReserved = Reservar una plaza para este jugador",
    "reservedSlots.requestAccess.toastTitle = Solicitud de plaza reservada",
    "reservedSlots.requestAccess.toast = {0} pide unirse a tu partida con plazas reservadas",
    "reservedSlots.requestAccess.approve = Aprobar",
    "reservedSlots.requestAccess.dismiss = Descartar",
    "reservedSlots.knocking.tooltip = {0} está solicitando una plaza reservada",
    "reservedSlots.knocking.approve.tooltip = Gestionar la lista y dejarle entrar",
    "reservedSlots.knocking.dismiss.tooltip = Rechazar esta solicitud",
    "reservedSlots.denied.title = No puedes unirte — plazas reservadas",
    "reservedSlots.denied.fallback = Esta partida tiene plazas reservadas y ahora mismo no admite a jugadores no reservados.",
    "reservedSlots.denied.requestAccess = Solicitar acceso",
    "reservedSlots.denied.acknowledge = Aceptar",
    "reservedSlots.denied.alreadyRequested = Ya has solicitado acceso para esta partida. Esperando al anfitrión.",
    "reservedSlots.invitedToJoin = El anfitrión te ha invitado a unirte a su partida.",
    "reservedSlots.invitedToJoin.joinNow = Unirse ahora",
]

# Read last byte to decide if we need a leading CRLF
with open(target, "rb") as f:
    f.seek(-1, os.SEEK_END)
    last = f.read(1)

prefix = b""
if last != b"\n":
    prefix = b"\r\n"
# Final file ends with LF only; ensure we start cleanly on a new line.
# If last is \n (LF), the previous line is terminated; we just start appending on new line.

payload = prefix + ("\r\n".join(lines) + "\r\n").encode("utf-8")

with open(target, "ab") as f:
    f.write(payload)

print("Appended", len(lines), "lines (", len(payload), "bytes )")
