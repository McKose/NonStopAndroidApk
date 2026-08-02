package com.gymapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberTest {

    /**
     * Regresyon: numara ham saklandığı için aynı kişi farklı formatlarda birden çok kez
     * kaydedilebiliyordu; `phone` üzerindeki UNIQUE index pratikte hiç çalışmıyordu.
     */
    @Test
    fun `ayni numaranin tum yazimlari ayni sonuca normalize olur`() {
        val expected = "+905551112233"
        listOf(
            "5551112233",
            "05551112233",
            "905551112233",
            "00905551112233",
            "+90 555 111 22 33",
            "0555 111 22 33",
            "(0555) 111-22-33",
        ).forEach { input ->
            assertEquals("Girdi: $input", expected, PhoneNumber.normalizeTr(input))
        }
    }

    @Test
    fun `gecersiz numaralar reddedilir`() {
        listOf(
            "",
            "123",
            "4441112233",      // cep değil (5 ile başlamıyor)
            "555111223",       // eksik hane
            "55511122334",     // fazla hane
            "abcdefghij",
        ).forEach { input ->
            assertNull("Girdi: $input", PhoneNumber.normalizeTr(input))
        }
    }

    @Test
    fun `ekran bicimi okunabilir`() {
        assertEquals("+90 555 111 22 33", PhoneNumber.formatForDisplay("+905551112233"))
    }

    @Test
    fun `beklenmeyen bicim oldugu gibi gosterilir`() {
        assertEquals("bilinmiyor", PhoneNumber.formatForDisplay("bilinmiyor"))
    }
}
