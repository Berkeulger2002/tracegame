import tempfile
import time
from pathlib import Path

from lives_test import ADMIN_COOKIE, call, check


def main():
    call("/admin/me", ADMIN_COOKIE)

    with tempfile.TemporaryDirectory() as directory:
        cookie = str(Path(directory) / "guest-cookies.txt")

        call("/auth/guest", cookie, method="POST", save_cookie=True)

        def resume():
            return call("/attempts/today/start", cookie, method="POST")

        state = resume()
        game = call(f"/admin/games/{state['gameId']}", ADMIN_COOKIE)
        steps = {step["id"]: step for step in game["steps"]}

        check(len(steps) > 0, "Oyunda en az bir adım olmalı.")

        last_request = None

        for _ in range(len(steps)):
            check(state["status"] == "PLAYING", "Oyun devam ediyor olmalı.")

            step = steps[state["currentStep"]["id"]]
            last_request = {
                "stepId": step["id"],
                "optionId": step["correctOptionId"],
                "version": state["version"],
            }

            call(
                "/attempts/today/answer",
                cookie,
                method="POST",
                body=last_request,
            )
            state = resume()

        check(state["status"] == "WON", "Tüm doğru cevaplarla kazanılmalı.")
        check(state["remainingLives"] == 3, "Üç can korunmalı.")
        check(state["mistakeCount"] == 0, "Yanlış sayısı sıfır olmalı.")
        check(state["currentStep"] is None, "Aktif adım kalmamalı.")
        check(
            len(state["completedSteps"]) == len(steps),
            "Tüm adımlar açılmış olmalı.",
            )
        check(
            all(step["mistakeCount"] == 0 for step in state["completedSteps"]),
            "Tamamlanan adımların yanlış sayısı sıfır olmalı.",
        )
        check(state["scoreFinal"] is True, "Oyun sonu puanı sabitlenmiş olmalı.")
        check(state["timer"]["running"] is False, "Süre durmuş olmalı.")

        duration = state["timer"]["activeDurationMillis"]
        check(duration >= 0, "Süre negatif olmamalı.")

        time.sleep(1)
        reopened = resume()

        for field in (
                "status",
                "remainingLives",
                "mistakeCount",
                "completedSteps",
                "score",
                "scoreFinal",
                "version",
        ):
            check(
                reopened[field] == state[field],
                f"Yeniden girişte {field} korunmalı.",
                )

        check(
            reopened["timer"]["activeDurationMillis"] == duration,
            "Oyun bittikten sonra süre artmamalı.",
            )
        check(
            reopened["timer"]["running"] is False,
            "Yeniden girişte sayaç başlamamalı.",
            )

        call(
            "/attempts/today/answer",
            cookie,
            method="POST",
            body=last_request,
            expected=409,
        )
        stats = call("/stats/me", cookie)

        check(stats["gamesPlayed"] == 1, "Bir tamamlanmış oyun olmalı.")
        check(stats["gamesWon"] == 1, "Bir kazanılan oyun olmalı.")
        check(stats["gamesLost"] == 0, "Kaybedilen oyun olmamalı.")
        check(stats["winRate"] == 100.0, "Kazanma oranı yüzde 100 olmalı.")
        check(stats["currentStreak"] == 1, "Güncel seri bir olmalı.")
        check(stats["bestStreak"] == 1, "En iyi seri bir olmalı.")

        print("DOĞRULANDI: Kazanılan oyun oyuncunun istatistiklerine yansıdı.")
        print("BAŞARILI: Kazanma, sonuç özeti ve sabit bitiş süresi doğrulandı.")
        print("NOT: Puan formülünün doğruluğu bu testin kapsamında değil.")

        feedback_path = "/attempts/today/feedback"

        feedback = call(feedback_path, cookie)
        check(feedback["rating"] is None, "İlk değerlendirme boş olmalı.")

        for rating in ("GOOD", "GOOD", "BAD"):
            saved = call(
                feedback_path,
                cookie,
                method="PUT",
                body={"rating": rating},
            )
            check(saved["rating"] == rating, "Değerlendirme kaydedilmeli.")

        feedback = call(feedback_path, cookie)
        check(
            feedback["rating"] == "BAD",
            "Son seçim yeniden okunduğunda korunmalı.",
            )

        call(
            feedback_path,
            cookie,
            method="PUT",
            body={"rating": "INVALID"},
            expected=400,
        )

        feedback = call(feedback_path, cookie)
        check(
            feedback["rating"] == "BAD",
            "Geçersiz istek kayıtlı seçimi değiştirmemeli.",
            )

        print("DOĞRULANDI: Geri bildirim kaydı, güncellemesi ve doğrulaması geçti.")
if __name__ == "__main__":
    main()