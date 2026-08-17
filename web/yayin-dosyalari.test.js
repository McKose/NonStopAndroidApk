// Yayın listesi testleri.
//
// Bu testin varlık sebebi yaşanmış bir arıza: panele dört yeni modül eklendi,
// yayın akışındaki ELLE sayılan listeye yazılmadı ve yayınlanan panel bomboş
// açıldı. Bir ES modülü importu 404 alırsa onu içe alan dosya tamamen
// yüklenmiyor — yani eksik tek dosya bütün paneli düşürüyor. CI ise yeşildi,
// çünkü testler depodaki dosyaları okuyor, yayınlananları değil.
//
// Liste artık türetiliyor. Bu test türetmenin doğru çalıştığını sınıyor: hem
// gerekeni aldığını hem de gereksizi ALMADIĞINI.

import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { yayinDosyalari, girisNoktalari } from "./yayin-dosyalari.mjs";

const BURADA = dirname(fileURLToPath(import.meta.url));

test("her yüzeyin giriş noktası bulunuyor", () => {
  const girisler = girisNoktalari();
  assert.ok(girisler.includes("panel/index.html"), `panel girişi bulunamadı: ${girisler}`);
  assert.ok(girisler.length > 0, "hiç giriş noktası yok");
});

test("panelin çekirdek dosyaları listede", () => {
  const { dosyalar } = yayinDosyalari();

  for (const gerekli of [
    "panel/index.html", "panel/styles.css", "panel/app.js",
    "panel/supabase.js", "panel/domain.js",
  ]) {
    assert.ok(dosyalar.includes(gerekli), `${gerekli} yayın listesinde yok`);
  }
});

/**
 * `app.js`in içe aldığı HER modül listede olmalı.
 *
 * Asıl arızayı yakalayan iddia bu. Elle yazılmış bir beklenti listesi
 * kullanılmıyor: `app.js` okunup importları çıkarılıyor, yani panele yarın yeni
 * bir modül eklenirse bu test onu da kapsıyor.
 */
test("app.js'in bütün importları yayın listesinde", () => {
  const kaynak = readFileSync(join(BURADA, "panel", "app.js"), "utf8");
  const importlar = [...kaynak.matchAll(/from\s+["']\.\/([^"']+)["']/g)].map((m) => m[1]);

  assert.ok(importlar.length >= 5, `app.js'te beklenenden az import bulundu: ${importlar.length}`);

  const { dosyalar } = yayinDosyalari();
  for (const modul of importlar) {
    assert.ok(
      dosyalar.includes(`panel/${modul}`),
      `app.js "${modul}" modülünü içe alıyor ama yayın listesinde yok — ` +
        `yayınlanan panel bu modül yüzünden hiç açılmaz`,
    );
  }
});

test("listedeki her dosya gerçekten var", () => {
  const { dosyalar, eksik } = yayinDosyalari();
  assert.deepEqual(eksik, [], `referans verilip bulunamayan dosyalar: ${eksik.join(", ")}`);
  for (const d of dosyalar) {
    assert.ok(existsSync(join(BURADA, d)), `${d} listede ama dosya yok`);
  }
});

/**
 * Testler ve kurulum dosyaları yayına GİRMEMELİ.
 *
 * Elle sayılan listenin gerekçesi buydu ve türetmeye geçerken kaybedilmemesi
 * gerekiyor: `web/` altında testler, `config.example.js` ve önizleme üreticisi
 * de var. Hiçbiri panelin çalışması için gerekli değil ve site kökünde durmaları
 * kafa karıştırıcı.
 */
test("test ve kurulum dosyaları yayına girmiyor", () => {
  const { dosyalar } = yayinDosyalari();

  for (const d of dosyalar) {
    assert.ok(!d.endsWith(".test.js"), `test dosyası yayına girmiş: ${d}`);
  }

  for (const girmemeli of [
    "panel/config.example.js",  // örnek ayar; gerçek config.js iş akışında üretiliyor
    "panel/onizleme.mjs",       // önizleme üreticisi, panelin parçası değil
    "panel/sema.js",            // yalnızca testler için (kendi başlığında yazılı)
    "package.json",
    "README.md",
    "yayin-dosyalari.mjs",      // yayın aracının kendisi
  ]) {
    assert.ok(!dosyalar.includes(girmemeli), `${girmemeli} yayına girmemeli`);
  }
});

/**
 * `config.js` listede olmamalı ama eksik de sayılmamalı.
 *
 * `index.html` onu `onerror` ile yüklüyor ve dosya iş akışı tarafından gizli
 * anahtarlardan üretiliyor (`.gitignore`'da). Kopyalanacak bir kaynağı yok, ama
 * "bulunamadı" hatası da vermemeli — aksi hâlde her yayın düşerdi.
 */
test("config.js üretilen dosya olarak ayrı tutuluyor", () => {
  const { dosyalar, uretilen, eksik } = yayinDosyalari();

  assert.ok(!dosyalar.includes("panel/config.js"), "config.js kopyalanacak listede olmamalı");
  assert.ok(uretilen.includes("panel/config.js"), `config.js üretilenler arasında olmalı: ${uretilen}`);
  assert.ok(
    !eksik.some((e) => e.includes("config.js")),
    "config.js eksik sayılmamalı — yoksa her yayın düşer",
  );
});

/** Döngüsel import sonsuza gitmemeli. */
test("aynı modül iki yerden içe alınsa bile bir kez listelenir", () => {
  const { dosyalar } = yayinDosyalari();
  assert.equal(new Set(dosyalar).size, dosyalar.length, "listede tekrar eden dosya var");
});
