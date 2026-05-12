package com.gymapp.presentation.finance

import android.content.Context
import android.net.Uri
import com.gymapp.data.local.entity.TransactionEntity
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExcelExporter {

    fun exportTransactionsToExcel(
        context: Context,
        uri: Uri,
        transactions: List<TransactionEntity>
    ) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Finansal Hareketler")
        val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr"))

        // Header Row
        val headerRow = sheet.createRow(0)
        val headers = listOf("Tarih", "Tip", "Kategori", "Açıklama", "Yöntem", "Tutar", "Durum", "Not")
        headers.forEachIndexed { index, title ->
            headerRow.createCell(index).setCellValue(title)
        }

        // Data Rows
        transactions.forEachIndexed { index, tx ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(dateFmt.format(Date(tx.date)))
            row.createCell(1).setCellValue(if (tx.type == "INCOME") "Gelir" else "Gider")
            row.createCell(2).setCellValue(categoryLabel(tx.category))
            row.createCell(3).setCellValue(tx.description)
            row.createCell(4).setCellValue(paymentMethodLabel(tx.paymentMethod))
            row.createCell(5).setCellValue(tx.amount)
            row.createCell(6).setCellValue(if (tx.isPending) "Bekleyen" else "Ödendi")
            row.createCell(7).setCellValue(tx.note ?: "")
        }

        // Auto-size columns
        for (i in headers.indices) {
            sheet.autoSizeColumn(i)
        }

        try {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            outputStream?.use {
                workbook.write(it)
            }
        } finally {
            workbook.close()
        }
    }

    private fun categoryLabel(category: String): String = when (category) {
        "MEMBERSHIP" -> "Üyelik"
        "MULTISPORT_SESSION" -> "MultiSport Seans"
        "TRAINER_COMMISSION" -> "Antrenör Hakedişi"
        "SALARY" -> "Maaş"
        "RENT" -> "Kira"
        "UTILITY" -> "Fatura"
        "MARKET_SALE" -> "Market Satışı"
        "TAX_VAT" -> "KDV (Otomatik)"
        "TAX_INCOME" -> "Gelir Vergisi (Otomatik)"
        "OTHER" -> "Diğer"
        else -> category
    }

    private fun paymentMethodLabel(method: String): String = when (method) {
        "CARD" -> "Kart"
        "MULTISPORT" -> "MultiSport"
        "CASH" -> "Nakit"
        else -> method
    }
}
