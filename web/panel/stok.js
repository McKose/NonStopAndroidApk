// Eldeki stok: hareketlerin toplamı.
//
// Kural uygulamadan geliyor ve birebir aynı (`StockMovementDao.onHand`):
//
//     SELECT COALESCE(SUM(quantityDelta), 0) FROM stock_movements
//     WHERE tenantId = :tenantId AND productId = :productId
//
// Sebebe göre süzme YOK: satış, alım, düzeltme ve iade hepsi toplama giriyor.
// Stok ürün üzerinde mutlak bir sayaç olarak tutulmuyor çünkü iki cihaz aynı anda
// satış yaptığında bir satış sessizce kaybolurdu; hareketler toplanabilir olduğu
// için çevrimdışı kayıtlar sırasından bağımsız doğru toplamı veriyor.
//
// `stock_movements` tablosunda `deleted_at_ms` **yok** — defter gibi yalnızca
// eklenen bir tablo. Bu yüzden burada tombstone süzmesi de yok; olsaydı hiçbir
// şeyi süzmeyen ölü bir satır olurdu ve okuyan biri silmenin desteklendiğini
// sanardı.
//
// ### Neden toplama panelde yapılabiliyor
// Panelin kuralı "sayma ve düz toplama dışında hesap yok". Bu düz toplama:
// hakediş ya da bakiye gibi bir iş kuralı değil, tek kolonun toplamı. Aynı
// gerekçeyle özet sekmesindeki defter toplamı da panelde yapılıyor.

/**
 * "Azalıyor" sayılan üst sınır (bu değer dahil).
 *
 * Tek yerde duruyor çünkü iki tüketicisi var: uyarı sayacı ve tablodaki rozet.
 * İkisinde ayrı yazılsaydı biri değiştiğinde panel "3 ürün azalıyor" der ama
 * tabloda 5 ürün sarı görünürdü — kullanıcının güvenini kaybettiren tam bu tür
 * tutarsızlık.
 *
 * Bir iş kuralı değil, panelde bir uyarı sınırı: uygulama tarafında karşılığı
 * yok, o yüzden ortak Kotlin modülünden gelmesi gerekmiyor.
 */
export const AZALMA_ESIGI = 5;

/**
 * Adet alanını okur; okunamıyorsa `null`.
 *
 * `Number()` doğrudan kullanılmıyor ve sebebi somut: `Number(null)`, `Number("")`
 * ve `Number(false)` **sıfır** veriyor. Yani eksik bir alan sessizce "0 adet
 * hareket" olarak toplanır, toplam olduğundan farklı çıkar ve sonuç tamamen
 * makul bir sayı gibi görünür. Bu tuzağa yazarken düşüldü ve testi de o yüzden
 * var.
 *
 * Sunucu `integer` döndürüyor, yani normal durum `typeof === "number"`. Metin de
 * kabul ediliyor ama yalnızca tam sayı biçimindeyse — savunma amaçlı.
 */
function adetOku(deger) {
  if (typeof deger === "number") {
    return Number.isInteger(deger) ? deger : null;
  }
  if (typeof deger === "string" && /^-?\d+$/.test(deger.trim())) {
    return Number(deger.trim());
  }
  return null;
}

/**
 * Ürün kimliği → eldeki stok.
 *
 * Okunamayan bir hareket varsa o ürünün stoğu `null` oluyor — **sıfır ya da
 * eksik toplam değil**. Bozuk bir satırı sessizce atlamak, olduğundan az
 * görünen bir stok sayısı üretirdi ve o sayı doğru görünürdü: yanlış olduğunu
 * gösteren hiçbir şey olmazdı. `null` ise ekranda "?" olarak çıkıyor.
 *
 * @param {Array<object>} hareketler `stock_movements` satırları
 * @returns {Map<string, number|null>}
 */
export function stokHaritasi(hareketler) {
  const harita = new Map();

  for (const h of hareketler ?? []) {
    const urun = h?.product_id;
    if (urun === null || urun === undefined || urun === "") continue;

    const delta = adetOku(h.quantity_delta);
    if (delta === null) {
      // Bu ürünün toplamı artık güvenilir değil ve bir daha güvenilir olamaz:
      // sonraki hareketler doğru olsa bile eksik olan bu satır.
      harita.set(urun, null);
      continue;
    }

    const oncesi = harita.get(urun);
    if (oncesi === null) continue; // zaten güvenilmez
    harita.set(urun, (oncesi ?? 0) + delta);
  }

  return harita;
}

/**
 * Bir ürünün stoğunu ekranda gösterilecek metne çevirir.
 *
 * Hiç hareketi olmayan ürün `0`: uygulamadaki `COALESCE(..., 0)` ile aynı.
 * "—" olsaydı "stoğu bilinmiyor" anlamına gelirdi, oysa biliniyor ve sıfır.
 */
export function stokYaz(harita, urunId, hepsiOkundu = true) {
  if (!hepsiOkundu) return "?";
  const deger = harita.get(urunId);
  if (deger === null) return "?";
  return String(deger ?? 0);
}

/**
 * Bir ürünün stok durumu — tek sınıflandırma.
 *
 * Hem uyarı sayaçları hem tablodaki rozet **bunu** kullanıyor. Ayrı ayrı
 * yazıldığında gerçekten ayrıştılar: sayaç negatif stoğu "tükendi" sayıyordu,
 * tablo ona ayrı bir rozet veriyordu. Sonuç, kutuda "3 ürün tükendi" yazarken
 * tabloda 2 tane "Tükendi" rozeti görünmesiydi — kullanıcının hangi sayıya
 * güveneceğini bilemediği tam o durum.
 *
 * Sınıflandırma sırası önemli: `bilinmiyor` en başta, çünkü bilinmeyen bir stok
 * hakkında başka hiçbir şey söylenemez.
 */
export function stokDurumu(harita, urunId, esik = AZALMA_ESIGI) {
  const deger = harita.get(urunId);
  if (deger === null) return "bilinmiyor";

  const adet = deger ?? 0;
  // Negatif, `tukendi`nin daha kötüsü değil: fazla satış ya da eksik alım kaydı
  // demek, yani veri sorunu. Aynı sınıfa koymak sebebini araştırılmaz kılardı.
  if (adet < 0) return "eksi";
  if (adet === 0) return "tukendi";
  if (adet <= esik) return "azaliyor";
  return "yeterli";
}

/**
 * Ürünleri stok durumuna göre gruplar.
 *
 * Eşik ürüne göre değişebilen bir iş kuralı değil, panelde bir uyarı sınırı;
 * varsayılanı [AZALMA_ESIGI].
 *
 * Stoğu bilinmeyen ürünler ne "yeterli" ne "tükendi" sayılıyor: ikisi de
 * bilmediğimiz bir şey hakkında iddia olurdu.
 */
export function stokUyarilari(urunler, harita, esik = AZALMA_ESIGI) {
  const gruplar = { yeterli: [], azaliyor: [], tukendi: [], eksi: [], bilinmiyor: [] };

  for (const u of urunler) {
    gruplar[stokDurumu(harita, u.id, esik)].push(u);
  }

  return gruplar;
}
