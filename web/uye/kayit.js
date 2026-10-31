// Üye kaydının AÇIK mı KAPALI mı olduğu — çizim yok, ağ yok.
//
// Ayrı dosya olmasının sebebi bu depoda birkaç kez kanıtlandı: `uye.js`
// içindeki mantık ancak tarayıcıda, gerçek bir sunucuya bağlıyken
// denenebiliyor. Buradaki her şey saf fonksiyon, dolayısıyla `kayit.test.js`
// doğrudan sınayabiliyor.

/**
 * Web'den üye kaydı alınıyor mu.
 *
 * **Şu anda `false` ve bu bir ürün kararı, arıza değil.** Yeniden açmak için
 * tek yapılacak şey bunu `true` yapmak — ama `SUPABASE_TENANT_ID` gizli
 * anahtarı tanımlı değilse kayıt yine açılmıyor (aşağıya bakın) ve ekran
 * bunu ayrı bir mesajla söylüyor.
 *
 * Form ve akış SİLİNMEDİ, kapatıldı. Silinseydi geri açmak yeniden yazmak
 * olurdu; kapalıyken taşıma maliyeti yalnızca bu satır.
 */
export const KAYIT_ACIK = false;

/**
 * Kaydın gerçek durumu.
 *
 * İki ayrı sebep var ve BİRLEŞTİRİLMEMELİ, çünkü yapılacak şey farklı:
 *
 *  - `kapali`     — bilinçli karar. Ziyaretçinin yapacağı şey salona
 *                   başvurmak; ortada düzeltilecek bir arıza yok.
 *  - `ayar_eksik` — `SUPABASE_TENANT_ID` tanımsız. Bu bir KURULUM eksiği ve
 *                   düzeltmesi salonun elinde (gizli anahtarı eklemek).
 *
 * İkisini tek "kapalı" hâlinde toplamak, kayıt yeniden açıldığı gün gerçek
 * bir yapılandırma eksiğini görünmez kılardı: ekran "kapalı" der, kimse
 * anahtarın eksik olduğunu anlamazdı.
 *
 * @param acik ürün kararı; varsayılanı [KAYIT_ACIK]
 * @param tenantId `window.NONSTOP_CONFIG.tenantId`
 * @returns `{ acik: true }` ya da `{ acik: false, sebep }`
 */
export function kayitDurumu({ acik = KAYIT_ACIK, tenantId = null } = {}) {
  if (!acik) return { acik: false, sebep: "kapali" };
  // Kayıt isteği bir salona ait olmak zorunda (`tenant_id not null`).
  if (!tenantId) return { acik: false, sebep: "ayar_eksik" };
  return { acik: true };
}

/**
 * Kapalıyken ekranda görünecek metin.
 *
 * Metin ziyaretçiye NE YAPACAĞINI söylüyor. "Kayıt kapalı" tek başına bir
 * çıkmaz: kişi salonda üye olabilir ve hesabının açılmasını bekliyordur.
 */
export function kayitKapaliMesaji(sebep) {
  if (sebep === "ayar_eksik") {
    return "Kayıt şu anda kapalı (salon ayarı eksik). Lütfen salonla iletişime geçin.";
  }
  return (
    "Web üzerinden üye kaydı şu anda alınmıyor. Salonumuzda üyeliğiniz varsa " +
    "hesabınızın açılması için salona başvurun; hesabınız açıldıktan sonra " +
    "yukarıdan giriş yapabilirsiniz."
  );
}
