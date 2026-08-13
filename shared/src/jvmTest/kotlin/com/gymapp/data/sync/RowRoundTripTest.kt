package com.gymapp.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Satırın sunucuya gidip geri gelmesi kayıpsız olmalı.
 *
 * ### Neden bu test var
 * [RowPayloads] ve [RowParsers] aynı kolon adlarını iki ayrı yerde tutuyor.
 * Sapmaları sessiz: bir kolon adı yalnızca yazma tarafında değiştirilirse
 * gönderim çalışmaya devam eder, okuma o alanı bulamaz ve **varsayılan değer**
 * üretir. Sonuç, sunucudaki doğru verinin cihazda yanlış bir değerle
 * değişmesi — ve o yanlış değerin bir sonraki gönderimde sunucuya geri
 * yazılması.
 *
 * Gidiş-dönüş bu sapmayı tek bir iddiada yakalıyor: satır JSON'a çevrilip geri
 * okunduğunda birebir aynı olmalı.
 *
 * Örnek satırlar [SampleRows] içinde ve isteğe bağlı alanlar bilinçli olarak
 * dolu; boş bırakılsalardı "hiç yazılmayan alan" ile "boş olan alan" aynı
 * görünürdü.
 */
class RowRoundTripTest {

    private fun jsonOf(payload: JsonObject): JsonObject =
        Json.parseToJsonElement(payload.toString()).jsonObject

    @Test
    fun `uye gidis donuste korunur`() {
        assertEquals(
            SampleRows.member,
            RowParsers.member(jsonOf(RowPayloads.of(SampleRows.member))),
        )
    }

    @Test
    fun `paket gidis donuste korunur`() {
        assertEquals(
            SampleRows.packageRow,
            RowParsers.packageRow(jsonOf(RowPayloads.of(SampleRows.packageRow))),
        )
    }

    @Test
    fun `urun gidis donuste korunur`() {
        assertEquals(
            SampleRows.product,
            RowParsers.product(jsonOf(RowPayloads.of(SampleRows.product))),
        )
    }

    @Test
    fun `randevu gidis donuste korunur`() {
        assertEquals(
            SampleRows.appointment,
            RowParsers.appointment(jsonOf(RowPayloads.of(SampleRows.appointment))),
        )
    }

    @Test
    fun `siparis gidis donuste korunur`() {
        assertEquals(
            SampleRows.order,
            RowParsers.order(jsonOf(RowPayloads.of(SampleRows.order))),
        )
    }

    @Test
    fun `olcum gidis donuste korunur`() {
        assertEquals(
            SampleRows.measurement,
            RowParsers.measurement(jsonOf(RowPayloads.of(SampleRows.measurement))),
        )
    }

    @Test
    fun `defter kaydi gidis donuste korunur`() {
        assertEquals(
            SampleRows.ledgerEntry,
            RowParsers.ledgerEntry(jsonOf(RowPayloads.of(SampleRows.ledgerEntry))),
        )
    }

    @Test
    fun `stok hareketi gidis donuste korunur`() {
        assertEquals(
            SampleRows.stockMovement,
            RowParsers.stockMovement(jsonOf(RowPayloads.of(SampleRows.stockMovement))),
        )
    }

    /**
     * Personel, şifre **hariç** korunur.
     *
     * Şifre sunucuya hiç gitmiyor, dolayısıyla geri de gelmiyor. Bu testin
     * değeri kuralı okuma tarafından da bağlaması: biri ileride şifreyi
     * gönderime eklerse burada dönen satır artık boş şifreli olmaz ve test
     * düşer.
     */
    @Test
    fun `personel sifre haric gidis donuste korunur`() {
        val geri = RowParsers.staff(jsonOf(RowPayloads.of(SampleRows.staff)))

        assertNotNull(geri)
        assertEquals("", geri.password, "Şifre sunucudan gelmemeli")
        assertEquals(SampleRows.staff.copy(password = ""), geri)
    }

    /**
     * Zorunlu alanı eksik satır **okunmaz**.
     *
     * Varsayılan üretmek, sunucudaki gerçek veriyi sessizce yanlış bir değerle
     * değiştirmek olurdu; üstelik o değer bir sonraki gönderimde sunucuya geri
     * yazılırdı. Eksik satırı atlamak, veriyi bozmaktan iyi.
     */
    @Test
    fun `zorunlu alani eksik satir okunmaz`() {
        val tam = RowPayloads.of(SampleRows.member).toString()

        for (alan in listOf("id", "tenant_id", "full_name", "phone", "created_at_ms", "updated_at_ms")) {
            val eksik = jsonOf(
                Json.parseToJsonElement(tam.replace("\"$alan\"", "\"yok_$alan\"")).jsonObject
            )
            assertNull(RowParsers.member(eksik), "$alan eksikken satır okunmamalı")
        }
    }

    /**
     * Boş metin ile `null` ayrı tutuluyor.
     *
     * Kullanıcı bir notu silmiş olabilir: `""` "silindi", `null` "hiç girilmedi".
     * İkisini birleştirmek silinen notun geri gelmesine yol açardı.
     */
    @Test
    fun `bos metin ile null ayrilir`() {
        val bosNot = SampleRows.member.copy(notes = "", healthNotes = null)

        val geri = assertNotNull(RowParsers.member(jsonOf(RowPayloads.of(bosNot))))
        assertEquals("", geri.notes)
        assertNull(geri.healthNotes)
    }

    /** Tombstone bilgisi de taşınmalı; yoksa silme cihaza hiç inmez. */
    @Test
    fun `tombstone bilgisi tasinir`() {
        val silinmis = SampleRows.product.copy(deletedAtMs = 12_345)

        assertEquals(
            12_345L,
            RowParsers.product(jsonOf(RowPayloads.of(silinmis)))?.deletedAtMs,
        )
    }
}
