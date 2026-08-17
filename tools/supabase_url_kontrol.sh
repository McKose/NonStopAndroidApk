#!/usr/bin/env bash
#
# `SUPABASE_URL` değerinin BİÇİMİNİ sınar — değerini yazdırmadan.
#
# Neden gerekli: yanlış bir adresle üretilen APK derlenir, kurulur ve yalnızca
# giriş denendiğinde "404 Invalid path specified in request URL" der. Yani hata
# üretimden saatler sonra, tamamen başka bir yerde görünüyor. Bir kez tam olarak
# bu yaşandı ve teşhis etmek APK'nın içine bakmayı gerektirdi.
#
# Neden değeri yazdırmıyor: GitHub gizli anahtarları günlükte maskeliyor, yani
# yazdırmak zaten işe yaramaz. Onun yerine yalnızca TÜRETİLMİŞ bilgi veriliyor
# (uzunluk, yol sayısı, hangi kuralın düştüğü) — bu, maskelemeden etkilenmiyor ve
# sorunu bulmaya yetiyor.
#
# Kullanım:  tools/supabase_url_kontrol.sh "$SUPABASE_URL"
# Çıkış:     0 = biçim geçerli, 1 = geçersiz (sebepler stdout'ta)

set -u

adres="${1-}"

if [ -z "$adres" ]; then
  echo "SUPABASE_URL boş."
  exit 1
fi

# Şemadan sonra kalan '/' sayısı. Sunucu adresinde hiç olmaması gerekiyor:
# uygulama yolları kendisi ekliyor (`/auth/v1/token`, `/rest/v1/...`).
yol_sayisi=$(printf '%s' "$adres" | sed -E 's#^https?://##' | tr -cd '/' | wc -c)
yol_sayisi=$((yol_sayisi))

echo "Adres uzunluğu: ${#adres}, şemadan sonraki '/' sayısı: $yol_sayisi"

hatalar=""
ekle() { hatalar="$hatalar
  - $1"; }

case "$adres" in
  https://*) ;;
  http://*)  ekle "'http://' değil 'https://' olmalı." ;;
  *)         ekle "'https://' ile başlamalı." ;;
esac

case "$adres" in
  */) ekle "Sonunda '/' olmamalı." ;;
esac

case "$adres" in
  *supabase.com/dashboard*|*/dashboard/project/*)
    ekle "Bu PANO (dashboard) adresi, API adresi değil.
    Doğrusu: Supabase -> Settings -> API -> Project URL" ;;
esac

case "$adres" in
  *" "*|*"	"*) ekle "Boşluk ya da sekme içeriyor." ;;
esac

# Sondaki '/' zaten ayrıca bildiriliyor; onu iki kez saymamak için tek '/' ve
# sonda-slash birlikteyse bu kuralı atlıyoruz.
if [ "$yol_sayisi" -gt 0 ]; then
  case "$adres" in
    */) ;;   # sebebi yukarıda yazıldı
    *)
      ekle "Adresin sonunda yol var; yalnızca sunucu adı olmalı.
    Doğru biçim: https://<proje-ref>.supabase.co" ;;
  esac
fi

if [ -n "$hatalar" ]; then
  echo "SUPABASE_URL biçimi geçersiz:$hatalar"
  echo ""
  echo "Bu adresle üretilen APK kurulur ama girişte 404 verir."
  echo "Düzeltmek için: depo Settings -> Secrets and variables -> Actions"
  exit 1
fi

echo "Adres biçimi geçerli."
exit 0
