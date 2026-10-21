import copy
import sys
from datetime import date

from lives_test import call, check, ADMIN_COOKIE


def main():
    target_date = sys.argv[1] if len(sys.argv) > 1 else "2026-10-22"
    date.fromisoformat(target_date)

    admin = call("/admin/me", ADMIN_COOKIE)
    check(admin["role"] == "ADMIN", "Admin oturumu gerekli.")

    # Hedef tarihte oyun varsa mevcut kayda dokunma.
    page = 0
    while True:
        games = call(
            f"/admin/games?page={page}&size=100",
            ADMIN_COOKIE
        )

        if any(game["publishDate"] == target_date for game in games):
            raise SystemExit(
                f"{target_date} tarihinde zaten oyun var. "
                "Mevcut oyun değiştirilmedi. Başka bir gelecek tarih kullan."
            )

        if len(games) < 100:
            break

        page += 1

    body = {
        "title": "OKU açıklama testi",
        "langCode": "tr",
        "publishDate": target_date,
        "difficulty": "EASY",
        "startEvent": "Muhabir olay yerinde bilgi topladı.",
        "endEvent": "Okur haberi okudu.",
        "startExplanation": (
            "Muhabir, olayın ayrıntılarını öğrenmek için "
            "görgü tanıklarıyla konuştu ve not aldı."
        ),
        "endExplanation": (
            "Okur, yayımlanan haber üzerinden olay hakkında bilgi edindi."
        ),
        "steps": [
            {
                "options": [
                    "Muhabir haber metnini hazırladı.",
                    "Henüz yazılmamış haber okundu.",
                    "Haber yayımlanmadan yorum yapıldı."
                ],
                "correctOptionIndex": 0,
                "explanation": (
                    "Toplanan bilgiler haber metnine dönüştürüldü. "
                    "Olayın kim, ne, nerede ve ne zaman soruları yanıtlandı."
                )
            },
            {
                "options": [
                    "Okur henüz hazırlanmayan metni paylaştı.",
                    "Editör metni kontrol ederek yayımladı.",
                    "Haberin konusu olaydan sonra iptal edildi."
                ],
                "correctOptionIndex": 1,
                "explanation": (
                    "Editör kaynakları ve metnin doğruluğunu kontrol etti. "
                    "Düzenlenen haber okurların erişimine açıldı."
                )
            }
        ]
    }

    def verify(game, expected):
        check(game["status"] == "DRAFT", "Oyun taslak kalmalı.")
        check(
            game["startExplanation"] == expected["startExplanation"],
            "Başlangıç açıklaması eşleşmiyor."
        )
        check(
            game["endExplanation"] == expected["endExplanation"],
            "Bitiş açıklaması eşleşmiyor."
        )
        check(
            len(game["steps"]) == len(expected["steps"]),
            "Aşama sayısı eşleşmiyor."
        )

        for stored, sent in zip(game["steps"], expected["steps"]):
            check(
                stored["explanation"] == sent["explanation"],
                f"{stored['position']}. adım açıklaması eşleşmiyor."
            )

    game = call(
        "/admin/games",
        ADMIN_COOKIE,
        method="POST",
        body=body,
        expected=201
    )
    game_id = game["id"]
    print("Oluşturulan taslak:", game_id, target_date)
    verify(game, body)

    detail = call(f"/admin/games/{game_id}", ADMIN_COOKIE)
    verify(detail, body)
    print("DOĞRULANDI: Dolu açıklamalar kaydedildi ve tekrar okundu.")

    edit = copy.deepcopy(body)
    edit["expectedUpdatedAt"] = detail["updatedAt"]
    edit["startExplanation"] += " Başlangıç açıklaması güncellendi."
    edit["endExplanation"] += " Bitiş açıklaması güncellendi."

    for step in edit["steps"]:
        step["explanation"] += " Adım açıklaması güncellendi."

    updated = call(
        f"/admin/games/{game_id}",
        ADMIN_COOKIE,
        method="PUT",
        body=edit
    )
    verify(updated, edit)

    detail = call(f"/admin/games/{game_id}", ADMIN_COOKIE)
    verify(detail, edit)

    print("DOĞRULANDI: Açıklama güncellemeleri kaydedildi.")
    print("\nBAŞARILI: Dolu açıklamaların kayıt ve güncelleme testi geçti.")
    print("Taslak yayınlanmadı; admin listesinde kalacak.")
    print("Oyuncuya dolu metin dönüşü bu testin kapsamına dahil değil.")


if __name__ == "__main__":
    main()