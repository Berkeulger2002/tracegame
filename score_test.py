import tempfile
from pathlib import Path

from lives_test import call, check, ADMIN_COOKIE


def start(cookie):
    return call("/attempts/today/start", cookie, method="POST")


def answer(cookie, state, option_id):
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
    return start(cookie)


def current_solution(state, game):
    return next(
        step
        for step in game["steps"]
        if step["id"] == state["currentStep"]["id"]
    )


def wrong_answer(cookie, state, game):
    step = current_solution(state, game)

    option_id = next(
        option["id"]
        for option in state["currentStep"]["options"]
        if option["id"] != step["correctOptionId"]
    )

    return answer(cookie, state, option_id)


def correct_answer(cookie, state, game):
    step = current_solution(state, game)
    return answer(cookie, state, step["correctOptionId"])


def verify_finished(cookie, state, expected_status, expected_score):
    check(
        state["status"] == expected_status,
        f"Beklenen durum: {expected_status}",
        )
    check(state["scoreFinal"] is True, "Puan kesinleşmeli.")
    check(
        state["score"] == expected_score,
        f"Beklenen puan {expected_score}, gelen {state['score']}",
        )

    resumed = start(cookie)

    check(
        resumed["score"] == expected_score
        and resumed["status"] == expected_status
        and resumed["scoreFinal"] is True,
        "Yeniden girişte sonuç ve puan korunmalı.",
        )
    ranking = call("/leaderboard/today/me", cookie)

    check(ranking["ranked"] is True, "Biten oyun sıralamaya girmeli.")
    check(
        ranking["status"] == expected_status,
        "Kişisel sıralamada oyun sonucu eşleşmiyor.",
        )
    check(
        ranking["score"] == expected_score,
        "Kişisel sıralamada puan eşleşmiyor.",
        )
    check(
        isinstance(ranking["rank"], int) and ranking["rank"] >= 1,
        "Geçerli bir sıra numarası dönmeli.",
        )

    print(
        f"DOĞRULANDI: Kişisel sıralama "
        f"durum={ranking['status']}, "
        f"puan={ranking['score']}, sıra={ranking['rank']}"
    )

def run_scenario(directory, name, mistakes=0, hint=None, lose=False):
    cookie = str(Path(directory) / f"{name}.txt")

    call("/auth/guest", cookie, method="POST", save_cookie=True)
    state = start(cookie)
    game = call(f"/admin/games/{state['gameId']}", ADMIN_COOKIE)

    check(state["status"] == "PLAYING", "Yeni oyun başlamalı.")
    check(state["score"] == 0, "Başlangıç puanı sıfır olmalı.")
    check(state["scoreFinal"] is False, "Başlangıç puanı geçici olmalı.")

    if lose:
        check(
            len(game["steps"]) >= 2,
            "Elenme senaryosu en az iki adımlı oyun gerektiriyor.",
            )

    if hint:
        call(
            "/attempts/today/hint",
            cookie,
            method="POST",
            body={
                "stepId": state["currentStep"]["id"],
                "type": hint,
                "version": state["version"],
            },
        )
        state = start(cookie)
        check(state["score"] == 0, "İpucu puan kazandırmamalı.")

    for _ in range(mistakes):
        state = wrong_answer(cookie, state, game)
        check(state["score"] == 0, "Çözülmeyen adım puan kazandırmamalı.")

    state = correct_answer(cookie, state, game)
    step_points = {0: 20, 1: 14, 2: 8}[mistakes]

    if lose:
        check(state["score"] == 8, "İki yanlış sonrası doğru adım 8 puan.")

        state = wrong_answer(cookie, state, game)
        verify_finished(cookie, state, "LOST", 0)

        print("DOĞRULANDI: Kazanılan 8 puan, üçüncü yanlışta sıfırlandı.")
        return

    while state["status"] == "PLAYING":
        check(
            state["score"] == step_points,
            "Oyun sırasında yalnızca tamamlanan adımlar puan kazandırmalı.",
            )
        check(state["scoreFinal"] is False, "Oyun sürerken puan geçici.")

        state = correct_answer(cookie, state, game)
        step_points += 20

    timer = state["timer"]
    duration = timer["activeDurationMillis"]

    check(
        timer["speedBadgeEligible"] is True,
        "Bu kısa senaryoda aktif süre güvenilir olmalı.",
        )
    check(
        isinstance(duration, int) and duration >= 0,
        "Geçerli aktif süre dönmeli.",
        )

    time_bonus = max(0, 30 - max(0, duration - 15000) // 4000)
    perfect_bonus = 50 if mistakes == 0 else 0
    no_hint_bonus = 20 if hint is None else 0

    expected = step_points + perfect_bonus + no_hint_bonus + time_bonus

    verify_finished(cookie, state, "WON", expected)

    print(
        f"DOĞRULANDI: {name}: "
        f"adım={step_points}, hatasız={perfect_bonus}, "
        f"ipucusuz={no_hint_bonus}, zaman={time_bonus}, "
        f"toplam={expected}"
    )


def main():
    admin = call("/admin/me", ADMIN_COOKIE)
    check(admin["role"] == "ADMIN", "Admin oturumu gerekli.")

    with tempfile.TemporaryDirectory() as directory:
        run_scenario(directory, "hatasiz")
        run_scenario(directory, "bir_yanlis", mistakes=1)
        run_scenario(directory, "iki_yanlis", mistakes=2)
        run_scenario(directory, "eleme_ipucu", hint="ELIMINATE")
        run_scenario(directory, "seyirci_ipucu", hint="AUDIENCE")
        run_scenario(directory, "elenme", mistakes=2, lose=True)

    print("\nBAŞARILI: Puanlama ve yeniden giriş kontrolleri geçti.")


if __name__ == "__main__":
    main()