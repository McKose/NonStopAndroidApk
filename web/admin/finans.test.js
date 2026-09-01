// Finans panosunun hesap testleri.
//
// Bu dosyanın sınadığı şeylerin ortak özelliği: hepsi YANLIŞ OLDUĞUNDA ekranda
// düzgün görünüyor. Şişmiş bir ciro, eksik bir kategori, kayıp bir ay — hiçbiri
// hata üretmiyor, yalnızca salon sahibine yanlış rakam gösteriyor. Kağıt üzerinde
// tutan bir rapor, tutmayan bir rapordan daha tehlikeli.

import test from "node:test";
import assert from "node:assert/strict";

import {
  FINANS_KATEGORI_ETIKETLERI,
  defterOzeti,
  kategoriKirilimi,
  yontemKirilimi,
  aylikSeyir,
  kayitIptalDurumu,
  kategoriEtiketi,
} from "./finans.js";
import { aktifDefterKayitlari, iptalEdilenKimlikler } from "./domain.js";

const AN = new Date(2026, 7, 15, 12, 0).getTime(); // 15 Ağustos 2026, yerel

function kayit(alanlar = {}) {
  return {
    id: alanlar.id ?? "k1",
    type: alanlar.type ?? "PAYMENT",
    category: alanlar.category ?? "MEMBERSHIP",
    amount_minor: alanlar.amount_minor ?? 100_00,
    payment_method: alanlar.payment_method ?? "CASH",
    description: alanlar.description ?? "kayıt",
    occurred_at_ms: alanlar.occurred_at_ms ?? AN,
    reverses_id: alanlar.reverses_id ?? null,
  };
}

// ─── İptal edilen kayıtların elenmesi ───────────────────────────────────────

/**
 * Düzeltilen hatanın kendisi.
 *
 * Ters kayıt orijinalin birebir kopyası: aynı tür, aynı POZİTİF tutar. İkisi
 * de sayıldığında tutar götürülmüyor, ikiye katlanıyor — yani iptal edilen bir
 * tahsilat ciroyu düşürmek yerine artırıyordu. Panelin eski `defterToplami`
 * yorumu tam olarak bunun tersini iddia ediyordu.
 */
test("iptal edilen tahsilat ciroyu ARTIRMIYOR", () => {
  const kayitlar = [
    kayit({ id: "a", amount_minor: 1_000_00 }),
    kayit({ id: "a-ters", amount_minor: 1_000_00, reverses_id: "a" }),
  ];

  const ozet = defterOzeti(kayitlar);
  assert.equal(ozet.tahsilat, 0, "çiftin iki tarafı da elenmeliydi");
  assert.equal(ozet.kayitSayisi, 0);
});

test("süzgeç iki yönlü: asıl kayıt da ters kayıt da düşüyor", () => {
  const kayitlar = [
    kayit({ id: "a" }),
    kayit({ id: "b" }),
    kayit({ id: "b-ters", reverses_id: "b" }),
  ];

  assert.deepEqual(aktifDefterKayitlari(kayitlar).map((k) => k.id), ["a"]);
  assert.deepEqual([...iptalEdilenKimlikler(kayitlar)], ["b"]);
});

/** İptal SAYISI çift başına: bir iptal iki satır üretiyor. */
test("iptal sayısı satır değil çift sayıyor", () => {
  const kayitlar = [
    kayit({ id: "a" }), kayit({ id: "a-ters", reverses_id: "a" }),
    kayit({ id: "b" }), kayit({ id: "b-ters", reverses_id: "b" }),
  ];

  assert.equal(defterOzeti(kayitlar).iptalSayisi, 2);
});

test("her kaydın iptal durumu ayırt ediliyor", () => {
  const kayitlar = [
    kayit({ id: "a" }),
    kayit({ id: "b" }),
    kayit({ id: "b-ters", reverses_id: "b" }),
  ];
  const iptaller = iptalEdilenKimlikler(kayitlar);

  assert.equal(kayitIptalDurumu(kayitlar[0], iptaller), "normal");
  assert.equal(kayitIptalDurumu(kayitlar[1], iptaller), "iptal");
  // Ters kaydı "iptal" saymak yanıltıcı olurdu: listede aynı tutar iki kez
  // görünüyor ve hangisinin düzeltme olduğu yalnızca bu ayrımla anlaşılıyor.
  assert.equal(kayitIptalDurumu(kayitlar[2], iptaller), "ters");
});

// ─── Özet ───────────────────────────────────────────────────────────────────

/**
 * Tahakkuk gelire eklenmiyor.
 *
 * Eklenseydi ciro, kasaya hiç girmemiş borçla şişerdi ve salon sahibi olmayan
 * parayı almış görünürdü — uygulamada bir kez düzeltilen hatanın aynısı.
 */
test("tahakkuk tahsilata eklenmiyor, ayrı duruyor", () => {
  const ozet = defterOzeti([
    kayit({ id: "a", type: "PAYMENT", amount_minor: 500_00 }),
    kayit({ id: "b", type: "CHARGE", amount_minor: 900_00 }),
    kayit({ id: "c", type: "EXPENSE", amount_minor: 200_00 }),
  ]);

  assert.equal(ozet.tahsilat, 500_00);
  assert.equal(ozet.gider, 200_00);
  assert.equal(ozet.tahakkuk, 900_00);
  assert.equal(ozet.net, 300_00, "net = tahsilat − gider; tahakkuk girmiyor");
});

test("bozuk tutar raporu NaN yapmıyor", () => {
  const ozet = defterOzeti([
    kayit({ id: "a", amount_minor: "abc" }),
    kayit({ id: "b", amount_minor: 500_00 }),
  ]);
  assert.equal(ozet.tahsilat, 500_00);
});

test("boş defter sıfır döndürüyor", () => {
  assert.deepEqual(defterOzeti([]), {
    tahsilat: 0, gider: 0, net: 0, tahakkuk: 0, kayitSayisi: 0, iptalSayisi: 0,
  });
  assert.deepEqual(kategoriKirilimi([]), []);
  assert.deepEqual(yontemKirilimi([]), []);
});

// ─── Kategori kırılımı ──────────────────────────────────────────────────────

/**
 * Gelir ve gider aynı kategoride ayrı tutuluyor.
 *
 * Tek "net" sayıya indirilseydi 10.000 gelir + 9.000 gider ile 1.000 gelir +
 * 0 gider aynı görünürdü; market gibi iki yönlü kalemlerde hacim bilgisi
 * tamamen kaybolurdu.
 */
test("kategori kırılımı gelir ve gideri ayrı tutuyor", () => {
  const satirlar = kategoriKirilimi([
    kayit({ id: "a", category: "MARKET", type: "PAYMENT", amount_minor: 10_000_00 }),
    kayit({ id: "b", category: "MARKET", type: "EXPENSE", amount_minor: 9_000_00 }),
  ]);

  assert.equal(satirlar.length, 1);
  assert.equal(satirlar[0].tahsilat, 10_000_00);
  assert.equal(satirlar[0].gider, 9_000_00);
});

test("kategori kırılımı hacme göre sıralı", () => {
  const satirlar = kategoriKirilimi([
    kayit({ id: "a", category: "MEMBERSHIP", amount_minor: 100_00 }),
    kayit({ id: "b", category: "RENT", type: "EXPENSE", amount_minor: 5_000_00 }),
    kayit({ id: "c", category: "MARKET", amount_minor: 900_00 }),
  ]);

  assert.deepEqual(satirlar.map((s) => s.kod), ["RENT", "MARKET", "MEMBERSHIP"]);
});

test("tanınmayan kategori kaybolmuyor, kodu gösteriliyor", () => {
  const satirlar = kategoriKirilimi([kayit({ id: "a", category: "YENI_KALEM" })]);
  assert.equal(satirlar[0].etiket, "YENI_KALEM");
  assert.equal(kategoriEtiketi("YENI_KALEM"), "YENI_KALEM");
});

test("her bilinen kategorinin etiketi var", () => {
  for (const kod of Object.keys(FINANS_KATEGORI_ETIKETLERI)) {
    assert.ok(kategoriEtiketi(kod).length > 0, `${kod} etiketsiz`);
  }
});

// ─── Ödeme yöntemi ──────────────────────────────────────────────────────────

/**
 * Gider ödeme yöntemi kırılımına girmiyor.
 *
 * Soru kasa sayımı için soruluyor ("bugün ne kadar nakit girdi") ve kiranın
 * karttan ödenmiş olması o soruyu ilgilendirmiyor. Girseydi nakit toplamı
 * kasada olmayan parayı gösterirdi.
 */
test("yöntem kırılımı yalnızca tahsilatı sayıyor", () => {
  const satirlar = yontemKirilimi([
    kayit({ id: "a", type: "PAYMENT", payment_method: "CASH", amount_minor: 300_00 }),
    kayit({ id: "b", type: "EXPENSE", payment_method: "CASH", amount_minor: 999_00 }),
    kayit({ id: "c", type: "CHARGE", payment_method: "CASH", amount_minor: 888_00 }),
  ]);

  assert.equal(satirlar.length, 1);
  assert.equal(satirlar[0].tahsilat, 300_00);
});

test("yöntem oranları toplam tahsilata göre", () => {
  const satirlar = yontemKirilimi([
    kayit({ id: "a", payment_method: "CASH", amount_minor: 750_00 }),
    kayit({ id: "b", payment_method: "CARD", amount_minor: 250_00 }),
  ]);

  assert.deepEqual(satirlar.map((s) => s.kod), ["CASH", "CARD"]);
  assert.equal(satirlar[0].oran, 0.75);
  assert.equal(satirlar[1].oran, 0.25);
});

/** Sıfır toplamda bölme yapılmıyor: yapılsaydı her satır `NaN` olurdu. */
test("tahsilat yokken oran NaN olmuyor", () => {
  const satirlar = yontemKirilimi([
    kayit({ id: "a", payment_method: "CASH", amount_minor: 0 }),
  ]);
  assert.equal(satirlar[0].oran, 0);
});

// ─── Aylık seyir ────────────────────────────────────────────────────────────

/**
 * Hareketsiz ay atlanmıyor.
 *
 * Atlansaydı iki dolu ay yan yana çizilir ve aradaki ölü ay hiç görünmezdi —
 * düşüşü göstermek grafiğin var oluş sebebi.
 */
test("hareketsiz ay sıfırla listede duruyor", () => {
  const temmuz = new Date(2026, 6, 10).getTime();
  const seyir = aylikSeyir([kayit({ id: "a", occurred_at_ms: temmuz })], AN, 3);

  assert.deepEqual(seyir.map((s) => s.etiket), ["Haz 2026", "Tem 2026", "Ağu 2026"]);
  assert.equal(seyir[0].tahsilat, 0);
  assert.equal(seyir[1].tahsilat, 100_00);
  assert.equal(seyir[2].tahsilat, 0);
});

test("seyir eskiden yeniye sıralı ve pencere kadar uzun", () => {
  const seyir = aylikSeyir([], AN, 6);
  assert.equal(seyir.length, 6);
  assert.equal(seyir[0].etiket, "Mar 2026");
  assert.equal(seyir[5].etiket, "Ağu 2026");
});

/** Yıl sınırı: aralıktan ocağa geçerken yıl da gerilemeli. */
test("seyir yıl sınırını doğru geçiyor", () => {
  const ocak = new Date(2027, 0, 15, 12, 0).getTime();
  const seyir = aylikSeyir([], ocak, 3);
  assert.deepEqual(seyir.map((s) => s.etiket), ["Kas 2026", "Ara 2026", "Oca 2027"]);
});

test("pencere dışındaki kayıt seyre girmiyor", () => {
  const cokEski = new Date(2025, 0, 5).getTime();
  const seyir = aylikSeyir([kayit({ id: "a", occurred_at_ms: cokEski })], AN, 3);
  assert.equal(seyir.reduce((t, s) => t + s.tahsilat, 0), 0);
});

test("iptal edilen kayıt seyre de girmiyor", () => {
  const seyir = aylikSeyir([
    kayit({ id: "a", amount_minor: 1_000_00 }),
    kayit({ id: "a-ters", amount_minor: 1_000_00, reverses_id: "a" }),
  ], AN, 3);

  assert.equal(seyir.at(-1).tahsilat, 0);
});

test("seyirde net gelir eksi gider", () => {
  const seyir = aylikSeyir([
    kayit({ id: "a", type: "PAYMENT", amount_minor: 800_00 }),
    kayit({ id: "b", type: "EXPENSE", amount_minor: 300_00 }),
    // Tahakkuk nete girmiyor: kasaya girmemiş para kâr değil.
    kayit({ id: "c", type: "CHARGE", amount_minor: 9_999_00 }),
  ], AN, 1);

  assert.equal(seyir[0].tahsilat, 800_00);
  assert.equal(seyir[0].gider, 300_00);
  assert.equal(seyir[0].net, 500_00);
});

test("okunamayan tarih seyri bozmuyor", () => {
  const seyir = aylikSeyir([kayit({ id: "a", occurred_at_ms: "bozuk" })], AN, 2);
  assert.equal(seyir.reduce((t, s) => t + s.tahsilat, 0), 0);
});
