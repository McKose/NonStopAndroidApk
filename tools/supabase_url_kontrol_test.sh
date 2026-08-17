#!/usr/bin/env bash
#
# `supabase_url_kontrol.sh` testleri.
#
# Neden gerekli: bu betik artık HER derlemenin kapısında duruyor ve düştüğünde
# derlemeyi durduruyor. İki yönde de bozulabilir ve ikisi de sessiz:
#
#   - Bir kural yanlışlıkla kaldırılırsa hatalı adres yine geçer ve APK yine
#     girişte 404 verir — yani korumanın varlığı yanıltıcı olur.
#   - Bir kural fazla katı olursa geçerli bir adres reddedilir ve tüm derlemeler
#     durur; özellikle özel alan adı (`https://api.ornek.tr`) bu riski taşıyor.
#
# Bu yüzden hem KABUL hem RED durumları sınanıyor.

set -u

betik="$(dirname "$0")/supabase_url_kontrol.sh"
gecen=0
dusen=0

# Geçerli bir adres: çıkış 0 olmalı.
gecerli() {
    if "$betik" "$1" > /dev/null 2>&1; then
        echo "PASS  kabul: '$1'"
        gecen=$((gecen + 1))
    else
        echo "FAIL  kabul edilmeliydi: '$1'"
        "$betik" "$1" | sed 's/^/        /'
        dusen=$((dusen + 1))
    fi
}

# Geçersiz bir adres: çıkış 1 olmalı VE sebebi beklenen metni içermeli.
#
# Sebep metni de sınanıyor çünkü "reddedildi" tek başına yetmiyor: yanlış
# gerekçeyle reddetmek kullanıcıyı yanlış yere bakmaya gönderir ve bu, düzeltmesi
# en zor hata türü.
gecersiz() {
    local adres="$1" beklenen="$2" cikti
    cikti="$("$betik" "$adres" 2>&1)"
    if [ $? -eq 0 ]; then
        echo "FAIL  reddedilmeliydi: '$adres'"
        dusen=$((dusen + 1))
        return
    fi
    case "$cikti" in
        *"$beklenen"*)
            echo "PASS  red: '$adres' ($beklenen)"
            gecen=$((gecen + 1)) ;;
        *)
            echo "FAIL  yanlış gerekçe: '$adres'"
            echo "        beklenen: $beklenen"
            echo "$cikti" | sed 's/^/        /'
            dusen=$((dusen + 1)) ;;
    esac
}

echo "── Geçerli adresler ──"
gecerli "https://jvkytncwedjcvssilhih.supabase.co"
gecerli "https://abc.supabase.co"
# Özel alan adı bilinçli olarak geçiyor: `panel.nonstopstudio.tr` gibi bir
# kurulum planlı ve kuralın onu engellememesi gerekiyor.
gecerli "https://api.nonstopstudio.tr"
gecerli "https://sunucu.ornek.com"

echo ""
echo "── Reddedilmesi gerekenler ──"
# Gerçek arıza tam buydu: 49 karakter, 3 eğik çizgi.
gecersiz "https://jvkytncwedjcvssilhih.supabase.co/rest/v1/" "Sonunda '/' olmamalı"
gecersiz "https://jvkytncwedjcvssilhih.supabase.co/"         "Sonunda '/' olmamalı"
gecersiz "https://jvkytncwedjcvssilhih.supabase.co/rest/v1"  "Adresin sonunda yol var"
gecersiz "https://supabase.com/dashboard/project/abc"         "PANO"
gecersiz "http://abc.supabase.co"                             "https://"
gecersiz "abc.supabase.co"                                    "'https://' ile başlamalı"
gecersiz "https://abc.supabase.co ekstra"                     "Boşluk"
gecersiz ""                                                   "boş"

echo ""
echo "Geçen: $gecen, düşen: $dusen"
[ "$dusen" -eq 0 ] || exit 1
echo "TÜMÜ GEÇTİ"
