import tempfile
from pathlib import Path

from lives_test import call, check, ADMIN_COOKIE


def main():
    admin = call("/admin/me", ADMIN_COOKIE)
    check(admin["role"] == "ADMIN", "Admin oturumu gerekli.")

    with tempfile.TemporaryDirectory(prefix="trace-read-") as folder:
        cookie = Path(folder) / "guest-cookies.txt"
        cookie.touch()

        call(
            "/auth/guest",
            cookie,
            method="POST",
            save_cookie=True
        )

        def start():
            return call(
                "/attempts/today/start",
                cookie,
                method="POST"
            )

        state = start()
        game = call(
            f"/admin/games/{state['gameId']}",
            ADMIN_COOKIE
        )

        check(state["status"] == "PLAYING", "Yeni oyun başlamadı.")
        check(
            state["completedSteps"] == [],
            "Başlangıçta ara adımlar açık olmamalı."
        )

        for field in ("startExplanation", "endExplanation"):
            check(
                isinstance(state[field], str),
                f"{field} metin olarak dönmeli."
            )
            check(
                state[field] == (game.get(field) or ""),
                f"{field} oyun kaydıyla eşleşmiyor."
            )

        print("DOĞRULANDI: Başlangıç/bitiş açıklama alanları geliyor.")

        definitions = {step["id"]: step for step in game["steps"]}
        expected_completed = []

        def check_completed(current):
            check(
                current["completedSteps"] == expected_completed,
                "Açılan adımlar veya açıklamaları beklenenle eşleşmiyor."
            )

            active = current["currentStep"]

            if active is not None:
                check(
                    "explanation" not in active,
                    "Çözülmemiş aşamanın açıklaması gönderilmemeli."
                )
                check(
                    "correctOptionId" not in active,
                    "Aktif aşamada doğru cevap kimliği gönderilmemeli."
                )

        check_completed(state)

        # Yanlış cevap açıklamayı açmamalı.
        first = state["currentStep"]
        definition = definitions[first["id"]]
        wrong_id = next(
            option["id"]
            for option in first["options"]
            if option["id"] != definition["correctOptionId"]
        )

        call(
            "/attempts/today/answer",
            cookie,
            method="POST",
            body={
                "stepId": first["id"],
                "optionId": wrong_id,
                "version": state["version"]
            }
        )

        state = start()
        check(state["mistakeCount"] == 1, "Yanlış cevap sayılmadı.")
        check_completed(state)
        print("DOĞRULANDI: Yanlış cevap açıklamayı açmadı.")

        # Bütün aşamaları doğru cevaplayıp açılan içerikleri kontrol et.
        for _ in range(len(game["steps"])):
            check(state["status"] == "PLAYING", "Oyun erken tamamlandı.")

            active = state["currentStep"]
            definition = definitions[active["id"]]
            correct_id = definition["correctOptionId"]

            correct_text = next(
                option["text"]
                for option in definition["options"]
                if option["id"] == correct_id
            )

            call(
                "/attempts/today/answer",
                cookie,
                method="POST",
                body={
                    "stepId": active["id"],
                    "optionId": correct_id,
                    "version": state["version"]
                }
            )

            expected_completed.append({
                "id": definition["id"],
                "position": definition["position"],
                "text": correct_text,
                "explanation": definition.get("explanation") or ""
            })

            state = start()
            check_completed(state)

            print(
                f"DOĞRULANDI: {definition['position']}. adım "
                "doğru cevapla açıldı ve yeniden girişte korundu."
            )

        check(state["status"] == "WON", "Son aşamada WON bekleniyordu.")
        check(state["currentStep"] is None, "Biten oyunda aktif aşama olmamalı.")
        check(state["mistakeCount"] == 1, "Toplam yanlış sayısı korunmalı.")

        has_step_explanation = any(
            (step.get("explanation") or "").strip()
            for step in game["steps"]
        )

        print("\nBAŞARILI: Adım açılma ve açıklama dönüş testi geçti.")

        if not has_step_explanation:
            print(
                "NOT: Bugünün ara adım açıklamaları boş. "
                "Dolu açıklama metinlerinin kaydedilip dönmesi "
                "bu testte doğrulanmadı."
            )


if __name__ == "__main__":
    main()