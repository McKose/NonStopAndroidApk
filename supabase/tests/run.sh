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
run "$root/migrations/0001_tenancy.sql"
run "$root/migrations/0002_data_tables.sql"
run "$here/10_rls_test.sql"

echo "Sunucu şeması ve erişim kuralları doğrulandı."
