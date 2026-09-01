import { test } from "node:test";
import assert from "node:assert/strict";
import { ayBasi, uyeDagilimi, yaklasanBitisler, defterToplami } from "./ozet.js";

const GUN = 24 * 60 * 60 * 1000;

test("üyeler durumlara göre sayılır", () => {
  const simdi = 1_000_000;
  const uyeler = [
    { end_date_ms: simdi + GUN },              // aktif
    { end_date_ms: simdi - GUN },              // süresi doldu
    { status: "FROZEN", end_date_ms: simdi + GUN },
    { status: "ARCHIVED", end_date_ms: simdi + GUN },
    { end_date_ms: null },                     // süresiz
  ];

  assert.deepEqual(uyeDagilimi(uyeler, simdi), {
    AKTIF: 1, SURESI_DOLDU: 1, DONDURULDU: 1, ARSIVDE: 1, SURESIZ: 1,
  });
});

test("silinmiş üye hiçbir sayıma girmez", () => {
  // Tombstone satırlar sunucudan geliyor; sayıma katılsalardı silinen üye
  // "aktif" olarak görünmeye devam ederdi.
  const simdi = 1_000_000;
  const sayim = uyeDagilimi([{ end_date_ms: simdi + GUN, deleted_at_ms: 5 }], simdi);
  assert.deepEqual(sayim, { AKTIF: 0, SURESI_DOLDU: 0, DONDURULDU: 0, ARSIVDE: 0, SURESIZ: 0 });
});

test("yaklaşan bitişler yalnızca ileri tarihli", () => {
  const simdi = 1_000_000;
  const uyeler = [
    { id: "gecmis", end_date_ms: simdi - GUN },
    { id: "yarin", end_date_ms: simdi + GUN },
    { id: "onGun", end_date_ms: simdi + 10 * GUN },
    { id: "birAy", end_date_ms: simdi + 30 * GUN },
    { id: "suresiz", end_date_ms: null },
  ];

  // Süresi dolmuş olanlar burada değil: onlar zaten ayrı sayımda.
  assert.deepEqual(yaklasanBitisler(uyeler, simdi, 14).map((u) => u.id), ["yarin", "onGun"]);
});

test("yaklaşan bitişler tarihe göre sıralı", () => {
  const simdi = 1_000_000;
  const uyeler = [
    { id: "gec", end_date_ms: simdi + 10 * GUN },
    { id: "erken", end_date_ms: simdi + GUN },
  ];
  assert.deepEqual(yaklasanBitisler(uyeler, simdi).map((u) => u.id), ["erken", "gec"]);
});

test("arşivlenmiş ve silinmiş üyeler yaklaşan bitişlerde yok", () => {
  const simdi = 1_000_000;
  const uyeler = [
    { id: "arsiv", status: "ARCHIVED", end_date_ms: simdi + GUN },
    { id: "silinmis", end_date_ms: simdi + GUN, deleted_at_ms: 5 },
    { id: "normal", end_date_ms: simdi + GUN },
  ];
  assert.deepEqual(yaklasanBitisler(uyeler, simdi).map((u) => u.id), ["normal"]);
});

test("defter toplamı türe göre ayrışır", () => {
  const kayitlar = [
    { type: "PAYMENT", amount_minor: 10000, occurred_at_ms: 100 },
    { type: "PAYMENT", amount_minor: 5000, occurred_at_ms: 150 },
    { type: "EXPENSE", amount_minor: 3000, occurred_at_ms: 120 },
    { type: "CHARGE", amount_minor: 20000, occurred_at_ms: 110 },
  ];
  assert.deepEqual(defterToplami(kayitlar, 0, 200), {
    PAYMENT: 15000, CHARGE: 20000, EXPENSE: 3000,
  });
});

/**
 * Özet kutusundaki "Bu ay tahsilat" iptal edilen kayıtla ŞİŞMİYOR.
 *
 * Ters kayıt orijinalin birebir kopyası: aynı tür, aynı pozitif tutar. İkisi de
 * sayıldığında tutar götürülmüyor, ikiye katlanıyor — yani iptal edilen bir
 * tahsilat ciroyu düşürmek yerine artırıyordu. Kutu tek bir sayı gösterdiği
 * için hata da tek bir yanlış sayı olarak, hiçbir uyarı vermeden görünüyordu.
 */
test("iptal edilen tahsilat ay toplamını artırmıyor", () => {
  const kayitlar = [
    { id: "a", type: "PAYMENT", amount_minor: 100000, occurred_at_ms: 100, reverses_id: null },
    { id: "a-ters", type: "PAYMENT", amount_minor: 100000, occurred_at_ms: 150, reverses_id: "a" },
    { id: "b", type: "PAYMENT", amount_minor: 25000, occurred_at_ms: 120, reverses_id: null },
  ];

  assert.equal(defterToplami(kayitlar, 0, 200).PAYMENT, 25000);
});

test("aralık dışındaki kayıtlar toplanmaz", () => {
  const kayitlar = [
    { type: "PAYMENT", amount_minor: 100, occurred_at_ms: 50 },
    { type: "PAYMENT", amount_minor: 200, occurred_at_ms: 150 },
    { type: "PAYMENT", amount_minor: 400, occurred_at_ms: 250 },
  ];
  // Sınırlar dahil: 100 ve 200 aralıkta, 50 ve 250 dışında.
  assert.equal(defterToplami(kayitlar, 100, 200).PAYMENT, 200);
});

test("bozuk tutar toplamı bozmaz", () => {
  // Tek bozuk satır bütün raporu NaN yapmamalı.
  const kayitlar = [
    { type: "PAYMENT", amount_minor: "abc", occurred_at_ms: 100 },
    { type: "PAYMENT", amount_minor: 500, occurred_at_ms: 100 },
  ];
  assert.equal(defterToplami(kayitlar, 0, 200).PAYMENT, 500);
});

test("ay başı ayın ilk gününe gider", () => {
  const ortasi = new Date(2026, 7, 13, 15, 30).getTime(); // 13 Ağustos 2026
  const bas = new Date(ayBasi(ortasi));
  assert.equal(bas.getDate(), 1);
  assert.equal(bas.getMonth(), 7);
  assert.equal(bas.getHours(), 0);
});
