package com.gymapp.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v8 → v9 migration.
 *
 * 1) Üyeye gömülü paket alanlarını koruyarak yeni `member_packages` tablosunu kurar.
 *    Mevcut aktif paketi olan her üye için bir satır insert eder (status=ACTIVE).
 * 2) `multisport_rates` tablosunu kurar. AppPreferences.multiSportCommission varsa
 *    ilk kayıt olarak eklenir — bu işi DatabaseModule yapar (prefs'e erişim gerek).
 * 3) `installment_commissions` tablosunu kurar. Varsayılan oranlar DatabaseModule
 *    tarafından seed edilir.
 * 4) `posture_comments` tablosunu kurar.
 * 5) `transactions` tablosuna staffId, memberPackageId, installmentCount,
 *    installmentSurchargeAmount kolonlarını ekler.
 *
 * Not: MemberEntity paket-ilişkili kolonları (activePackageId, totalSessions vb.)
 * şimdilik mevcut durumda bırakılır; ileride temizlenebilir.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ─── member_packages ────────────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS member_packages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                member_id INTEGER NOT NULL,
                package_id INTEGER NOT NULL,
                package_name_snapshot TEXT NOT NULL,
                package_type TEXT NOT NULL,
                total_sessions INTEGER NOT NULL,
                remaining_sessions INTEGER NOT NULL,
                start_date_ms INTEGER NOT NULL,
                end_date_ms INTEGER NOT NULL,
                package_price REAL NOT NULL,
                discount REAL NOT NULL DEFAULT 0,
                installment_surcharge REAL NOT NULL DEFAULT 0,
                price_paid REAL NOT NULL,
                payment_type TEXT NOT NULL,
                installment_count INTEGER NOT NULL DEFAULT 1,
                payment_status TEXT NOT NULL DEFAULT 'PENDING',
                payment_date_ms INTEGER,
                status TEXT NOT NULL DEFAULT 'ACTIVE',
                created_at_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                FOREIGN KEY(member_id) REFERENCES gym_members(id) ON DELETE CASCADE,
                FOREIGN KEY(package_id) REFERENCES gym_packages(id) ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_member_packages_member_id ON member_packages(member_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_member_packages_package_id ON member_packages(package_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_member_packages_status ON member_packages(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_member_packages_end_date_ms ON member_packages(end_date_ms)")

        // Mevcut aktif paketi olan üyeler için snapshot — paket meta bilgisi olan
        // gym_packages ile join ederek taşıyoruz.
        db.execSQL("""
            INSERT INTO member_packages (
                member_id, package_id, package_name_snapshot, package_type,
                total_sessions, remaining_sessions, start_date_ms, end_date_ms,
                package_price, discount, installment_surcharge, price_paid,
                payment_type, installment_count, payment_status, payment_date_ms,
                status, created_at_ms, updated_at_ms
            )
            SELECT
                m.id,
                m.activePackageId,
                COALESCE(p.name, 'Legacy Paket'),
                COALESCE(p.type, 'FITNESS'),
                m.totalSessions,
                m.remainingSessions,
                COALESCE(m.startDateMs, m.createdAtMs),
                COALESCE(m.endDateMs, m.createdAtMs),
                m.packagePrice,
                m.discount,
                0.0,
                m.pricePaid,
                m.paymentType,
                m.installmentCount,
                m.paymentStatus,
                m.paymentDateMs,
                CASE WHEN m.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'HISTORY' END,
                m.createdAtMs,
                m.updatedAtMs
            FROM gym_members m
            LEFT JOIN gym_packages p ON p.id = m.activePackageId
            WHERE m.activePackageId IS NOT NULL
        """.trimIndent())

        // ─── multisport_rates ───────────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS multisport_rates (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                amount REAL NOT NULL,
                effectiveFromMs INTEGER NOT NULL,
                supersededByMs INTEGER,
                note TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_multisport_rates_effectiveFromMs ON multisport_rates(effectiveFromMs)")

        // ─── installment_commissions ────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS installment_commissions (
                installmentCount INTEGER PRIMARY KEY NOT NULL,
                ratePercent REAL NOT NULL
            )
        """.trimIndent())

        // ─── posture_comments ───────────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS posture_comments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                memberId INTEGER NOT NULL,
                dateMs INTEGER NOT NULL,
                comment TEXT NOT NULL,
                authorStaffId INTEGER,
                FOREIGN KEY(memberId) REFERENCES gym_members(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_posture_comments_memberId ON posture_comments(memberId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_posture_comments_dateMs ON posture_comments(dateMs)")

        // ─── transactions genişletme ────────────────────────────────────────────
        db.execSQL("ALTER TABLE transactions ADD COLUMN staffId INTEGER")
        db.execSQL("ALTER TABLE transactions ADD COLUMN memberPackageId INTEGER")
        db.execSQL("ALTER TABLE transactions ADD COLUMN installmentCount INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE transactions ADD COLUMN installmentSurchargeAmount REAL NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_staffId ON transactions(staffId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_memberPackageId ON transactions(memberPackageId)")
    }
}
