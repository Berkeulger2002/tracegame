import tempfile
from pathlib import Path

from lives_test import ADMIN_COOKIE, call, check


def main():
    call("/admin/me", ADMIN_COOKIE)

    with tempfile.TemporaryDirectory() as directory:
        cookie = str(Path(directory) / "guest-cookies.txt")

        call("/auth/guest", cookie, method="POST", save_cookie=True)

        def resume():
            return call("/attempts/today/start", cookie, method="POST")

        def answer(state, option_id):
            call(
                "/attempts/today/answer",
                cookie,
                method="POST",
                body={
                    "stepId": state["currentStep"]["id"],
                    "optionId": option_id,
                    "version": state["version"],
                },
            )
            return resume()

        state = resume()
        game = call(f"/admin/games/{state['gameId']}", ADMIN_COOKIE)

        check(len(game["steps"]) >= 2, "Test için en az iki adım gerekli.")
        check(state["remainingLives"] == 3, "Başlangıçta üç can olmalı.")
        check(state["completedSteps"] == [], "Başlangıçta açılmış adım olmamalı.")

        first = next(
            step for step in game["steps"]
            if step["id"] == state["currentStep"]["id"]
        )
        wrong_options = [
            option["id"] for option in first["options"]
            if option["id"] != first["correctOptionId"]
        ]
        check(len(wrong_options) == 2, "İlk adımda iki yanlış seçenek olmalı.")

        for expected_lives, option_id in zip((2, 1), wrong_options):
            state = answer(state, option_id)
            check(
                state["remainingLives"] == expected_lives,
                f"Kalan can {expected_lives} olmalı.",
                )
            check(
                state["completedSteps"] == [],
                "Yanlış cevap adımı veya açıklamasını açmamalı.",
                )

        state = answer(state, first["correctOptionId"])

        check(state["remainingLives"] == 1, "Adım geçince can yenilenmemeli.")
        check(len(state["completedSteps"]) == 1, "Bir adım açılmış olmalı.")

        completed = state["completedSteps"][0]
        check(completed["id"] == first["id"], "Doğru adım açılmalı.")
        check(completed["mistakeCount"] == 2, "İlk adımın yanlış sayısı iki olmalı.")
        check(
            state["currentStepMistakeCount"] == 0,
            "Yeni adımın yanlış sayısı sıfır olmalı.",
            )
        check(
            completed["explanation"] == (first.get("explanation") or ""),
            "Açılan açıklama kayıtlı metinle aynı olmalı.",
            )
        print("DOĞRULANDI: İki yanlış sonrası doğru adım açıldı; bir can kaldı.")

        reopened = resume()
        check(
            reopened["completedSteps"] == state["completedSteps"],
            "Yeniden girişte açılmış adım ve yanlış sayısı korunmalı.",
            )
        check(reopened["remainingLives"] == 1, "Yeniden girişte bir can kalmalı.")

        second = next(
            step for step in game["steps"]
            if step["id"] == reopened["currentStep"]["id"]
        )
        wrong_option = next(
            option["id"] for option in second["options"]
            if option["id"] != second["correctOptionId"]
        )
        lost = answer(reopened, wrong_option)

        check(lost["status"] == "LOST", "Toplam üçüncü yanlışta elenmeli.")
        check(lost["remainingLives"] == 0, "Elendiğinde can sıfır olmalı.")
        check(lost["mistakeCount"] == 3, "Toplam yanlış sayısı üç olmalı.")
        check(lost["currentStep"] is None, "Elendikten sonra aktif adım olmamalı.")
        check(
            lost["completedSteps"] == reopened["completedSteps"],
            "Elendiğinde yalnızca doğru tamamlanan adım açık kalmalı.",
            )

        reopened_lost = resume()
        check(reopened_lost["status"] == "LOST", "Elendiği durum korunmalı.")
        check(reopened_lost["remainingLives"] == 0, "Can tekrar verilmemeli.")
        check(
            reopened_lost["completedSteps"] == lost["completedSteps"],
            "Elendikten sonra yeniden girişte adım özeti korunmalı.",
            )
        stats = call("/stats/me", cookie)

        check(stats["gamesPlayed"] == 1, "Bir tamamlanmış oyun olmalı.")
        check(stats["gamesWon"] == 0, "Kazanılan oyun olmamalı.")
        check(stats["gamesLost"] == 1, "Bir kaybedilen oyun olmalı.")
        check(stats["winRate"] == 0.0, "Kazanma oranı sıfır olmalı.")
        check(stats["currentStreak"] == 0, "Galibiyet serisi başlamamalı.")
        check(stats["bestStreak"] == 0, "En iyi seri sıfır olmalı.")

        print("DOĞRULANDI: Kaybedilen oyun istatistiklere doğru yansıdı.")
        print("BAŞARILI: Kalan can ve adım bazında yanlış sayısı doğrulandı.")

        if not completed["explanation"]:
            print("NOT: İlk adımın açıklaması boş; dolu metin dönüşü doğrulanmadı.")


if __name__ == "__main__":
    main()