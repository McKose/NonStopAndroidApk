// Üye kaydının açık/kapalı kararı.
//
// Sınanan şeylerin ortak özelliği: yanlış olduklarında ekran ÇALIŞIR görünüyor.
// Yanlış tarafa düşen bir karar ya kapalı sanılan bir kaydı açık bırakır, ya da
// gerçek bir kurulum eksiğini "bilinçli kapalı" diye gizler. İkisi de sessiz.

import test from "node:test";
import assert from "node:assert/strict";

import { KAYIT_ACIK, kayitDurumu, kayitKapaliMesaji } from "./kayit.js";

const SALON = "aaaaaaaa-0000-0000-0000-000000000001";

/**
 * Bugünkü karar: kayıt KAPALI.
 *
 * Bu test, anahtarın yanlışlıkla açılmasını yakalamak için var. Açılması
 * gerektiğinde bu iddia da bilerek değiştirilecek — yani değişiklik
 * görünür oluyor, sessizce geçmiyor.
 */
test("kayıt şu anda kapalı", () => {
  assert.equal(KAYIT_ACIK, false);
  assert.equal(kayitDurumu({ tenantId: SALON }).acik, false);
});

/** Ürün kararı, ayar varlığından BAĞIMSIZ: anahtar kapalıysa kapalı. */
test("kapalıyken salon ayarı dolu olsa da açılmıyor", () => {
  const sonuc = kayitDurumu({ acik: false, tenantId: SALON });
  assert.equal(sonuc.acik, false);
  assert.equal(sonuc.sebep, "kapali");
});

test("açıkken ve ayar tamken kayıt alınıyor", () => {
  assert.equal(kayitDurumu({ acik: true, tenantId: SALON }).acik, true);
});

/**
 * İki sebep AYRI kalmalı.
 *
 * Kayıt yeniden açıldığı gün `SUPABASE_TENANT_ID` hâlâ tanımsızsa, ekranın
 * "bilinçli kapalı" demesi gerçek bir kurulum eksiğini görünmez kılardı:
 * kimse anahtarın eksik olduğunu anlamaz, kayıt da hiç çalışmazdı.
 */
test("ayar eksikliği bilinçli kapatmadan ayrı raporlanıyor", () => {
  const sonuc = kayitDurumu({ acik: true, tenantId: null });
  assert.equal(sonuc.acik, false);
  assert.equal(sonuc.sebep, "ayar_eksik");
  assert.notEqual(kayitKapaliMesaji("ayar_eksik"), kayitKapaliMesaji("kapali"));
});

/** Boş metin de "ayar yok" sayılmalı; `""` dolu bir değer değil. */
test("boş salon kimliği ayar eksiği sayılıyor", () => {
  assert.equal(kayitDurumu({ acik: true, tenantId: "" }).sebep, "ayar_eksik");
});

/** Girdi hiç verilmezse varsayılan "kapalı" olmalı — açık tarafa düşmemeli. */
test("girdisiz çağrı kapalı dönüyor", () => {
  assert.equal(kayitDurumu().acik, false);
});

/**
 * Metin ziyaretçiye NE YAPACAĞINI söylüyor.
 *
 * "Kayıt kapalı" tek başına çıkmaz: kişi salonda üye olabilir ve hesabının
 * açılmasını bekliyordur. Metin onu salona yönlendirmezse akış orada biter.
 */
test("kapalı mesajı ne yapılacağını söylüyor", () => {
  const mesaj = kayitKapaliMesaji("kapali");
  assert.match(mesaj, /salona başvur/i);
  assert.match(mesaj, /giriş yap/i);
});

test("ayar eksik mesajı salonla iletişime yönlendiriyor", () => {
  assert.match(kayitKapaliMesaji("ayar_eksik"), /salonla iletişime/i);
});
