// Sekmelerin veri tanımı: hangi tablo, hangi sıra, hangi kolonlarda aranıyor.
//
// Çizim işi burada değil (`app.js`), çünkü o DOM'a dokunuyor ve Node'da
// koşamıyor. Burada yalnızca **veri** var ve bu ayrımın tek sebebi test
// edilebilirlik: bu tanımlar 20'den fazla kolon adı taşıyor ve bir yazım hatası
// sessiz kalıyor.
//
//   - `order`da yazım hatası → sunucu 400 döndürür, sekme boş açılır
//   - `ara`da yazım hatası → arama o alanda çalışmaz, hiçbir hata görünmez
//   - `tarihAlani`nda yazım hatası → tarih süzgeci hiçbir şeyi süzmez
//
// Üçü de yalnızca o sekmeye basıp deneyerek fark edilir. `sekmeler.test.js` bu
// yüzden her kolon adını demo satırlarıyla karşılaştırıyor — demo verisi
// sunucudan gelen biçimin aynısı olduğu için kolon adları da aynı.

/**
 * Sekme → veri tanımı.
 *
 * `ozel: true` olanlar (özet, market) birden fazla tablo okuyup kendi düzenini
 * kuruyor; onların tablo/sıra bilgisi `app.js` içindeki yükleyicilerinde.
 *
 * `ara`: arama kutusunun baktığı kolonlar. Bilinçli olarak dar — her kolonda
 * aramak, "notlar" gibi uzun alanlar yüzünden alakasız eşleşmeler üretirdi.
 *
 * `tarihAlani`: tarih aralığının süzdüğü kolon. Tablo başına farklı ve anlamı da
 * farklı: randevuda "ne zaman yapıldı", defterde "ne zaman gerçekleşti", üyede
 * "ne zaman bitiyor", satışta "ne zaman satıldı".
 */
export const SEKME_VERISI = {
  ozet: { ozel: true, okunanTablolar: ["gym_members", "ledger_entries"] },

  uyeler: {
    tablo: "gym_members",
    order: "full_name.asc",
    ara: ["full_name", "phone", "email"],
    tarihAlani: "end_date_ms",
    tarihEtiketi: "Üyelik bitişi",
  },

  paketler: {
    tablo: "gym_packages",
    order: "name.asc",
    ara: ["name", "type", "category"],
  },

  randevular: {
    tablo: "appointments",
    order: "start_time_ms.desc",
    ara: ["training_type", "state", "notes"],
    tarihAlani: "start_time_ms",
    tarihEtiketi: "Randevu tarihi",
  },

  market: { ozel: true, okunanTablolar: ["products", "stock_movements"] },

  satislar: {
    tablo: "orders",
    order: "date_ms.desc",
    ara: ["payment_method", "payment_status", "delivery_status", "notes"],
    tarihAlani: "date_ms",
    tarihEtiketi: "Satış tarihi",
  },

  personel: {
    tablo: "staff",
    order: "full_name.asc",
    ara: ["full_name", "title", "role", "branch", "nickname", "phone"],
  },

  finans: {
    tablo: "ledger_entries",
    order: "occurred_at_ms.desc",
    ara: ["description", "type", "category", "payment_method"],
    tarihAlani: "occurred_at_ms",
    tarihEtiketi: "İşlem tarihi",
  },

  // ─── Panele özgü bölümler ───────────────────────────────────────────────
  // Bu ikisi uygulamada karşılığı olmayan, web'e ait işler (bkz. roller.js).
  // İkisi de YAZMA yapıyor — panelin geri kalanı salt okunur, ve bu ayrımın
  // gerekçesi `supabase.js` içindeki `yaz` yönteminde yazılı: bu iki tabloda
  // ortak Kotlin modülünde tanımlı hiçbir iş kuralı yok.
  duyurular: { ozel: true, okunanTablolar: ["announcements"] },
  "uye-hesaplari": { ozel: true, okunanTablolar: ["member_accounts", "gym_members"] },
};

/** `order` değerinin kolon kısmı: `"full_name.asc"` → `"full_name"`. */
export function siraKolonu(order) {
  if (typeof order !== "string" || order === "") return null;
  // PostgREST biçimi: `kolon.yon` (yön isteğe bağlı).
  return order.split(".")[0] || null;
}

/**
 * Bir sekmenin ekranda gösterdiği tabloların adı.
 *
 * Tek tablolu sekmelerde `tablo`, çok tablolu (`ozel`) sekmelerde
 * `okunanTablolar`. İkisini tek yerde toplamak, testin sekme türüne göre
 * dallanmasını gereksiz kılıyor.
 */
export function tablolari(tanim) {
  if (tanim.okunanTablolar) return tanim.okunanTablolar;
  return tanim.tablo ? [tanim.tablo] : [];
}
