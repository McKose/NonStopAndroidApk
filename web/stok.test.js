// Stok toplamı testleri.
//
// Buradaki asıl konu doğru toplamak değil — o kolay kısım. Asıl konu **yanlış
// bir sayı üretmemek**: stok sayısı ekranda tek başına duruyor ve doğru
// görünüyor. Eksik veriden hesaplanmış bir stok, yanlış olduğunu gösteren hiçbir
// iz taşımaz; salon sahibi ona bakıp sipariş verir.

import test from "node:test";
import assert from "node:assert/strict";

import { stokHaritasi, stokYaz, stokUyarilari, stokDurumu } from "./stok.js";

const hareket = (urun, delta, sebep = "PURCHASE") => ({
  id: `h-${urun}-${delta}-${Math.random()}`,
  tenant_id: "t1",
  product_id: urun,
  quantity_delta: delta,
  reason: sebep,
  occurred_at_ms: 1_700_000_000_000,
  created_at_ms: 1_700_000_000_000,
});

test("stok, hareketlerin toplamı", () => {
  const harita = stokHaritasi([
    hareket("su", 24, "PURCHASE"),
    hareket("su", -2, "SALE"),
    hareket("su", -1, "SALE"),
    hareket("bar", 10, "PURCHASE"),
  ]);

  assert.equal(harita.get("su"), 21);
  assert.equal(harita.get("bar"), 10);
});

/**
 * Sebep süzgeci olmadığı uygulamadaki sorgudan geliyor; burada sabitleniyor.
 *
 * Biri "iadeler stoğa girmesin" diye düşünüp süzgeç eklerse panel ile uygulama
 * farklı stok gösterir — ve hangisinin doğru olduğu ancak elle sayarak anlaşılır.
 */
test("her sebep toplamaya giriyor: satış, alım, düzeltme, iade", () => {
  const harita = stokHaritasi([
    hareket("su", 100, "PURCHASE"),
    hareket("su", -30, "SALE"),
    hareket("su", -5, "CORRECTION"),
    hareket("su", 3, "RETURN"),
  ]);

  assert.equal(harita.get("su"), 68);
});

test("hiç hareketi olmayan ürün sıfır sayılıyor", () => {
  const harita = stokHaritasi([]);
  // Haritada yok, ama gösterimde 0 — uygulamadaki COALESCE(..., 0) ile aynı.
  assert.equal(harita.get("su"), undefined);
  assert.equal(stokYaz(harita, "su"), "0");
});

test("negatif stok gizlenmiyor", () => {
  // Fazla satış ya da eksik alım kaydı gerçek bir veri sorunu. Sıfıra
  // kırpılsaydı sorun ekrandan kaybolur, sebebi araştırılmazdı.
  const harita = stokHaritasi([hareket("su", 2), hareket("su", -5, "SALE")]);
  assert.equal(harita.get("su"), -3);
  assert.equal(stokYaz(harita, "su"), "-3");
});

// ─── Yanlış sayı üretmeme ───────────────────────────────────────────────────

test("okunamayan hareket ürünün stoğunu bilinmez yapıyor, toplamı bozmuyor", () => {
  const harita = stokHaritasi([
    hareket("su", 24),
    { ...hareket("su", 0), quantity_delta: "bilinmiyor" },
    hareket("su", -2, "SALE"),
  ]);

  assert.equal(harita.get("su"), null, "eksik veriden sayı üretilmemeli");
  assert.equal(stokYaz(harita, "su"), "?");
});

test("bir ürünün bozuk verisi diğerini etkilemiyor", () => {
  const harita = stokHaritasi([
    { ...hareket("su", 0), quantity_delta: null },
    hareket("bar", 7),
  ]);

  assert.equal(harita.get("su"), null);
  assert.equal(harita.get("bar"), 7, "sağlam ürün de bilinmez sayılmamalı");
});

/**
 * `Number()` tuzakları.
 *
 * Bu test yazılırken gerçekten yaşanan bir hatayı sabitliyor: kod `Number()`
 * kullanıyordu ve `Number(null)`, `Number("")`, `Number(false)` üçü de **sıfır**
 * veriyor. Yani eksik bir alan "0 adet hareket" olarak toplanıyor, toplam
 * olduğundan farklı çıkıyor ve sonuç makul bir sayı gibi görünüyordu — yanlış
 * olduğunu gösteren hiçbir iz yok.
 */
test("sıfıra çevrilen değerler adet sayılmıyor", () => {
  for (const bozuk of [null, undefined, "", false, true, [], {}, NaN, Infinity]) {
    const harita = stokHaritasi([hareket("su", 10), { ...hareket("su", 0), quantity_delta: bozuk }]);
    assert.equal(
      harita.get("su"),
      null,
      `${JSON.stringify(bozuk)} adet olarak kabul edilmiş — toplam sessizce kayar`,
    );
  }
});

test("metin biçimindeki tam sayı kabul ediliyor", () => {
  // Sunucu `integer` döndürüyor ama savunma amaçlı metin de okunuyor.
  const harita = stokHaritasi([hareket("su", 10), { ...hareket("su", 0), quantity_delta: "12" }]);
  assert.equal(harita.get("su"), 22);
});

test("kesirli adet reddediliyor", () => {
  // Adet `integer`; kesirli bir değer şema dışı ve toplamı sessizce kaydırırdı.
  const harita = stokHaritasi([hareket("su", 10), { ...hareket("su", 0), quantity_delta: 1.5 }]);
  assert.equal(harita.get("su"), null);
});

test("ürünsüz hareket atlanıyor, çökme yok", () => {
  const harita = stokHaritasi([
    { ...hareket("su", 5), product_id: null },
    { ...hareket("su", 5), product_id: "" },
    hareket("bar", 3),
  ]);
  assert.equal(harita.size, 1);
  assert.equal(harita.get("bar"), 3);
});

test("boş ve tanımsız girdi çökmüyor", () => {
  assert.equal(stokHaritasi([]).size, 0);
  assert.equal(stokHaritasi(null).size, 0);
  assert.equal(stokHaritasi(undefined).size, 0);
});

/**
 * Liste kesildiyse hiçbir stok sayısı gösterilmiyor.
 *
 * En önemli test. Sunucudan gelen satır sayısı sınıra dayandığında toplam
 * **eksik** hesaplanıyor ve sonuç tamamen makul bir sayı gibi görünüyor: 500
 * hareketin ilk 500'ü toplanmış bir stok, doğru bir stoktan ayırt edilemez.
 */
test("liste kesildiyse stok bilinmez gösteriliyor", () => {
  const harita = stokHaritasi([hareket("su", 24), hareket("su", -2, "SALE")]);

  assert.equal(stokYaz(harita, "su", true), "22");
  assert.equal(stokYaz(harita, "su", false), "?", "kesik listeden sayı gösterilmemeli");
});

// ─── Uyarılar ───────────────────────────────────────────────────────────────

test("tükenen, azalan ve bilinmeyen ayrı ayrı", () => {
  const urunler = [
    { id: "su", name: "Su" },
    { id: "bar", name: "Protein bar" },
    { id: "shaker", name: "Shaker" },
    { id: "havlu", name: "Havlu" },
    { id: "kilit", name: "Kilit" },
  ];
  const harita = stokHaritasi([
    hareket("su", 40),
    hareket("bar", 3),
    hareket("shaker", 0),
    hareket("havlu", 5),
    { ...hareket("kilit", 0), quantity_delta: "x" },
  ]);

  const g = stokUyarilari(urunler, harita, 5);

  assert.deepEqual(g.yeterli.map((u) => u.id), ["su"]);
  assert.deepEqual(g.azaliyor.map((u) => u.id), ["bar", "havlu"], "eşik dahil olmalı");
  assert.deepEqual(g.tukendi.map((u) => u.id), ["shaker"]);
  assert.deepEqual(g.bilinmiyor.map((u) => u.id), ["kilit"]);
});

test("hiç hareketi olmayan ürün tükenmiş sayılıyor", () => {
  // Stoğu sıfır olan ürün ile hiç hareketi olmayan ürün aynı durumda: elde yok.
  const g = stokUyarilari([{ id: "su" }], stokHaritasi([]), 5);
  assert.deepEqual(g.tukendi.map((u) => u.id), ["su"]);
});

/**
 * Negatif stok AYRI bir durum: `tukendi` değil `eksi`.
 *
 * Sayaçlar ve tablodaki rozetler aynı sınıflandırmayı kullanıyor; negatifi
 * "tükendi" saymak kutuda 3 yazarken tabloda 2 rozet görünmesine yol açıyordu.
 */
test("negatif stok ayrı sınıflandırılıyor", () => {
  const harita = stokHaritasi([hareket("su", -4, "SALE")]);
  const g = stokUyarilari([{ id: "su" }], harita, 5);
  assert.deepEqual(g.eksi.map((u) => u.id), ["su"]);
  assert.equal(g.tukendi.length, 0, "negatif, tükenmişten farklı");
  assert.equal(g.azaliyor.length, 0);
  assert.equal(stokDurumu(harita, "su"), "eksi");
});

test("her ürün tam olarak bir gruba giriyor", () => {
  const urunler = [{ id: "a" }, { id: "b" }, { id: "c" }, { id: "d" }, { id: "e" }];
  const harita = stokHaritasi([
    hareket("a", 40), hareket("b", 3), hareket("c", 0), hareket("d", -2, "SALE"),
    { ...hareket("e", 0), quantity_delta: "x" },
  ]);
  const g = stokUyarilari(urunler, harita);
  const toplam = Object.values(g).reduce((n, l) => n + l.length, 0);
  assert.equal(toplam, urunler.length, "bir ürün ya sayılmamış ya iki kez sayılmış");
});

test("stoğu bilinmeyen ürün ne yeterli ne tükenmiş sayılıyor", () => {
  const harita = stokHaritasi([{ ...hareket("su", 0), quantity_delta: "x" }]);
  const g = stokUyarilari([{ id: "su" }], harita, 5);
  assert.equal(g.tukendi.length, 0, "bilinmeyen stok tükenmiş sayılmamalı");
  assert.equal(g.yeterli.length, 0, "bilinmeyen stok yeterli de sayılmamalı");
  assert.deepEqual(g.bilinmiyor.map((u) => u.id), ["su"]);
});

/**
 * Her stok durumunun bir CSS sınıfı olmalı.
 *
 * Olmazsa rozet **sessizce** biçimsiz görünür: metin doğru, renk yok. Testsiz
 * kalırsa yeni bir durum eklendiğinde fark edilmesi ancak ekrana bakmakla
 * mümkün — ve o durum yalnızca belirli veride ortaya çıkıyorsa hiç görülmez
 * (ör. "eksi stok" yalnızca negatif stokta).
 */
test("her stok durumunun CSS sınıfı var", async () => {
  const { readFileSync } = await import("node:fs");
  const { fileURLToPath } = await import("node:url");
  const { dirname, join } = await import("node:path");

  const burada = dirname(fileURLToPath(import.meta.url));
  const css = readFileSync(join(burada, "styles.css"), "utf8");

  // Bütün durumlar: sınıflandırmanın döndürebileceği her değer.
  const urunler = [{ id: "a" }, { id: "b" }, { id: "c" }, { id: "d" }, { id: "e" }];
  const harita = stokHaritasi([
    hareket("a", 40), hareket("b", 3), hareket("c", 0), hareket("d", -2, "SALE"),
    { ...hareket("e", 0), quantity_delta: "x" },
  ]);
  const durumlar = Object.keys(stokUyarilari(urunler, harita));

  for (const durum of durumlar) {
    assert.ok(
      css.includes(`.rozet-stok-${durum}`),
      `\`.rozet-stok-${durum}\` styles.css içinde yok; rozet biçimsiz görünür.`,
    );
  }
});
