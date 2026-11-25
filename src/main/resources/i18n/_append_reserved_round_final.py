#!/usr/bin/env python3
# Appends the 42 reservedSlots.* i18n keys to the 4 remaining locale files
# (zh, he, ko, tr) in UTF-8 with CRLF line endings.

import os

# Comment header to keep parity with messages.properties section divider.
HEADER = "\r\n# Reserved slots feature (TAF custom games)\r\n"

# Apostrophe-doubling note: MessageFormat doubles ' only in strings that
# contain {n} placeholders. Keys with {n} are:
#   reservedSlots.summary.count, .editor.addFromLastGame, .editor.capacity,
#   .requestAccess.toast, .knocking.tooltip
# None of the translations below use a literal apostrophe in those keys,
# so no doubling is required.

TRANSLATIONS = {
    "messages_zh.properties": {
        "reservedSlots.enable":                          "预留位置",
        "reservedSlots.enable.tooltip":                  "为选定的玩家保留特定位置。如果会挤掉预留玩家，其他人无法加入。",
        "reservedSlots.manage":                          "管理...",
        "reservedSlots.summary.empty":                   "（尚未选择玩家）",
        "reservedSlots.summary.count":                   "已预留 {0} 位玩家",
        "reservedSlots.editor.title":                    "预留玩家",
        "reservedSlots.editor.add":                      "添加玩家...",
        "reservedSlots.editor.addFriends":               "添加好友",
        "reservedSlots.editor.addFromLastGame":          "来自上一场游戏 ({0})",
        "reservedSlots.editor.addFromLastGame.empty":    "来自上一场游戏（无）",
        "reservedSlots.editor.clear":                    "清除",
        "reservedSlots.editor.capacity":                 "已预留 {0}/{1}",
        "reservedSlots.editor.hostSuffix":               " （房主）",
        "reservedSlots.editor.statusOffline":            "离线",
        "reservedSlots.editor.statusOnline":             "在线",
        "reservedSlots.editor.statusInGame":             "在其他游戏中",
        "reservedSlots.editor.statusInThisGame":         "在本场游戏中",
        "reservedSlots.detail.title":                    "预留玩家",
        "reservedSlots.detail.empty":                    "所有预留玩家均已加入",
        "reservedSlots.detail.edit":                     "编辑...",
        "reservedSlots.detail.edit.tooltip":             "编辑预留玩家列表",
        "reservedSlots.leaveDialog.title":               "离开游戏",
        "reservedSlots.leaveDialog.message":             "此游戏有预留位置。是否要保留你的位置以便在 5 分钟内返回？",
        "reservedSlots.leaveDialog.justLeave":           "直接离开",
        "reservedSlots.leaveDialog.reserveAndLeave":     "保留我的位置并离开（5 分钟）",
        "reservedSlots.exitedDialog.message":            "你刚刚离开了一场有预留位置的游戏的等候室。要保留位置 5 分钟吗？",
        "reservedSlots.exitedDialog.dontReserve":        "不保留",
        "reservedSlots.contextMenu.addToReserved":       "为此玩家预留位置",
        "reservedSlots.requestAccess.toastTitle":        "预留位置请求",
        "reservedSlots.requestAccess.toast":             "{0} 正请求加入你的预留位置游戏",
        "reservedSlots.requestAccess.approve":           "同意",
        "reservedSlots.requestAccess.dismiss":           "拒绝",
        "reservedSlots.knocking.tooltip":                "{0} 正请求一个预留位置",
        "reservedSlots.knocking.approve.tooltip":        "管理列表并允许进入",
        "reservedSlots.knocking.dismiss.tooltip":        "拒绝此请求",
        "reservedSlots.denied.title":                    "无法加入 — 预留位置",
        "reservedSlots.denied.fallback":                 "此游戏有预留位置，目前不接受非预留玩家加入。",
        "reservedSlots.denied.requestAccess":            "请求访问",
        "reservedSlots.denied.acknowledge":              "确定",
        "reservedSlots.denied.alreadyRequested":         "你已为此游戏请求过访问权限。请等待房主回应。",
        "reservedSlots.invitedToJoin":                   "房主邀请你加入他们的游戏。",
        "reservedSlots.invitedToJoin.joinNow":           "立即加入",
    },
    "messages_he.properties": {
        "reservedSlots.enable":                          "משבצות שמורות",
        "reservedSlots.enable.tooltip":                  "שמירה על משבצות מסוימות לשחקנים נבחרים. אחרים לא יוכלו להצטרף אם זה ידחק שחקנים שמורים החוצה.",
        "reservedSlots.manage":                          "ניהול...",
        "reservedSlots.summary.empty":                   "(עדיין לא נבחרו שחקנים)",
        "reservedSlots.summary.count":                   "{0} שחקנים שמורים",
        "reservedSlots.editor.title":                    "שחקנים שמורים",
        "reservedSlots.editor.add":                      "הוסף שחקן...",
        "reservedSlots.editor.addFriends":               "הוסף חברים",
        "reservedSlots.editor.addFromLastGame":          "מהמשחק האחרון ({0})",
        "reservedSlots.editor.addFromLastGame.empty":    "מהמשחק האחרון (אין)",
        "reservedSlots.editor.clear":                    "נקה",
        "reservedSlots.editor.capacity":                 "{0}/{1} שמורים",
        "reservedSlots.editor.hostSuffix":               " (מארח)",
        "reservedSlots.editor.statusOffline":            "לא מחובר",
        "reservedSlots.editor.statusOnline":             "מחובר",
        "reservedSlots.editor.statusInGame":             "במשחק אחר",
        "reservedSlots.editor.statusInThisGame":         "במשחק זה",
        "reservedSlots.detail.title":                    "שחקנים שמורים",
        "reservedSlots.detail.empty":                    "כל השחקנים השמורים הצטרפו",
        "reservedSlots.detail.edit":                     "עריכה...",
        "reservedSlots.detail.edit.tooltip":             "ערוך את רשימת השחקנים השמורים",
        "reservedSlots.leaveDialog.title":               "יציאה מהמשחק",
        "reservedSlots.leaveDialog.message":             "במשחק זה יש משבצות שמורות. האם ברצונך לשמור את מקומך למקרה שתחזור תוך 5 דקות?",
        "reservedSlots.leaveDialog.justLeave":           "פשוט צא",
        "reservedSlots.leaveDialog.reserveAndLeave":     "שמור את מקומי וצא (5 דקות)",
        "reservedSlots.exitedDialog.message":            "הרגע יצאת מחדר ההמתנה של משחק עם משבצות שמורות. לשמור את מקומך ל-5 דקות?",
        "reservedSlots.exitedDialog.dontReserve":        "אל תשמור",
        "reservedSlots.contextMenu.addToReserved":       "שמור משבצת לשחקן זה",
        "reservedSlots.requestAccess.toastTitle":        "בקשה למשבצת שמורה",
        "reservedSlots.requestAccess.toast":             "{0} מבקש להצטרף למשחק שלך עם משבצות שמורות",
        "reservedSlots.requestAccess.approve":           "אישור",
        "reservedSlots.requestAccess.dismiss":           "דחייה",
        "reservedSlots.knocking.tooltip":                "{0} מבקש משבצת שמורה",
        "reservedSlots.knocking.approve.tooltip":        "נהל את הרשימה והכנס אותם",
        "reservedSlots.knocking.dismiss.tooltip":        "דחה בקשה זו",
        "reservedSlots.denied.title":                    "לא ניתן להצטרף — משבצות שמורות",
        "reservedSlots.denied.fallback":                 "במשחק זה יש משבצות שמורות והוא אינו מקבל כעת מצטרפים שאינם שמורים.",
        "reservedSlots.denied.requestAccess":            "בקש גישה",
        "reservedSlots.denied.acknowledge":              "אישור",
        "reservedSlots.denied.alreadyRequested":         "כבר ביקשת גישה למשחק זה. ממתין למארח.",
        "reservedSlots.invitedToJoin":                   "המארח הזמין אותך להצטרף למשחק שלו.",
        "reservedSlots.invitedToJoin.joinNow":           "הצטרף עכשיו",
    },
    "messages_ko.properties": {
        "reservedSlots.enable":                          "예약 슬롯",
        "reservedSlots.enable.tooltip":                  "선택한 플레이어를 위해 특정 슬롯을 보유합니다. 예약된 플레이어를 밀어낼 경우 다른 사람은 참가할 수 없습니다.",
        "reservedSlots.manage":                          "관리...",
        "reservedSlots.summary.empty":                   "(아직 선택된 플레이어 없음)",
        "reservedSlots.summary.count":                   "{0}명 예약됨",
        "reservedSlots.editor.title":                    "예약된 플레이어",
        "reservedSlots.editor.add":                      "플레이어 추가...",
        "reservedSlots.editor.addFriends":               "친구 추가",
        "reservedSlots.editor.addFromLastGame":          "마지막 게임에서 ({0})",
        "reservedSlots.editor.addFromLastGame.empty":    "마지막 게임에서 (없음)",
        "reservedSlots.editor.clear":                    "지우기",
        "reservedSlots.editor.capacity":                 "{0}/{1} 예약됨",
        "reservedSlots.editor.hostSuffix":               " (호스트)",
        "reservedSlots.editor.statusOffline":            "오프라인",
        "reservedSlots.editor.statusOnline":             "온라인",
        "reservedSlots.editor.statusInGame":             "다른 게임 중",
        "reservedSlots.editor.statusInThisGame":         "이 게임 중",
        "reservedSlots.detail.title":                    "예약된 플레이어",
        "reservedSlots.detail.empty":                    "모든 예약된 플레이어가 참가했습니다",
        "reservedSlots.detail.edit":                     "편집...",
        "reservedSlots.detail.edit.tooltip":             "예약된 플레이어 목록 편집",
        "reservedSlots.leaveDialog.title":               "게임 나가기",
        "reservedSlots.leaveDialog.message":             "이 게임에는 예약 슬롯이 있습니다. 5분 이내에 돌아올 경우를 대비해 자리를 보유하시겠습니까?",
        "reservedSlots.leaveDialog.justLeave":           "그냥 나가기",
        "reservedSlots.leaveDialog.reserveAndLeave":     "자리 예약하고 나가기 (5분)",
        "reservedSlots.exitedDialog.message":            "예약 슬롯이 있는 게임의 대기실에서 나가셨습니다. 5분 동안 자리를 보유하시겠습니까?",
        "reservedSlots.exitedDialog.dontReserve":        "예약하지 않음",
        "reservedSlots.contextMenu.addToReserved":       "이 플레이어를 위한 슬롯 예약",
        "reservedSlots.requestAccess.toastTitle":        "예약 슬롯 요청",
        "reservedSlots.requestAccess.toast":             "{0}님이 예약 슬롯 게임 참가를 요청합니다",
        "reservedSlots.requestAccess.approve":           "승인",
        "reservedSlots.requestAccess.dismiss":           "거부",
        "reservedSlots.knocking.tooltip":                "{0}님이 예약 슬롯을 요청 중입니다",
        "reservedSlots.knocking.approve.tooltip":        "목록을 관리하고 입장 허용",
        "reservedSlots.knocking.dismiss.tooltip":        "이 요청 거부",
        "reservedSlots.denied.title":                    "참가할 수 없음 — 예약 슬롯",
        "reservedSlots.denied.fallback":                 "이 게임에는 예약 슬롯이 있으며 현재 비예약 참가자를 받지 않습니다.",
        "reservedSlots.denied.requestAccess":            "액세스 요청",
        "reservedSlots.denied.acknowledge":              "확인",
        "reservedSlots.denied.alreadyRequested":         "이 게임에 대해 이미 액세스를 요청했습니다. 호스트의 응답을 기다리는 중입니다.",
        "reservedSlots.invitedToJoin":                   "호스트가 게임에 초대했습니다.",
        "reservedSlots.invitedToJoin.joinNow":           "지금 참가",
    },
    "messages_tr.properties": {
        "reservedSlots.enable":                          "Ayrılmış slotlar",
        "reservedSlots.enable.tooltip":                  "Seçilen oyuncular için belirli slotları tutar. Ayrılmış oyuncuları dışarı itecekse diğerleri katılamaz.",
        "reservedSlots.manage":                          "Yönet...",
        "reservedSlots.summary.empty":                   "(henüz oyuncu seçilmedi)",
        "reservedSlots.summary.count":                   "{0} oyuncu ayrıldı",
        "reservedSlots.editor.title":                    "Ayrılmış oyuncular",
        "reservedSlots.editor.add":                      "Oyuncu ekle...",
        "reservedSlots.editor.addFriends":               "Arkadaşları ekle",
        "reservedSlots.editor.addFromLastGame":          "Son oyundan ({0})",
        "reservedSlots.editor.addFromLastGame.empty":    "Son oyundan (yok)",
        "reservedSlots.editor.clear":                    "Temizle",
        "reservedSlots.editor.capacity":                 "{0}/{1} ayrıldı",
        "reservedSlots.editor.hostSuffix":               " (sunucu)",
        "reservedSlots.editor.statusOffline":            "Çevrimdışı",
        "reservedSlots.editor.statusOnline":             "Çevrimiçi",
        "reservedSlots.editor.statusInGame":             "Başka bir oyunda",
        "reservedSlots.editor.statusInThisGame":         "Bu oyunda",
        "reservedSlots.detail.title":                    "Ayrılmış oyuncular",
        "reservedSlots.detail.empty":                    "Tüm ayrılmış oyuncular katıldı",
        "reservedSlots.detail.edit":                     "Düzenle...",
        "reservedSlots.detail.edit.tooltip":             "Ayrılmış oyuncular listesini düzenle",
        "reservedSlots.leaveDialog.title":               "Oyundan ayrıl",
        "reservedSlots.leaveDialog.message":             "Bu oyunda ayrılmış slotlar var. 5 dakika içinde dönmeniz durumunda yerinizi tutmak ister misiniz?",
        "reservedSlots.leaveDialog.justLeave":           "Sadece ayrıl",
        "reservedSlots.leaveDialog.reserveAndLeave":     "Yerimi ayır ve ayrıl (5 dk)",
        "reservedSlots.exitedDialog.message":            "Ayrılmış slotlu bir oyunun bekleme odasından az önce ayrıldınız. Yerinizi 5 dakika tutalım mı?",
        "reservedSlots.exitedDialog.dontReserve":        "Ayırma",
        "reservedSlots.contextMenu.addToReserved":       "Bu oyuncu için bir slot ayır",
        "reservedSlots.requestAccess.toastTitle":        "Ayrılmış slot isteği",
        "reservedSlots.requestAccess.toast":             "{0} ayrılmış slotlu oyununuza katılmak istiyor",
        "reservedSlots.requestAccess.approve":           "Onayla",
        "reservedSlots.requestAccess.dismiss":           "Reddet",
        "reservedSlots.knocking.tooltip":                "{0} ayrılmış bir slot istiyor",
        "reservedSlots.knocking.approve.tooltip":        "Listeyi yönet ve girmesine izin ver",
        "reservedSlots.knocking.dismiss.tooltip":        "Bu isteği reddet",
        "reservedSlots.denied.title":                    "Katılınamıyor — ayrılmış slotlar",
        "reservedSlots.denied.fallback":                 "Bu oyunda ayrılmış slotlar var ve şu anda ayrılmamış katılımcıları kabul etmiyor.",
        "reservedSlots.denied.requestAccess":            "Erişim iste",
        "reservedSlots.denied.acknowledge":              "Tamam",
        "reservedSlots.denied.alreadyRequested":         "Bu oyun için zaten erişim istediniz. Sunucunun yanıtı bekleniyor.",
        "reservedSlots.invitedToJoin":                   "Sunucu sizi oyununa katılmaya davet etti.",
        "reservedSlots.invitedToJoin.joinNow":           "Şimdi katıl",
    },
}

# Preserves the same key order as messages.properties.
ORDERED_KEYS = [
    "reservedSlots.enable",
    "reservedSlots.enable.tooltip",
    "reservedSlots.manage",
    "reservedSlots.summary.empty",
    "reservedSlots.summary.count",
    "reservedSlots.editor.title",
    "reservedSlots.editor.add",
    "reservedSlots.editor.addFriends",
    "reservedSlots.editor.addFromLastGame",
    "reservedSlots.editor.addFromLastGame.empty",
    "reservedSlots.editor.clear",
    "reservedSlots.editor.capacity",
    "reservedSlots.editor.hostSuffix",
    "reservedSlots.editor.statusOffline",
    "reservedSlots.editor.statusOnline",
    "reservedSlots.editor.statusInGame",
    "reservedSlots.editor.statusInThisGame",
    "reservedSlots.detail.title",
    "reservedSlots.detail.empty",
    "reservedSlots.detail.edit",
    "reservedSlots.detail.edit.tooltip",
    "reservedSlots.leaveDialog.title",
    "reservedSlots.leaveDialog.message",
    "reservedSlots.leaveDialog.justLeave",
    "reservedSlots.leaveDialog.reserveAndLeave",
    "reservedSlots.exitedDialog.message",
    "reservedSlots.exitedDialog.dontReserve",
    "reservedSlots.contextMenu.addToReserved",
    "reservedSlots.requestAccess.toastTitle",
    "reservedSlots.requestAccess.toast",
    "reservedSlots.requestAccess.approve",
    "reservedSlots.requestAccess.dismiss",
    "reservedSlots.knocking.tooltip",
    "reservedSlots.knocking.approve.tooltip",
    "reservedSlots.knocking.dismiss.tooltip",
    "reservedSlots.denied.title",
    "reservedSlots.denied.fallback",
    "reservedSlots.denied.requestAccess",
    "reservedSlots.denied.acknowledge",
    "reservedSlots.denied.alreadyRequested",
    "reservedSlots.invitedToJoin",
    "reservedSlots.invitedToJoin.joinNow",
]

BASE = os.path.dirname(os.path.abspath(__file__))

for fname, entries in TRANSLATIONS.items():
    path = os.path.join(BASE, fname)
    assert len(entries) == 42, f"{fname}: expected 42 entries, got {len(entries)}"
    # Sanity: every ordered key must be present
    for k in ORDERED_KEYS:
        assert k in entries, f"{fname}: missing key {k}"

    # Read existing file as raw bytes to detect trailing newline.
    with open(path, "rb") as fh:
        existing = fh.read()

    # Verify these keys aren't already present (avoid double-append).
    existing_str = existing.decode("utf-8", errors="replace")
    if "reservedSlots." in existing_str:
        print(f"SKIP {fname}: already contains reservedSlots.* entries")
        continue

    # Ensure file ends with a newline before our block.
    prefix = b""
    if existing and not existing.endswith(b"\n"):
        prefix = b"\r\n"

    # Build the block.
    lines = [HEADER]
    for k in ORDERED_KEYS:
        lines.append(f"{k} = {entries[k]}\r\n")
    block = "".join(lines).encode("utf-8")

    with open(path, "ab") as fh:
        fh.write(prefix + block)

    print(f"OK   {fname}: appended 42 entries ({len(block)} bytes)")
