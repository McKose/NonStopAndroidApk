package com.gymapp.data.local.entity

import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.LedgerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Hangi kayıt hâlâ yaşıyor" kuralının testi.
 *
 * Defter append-only: iptal edilen kayıt listeden çıkmıyor, yanına bir ters
 * kayıt ekleniyor. Yani listede aynı tutar İKİ kez görünüyor ve hangisinin
 * geçerli olduğu ancak [LedgerEntryEntity.reversesId] bağlarına bakılarak
 * anlaşılıyor.
 *
 * Süzgeç yanlış olduğunda ekranda hata çıkmıyor, yalnızca yanlış şey
 * gösteriliyor: kullanıcıya iptal edilmiş bir kayıt "iptal edilebilir" diye
 * sunuluyor, seçiliyor, hiçbir şey olmuyor ve sebebi hiçbir yerde yazmıyor.
 */
class AktifKayitlarTesti {

    private fun kayit(
        id: String,
        tur: LedgerType = LedgerType.PAYMENT,
        tersledigi: String? = null,
    ) = LedgerEntryEntity(
        id = id,
        tenantId = "t",
        type = tur,
        category = LedgerCategory.MEMBERSHIP,
        amountMinor = 100_00,
        description = id,
        occurredAtMs = 0,
        reversesId = tersledigi,
        createdAtMs = 0,
    )

    @Test
    fun `iptal edilmemis kayitlarin hepsi yasiyor`() {
        val liste = listOf(kayit("a"), kayit("b", LedgerType.CHARGE))

        assertEquals(listOf("a", "b"), liste.aktifKayitlar().map { it.id })
    }

    /**
     * İptal edilmiş kayıt DA, onu iptal eden ters kayıt DA düşüyor.
     *
     * İki yönlü olması şart. Yalnızca `reversesId == null` bakılsaydı iptal
     * edilmiş asıl kayıt hâlâ yaşıyor görünürdü ve kullanıcıya ikinci kez
     * iptal için sunulurdu. Yalnızca "birinin işaret ettiği" ayıklansaydı bu
     * kez ters kaydın kendisi listede kalır ve iptal edilmeye çalışılırdı —
     * iptalin iptali, tutarı toplamlara geri getirirdi.
     */
    @Test
    fun `iptal edilen kayit da ters kaydi da dusuyor`() {
        val liste = listOf(
            kayit("a"),
            kayit("b"),
            kayit("b-ters", tersledigi = "b"),
        )

        assertEquals(listOf("a"), liste.aktifKayitlar().map { it.id })
    }

    /** Ters kaydın işaret ettiği kimlikler doğru toplanıyor. */
    @Test
    fun `iptal edilen kimlikler ters kayitlardan okunuyor`() {
        val liste = listOf(
            kayit("a"),
            kayit("a-ters", tersledigi = "a"),
            kayit("c"),
            kayit("c-ters", tersledigi = "c"),
        )

        assertEquals(setOf("a", "c"), liste.iptalEdilenKimlikler())
    }

    /**
     * Ters kaydı listede olmayan kayıt yaşıyor sayılıyor.
     *
     * Ekran her zaman kaydın tamamını görmüyor (dönem süzgeci, sayfalama).
     * Kural "listede ters kaydı yoksa yaşıyor" — sunucu tarafı zaten ikinci
     * kez iptal etmiyor, dolayısıyla en kötü ihtimalde işlem sonuçsuz kalıyor,
     * tutar iki kez düşmüyor.
     */
    @Test
    fun `eksik listede kayit yasiyor sayiliyor`() {
        val liste = listOf(kayit("a"))

        assertEquals(listOf("a"), liste.aktifKayitlar().map { it.id })
        assertTrue(liste.iptalEdilenKimlikler().isEmpty())
    }

    @Test
    fun `bos liste bos donuyor`() {
        assertTrue(emptyList<LedgerEntryEntity>().aktifKayitlar().isEmpty())
    }
}
