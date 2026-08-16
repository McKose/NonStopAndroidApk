package com.gymapp.data.access

import com.gymapp.data.sync.SyncTable
import com.gymapp.domain.StaffRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ekran görünürlüğü ve yetki özeti tek kaynaktan gelmeli.
 *
 * Düzeltilen hata sessizdi ve tam olarak "kısıt varmış gibi görünüyor" sınıfına
 * giriyordu: pano eğitmene Finans, Market, Paketler ve Ayarlar kısayollarını
 * gizliyor, üye listesindeki çekmece aynı dört hedefi herkese açıyordu. İki ekran
 * aynı kararı birbirinden habersiz veriyordu, dolayısıyla kısıt yalnızca bir
 * ekranda vardı — kullanıcı ikinci yoldan aynı yere gidiyordu.
 */
class RoleAccessTest {

    @Test
    fun `finans yalnizca yonetime acik`() {
        assertTrue(AppDestination.FINANCE.isVisibleTo(StaffRole.ADMIN))
        assertTrue(AppDestination.FINANCE.isVisibleTo(StaffRole.MANAGER))
        assertFalse(AppDestination.FINANCE.isVisibleTo(StaffRole.TRAINER))
    }

    /**
     * Çıkış yolu her rolde açık kalmalı.
     *
     * "Çıkış Yap" Ayarlar ekranında; Ayarlar eğitmene kapatılsaydı eğitmenin
     * uygulamadan çıkmasının hiçbir yolu kalmazdı. Bu test o kapıyı kilitliyor.
     */
    @Test
    fun `ayarlar her role acik`() {
        StaffRole.entries.forEach { role ->
            assertTrue(
                AppDestination.SETTINGS.isVisibleTo(role),
                "Çıkış yolu $role rolünde kapanmış.",
            )
        }
    }

    /**
     * Eğitmenin işini yapabildiği ekranlar açık.
     *
     * Market özellikle önemli: satış eğitmenin işinin kendisi (`orders` üç role
     * de yazılabilir) ve pano bu ekranı eğitmene gizliyordu.
     */
    @Test
    fun `egitmen gunluk isini yapabilecegi ekranlari gorur`() {
        listOf(
            AppDestination.DASHBOARD,
            AppDestination.CALENDAR,
            AppDestination.MEMBERS,
            AppDestination.MARKET,
            AppDestination.PACKAGES,
            AppDestination.PERSONNEL,
        ).forEach { destination ->
            assertTrue(
                destination.isVisibleTo(StaffRole.TRAINER),
                "$destination eğitmene kapalı; günlük iş bu ekranda yapılıyor.",
            )
        }
    }

    /** Hiçbir hedef tüm rollere kapalı olmamalı — erişilemez ekran ölü koddur. */
    @Test
    fun `her hedefi gorebilen en az bir rol var`() {
        AppDestination.entries.forEach { destination ->
            assertTrue(
                destination.visibleTo.isNotEmpty(),
                "$destination hiçbir rolde görünmüyor.",
            )
        }
    }

    /**
     * Personel kartındaki özet, uygulanan kuralın kendisinden üretiliyor.
     *
     * Elle yazılmış bir açıklama kural değiştiğinde sessizce yanlış olurdu; bu
     * testler o metnin gerçekten kurala bağlı olduğunu doğruluyor.
     */
    @Test
    fun `yetki ozeti kuralla ayni seyi soyluyor`() {
        val admin = StaffRole.ADMIN.yetkiOzetiTr()
        assertTrue(admin.any { it.startsWith("Personel kartları") && it.endsWith("değiştirebilir") })
        assertTrue(admin.any { it.startsWith("Finans ekranı") && it.endsWith("görünür") })

        val trainer = StaffRole.TRAINER.yetkiOzetiTr()
        assertTrue(trainer.any { it.startsWith("Personel kartları") && it.endsWith("salt okunur") })
        assertTrue(trainer.any { it.startsWith("Paket ve ürün fiyatları") && it.endsWith("salt okunur") })
        assertTrue(trainer.any { it.startsWith("Market satışı") && it.endsWith("değiştirebilir") })
        assertTrue(trainer.any { it.startsWith("Finans ekranı") && it.endsWith("görünmez") })

        // Yönetici fiyat listesini değiştirebilir ama personel kartına dokunamaz.
        val manager = StaffRole.MANAGER.yetkiOzetiTr()
        assertTrue(manager.any { it.startsWith("Paket ve ürün fiyatları") && it.endsWith("değiştirebilir") })
        assertTrue(manager.any { it.startsWith("Personel kartları") && it.endsWith("salt okunur") })
    }

    /**
     * Paket ve ürün tek satırda özetleniyor; ikisi ayrışırsa o satır yalan olur.
     *
     * Ayrışmaları teknik olarak mümkün — ikisi `SyncTable` içinde ayrı sabitler.
     * Bu test ayrışmayı yakalayıp özeti bölmeye zorluyor.
     */
    @Test
    fun `paket ve urun ayni yazma kuralini paylasiyor`() {
        assertEquals(SyncTable.PACKAGES.writableBy, SyncTable.PRODUCTS.writableBy)
    }

    /** Her rol için özet üretilebilmeli; eksik dal sessizce boş liste döndürmemeli. */
    @Test
    fun `her rol icin ozet uretiliyor`() {
        StaffRole.entries.forEach { role ->
            assertEquals(5, role.yetkiOzetiTr().size, "$role için özet eksik.")
        }
    }
}
