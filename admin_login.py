import getpass

from lives_test import ADMIN_COOKIE, call

email = input("Admin e-posta: ").strip()
password = getpass.getpass("Admin şifre: ")

call(
    "/auth/login",
    ADMIN_COOKIE,
    method="POST",
    body={"email": email, "password": password},
    save_cookie=True,
)

call("/admin/me", ADMIN_COOKIE)

print("BAŞARILI: Admin oturumu yenilendi.")