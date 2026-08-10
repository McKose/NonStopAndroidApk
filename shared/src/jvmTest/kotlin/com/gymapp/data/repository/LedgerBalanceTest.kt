package com.gymapp.data.repository

import com.gymapp.data.createTestDatabase
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.sync.SyncQueue
import com.gymapp.domain.Ids
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Üye bakiyesinin market hareketlerinden yalıtımı.
 *
 * Bu testler gerçek bir hatanın üzerine yazıldı: market satışı deftere
 * `category = MARKET`, `memberId = <üye>` ile PAYMENT olarak yazılıyor, ama
 * bakiye sorgusu kategoriden bağımsız topluyordu. Üye 100 TL'lik ürün aldığında
 * paket borcu 100 TL düşüyordu. Hata SQL düzeyindeydi; sahte DAO'yla yazılmış bir
 * test onu göremezdi.
 */
class LedgerBalanceTest {

    private lateinit var db: GymDatabase
    private lateinit var ledger: LedgerRepository

    private val memberId = Ids.new()

    @BeforeTest
    fun setUp() {
        db = createTestDatabase()
        ledger = LedgerRepository(db.ledgerDao(), SyncQueue(db.syncOutboxDao()))
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `market tahsilati paket borcunu kapatmaz`() = runTest {
        ledger.recordCharge(
            memberId = memberId,
            amount = Money.ofMajor(1000.0),
            description = "Paket satışı",
        ).getOrThrow()

        ledger.recordPayment(
            amount = Money.ofMajor(100.0),
            method = PaymentMethod.CASH,
            description = "Market satışı",
            category = LedgerCategory.MARKET,
            memberId = memberId,
        ).getOrThrow()

        assertEquals(
            Money.ofMajor(1000.0),
            ledger.outstandingBalance(memberId),
            "Market tahsilatı paket borcunu azaltmamalı",
        )
    }

    @Test
    fun `paket tahsilati borcu kapatir`() = runTest {
        ledger.recordCharge(
            memberId = memberId,
            amount = Money.ofMajor(1000.0),
            description = "Paket satışı",
        ).getOrThrow()

        ledger.recordPayment(
            amount = Money.ofMajor(1000.0),
            method = PaymentMethod.CASH,
            description = "Paket tahsilatı",
            memberId = memberId,
        ).getOrThrow()

        assertEquals(Money.ZERO, ledger.outstandingBalance(memberId))
    }

    /**
     * "Paket ödemesini geri al" market alışverişlerini de iptal ediyordu: kasada
     * kapanmış satışlar ciro toplamından siliniyor, üyenin borcu şişiyordu.
     */
    @Test
    fun `odeme geri alma market satisina dokunmaz`() = runTest {
        ledger.recordCharge(
            memberId = memberId,
            amount = Money.ofMajor(1000.0),
            description = "Paket satışı",
        ).getOrThrow()
        ledger.recordPayment(
            amount = Money.ofMajor(1000.0),
            method = PaymentMethod.CASH,
            description = "Paket tahsilatı",
            memberId = memberId,
        ).getOrThrow()
        ledger.recordPayment(
            amount = Money.ofMajor(100.0),
            method = PaymentMethod.CASH,
            description = "Market satışı",
            category = LedgerCategory.MARKET,
            memberId = memberId,
        ).getOrThrow()

        val reversed = ledger.reversePaymentsForMember(memberId, "test").getOrThrow()

        assertEquals(1, reversed, "Yalnızca paket tahsilatı iptal edilmeli")
        assertEquals(
            Money.ofMajor(1000.0),
            ledger.outstandingBalance(memberId),
            "Geri alma sonrası borç, market satışından etkilenmeden ilk hâline dönmeli",
        )
    }

    /** Ters kayıt idempotent: aynı tahsilat iki kez iptal edilmez. */
    @Test
    fun `geri alma tekrarlandiginda ikinci kez iptal olmaz`() = runTest {
        ledger.recordCharge(
            memberId = memberId,
            amount = Money.ofMajor(500.0),
            description = "Paket satışı",
        ).getOrThrow()
        ledger.recordPayment(
            amount = Money.ofMajor(500.0),
            method = PaymentMethod.CASH,
            description = "Paket tahsilatı",
            memberId = memberId,
        ).getOrThrow()

        assertEquals(1, ledger.reversePaymentsForMember(memberId, "test").getOrThrow())
        assertEquals(0, ledger.reversePaymentsForMember(memberId, "test").getOrThrow())
        assertEquals(Money.ofMajor(500.0), ledger.outstandingBalance(memberId))
    }
}
