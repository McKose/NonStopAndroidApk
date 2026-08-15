#!/usr/bin/env bash
# Sunucu şemasını boş bir PostgreSQL veritabanına uygular ve salon yalıtımını sınar.
#
# Herhangi bir adım düşerse betik sıfırdan farklı kodla çıkar: `ON_ERROR_STOP`
# psql'i ilk hatada durdurur, `set -e` de zinciri keser. Hatanın sessizce
# yutulmaması testin tek değeri.
#
# Kullanım:  PGURL="postgres://postgres:postgres@localhost:5432/postgres" ./run.sh
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname "$here")"
: "${PGURL:?PGURL ayarlanmalı}"

run() {
    echo "→ $1"
    psql "$PGURL" -v ON_ERROR_STOP=1 -q -f "$1"
}

run "$here/00_auth_stub.sql"

# Migrasyonlar tek tek sayılmıyor, dizinden okunuyor.
#
# Elle sayıldığında yeni bir dosya eklenip listeye yazılmayı unutabiliyor ve o
# migrasyon **hiç sınanmamış** oluyor — üstelik testler yeşil kalıyor. Dosya
# adları sıfır dolgulu olduğu için sözlük sırası uygulama sırasıyla aynı.
shopt -s nullglob
migrasyonlar=("$root"/migrations/*.sql)
if [ ${#migrasyonlar[@]} -eq 0 ]; then
    echo "HATA: migrations/ altında hiç .sql yok — yol yanlış olabilir." >&2
    exit 1
fi

hepsini_uygula() {
    for m in "${migrasyonlar[@]}"; do run "$m"; done
}

hepsini_uygula

# Migrasyonlar İKİNCİ kez uygulanıyor: tekrar çalıştırılabilir olmaları şart.
#
# Kurulum sırasında bir dosya yarıda kalıp tekrar çalıştırıldığında ya da
# şemanın güncel olduğundan emin olmak için yeniden koşulduğunda "already
# exists" ile düşmemeli. 0001 tam olarak bu yüzden düştü: `create policy`'nin
# `if not exists` biçimi yok ve "varsa sil" adımı unutulmuştu. Kullanıcı bunu
# gerçek kurulumda yakaladı, testler yakalamamıştı — çünkü test her şeyi bir
# kez çalıştırıyordu.
hepsini_uygula

# Test dosyaları da dizinden okunuyor, migrasyonlarla aynı gerekçeyle: elle
# sayıldığında yeni bir test dosyası eklenip listeye yazılmayı unutulabiliyor ve
# o test HİÇ koşmuyor — üstelik takım yeşil kalıyor. `00_` önekli dosyalar
# kurulum, testten önce ve migrasyonlardan da önce koşuyorlar.
testler=("$here"/[1-9]*_*.sql)
if [ ${#testler[@]} -eq 0 ]; then
    echo "HATA: tests/ altında hiç test dosyası yok — yol yanlış olabilir." >&2
    exit 1
fi

for t in "${testler[@]}"; do run "$t"; done

echo "Sunucu şeması ve erişim kuralları doğrulandı."
