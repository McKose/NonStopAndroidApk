#!/usr/bin/env python3
"""Derlenen APK'yı Appetize.io'ya yükler ve çalıştırılabilir bağlantıyı döndürür.

Neden var
---------
APK'yı denemek şimdiye kadar şu adımları gerektiriyordu: Actions sekmesini aç,
koşuyu bul, artefaktı indir, zip'i aç, telefona kur. Appetize aynı APK'yı
tarayıcıda çalıştırıyor; bu betik yüklemeyi CI'ın işi hâline getiriyor.

Neden bağımlılık yok
--------------------
Yalnızca standart kütüphane kullanılıyor (`urllib`, `http.client` değil).
`requests` eklemek CI'a bir kurulum adımı ve bir tedarik zinciri yüzeyi daha
katardı; çok parçalı (multipart) gövde elle kurulacak kadar basit.

Neden `--api-base` var
----------------------
Testte sahte bir sunucuya yönlendirebilmek için. Bu betik yazıldığı ortamdan
Appetize'a **erişilemiyordu** (ağ ilkesi `api.appetize.io`'yu reddediyor), yani
gerçek API çağrısı yerelde hiç denenemedi. Denenebilen şey isteğin nasıl
kurulduğu: yerel bir sahte sunucu isteği yakalayıp alanları doğruluyor
(`appetize_runner_test.py`).

Bunun sonucu dürüstçe şu: **istek gövdesi sınandı, sunucunun onu kabul ettiği
sınanmadı.** Bu yüzden hata yolu özellikle konuşkan: sunucu beklenmedik bir şey
dönerse HTTP kodu ve yanıt gövdesi olduğu gibi raporlanıyor. Sessiz bir
başarısızlık, "yükleniyor gibi görünen ama hiç yüklenmemiş" bir kuruluma yol
açardı.
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import sys
import uuid
from pathlib import Path
from urllib import error, request

VARSAYILAN_API = "https://api.appetize.io"

# Yanıt gövdesinden raporlanacak azami karakter. Sınır yoksa bir HTML hata
# sayfası bütün CI günlüğünü doldurabilir.
AZAMI_GOVDE = 2000


class AppetizeHatasi(Exception):
    """Kullanıcıya gösterilecek, anlaşılır bir hata."""


def coklu_govde(alanlar: dict[str, str], dosya: Path) -> tuple[bytes, str]:
    """multipart/form-data gövdesi kurar.

    Elle kuruluyor çünkü tek ihtiyaç bu; `requests` bağımlılığının karşılığı yok.
    Sınır (boundary) rastgele: dosya içeriğinde geçme ihtimali pratikte sıfır.
    """
    sinir = f"----appetize{uuid.uuid4().hex}"
    parcalar: list[bytes] = []

    for ad, deger in alanlar.items():
        parcalar.append(
            f"--{sinir}\r\n"
            f'Content-Disposition: form-data; name="{ad}"\r\n\r\n'
            f"{deger}\r\n".encode()
        )

    tur = mimetypes.guess_type(dosya.name)[0] or "application/octet-stream"
    parcalar.append(
        f"--{sinir}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{dosya.name}"\r\n'
        f"Content-Type: {tur}\r\n\r\n".encode()
    )
    parcalar.append(dosya.read_bytes())
    parcalar.append(f"\r\n--{sinir}--\r\n".encode())

    return b"".join(parcalar), f"multipart/form-data; boundary={sinir}"


def yukle(
    dosya: Path,
    token: str,
    platform: str,
    public_key: str | None,
    api_base: str,
    zaman_asimi: int,
) -> dict:
    """APK'yı yükler. [public_key] verilirse var olan uygulamayı günceller."""
    if not dosya.is_file():
        raise AppetizeHatasi(f"Dosya bulunamadı: {dosya}")
    if dosya.stat().st_size == 0:
        raise AppetizeHatasi(f"Dosya boş: {dosya}")

    # Var olanı GÜNCELLEMEK, her koşuda yeni uygulama açmaktan iyi: yenisi her
    # seferinde yeni bir bağlantı üretir ve daha önce paylaşılan bağlantı eski
    # sürümü göstermeye devam ederdi.
    yol = f"/v1/apps/{public_key}" if public_key else "/v1/apps"
    govde, icerik_turu = coklu_govde({"platform": platform}, dosya)

    istek = request.Request(
        api_base.rstrip("/") + yol,
        data=govde,
        method="POST",
        headers={
            "Content-Type": icerik_turu,
            "Content-Length": str(len(govde)),
            # Appetize v1: token HTTP Basic kullanıcı adı olarak gidiyor.
            # Adresin içine gömmek (https://TOKEN@host) de mümkün ama o hâlde
            # token günlüklere ve hata mesajlarına sızardı.
            "Authorization": "Basic "
            + __import__("base64").b64encode(f"{token}:".encode()).decode(),
        },
    )

    try:
        with request.urlopen(istek, timeout=zaman_asimi) as yanit:
            ham = yanit.read().decode("utf-8", "replace")
    except error.HTTPError as e:
        detay = e.read().decode("utf-8", "replace")[:AZAMI_GOVDE]
        ipucu = ""
        if e.code in (401, 403):
            ipucu = " — API token'ı geçersiz görünüyor (APPETIZE_API_TOKEN)."
        elif e.code == 404 and public_key:
            ipucu = (
                f" — '{public_key}' anahtarlı uygulama yok. Silinmiş olabilir;"
                " APPETIZE_PUBLIC_KEY değişkenini kaldırın, yeni uygulama açılır."
            )
        elif e.code == 413:
            ipucu = " — dosya Appetize'ın boyut sınırını aşıyor."
        raise AppetizeHatasi(f"Appetize HTTP {e.code}{ipucu}\nYanıt: {detay}") from e
    except error.URLError as e:
        raise AppetizeHatasi(f"Appetize'a ulaşılamadı: {e.reason}") from e

    try:
        return json.loads(ham)
    except json.JSONDecodeError as e:
        # Yanıt JSON değilse sessizce "başarılı" saymak, çalışmayan bir
        # bağlantıyı çalışıyormuş gibi sunmak olurdu.
        raise AppetizeHatasi(
            "Appetize JSON olmayan bir yanıt döndü.\n"
            f"Yanıt: {ham[:AZAMI_GOVDE]}"
        ) from e


def calistirma_adresi(yanit: dict, cihaz: str | None, os_surumu: str | None) -> str:
    """Tarayıcıda açılacak adres.

    Sunucunun verdiği adres temel alınıyor; cihaz ve işletim sistemi sürümü
    sorgu parametresi olarak ekleniyor. Adresi baştan kurmak, Appetize alan adını
    değiştirdiğinde sessizce bozulurdu.
    """
    temel = yanit.get("publicURL") or yanit.get("appURL") or ""
    if not temel:
        anahtar = yanit.get("publicKey")
        if not anahtar:
            raise AppetizeHatasi(
                "Yanıtta ne publicURL ne publicKey var; bağlantı üretilemiyor.\n"
                f"Yanıt: {json.dumps(yanit)[:AZAMI_GOVDE]}"
            )
        temel = f"https://appetize.io/app/{anahtar}"

    parametreler = []
    if cihaz:
        parametreler.append(f"device={cihaz}")
    if os_surumu:
        parametreler.append(f"osVersion={os_surumu}")
    if not parametreler:
        return temel
    ayirac = "&" if "?" in temel else "?"
    return f"{temel}{ayirac}{'&'.join(parametreler)}"


def main(argv: list[str] | None = None) -> int:
    ayristirici = argparse.ArgumentParser(
        description="APK'yı Appetize.io'ya yükler ve çalıştırma bağlantısını basar.",
    )
    ayristirici.add_argument("--file", required=True, help="Yüklenecek .apk dosyası")
    ayristirici.add_argument(
        "--public-key",
        default=os.environ.get("APPETIZE_PUBLIC_KEY") or None,
        help="Var olan uygulamayı günceller. Boşsa yeni uygulama açılır.",
    )
    ayristirici.add_argument("--device", default=None, help="Örn: pixel7")
    ayristirici.add_argument("--os-version", default=None, help="Örn: 13.0")
    ayristirici.add_argument("--platform", default="android", choices=["android", "ios"])
    ayristirici.add_argument("--api-base", default=os.environ.get("APPETIZE_API_BASE", VARSAYILAN_API))
    ayristirici.add_argument("--timeout", type=int, default=300)
    ayristirici.add_argument(
        "--json", action="store_true", help="Sonucu JSON olarak bas (otomasyon için)",
    )
    a = ayristirici.parse_args(argv)

    def bitir(veri: dict, kod: int) -> int:
        if a.json:
            print(json.dumps(veri, ensure_ascii=False, indent=2))
        elif veri["status"] == "success":
            print(f"Yüklendi: {veri['simulator_url']}")
            print(f"publicKey: {veri['public_key']}")
        else:
            print(f"HATA: {veri['error']}", file=sys.stderr)
        return kod

    token = os.environ.get("APPETIZE_API_TOKEN", "").strip()
    if not token:
        return bitir(
            {
                "status": "error",
                "error": (
                    "APPETIZE_API_TOKEN tanımlı değil. Depo ayarlarından ekleyin: "
                    "Settings -> Secrets and variables -> Actions -> New repository secret."
                ),
            },
            2,
        )

    try:
        yanit = yukle(
            dosya=Path(a.file),
            token=token,
            platform=a.platform,
            public_key=a.public_key,
            api_base=a.api_base,
            zaman_asimi=a.timeout,
        )
        adres = calistirma_adresi(yanit, a.device, a.os_version)
    except AppetizeHatasi as e:
        return bitir({"status": "error", "error": str(e)}, 1)

    return bitir(
        {
            "status": "success",
            "simulator_url": adres,
            "public_key": yanit.get("publicKey", ""),
            "updated": bool(a.public_key),
        },
        0,
    )


if __name__ == "__main__":
    raise SystemExit(main())
