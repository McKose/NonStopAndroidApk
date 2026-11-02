// İletişim bilgisi: sayfa ile yapısal veri AYNI şeyi söylemeli.
//
// Telefon, adres ve çalışma saatleri açılış sayfasında iki yerde birden
// duruyor: gözle okunan iletişim bölümünde ve arama motorunun okuduğu
// `application/ld+json` bloğunda. İkisi ayrı ayrı yazıldığı için ayrışabilir
// ve ayrıştıklarında hiçbir belirti vermezler — sayfa doğru görünür,
// yapısal veri sessizce yanlış bilgi yayınlar.
//
// Bu, gerçek bir zarar: eski bir telefon numarasıyla arama sonucunda çıkmak,
// hiç çıkmamaktan kötü. Arama motoru da iki kaynağı karşılaştırıp
// uyuşmadığını görürse yapısal veriye güvenmeyi bırakıyor.
//
// Bu yüzden testler eşitliği sınıyor, varlığı değil: biri değişip diğeri
// unutulursa CI kırmızı döner.

import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const BURADA = dirname(fileURLToPath(import.meta.url));
const KAYNAK = readFileSync(join(BURADA, "index.html"), "utf8");

/** Yapısal veri bloğu. */
const yapisalVeri = (() => {
  const m = KAYNAK.match(
    /<script type="application\/ld\+json">([\s\S]*?)<\/script>/,
  );
  assert.ok(m, "index.html içinde application/ld+json bloğu yok");
  return JSON.parse(m[1]);
})();

/**
 * Sayfanın yapısal veri DIŞINDA kalan kısmı.
 *
 * Karşılaştırma bunun üzerinde yapılıyor: blok kendi içinden doğrulanırsa
 * test her zaman geçer ve hiçbir şey sınamamış olur.
 */
const SAYFA = KAYNAK.replace(
  /<script type="application\/ld\+json">[\s\S]*?<\/script>/,
  "",
);

/** Yalnızca rakamlar — biçim farkları (nokta, boşluk, tire) elensin. */
const rakamlar = (s) => s.replace(/\D/g, "");

test("yapısal veri iletişim alanlarını taşıyor", () => {
  assert.equal(yapisalVeri["@type"], "HealthClub");
  assert.ok(yapisalVeri.telephone, "telephone yok");
  assert.ok(yapisalVeri.address?.streetAddress, "streetAddress yok");
  assert.ok(yapisalVeri.address?.postalCode, "postalCode yok");
  assert.ok(
    Array.isArray(yapisalVeri.openingHoursSpecification),
    "openingHoursSpecification yok",
  );
});

/**
 * Telefon iki yerde aynı.
 *
 * Karşılaştırma son 10 hane üzerinden: sayfada `0541 971 50 95` yazıyor,
 * yapısal veride `+90 541 971 50 95`. Ülke kodu ve baştaki sıfır biçim
 * tercihi, numara aynı.
 */
test("telefon sayfa ile yapısal veride aynı", () => {
  const veridekiSon10 = rakamlar(yapisalVeri.telephone).slice(-10);
  assert.equal(veridekiSon10.length, 10, "telefon 10 haneye inmiyor");

  assert.ok(
    rakamlar(SAYFA).includes(veridekiSon10),
    `yapısal verideki telefon (${yapisalVeri.telephone}) sayfada geçmiyor`,
  );
});

/** Telefon tıklanabilir olmalı — ziyaretçilerin çoğu telefondan geliyor. */
test("telefon tel: bağlantısı olarak veriliyor", () => {
  const m = SAYFA.match(/href="tel:([^"]+)"/);
  assert.ok(m, "sayfada tel: bağlantısı yok");
  assert.equal(
    rakamlar(m[1]).slice(-10),
    rakamlar(yapisalVeri.telephone).slice(-10),
    "tel: bağlantısındaki numara yapısal veridekinden farklı",
  );
});

test("açık adres sayfa ile yapısal veride aynı", () => {
  const sokak = yapisalVeri.address.streetAddress;
  assert.ok(
    SAYFA.includes(sokak),
    `yapısal verideki adres sayfada birebir geçmiyor: "${sokak}"`,
  );
  assert.ok(
    SAYFA.includes(yapisalVeri.address.postalCode),
    "posta kodu sayfada geçmiyor",
  );
});

/**
 * Çalışma saatleri sayfa ile yapısal veride aynı.
 *
 * Sayfa `10.00 – 22.00` yazıyor, yapısal veri `10:00`/`22:00` — ayraç farkı
 * biçim tercihi. Rakamlar üzerinden karşılaştırılıyor.
 */
test("çalışma saatleri sayfa ile yapısal veride aynı", () => {
  const acik = yapisalVeri.openingHoursSpecification.filter(
    (s) => s.opens !== s.closes,
  );
  assert.equal(acik.length, 1, "birden fazla farklı açık saat aralığı var");

  const sayfaRakamlari = rakamlar(SAYFA);
  assert.ok(
    sayfaRakamlari.includes(rakamlar(acik[0].opens)),
    `açılış saati (${acik[0].opens}) sayfada geçmiyor`,
  );
  assert.ok(
    sayfaRakamlari.includes(rakamlar(acik[0].closes)),
    `kapanış saati (${acik[0].closes}) sayfada geçmiyor`,
  );
});

/**
 * Kapalı günler AÇIKÇA yazılmış olmalı.
 *
 * Cumartesi–pazarı hiç yazmamak "bilinmiyor" demek ve arama sonucu "açık
 * olabilir" izlenimi verir. Kapalı bir salona gelen ziyaretçi, hiç bilgi
 * bulamayandan daha kötü bir deneyim yaşıyor. schema.org'da kapalı günün
 * karşılığı `opens` ve `closes` değerlerinin ikisinin de aynı olması.
 */
test("kapalı günler yapısal veride açıkça belirtilmiş", () => {
  const gunler = yapisalVeri.openingHoursSpecification.flatMap((s) =>
    Array.isArray(s.dayOfWeek) ? s.dayOfWeek : [s.dayOfWeek],
  );

  assert.equal(
    new Set(gunler).size,
    7,
    `haftanın yedi günü de belirtilmeli, belirtilen: ${[...new Set(gunler)].join(", ")}`,
  );
});

/**
 * WhatsApp numarası `tel:` ve yapısal veriyle AYNI.
 *
 * Numara artık ÜÇ yerde: iletişim kutusundaki `tel:` bağlantısı, yapısal
 * veri ve `wa.me` adresleri. Üçüncüsünü eklemek bu dosyanın var oluş
 * sebebini büyüttü — biri güncellenip diğeri unutulduğunda hiçbir belirti
 * yok ve yanlış numaraya yazan ziyaretçi sessizce kayboluyor.
 */
test("WhatsApp numarası tel: ve yapısal veriyle aynı", () => {
  const adresler = [...SAYFA.matchAll(/href="https:\/\/wa\.me\/(\d+)/g)];
  assert.ok(adresler.length > 0, "sayfada wa.me bağlantısı yok");

  const beklenen = rakamlar(yapisalVeri.telephone).slice(-10);
  for (const [, numara] of adresler) {
    assert.equal(
      rakamlar(numara).slice(-10),
      beklenen,
      `wa.me bağlantısındaki numara (${numara}) yapısal veridekinden farklı`,
    );
  }
});

/**
 * `wa.me` numarası ULUSLARARASI biçimde ve baştaki sıfır OLMADAN.
 *
 * `wa.me/0541...` sessizce yanlış çalışıyor: WhatsApp bunu ülke kodu sanıp
 * başka bir numaraya götürüyor ya da "geçersiz" diyor. Hata sayfada
 * görünmüyor — yalnızca kimse cevap vermiyor. Bu yüzden biçim ayrıca
 * sınanıyor, eşitlik tek başına yetmiyor (son 10 hane ikisinde de aynı).
 */
test("wa.me numarası ülke koduyla, baştaki sıfırsız", () => {
  const adresler = [...SAYFA.matchAll(/href="https:\/\/wa\.me\/(\d+)/g)];
  for (const [, numara] of adresler) {
    assert.doesNotMatch(numara, /^0/, `wa.me numarası sıfırla başlıyor: ${numara}`);
    assert.match(numara, /^90/, `wa.me numarası ülke koduyla başlamıyor: ${numara}`);
    assert.equal(numara.length, 12, `wa.me numarası 12 hane olmalı: ${numara}`);
  }
});

/** Sabit düğme erişilebilir bir adla verilmeli — simge tek başına ne olduğunu söylemiyor. */
test("sabit WhatsApp düğmesinin erişilebilir adı var", () => {
  const m = SAYFA.match(/<a class="wa-dugme"[\s\S]*?>/);
  assert.ok(m, "sabit WhatsApp düğmesi bulunamadı");
  assert.match(m[0], /aria-label="[^"]+"/, "wa-dugme aria-label taşımıyor");
});
