// Panelin özet sayıları.
//
// ### Neyin hesaplandığı, neyin hesaplanmadığı
// Burada yalnızca **sayma ve düz toplama** var: kaç aktif üye, seçilen aralıkta
// ne kadar tahsilat, ne kadar gider. Bunlar rapor; iş kuralı değil.
//
// Üye bazında **bakiye** ve **hakediş** bilinçli olarak yok. İkisi de ortak
// Kotlin modülünde tanımlı gerçek iş kuralları (ters kayıt mantığı, seans
// matrahının dondurulması, oran birimleri) ve buraya kopyalanmaları sessiz bir
// sapma üretirdi: panelde farklı, uygulamada farklı bir rakam. Panelde bunlar
// gerektiğinde doğru yol, tanımı sunucuda tek bir görünüme (view) taşımak —
// böylece iki taraf da aynı yerden okur.

import { uyelikDurumu } from "./domain.js";

/** Ayın ilk gününün epoch ms karşılığı. */
export function ayBasi(simdiMs) {
  const t = new Date(simdiMs);
  return new Date(t.getFullYear(), t.getMonth(), 1).getTime();
}

/**
 * Üyelerin durumlara göre dağılımı.
 *
 * Durum, uygulamadakiyle aynı kuralla türetiliyor (bitiş tarihi + elle konan
 * durum); ayrı bir "aktif mi" kolonu okunmuyor çünkü öyle bir kolon yok — ve
 * olmaması bilinçli.
 */
export function uyeDagilimi(uyeler, simdiMs) {
  const sayim = { AKTIF: 0, SURESI_DOLDU: 0, DONDURULDU: 0, ARSIVDE: 0, SURESIZ: 0 };
  for (const uye of uyeler) {
    const durum = uyelikDurumu(uye, simdiMs);
    if (durum in sayim) sayim[durum] += 1;
  }
  return sayim;
}

/**
 * Yakında bitecek üyelikler.
 *
 * Süresi **dolmuş** olanlar dahil değil: onlar zaten "süresi doldu" sayımında.
 * Buradaki liste "aramak için hâlâ vakit var" demek.
 */
export function yaklasanBitisler(uyeler, simdiMs, gun = 14) {
  const sinir = simdiMs + gun * 24 * 60 * 60 * 1000;
  return uyeler
    .filter((u) => {
      if (u.deleted_at_ms) return false;
      if (u.status === "ARCHIVED") return false;
      const bitis = u.end_date_ms == null ? null : Number(u.end_date_ms);
      return bitis !== null && bitis >= simdiMs && bitis <= sinir;
    })
    .sort((a, b) => Number(a.end_date_ms) - Number(b.end_date_ms));
}

/**
 * Aralıktaki defter toplamları.
 *
 * `type` yönü taşıyor ve tutarlar **daima pozitif** — sunucudaki kısıt da bunu
 * garanti ediyor. İşaretle yön belirtilseydi bir eksi unutulduğunda tahsilat
 * gider gibi toplanırdı.
 *
 * Ters kayıtlar (`reverses_id` dolu) toplamdan **düşülmüyor, çıkarılıyor**:
 * ters kayıt kendi türüyle zaten karşı yönde yazılıyor. Burada ayrıca işlem
 * yapmak çift sayıma yol açardı.
 */
export function defterToplami(kayitlar, baslangicMs, bitisMs) {
  const toplam = { PAYMENT: 0, CHARGE: 0, EXPENSE: 0 };
  for (const k of kayitlar) {
    const an = Number(k.occurred_at_ms);
    if (!Number.isFinite(an) || an < baslangicMs || an > bitisMs) continue;
    const tutar = Number(k.amount_minor);
    if (!Number.isFinite(tutar)) continue;
    if (k.type in toplam) toplam[k.type] += tutar;
  }
  return toplam;
}
