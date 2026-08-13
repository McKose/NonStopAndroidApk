// Panelin iş kuralları.
//
// Uygulamadaki Kotlin karşılıklarının **kopyası** ve bu bir risk: iki taraf
// birbirinden sapabilir. Sapmayı sınırlamak için buraya yalnızca gösterime dair
// ve sunucudaki veriden doğrudan türeyen kurallar giriyor — hesaplama yapan
// hiçbir şey yok. Tutar toplamak, hakediş hesaplamak gibi işler panelde
// yapılmıyor: onların tek doğru yeri ortak Kotlin modülü.
//
// Buradaki her kuralın Kotlin tarafında bir karşılığı var ve testleri de aynı
// örnekleri kullanıyor.

/**
 * Kuruş cinsinden tam sayıyı okunur tutara çevirir.
 *
 * Sunucuda para `bigint` ve kuruş cinsinden; `Double` kullanılmamasının sebebi
 * uygulama tarafında yazılı (toplamlarda sapma). Panelde de aynı: bölme yalnızca
 * gösterim anında, bir kez yapılıyor.
 */
export function tutarYaz(kurus) {
  if (kurus === null || kurus === undefined) return "—";
  const sayi = Number(kurus);
  if (!Number.isFinite(sayi)) return "—";
  return (sayi / 100).toLocaleString("tr-TR", {
    style: "currency",
    currency: "TRY",
    minimumFractionDigits: 2,
  });
}

/**
 * Epoch milisaniyeyi tarihe çevirir.
 *
 * Sunucuda zaman damgaları `bigint` epoch ms olarak duruyor; `timestamptz`e
 * çevrilmemesinin sebebi uygulama tarafında yazılı (saat dilimi ve yuvarlama
 * farkları senkronizasyonu kırılgan yapardı). Çevrim burada, gösterim anında.
 */
export function tarihYaz(ms) {
  if (ms === null || ms === undefined || ms === "") return "—";
  const sayi = Number(ms);
  if (!Number.isFinite(sayi)) return "—";
  return new Date(sayi).toLocaleDateString("tr-TR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

/**
 * Üyeliğin o andaki durumu.
 *
 * Uygulamadaki kuralın aynısı: durum bir kolonda **saklanmıyor**, bitiş
 * tarihinden türetiliyor. Saklansaydı, tarih geçtiğinde birinin o kolonu
 * güncellemesi gerekirdi ve güncellenmediğinde üye süresiz aktif görünürdü.
 *
 * `status` alanı ise elle konan durum: dondurulmuş ya da arşivlenmiş bir üye
 * tarihi geçmemiş olsa da aktif sayılmaz.
 */
export function uyelikDurumu(uye, simdiMs) {
  if (uye.deleted_at_ms) return "SILINDI";
  if (uye.status === "ARCHIVED") return "ARSIVDE";
  if (uye.status === "FROZEN") return "DONDURULDU";
  if (uye.end_date_ms === null || uye.end_date_ms === undefined) return "SURESIZ";
  return Number(uye.end_date_ms) >= simdiMs ? "AKTIF" : "SURESI_DOLDU";
}

/** Durum kodunun ekranda görünen Türkçe karşılığı. */
export function durumEtiketi(durum) {
  switch (durum) {
    case "AKTIF": return "Aktif";
    case "SURESI_DOLDU": return "Süresi doldu";
    case "DONDURULDU": return "Donduruldu";
    case "ARSIVDE": return "Arşivde";
    case "SURESIZ": return "Süresiz";
    case "SILINDI": return "Silindi";
    default: return durum;
  }
}

/**
 * Silinmiş satırları ayıklar.
 *
 * Sunucudan tombstone'lar da geliyor — silme senkronize edilebilsin diye satır
 * fiziksel olarak duruyor. Panelin varsayılan görünümünde bunlar yok; süzme
 * unutulsaydı silinen üyeler listede kalırdı.
 */
export function silinmemisler(satirlar) {
  return satirlar.filter((s) => !s.deleted_at_ms);
}
