import json
import subprocess
import tempfile
from pathlib import Path

BASE = "http://localhost:8080/api"
ADMIN_COOKIE = "/tmp/trace-admin-cookies.txt"


def check(condition, message):
    if not condition:
        raise SystemExit("BAŞARISIZ: " + message)


def call(path, cookie_file, method="GET", body=None, expected=200,
         save_cookie=False):
    command = [
        "curl", "-sS",
        "--connect-timeout", "5",
        "--max-time", "20",
        "-X", method,
        "-b", str(cookie_file),
        "-H", "Content-Type: application/json",
        "-H", "X-Trace-Request: true",
        "-w", "\n%{http_code}",
        BASE + path
    ]

    if save_cookie:
        command.extend(["-c", str(cookie_file)])

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
        raise SystemExit("Bağlantı hatası: " + result.stderr)

    raw_body, raw_status = result.stdout.rsplit("\n", 1)
    status = int(raw_status)

    if status != expected:
        print(f"HATA: {method} {path}")
        print(f"Beklenen: {expected}, gelen: {status}")
        print(raw_body)
        raise SystemExit(1)

    print(f"OK: {method} {path} → {status}")

    if expected >= 400:
        return None

    return json.loads(raw_body) if raw_body.strip() else None


def main():
    check(
        Path(ADMIN_COOKIE).is_file(),
        "Admin cookie dosyası bulunamadı."
    )

    admin = call("/admin/me", ADMIN_COOKIE)
    check(admin["role"] == "ADMIN", "Admin oturumu gerekli.")

    with tempfile.TemporaryDirectory(prefix="trace-lives-") as folder:
        guest_cookie = Path(folder) / "guest-cookies.txt"
        guest_cookie.touch()

        call(
            "/auth/guest",
            guest_cookie,
            method="POST",
            save_cookie=True
        )

        def start():
            return call(
                "/attempts/today/start",
                guest_cookie,
                method="POST"
            )

        state = start()

        check(state["status"] == "PLAYING", "Yeni oyun başlamadı.")
        check(state["mistakeCount"] == 0, "Yeni oyun sıfır yanlışla başlamalı.")
        check(state["totalSteps"] >= 2, "Bu test en az iki aşama gerektirir.")

        # Doğru cevaplar yalnızca yetkili admin endpoint'inden okunur.
        game = call(
            f"/admin/games/{state['gameId']}",
            ADMIN_COOKIE
        )

        steps = {step["id"]: step for step in game["steps"]}

        first = state["currentStep"]
        first_definition = steps[first["id"]]
        first_correct = first_definition["correctOptionId"]

        wrong_options = [
            option["id"]
            for option in first["options"]
            if option["id"] != first_correct
        ]

        check(
            len(wrong_options) == 2,
            "İlk aşamada bir doğru ve iki yanlış seçenek bulunmalı."
        )

        def answer(current, option_id):
            call(
                "/attempts/today/answer",
                guest_cookie,
                method="POST",
                body={
                    "stepId": current["currentStep"]["id"],
                    "optionId": option_id,
                    "version": current["version"]
                }
            )
            # Kaydedilen durumu yeniden oku.
            # Oyun sürüyorsa bu çağrı süre oturumunu da yeniler.
            return start()

        # 1. yanlış: iki can kalmalı.
        state = answer(state, wrong_options[0])
        check(state["mistakeCount"] == 1, "İlk yanlış sayılmadı.")
        check(state["status"] == "PLAYING", "İlk yanlışta oyun bitti.")
        check(
            state["currentStep"]["id"] == first["id"],
            "Yanlış cevap aşamayı ilerletmemeli."
        )
        print("DOĞRULANDI: İlk yanlış → 2 can.")

        # 2. yanlış: aynı aşamada bir can kalmalı.
        state = answer(state, wrong_options[1])
        check(state["mistakeCount"] == 2, "İkinci yanlış sayılmadı.")
        check(state["status"] == "PLAYING", "İkinci yanlışta oyun bitti.")
        check(
            state["currentStep"]["id"] == first["id"],
            "İkinci yanlış aşamayı ilerletmemeli."
        )
        print("DOĞRULANDI: İkinci yanlış → 1 can.")

        # Doğru cevap: aşama ilerler, can yenilenmez.
        state = answer(state, first_correct)
        check(state["status"] == "PLAYING", "Oyun devam etmeliydi.")
        check(state["mistakeCount"] == 2, "Doğru cevap can yenilememeli.")
        check(
            state["currentStepPosition"] == first["position"] + 1,
            "Sonraki aşamaya geçilmedi."
        )
        check(
            state["currentStepMistakeCount"] == 0,
            "Yeni aşamanın yanlış sayısı sıfır olmalı."
        )
        print("DOĞRULANDI: Aşama geçildi; kalan can hâlâ 1.")

        second = state["currentStep"]
        second_definition = steps[second["id"]]
        second_correct = second_definition["correctOptionId"]
        second_wrong = next(
            option["id"]
            for option in second["options"]
            if option["id"] != second_correct
        )

        # 3. yanlış farklı aşamada: oyun bitmeli.
        state = answer(state, second_wrong)
        check(state["mistakeCount"] == 3, "Üçüncü yanlış sayılmadı.")
        check(
            state["status"] == "LOST",
            "Toplam üçüncü yanlışta LOST bekleniyordu. "
            "Eski aşama bazlı kural hâlâ çalışıyor olabilir."
        )
        check(
            state["currentStep"] is None,
            "Biten oyunda aktif aşama dönmemeli."
        )
        print("DOĞRULANDI: Toplam üçüncü yanlış → LOST.")

        # Biten oyuna güncel sürümle bile cevap verilememeli.
        call(
            "/attempts/today/answer",
            guest_cookie,
            method="POST",
            body={
                "stepId": second["id"],
                "optionId": second_correct,
                "version": state["version"]
            },
            expected=409
        )

        # Yeniden giriş oyunu sıfırlamamalı.
        resumed = start()
        check(resumed["status"] == "LOST", "Biten oyun yeniden açıldı.")
        check(resumed["mistakeCount"] == 3, "Yanlış sayısı korunmadı.")
        check(
            resumed["version"] == state["version"],
            "Reddedilen cevap veya yeniden giriş oyun kaydını değiştirdi."
        )

        print("\nBAŞARILI: Üç can testi geçti.")
        print("Üçüncü yanlışta elenme ve yeniden giriş kontrolü doğrulandı.")


if __name__ == "__main__":
    main()