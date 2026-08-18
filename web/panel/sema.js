// Sunucu şemasını SQL migrasyonlarından okur.
//
// Yalnızca testler için; panel bunu çalışma zamanında kullanmıyor.
//
// Neden demo verisi yerine SQL: demo verisi de bir kopya ve kopyayı kopyayla
// karşılaştırmak ikisinin birlikte yanlış olmasını yakalamıyor. `supabase/migrations`
// ise sunucuda gerçekten ne olduğunun tek kaynağı — panelin sorguları oraya
// gidiyor.
//
// Bu ayrıştırıcı basit ve bunu bilerek kabul ediyoruz: `create table` bloklarındaki
// kolon adlarını okuyor, tip sistemini anlamıyor. Ayrıştıramazsa testin AÇIKÇA
// düşmesi gerekiyor — sessizce boş küme döndürmesi, her kolon adını "geçerli"
// saymak olurdu.

import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const burada = dirname(fileURLToPath(import.meta.url));
const MIGRASYON_DIZINI = join(burada, "..", "..", "supabase", "migrations");

/**
 * Tablo adı → kolon adları kümesi.
 *
 * Bütün migrasyonlar sırayla okunuyor: `create table` blokları kolonları kuruyor,
 * sonraki `alter table ... add column` satırları ekliyor. Yalnızca ilk dosyaya
 * bakmak, sonradan eklenen bir kolonu "yok" saymak olurdu.
 */
export function sunucuSemasi() {
  const dosyalar = readdirSync(MIGRASYON_DIZINI).filter((a) => a.endsWith(".sql")).sort();
  if (dosyalar.length === 0) {
    throw new Error(`Migrasyon bulunamadı: ${MIGRASYON_DIZINI}`);
  }

  const sema = {};

  for (const dosya of dosyalar) {
    const sql = readFileSync(join(MIGRASYON_DIZINI, dosya), "utf8");

    // `create table [if not exists] public.<ad> ( ... );`
    const desen = /create\s+table\s+(?:if\s+not\s+exists\s+)?(?:public\.)?(\w+)\s*\(([\s\S]*?)\n\s*\);/gi;
    for (const m of sql.matchAll(desen)) {
      const tablo = m[1];
      sema[tablo] = sema[tablo] ?? new Set();
      for (const kolon of kolonlariAyikla(m[2])) sema[tablo].add(kolon);
    }

    // Sonradan eklenen kolonlar.
    const ekle = /alter\s+table\s+(?:public\.)?(\w+)\s+add\s+column\s+(?:if\s+not\s+exists\s+)?(\w+)/gi;
    for (const m of sql.matchAll(ekle)) {
      sema[m[1]] = sema[m[1]] ?? new Set();
      sema[m[1]].add(m[2]);
    }
  }

  return sema;
}

/**
 * `create table` gövdesinden kolon adlarını çıkarır.
 *
 * Tablo düzeyindeki kısıtlar (`unique (...)`, `primary key (...)`, `check (...)`,
 * `constraint ...`, `foreign key ...`) kolon değil; atlanıyor. Atlanmasalar
 * "unique" adında bir kolon varmış gibi görünürdü ve test yanlış bir adı geçerli
 * sayardı.
 */
function kolonlariAyikla(govde) {
  // Yorumlar virgülle bölmeden ÖNCE atılıyor.
  //
  // Sonra atılırsa yorumun içindeki bir virgül kolonu ikiye böler ve ikinci
  // parçanın ilk kelimesi kolon adı sanılır. Gerçekte olan buydu:
  //
  //     -- `on delete set null` değil, kasıtlı olarak kısıt yok.
  //     linked_by    uuid,
  //
  // Yorumdaki virgül parçayı bölüyor, ikinci parça "kasıtlı olarak kısıt
  // yok. linked_by uuid" oluyor, ilk kelime "kasıtlı" olarak okunuyor ve
  // `\w` sınamasından geçemediği için ELENİYOR. Sonuç: `linked_by` şemada
  // görünmez oluyordu — sessizce. Bunu yakalayan şey demo verisi kontrolü
  // oldu; kolonun kendisi hiçbir yerde hata vermiyordu.
  govde = govde.replace(/--[^\n]*/g, "");

  const kolonlar = [];
  let derinlik = 0;
  let parca = "";

  // Virgülle bölmek yeterli değil: `check (x in ('A', 'B'))` içindeki virgüller
  // de bölerdi. Parantez derinliği sayılıyor.
  for (const ch of govde) {
    if (ch === "(") derinlik++;
    else if (ch === ")") derinlik--;

    if (ch === "," && derinlik === 0) {
      kolonlar.push(parca);
      parca = "";
    } else {
      parca += ch;
    }
  }
  kolonlar.push(parca);

  const atlanan = /^(unique|primary|foreign|check|constraint|exclude)\b/i;

  return kolonlar
    .map((p) =>
      p
        .split("\n")
        .map((s) => s.replace(/--.*$/, "").trim()) // satır yorumlarını at
        .filter(Boolean)
        .join(" ")
        .trim(),
    )
    .filter((p) => p && !atlanan.test(p))
    .map((p) => p.split(/\s+/)[0])
    .filter((ad) => /^\w+$/.test(ad));
}
