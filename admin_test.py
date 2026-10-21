import copy
import json
import subprocess
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

BASE = "http://localhost:8080/api"
COOKIE_FILE = "/tmp/trace-admin-cookies.txt"


def call(path, method="GET", body=None, expected=200):
    command = [
        "curl", "-sS",
        "--connect-timeout", "5",
        "--max-time", "20",
        "-X", method,
        "-b", COOKIE_FILE,
        "-H", "Content-Type: application/json",
        "-H", "X-Trace-Request: true",
        "-w", "\n%{http_code}",
        BASE + path
    ]

    payload = None

    if body is not None:
        command.extend(["--data-binary", "@-"])
        payload = json.dumps(body)

    result = subprocess.run(
        command,
        input=payload,
        capture_output=True,
        text=True
    )

    if result.returncode != 0:
        raise SystemExit("curl bağlantı hatası: " + result.stderr)

    raw_body, raw_status = result.stdout.rsplit("\n", 1)
    status = int(raw_status)

    if status != expected:
        print(f"HATA: {method} {path}")
        print(f"Beklenen HTTP {expected}, gelen HTTP {status}")
        print(raw_body)
        raise SystemExit(1)

    print(f"OK: {method} {path} → {status}")

    return json.loads(raw_body) if raw_body.strip() else None


# Admin oturumunu kontrol et.
admin = call("/admin/me")
assert admin["role"] == "ADMIN"
print("Admin oturumu doğrulandı.")

# Bugünün oyununu karşılaştırmak için sakla.
today_before = call("/games/today")

tomorrow = (
        datetime.now(ZoneInfo("Europe/Istanbul")).date()
        + timedelta(days=1)
).isoformat()

body = {
    "title": "Bir haberin okura yolculuğu",
    "langCode": "tr",
    "publishDate": tomorrow,
    "difficulty": "EASY",
    "startEvent": "Muhabir olay yerinde bilgi topladı.",
    "endEvent": "Okur yayımlanan haberi okudu.",
    "steps": [
        {
            "options": [
                "Haber metni editöre gönderildi.",
                "Okur henüz yazılmamış haberi paylaştı.",
                "Haber yayımlanmadan yorumlar silindi."
            ],
            "correctOptionIndex": 0
        },
        {
            "options": [
                "Henüz yaşanmamış olay için röportaj tamamlandı.",
                "Editör bilgileri kontrol edip metni düzenledi.",
                "Okur haber yayımlandıktan bir yıl önce yorum yaptı."
            ],
            "correctOptionIndex": 1
        },
        {
            "options": [
                "Muhabir henüz haberin konusunu seçmedi.",
                "Haberin bütün kaynakları olaydan önce silindi.",
                "Onaylanan haber web sitesinde yayımlandı."
            ],
            "correctOptionIndex": 2
        }
    ]
}

# Geçersiz doğru cevap reddedilmeli.
invalid = copy.deepcopy(body)
invalid["steps"][0]["correctOptionIndex"] = 3
call("/admin/games", "POST", invalid, expected=400)

# Yarına ait taslak oluştur.
game = call("/admin/games", "POST", body, expected=201)
assert game["status"] == "DRAFT"

game_id = game["id"]
print("Taslak oluşturuldu:", game_id, game["publishDate"])

# Aynı tarihe ikinci oyun eklenememeli.
call("/admin/games", "POST", body, expected=409)

# Taslağı düzenle.
old_timestamp = game["updatedAt"]

edit = copy.deepcopy(body)
edit["title"] = "Bir haberin okura yolculuğu — Günlük Trace"
edit["expectedUpdatedAt"] = old_timestamp

game = call(f"/admin/games/{game_id}", "PUT", edit)
assert game["title"] == edit["title"]
assert game["updatedAt"] != old_timestamp
print("Taslak düzenlendi.")

# Eski sürümle güncelleme reddedilmeli.
call(
    f"/admin/games/{game_id}",
    "PUT",
    edit,
    expected=409
)

# Güncel sürümü yayınla.
game = call(
    f"/admin/games/{game_id}/publish",
    "POST",
    {"expectedUpdatedAt": game["updatedAt"]}
)
assert game["status"] == "PUBLISHED"
print("Oyun yayınlandı:", game["publishDate"])

# Yayınlanmış oyun değiştirilememeli.
edit["expectedUpdatedAt"] = game["updatedAt"]

call(
    f"/admin/games/{game_id}",
    "PUT",
    edit,
    expected=409
)

# Detay endpoint'ini kontrol et.
detail = call(f"/admin/games/{game_id}")
assert detail["status"] == "PUBLISHED"

# Yayınlanmış oyunlar listesinde bulunmalı.
published = call(
    "/admin/games?status=PUBLISHED&page=0&size=100"
)
assert any(item["id"] == game_id for item in published)

# Bugünün oyunu değişmemiş olmalı.
today_after = call("/games/today")
assert today_before == today_after

print("BAŞARILI: Admin level yönetimi testi geçti.")
print("Bugünün oyunu korundu; yarının oyunu yayınlandı.")