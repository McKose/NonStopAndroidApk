#!/usr/bin/env python3
"""`appetize_runner.py` testleri — yerel sahte Appetize sunucusuyla.

### Neyi kapsıyor, neyi kapsamıyor
Bu betik yazıldığı ortamdan `api.appetize.io`'ya **erişilemiyor** (ağ ilkesi
CONNECT isteğini reddediyor), yani gerçek API çağrısı hiç denenemedi.
Denenebilen ve burada denenen şey, isteğin nasıl kurulduğu: adres, kimlik
doğrulama başlığı, çok parçalı gövdenin alanları ve dosya içeriği, ve yanıtın
nasıl yorumlandığı.

Kapsamayan: sunucunun bu isteği kabul ettiği. Onu ilk CI koşusu söyleyecek — ve
betik hata yolunda HTTP kodunu ve yanıt gövdesini olduğu gibi bastığı için
söylediği şey anlaşılır olacak.

Sahte sunucu gerçek bir HTTP sunucusu, sahte bir nesne değil: `urllib`'in
gövdeyi nasıl kodladığını da kapsama alıyor. Sahte nesneyle sınansaydı test,
kendi kurduğu gövdeyi doğrulardı.
"""

from __future__ import annotations

import base64
import io
import json
import subprocess
import sys
import threading
import zipfile
from email.parser import BytesParser
from email.policy import default as varsayilan_politika
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from tempfile import TemporaryDirectory

BETIK = Path(__file__).with_name("appetize_runner.py")

hatalar: list[str] = []
gecen = 0


def kontrol(ad: str, beklenen, gercek) -> None:
    global gecen
    if beklenen == gercek:
        gecen += 1
    else:
        hatalar.append(f"{ad}: beklenen {beklenen!r}, gerçek {gercek!r}")


def dogru(ad: str, kosul: bool, ipucu: str = "") -> None:
    global gecen
    if kosul:
        gecen += 1
    else:
        hatalar.append(f"{ad}: yanlış {ipucu}")


class SahteAppetize(BaseHTTPRequestHandler):
    """İsteği kaydeden, ayarlanabilir yanıt dönen sunucu."""

    kayit: dict = {}
    kod = 200
    govde = b'{"publicKey": "abc123", "publicURL": "https://appetize.io/app/abc123"}'

    def do_POST(self) -> None:  # noqa: N802
        uzunluk = int(self.headers.get("Content-Length", "0"))
        ham = self.rfile.read(uzunluk)

        # Çok parçalı gövdeyi standart kütüphaneyle çöz: elle ayrıştırmak,
        # testin kendi ayrıştırıcısını doğrulaması olurdu.
        basliklar = f"Content-Type: {self.headers['Content-Type']}\r\n\r\n".encode()
        mesaj = BytesParser(policy=varsayilan_politika).parsebytes(basliklar + ham)

        alanlar: dict[str, bytes] = {}
        dosya_adi = None
        for parca in mesaj.iter_parts():
            ad = parca.get_param("name", header="content-disposition")
            alanlar[ad] = parca.get_payload(decode=True)
            if ad == "file":
                dosya_adi = parca.get_param("filename", header="content-disposition")

        SahteAppetize.kayit = {
            "path": self.path,
            "auth": self.headers.get("Authorization", ""),
            "alanlar": alanlar,
            "dosya_adi": dosya_adi,
        }

        self.send_response(self.kod)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(self.govde)))
        self.end_headers()
        self.wfile.write(self.govde)

    def log_message(self, *_args) -> None:
        pass  # test çıktısını kirletmesin


def sunucu_ile(kod: int = 200, govde: bytes | None = None):
    SahteAppetize.kod = kod
    if govde is not None:
        SahteAppetize.govde = govde
    else:
        SahteAppetize.govde = (
            b'{"publicKey": "abc123", "publicURL": "https://appetize.io/app/abc123"}'
        )
    SahteAppetize.kayit = {}
    s = HTTPServer(("127.0.0.1", 0), SahteAppetize)
    threading.Thread(target=s.serve_forever, daemon=True).start()
    return s


def kosu(args: list[str], token: str = "test-token", ek_env: dict | None = None):
    import os

    env = {**os.environ, "APPETIZE_API_TOKEN": token}
    env.pop("APPETIZE_PUBLIC_KEY", None)
    if ek_env:
        env.update(ek_env)
    p = subprocess.run(
        [sys.executable, str(BETIK), *args],
        capture_output=True, text=True, env=env,
    )
    try:
        veri = json.loads(p.stdout)
    except json.JSONDecodeError:
        veri = {}
    return p, veri


def main() -> int:
    with TemporaryDirectory() as gecici:
        d = Path(gecici)

        # Gerçek bir APK gerekmiyor ama gerçek bir zip olması iyi: dosyanın
        # bozulmadan gittiğini içerik karşılaştırmasıyla göreceğiz.
        apk = d / "app-debug.apk"
        tampon = io.BytesIO()
        with zipfile.ZipFile(tampon, "w") as z:
            z.writestr("AndroidManifest.xml", "sahte")
        apk.write_bytes(tampon.getvalue())

        # ─── 1. Yeni yükleme ────────────────────────────────────────────────
        s = sunucu_ile()
        temel = f"http://127.0.0.1:{s.server_port}"
        p, veri = kosu(["--file", str(apk), "--api-base", temel,
                        "--device", "pixel7", "--os-version", "13.0", "--json"])
        k = SahteAppetize.kayit

        kontrol("yeni yükleme çıkış kodu", 0, p.returncode)
        kontrol("yeni yükleme durumu", "success", veri.get("status"))
        kontrol("yeni yükleme yolu", "/v1/apps", k.get("path"))
        kontrol("platform alanı", b"android", k.get("alanlar", {}).get("platform"))
        kontrol("dosya adı", "app-debug.apk", k.get("dosya_adi"))
        kontrol("dosya içeriği bozulmamış",
                apk.read_bytes(), k.get("alanlar", {}).get("file"))
        kontrol("public_key okundu", "abc123", veri.get("public_key"))
        kontrol("güncelleme değil", False, veri.get("updated"))

        # Token Basic olarak gitmeli; adrese gömülmemeli (günlüklere sızardı).
        beklenen_auth = "Basic " + base64.b64encode(b"test-token:").decode()
        kontrol("kimlik doğrulama başlığı", beklenen_auth, k.get("auth"))
        dogru("token adreste değil", "test-token" not in k.get("path", ""),
              k.get("path", ""))

        # Cihaz ve sürüm bağlantıya eklenmeli.
        adres = veri.get("simulator_url", "")
        dogru("adres sunucudan geliyor", adres.startswith("https://appetize.io/app/abc123"), adres)
        dogru("cihaz eklendi", "device=pixel7" in adres, adres)
        dogru("os sürümü eklendi", "osVersion=13.0" in adres, adres)
        s.shutdown()

        # ─── 2. Var olanı güncelleme ────────────────────────────────────────
        s = sunucu_ile()
        temel = f"http://127.0.0.1:{s.server_port}"
        p, veri = kosu(["--file", str(apk), "--api-base", temel,
                        "--public-key", "abc123", "--json"])
        kontrol("güncelleme yolu", "/v1/apps/abc123", SahteAppetize.kayit.get("path"))
        kontrol("güncelleme durumu", "success", veri.get("status"))
        kontrol("güncelleme bayrağı", True, veri.get("updated"))
        s.shutdown()

        # public-key ortam değişkeninden de okunmalı: CI orayı kullanıyor.
        s = sunucu_ile()
        temel = f"http://127.0.0.1:{s.server_port}"
        p, veri = kosu(["--file", str(apk), "--api-base", temel, "--json"],
                       ek_env={"APPETIZE_PUBLIC_KEY": "envkey"})
        kontrol("ortam değişkeninden public key",
                "/v1/apps/envkey", SahteAppetize.kayit.get("path"))
        s.shutdown()

        # ─── 3. Hata yolları ────────────────────────────────────────────────
        # Sessiz başarısızlık bu betikteki en pahalı hata olurdu: CI yeşil kalır,
        # paylaşılan bağlantı eski sürümü gösterirdi.

        s = sunucu_ile(kod=401, govde=b'{"message": "bad token"}')
        temel = f"http://127.0.0.1:{s.server_port}"
        p, veri = kosu(["--file", str(apk), "--api-base", temel, "--json"])
        kontrol("401 çıkış kodu", 1, p.returncode)
        kontrol("401 durumu", "error", veri.get("status"))
        dogru("401 ipucu veriyor", "APPETIZE_API_TOKEN" in veri.get("error", ""),
              veri.get("error", ""))
        dogru("401 yanıt gövdesini gösteriyor", "bad token" in veri.get("error", ""),
              veri.get("error", ""))
        s.shutdown()

        s = sunucu_ile(kod=404, govde=b'{"message": "not found"}')
        temel = f"http://127.0.0.1:{s.server_port}"
        p, veri = kosu(["--file", str(apk), "--api-base", temel,
                        "--public-key", "silinmis", "--json"])
        dogru("404 ne yapılacağını söylüyor",
              "APPETIZE_PUBLIC_KEY" in veri.get("error", ""), veri.get("error", ""))
        s.shutdown()

        # JSON olmayan yanıt başarı sayılmamalı.
        s = sunucu_ile(govde=b"<html>gateway error</html>")
        temel = f"http://127.0.0.1:{s.server_port}"
        p, veri = kosu(["--file", str(apk), "--api-base", temel, "--json"])
        kontrol("JSON olmayan yanıt hata", "error", veri.get("status"))
        kontrol("JSON olmayan yanıt çıkış kodu", 1, p.returncode)
        s.shutdown()

        # publicKey de publicURL de yoksa uydurma bağlantı üretilmemeli.
        s = sunucu_ile(govde=b'{"ok": true}')
        temel = f"http://127.0.0.1:{s.server_port}"
        p, veri = kosu(["--file", str(apk), "--api-base", temel, "--json"])
        kontrol("anahtarsız yanıt hata", "error", veri.get("status"))
        s.shutdown()

        # publicURL yok ama publicKey var → bağlantı anahtardan kurulabilmeli.
        s = sunucu_ile(govde=b'{"publicKey": "sadecekey"}')
        temel = f"http://127.0.0.1:{s.server_port}"
        p, veri = kosu(["--file", str(apk), "--api-base", temel, "--json"])
        kontrol("yalnız publicKey ile başarı", "success", veri.get("status"))
        dogru("anahtardan adres kuruldu",
              "sadecekey" in veri.get("simulator_url", ""), veri.get("simulator_url", ""))
        s.shutdown()

        # ─── 4. Yerel doğrulamalar (sunucuya hiç gitmeden) ──────────────────
        p, veri = kosu(["--file", str(d / "yok.apk"), "--json"])
        kontrol("olmayan dosya hata", "error", veri.get("status"))
        dogru("olmayan dosya mesajı", "bulunamadı" in veri.get("error", ""),
              veri.get("error", ""))

        bos = d / "bos.apk"
        bos.write_bytes(b"")
        p, veri = kosu(["--file", str(bos), "--json"])
        dogru("boş dosya reddediliyor", "boş" in veri.get("error", ""),
              veri.get("error", ""))

        # Token yoksa anlaşılır hata ve ayrı çıkış kodu.
        p, veri = kosu(["--file", str(apk), "--json"], token="")
        kontrol("tokensiz çıkış kodu", 2, p.returncode)
        dogru("tokensiz mesaj yol gösteriyor",
              "Secrets" in veri.get("error", ""), veri.get("error", ""))

    print(f"{gecen} kontrol geçti.")
    if hatalar:
        print(f"\n{len(hatalar)} KONTROL DÜŞTÜ:")
        for h in hatalar:
            print(f"  - {h}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
