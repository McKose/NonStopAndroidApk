// Üye kaydının açık/kapalı kararı.
//
// Sınanan şeylerin ortak özelliği: yanlış olduklarında ekran ÇALIŞIR görünüyor.
// Yanlış tarafa düşen bir karar ya kapalı sanılan bir kaydı açık bırakır, ya da
// gerçek bir kurulum eksiğini "bilinçli kapalı" diye gizler. İkisi de sessiz.

import test from "node:test";
import assert from "node:assert/strict";

import { KAYIT_ACIK, kayitDurumu, kayitKapaliMesaji } from "./kayit.js";

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const BURADA = dirname(fileURLToPath(import.meta.url));
const SAYFA = readFileSync(join(BURADA, "index.html"), "utf8");

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

// ─── İşaretlemenin varsayılanı ──────────────────────────────────────────────

/**
 * Kayıt düğmesi `index.html` içinde GİZLİ başlamalı.
 *
 * Bu bir JS kararı değil, işaretleme kararı — ama aynı kararın parçası, o
 * yüzden burada sınanıyor.
 *
 * Gerekçe dar ve abartılmamalı: bugün bu düğmeyi taşıyan `#giris` bölümü
 * zaten `hidden` başlıyor ve hangi bölümün açılacağına JS karar veriyor.
 * Yani JS hiç çalışmazsa sayfada hiçbir şey görünmüyor ve düğme de
 * görünmüyor — "JS'siz düğme sızar" diye bir sorun BUGÜN yok.
 *
 * Sebep ileriye dönük: bölümlerin gizli başlaması bir düzenleme kararı ve
 * değişebilir (ör. giriş ekranı varsayılan çizilen bölüm yapılabilir). O gün
 * düğme, kayıt kapalı olmasına rağmen JS'ten bağımsız görünür hâle gelirdi.
 * Bu test o sapmayı yakalıyor.
 */
test("kayıt düğmesi işaretlemede gizli başlıyor", () => {
  const dugme = SAYFA.match(/<a[^>]*id="kayda-git"[^>]*>/);
  assert.ok(dugme, "kayda-git düğmesi bulunamadı");
  assert.match(dugme[0], /\bhidden\b/, "kayda-git `hidden` olmadan başlıyor");
});

/** "Kayıt açık" metni de gizli başlamalı: düğmeyle aynı gerekçe. */
test("kayıt açık metni gizli başlıyor", () => {
  const p = SAYFA.match(/<p[^>]*id="kayit-acik-metin"[^>]*>/);
  assert.ok(p, "kayit-acik-metin bulunamadı");
  assert.match(p[0], /\bhidden\b/);
});

/**
 * Kapalı metin GÖRÜNÜR başlamalı ve metni işaretlemede YAZILI olmalı.
 *
 * JS'ten doldurulsaydı işaretleme tek başına eksik kalırdı: kaydın kapalı
 * olduğu bilgisi yalnızca çalışma anında var olurdu. Burada tersi tercih
 * edildi — doğru hâli işaretleme anlatıyor, JS yalnızca "ayar eksik"
 * durumunda metni değiştiriyor.
 */
test("kapalı metni işaretlemede yazılı ve görünür", () => {
  const p = SAYFA.match(/<p[^>]*id="kayit-kapali-metin"[^>]*>([\s\S]*?)<\/p>/);
  assert.ok(p, "kayit-kapali-metin bulunamadı");
  assert.doesNotMatch(p[0], /\bhidden\b/, "kapalı metin gizli başlamamalı");
  assert.match(p[1], /üye kaydı şu anda alınmıyor/i);
});
