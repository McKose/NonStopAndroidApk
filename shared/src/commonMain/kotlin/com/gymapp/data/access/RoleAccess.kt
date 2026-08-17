package com.gymapp.data.access

import com.gymapp.data.sync.SyncTable
import com.gymapp.domain.StaffRole

/**
 * Rolün **hangi ekranı göreceği**.
 *
 * [SyncTable.writableBy] "kim yazabilir" sorusunu yanıtlıyor; bu enum onun
 * eksik kalan yarısı: "kim girebilir". İkisi ayrı sorular — market ekranı
 * eğitmene açık (satış onun işi) ama ürün fiyatını değiştiremiyor.
 *
 * Ayrı bir yerde durmasının sebebi somut: aynı karar bugüne kadar **iki**
 * ekranda birbirinden habersiz kopyalanmıştı. Pano eğitmene Market, Paketler,
 * Finans ve Ayarlar kısayollarını gizliyordu; üye listesindeki çekmece aynı dört
 * hedefi herkese açıyordu. Yani kısıt gerçek bir kısıt değildi, yalnızca bir
 * ekranda görünmüyordu — kullanıcı ikinci yoldan aynı yere gidiyordu.
 *
 * ### Bu bir güvenlik sınırı değil
 * Okuma tarafında sunucu kuralları salona bağlı herkese açık (migrasyon `0004`);
 * defter satırlarını eğitmenden **saklayan** bir kural yok. Buradaki gizleme
 * arayüz kararı: eğitmenin işine yaramayan ve yanlış anlaşılması kolay olan bir
 * ekranı yoluna koymamak. Gerçek sınır yazma tarafında ve o sunucuda.
 */
enum class AppDestination {
    DASHBOARD,
    CALENDAR,
    MEMBERS,
    PACKAGES,
    MARKET,
    FINANCE,
    SETTINGS,
    PERSONNEL,
    ;

    /**
     * Bu hedefi görebilen roller.
     *
     * Kurucu parametresi değil hesaplanan özellik — sebebi [SyncTable.writableBy]
     * ile aynı: enum sabitleri dosya düzeyindeki `val`lardan önce ilkleniyor,
     * ortak bir küme sabiti kurucuya verilseydi ilklenme sırası platforma göre
     * değişir ve en kötü ihtimalle boş küme okunurdu.
     */
    val visibleTo: Set<StaffRole>
        get() = when (this) {
            // Salonun tüm parası: ciro, gider, hakediş. Eğitmenin günlük işinde
            // karşılığı yok ve tek bir ekranda salonun tamamının cirosunu
            // göstermek, göstermemekten daha çok soru doğuruyor.
            FINANCE -> setOf(StaffRole.ADMIN, StaffRole.MANAGER)

            // Ayarlar **herkese** açık olmak zorunda: "Çıkış Yap" burada.
            // Panonun eğitmene Ayarlar'ı gizlemesi, eğitmenin uygulamadan hiç
            // çıkamaması demekti — çekmecedeki ikinci yol olmasa oturum
            // cihazda kilitli kalırdı.
            //
            // Personel listesi de açık: eğitmen kimin hangi branşta olduğunu
            // görebilmeli. Yazma zaten kapalı (`SyncTable.STAFF`) ve ekran bunu
            // açıkça söylüyor.
            //
            // Paketler ve Market salt okunur açılıyor: fiyatı görmek satışın
            // kendisi için gerekli, değiştirmek değil.
            DASHBOARD, CALENDAR, MEMBERS, PACKAGES, MARKET, SETTINGS, PERSONNEL ->
                setOf(StaffRole.ADMIN, StaffRole.MANAGER, StaffRole.TRAINER)
        }

    /** Bu rol bu ekranı görebilir mi? */
    fun isVisibleTo(role: StaffRole): Boolean = role in visibleTo
}

/**
 * Rolün ne yapabildiğinin insan diliyle özeti — personel kartında gösterilir.
 *
 * Metin elle yazılmıyor, **uygulanan kuralların kendisinden** üretiliyor
 * ([SyncTable.writableBy] ve [AppDestination.visibleTo]). Elle yazılsaydı
 * kaçınılmaz olan şey şu: biri kuralı değiştirir, açıklamayı unutur ve ekran
 * salon sahibine artık doğru olmayan bir söz verir. Bu projede tekrar tekrar
 * düzelttiğimiz hata sınıfı tam olarak bu.
 *
 * Salon sahibi rolü seçerken ne verdiğini bilmiyordu: açılır listede yalnızca
 * "Admin / Yönetici / Antrenör" yazıyordu ve bu üç kelimenin somut karşılığı
 * hiçbir yerde görünmüyordu.
 */
fun StaffRole.yetkiOzetiTr(): List<String> = listOf(
    "Üye, randevu ve ölçüm: ${yazabilirMi(SyncTable.MEMBERS)}",
    "Market satışı: ${yazabilirMi(SyncTable.ORDERS)}",
    // Paket ve ürün tek satırda: ikisi aynı kuralı paylaşıyor ve
    // `RoleAccessTest` ayrışmadıklarını doğruluyor.
    "Paket ve ürün fiyatları: ${yazabilirMi(SyncTable.PACKAGES)}",
    "Personel kartları: ${yazabilirMi(SyncTable.STAFF)}",
    "Finans ekranı: " + if (AppDestination.FINANCE.isVisibleTo(this)) "görünür" else "görünmez",
)

private fun StaffRole.yazabilirMi(table: SyncTable): String =
    if (table.isWritableBy(this)) "değiştirebilir" else "salt okunur"
