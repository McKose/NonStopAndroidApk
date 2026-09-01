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
 * Bu listede iptal edilmiş defter kayıtlarının kimlikleri.
 *
 * Ters kayıt neyi iptal ettiğini `reverses_id` ile söylüyor; iptal edilmiş
 * kaydın kendisinde bunu gösteren hiçbir alan yok. Dolayısıyla "iptal edildi
 * mi" sorusu tek satıra bakarak yanıtlanamıyor, listenin tamamı gerekiyor.
 */
export function iptalEdilenKimlikler(kayitlar) {
  const kume = new Set();
  for (const k of kayitlar) {
    if (k?.reverses_id) kume.add(k.reverses_id);
  }
  return kume;
}

/**
 * Toplamlara giren defter kayıtları: ne ters kayıt, ne de iptal edilmiş.
 *
 * ### Bu süzgeç neden ŞART
 * Ters kayıt, iptal ettiği kaydın **birebir kopyası**: aynı tür, aynı pozitif
 * tutar — yalnızca `reverses_id` dolu. Yani ikisi toplamda birbirini
 * götürmüyor, tam tersine tutarı **ikiye katlıyor**. İptal edilen 1.000 TL'lik
 * bir tahsilat, süzülmediğinde ciroyu 1.000 TL düşürmek yerine 1.000 TL
 * artırıyor.
 *
 * Uygulamadaki karşılığı `LedgerDao.outstandingBalanceMinor` ve
 * `FinansKaydi.isVoided`: ikisi de çiftin iki tarafını da eliyor.
 *
 * Süzgecin iki yönlü olması bu yüzden zorunlu. Yalnızca `reverses_id == null`
 * bakılsaydı iptal edilen asıl kayıt toplamda kalırdı; yalnızca "birinin
 * işaret ettiği" elenseydi ters kaydın kendisi kalırdı. İkisi de tutarı tek
 * taraflı olarak sayardı.
 */
export function aktifDefterKayitlari(kayitlar) {
  const iptalEdilenler = iptalEdilenKimlikler(kayitlar);
  return kayitlar.filter((k) => !k?.reverses_id && !iptalEdilenler.has(k?.id));
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
