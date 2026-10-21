import json
import sys
from http.cookiejar import CookieJar
from urllib.error import HTTPError
from urllib.request import (
    Request,
    build_opener,
    HTTPCookieProcessor,
)

BASE = "http://localhost:8081/api"

START = "Muhabir kaynaklarla konuştu."
END = "Okur yayımlanan haberden bilgi edindi."
FIRST = "Toplanan bilgiler haber metnine dönüştürüldü."
SECOND = "Editör doğruluğu kontrol etti ve haberi yayımladı."

opener = build_opener(HTTPCookieProcessor(CookieJar()))


def check(condition, message):
    if not condition:
        raise SystemExit("HATA: " + message)


def call(path, body=None):
    request = Request(
        BASE + path,
        data=json.dumps(
            body if body is not None else {},
            ensure_ascii=False,
        ).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "X-Trace-Request": "true",
        },
        method="POST",
        )

    try:
        with opener.open(request, timeout=20) as response:
            result = json.load(response)
            print(f"OK: POST {path} → {response.status}")
            return result
    except HTTPError as error:
        print(error.read().decode("utf-8"), file=sys.stderr)
        raise SystemExit(f"HATA: POST {path} → {error.code}")


def resume():
    return call("/attempts/today/start")


def answer(state, option_id):
    call(
        "/attempts/today/answer",
        {
            "stepId": state["currentStep"]["id"],
            "optionId": option_id,
            "version": state["version"],
        },
    )
    return resume()


def check_hidden(state, *texts):
    serialized = json.dumps(state, ensure_ascii=False)
    for text in texts:
        check(text not in serialized, "Kilitli adımın açıklaması sızdı.")


def main():
    call("/auth/guest")
    state = resume()

    check(
        state["title"] == "Dolu açıklama oyuncu testi",
        "Beklenen test oyunu açılmadı.",
        )
    check(state["startExplanation"] == START, "Başlangıç metni eşleşmiyor.")
    check(state["endExplanation"] == END, "Bitiş metni eşleşmiyor.")
    check(state["completedSteps"] == [], "Başlangıçta adım açık olmamalı.")
    check_hidden(state, FIRST, SECOND)

    state = answer(state, "s1-b")
    check(state["remainingLives"] == 2, "Yanlış cevap bir can azaltmalı.")
    check(state["completedSteps"] == [], "Yanlış cevap açıklamayı açmamalı.")
    check_hidden(state, FIRST, SECOND)
    print("DOĞRULANDI: Yanlış cevap dolu açıklamayı açmadı.")

    state = answer(state, "s1-a")
    check(len(state["completedSteps"]) == 1, "Yalnız ilk adım açılmalı.")
    first = state["completedSteps"][0]
    check(first["id"] == "step-1", "Yanlış adım açıldı.")
    check(first["explanation"] == FIRST, "İlk açıklama birebir eşleşmiyor.")
    check(first["mistakeCount"] == 1, "İlk adımda bir yanlış olmalı.")
    check_hidden(state, SECOND)

    reopened = resume()
    check(
        reopened["completedSteps"] == state["completedSteps"],
        "Yeniden girişte açıklama korunmadı.",
        )
    check_hidden(reopened, SECOND)
    print("DOĞRULANDI: İlk dolu açıklama açıldı ve yeniden girişte korundu.")

    state = answer(reopened, "s2-b")
    check(state["status"] == "WON", "Oyun kazanılmış olmalı.")
    check(state["remainingLives"] == 2, "Kalan can iki olmalı.")

    check(
        [step["id"] for step in state["completedSteps"]]
        == ["step-1", "step-2"],
        "Tamamlanan adımların sırası yanlış.",
        )
    check(
        [step["explanation"] for step in state["completedSteps"]]
        == [FIRST, SECOND],
        "Dolu açıklamalar birebir eşleşmiyor.",
        )

    reopened = resume()
    check(reopened["status"] == "WON", "Kazanma durumu korunmadı.")
    check(
        reopened["completedSteps"] == state["completedSteps"],
        "Oyun bittikten sonra açıklamalar korunmadı.",
        )

    print("BAŞARILI: Dolu açıklamaların oyuncuya dönüş testi geçti.")


if __name__ == "__main__":
    main()