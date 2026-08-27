// Panelin iş kurallarının testleri.
//
// Bağımlılık yok: Node'un yerleşik test koşucusuyla çalışıyor
// (`node --test web/`). Panel için ayrı bir derleme adımı ya da paket yöneticisi
// kurmamanın bedeli bu testleri elle yazmak; karşılığı, panelin herhangi bir
// statik sunucuya olduğu gibi konabilmesi.

import { test } from "node:test";
import assert from "node:assert/strict";
import { tutarYaz, tarihYaz, uyelikDurumu, durumEtiketi, silinmemisler } from "./domain.js";

test("kuruş tutarı bölünerek gösterilir", () => {
  // 100.000 kuruş = 1.000,00 TL. Bölmenin yalnızca gösterimde yapılması şart:
  // ara hesaplarda yapılsaydı toplamlar sapardı.
  assert.match(tutarYaz(100000), /1\.000,00/);
  assert.match(tutarYaz(0), /0,00/);
  assert.match(tutarYaz(1), /0,01/);
});

test("eksik tutar çizgiyle gösterilir, sıfır değil", () => {
  // "—" ile "0,00 TL" farklı: ilki "bilinmiyor", ikincisi "bedava".
  assert.equal(tutarYaz(null), "—");
  assert.equal(tutarYaz(undefined), "—");
  assert.equal(tutarYaz("abc"), "—");
});

test("epoch milisaniye tarihe çevrilir", () => {
  // 2026-08-13T00:00:00Z
  assert.equal(tarihYaz(1786579200000), "13.08.2026");
  assert.equal(tarihYaz(null), "—");
  assert.equal(tarihYaz(""), "—");
});

test("üyelik durumu bitiş tarihinden türetilir", () => {
  const simdi = 1000;
  assert.equal(uyelikDurumu({ end_date_ms: 2000 }, simdi), "AKTIF");
  assert.equal(uyelikDurumu({ end_date_ms: 500 }, simdi), "SURESI_DOLDU");
  // Tam bugün biten üyelik hâlâ aktif: sınır dahil.
  assert.equal(uyelikDurumu({ end_date_ms: 1000 }, simdi), "AKTIF");
});

test("elle konan durum tarihin önüne geçer", () => {
  // Dondurulmuş üye tarihi geçmemiş olsa da aktif sayılmamalı.
  const simdi = 1000;
  assert.equal(uyelikDurumu({ status: "FROZEN", end_date_ms: 2000 }, simdi), "DONDURULDU");
  assert.equal(uyelikDurumu({ status: "ARCHIVED", end_date_ms: 2000 }, simdi), "ARSIVDE");
});

test("silinmiş üye her şeyin önüne geçer", () => {
  const uye = { status: "ACTIVE", end_date_ms: 2000, deleted_at_ms: 5 };
  assert.equal(uyelikDurumu(uye, 1000), "SILINDI");
});

test("bitiş tarihi olmayan üyelik süresiz", () => {
  // Abonman: `-1` gibi bir sihirli sayı değil, alanın boş olması.
  assert.equal(uyelikDurumu({ end_date_ms: null }, 1000), "SURESIZ");
  assert.equal(uyelikDurumu({}, 1000), "SURESIZ");
});

test("her durumun Türkçe karşılığı var", () => {
  const durumlar = ["AKTIF", "SURESI_DOLDU", "DONDURULDU", "ARSIVDE", "SURESIZ", "SILINDI"];
  for (const d of durumlar) {
    const etiket = durumEtiketi(d);
    assert.notEqual(etiket, d, `${d} için Türkçe karşılık yok`);
    assert.ok(etiket.length > 0);
  }
});

test("tombstone satırlar listeden ayıklanır", () => {
  // Sunucudan silinmiş satırlar da geliyor; süzme unutulsaydı silinen üye
  // listede kalırdı.
  const satirlar = [
    { id: "a" },
    { id: "b", deleted_at_ms: 123 },
    { id: "c", deleted_at_ms: null },
  ];
  assert.deepEqual(silinmemisler(satirlar).map((s) => s.id), ["a", "c"]);
});
