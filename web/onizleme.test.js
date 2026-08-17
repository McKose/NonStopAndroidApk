// Önizleme birleştirmesi testleri.
//
// `onizleme.mjs` modülleri elle tutulan bir listeye göre birleştiriyor ve o
// listenin eksik kalması **sessiz** bir hata: unutulan modülün fonksiyonları
// önizlemede tanımsız olur, sayfa da yalnızca ilgili sekmeye basıldığında
// bozulur. Yani hata, üreten kişinin göremeyeceği bir yerde ortaya çıkıyor.
//
// Bu tam olarak yaşandı: `stok.js` ve `roller.js` eklendiğinde listeye
// yazılmamıştı ve önizleme Market sekmesinde bozulacaktı.

import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

import { BIRLESTIRILEN } from "./onizleme.mjs";

const burada = dirname(fileURLToPath(import.meta.url));
const oku = (ad) => readFileSync(join(burada, ad), "utf8");

/** Bir dosyanın yerel (`./`) import ettiği dosya adları. */
function yerelImportlar(kaynak) {
  return [...kaynak.matchAll(/^\s*import\s[\s\S]*?from\s+"\.\/([^"]+)"/gm)].map((m) => m[1]);
}

test("birleştirme listesi bütün yerel importları kapsıyor", () => {
  const eksikler = [];

  for (const dosya of BIRLESTIRILEN) {
    for (const bagimlilik of yerelImportlar(oku(dosya))) {
      if (!BIRLESTIRILEN.includes(bagimlilik)) {
        eksikler.push(`${dosya} → ${bagimlilik}`);
      }
    }
  }

  assert.deepEqual(
    eksikler,
    [],
    "Bu dosyalar birleştirmeye girmiyor ama import ediliyor; önizleme bozulur:\n  " +
      eksikler.join("\n  "),
  );
});

/**
 * Bağımlılık sırası doğru mu?
 *
 * Birleştirme dosyaları sırayla ekliyor ve hepsi aynı kapsamda çalışıyor.
 * `function` bildirimleri yukarı kaldırıldığı için çoğu durumda sıra sorun
 * yaratmıyor — ama `const` öyle değil: `AZALMA_ESIGI` gibi bir sabit,
 * kullanıldığı dosyadan sonra tanımlanırsa çalışma zamanında patlıyor.
 */
test("importlanan dosya, importlayandan önce geliyor", () => {
  const sira = (ad) => BIRLESTIRILEN.indexOf(ad);
  const hatalar = [];

  for (const dosya of BIRLESTIRILEN) {
    for (const bagimlilik of yerelImportlar(oku(dosya))) {
      if (sira(bagimlilik) > sira(dosya)) {
        hatalar.push(`${bagimlilik} (${sira(bagimlilik)}) → ${dosya} (${sira(dosya)})`);
      }
    }
  }

  assert.deepEqual(hatalar, [], `Bağımlılık sırası ters:\n  ${hatalar.join("\n  ")}`);
});

/**
 * Panelin bütün modülleri ya birleştirmede ya bilinçli olarak dışında.
 *
 * Yeni bir modül eklenip hiçbir yere bağlanmazsa bunu bilmek istiyoruz; ama
 * birleştirmeye girmemesi gereken dosyalar da var (testler, betiğin kendisi,
 * sunucudan gelen ayar).
 */
test("dizindeki her modül ya birleştirmede ya bilinçli dışında", () => {
  const disinda = new Set([
    "onizleme.mjs",      // birleştirmeyi yapan betiğin kendisi
    "config.example.js", // örnek ayar; kuruluma özgü `config.js` üretmek için
    "sema.js",           // yalnızca testler için: SQL migrasyonlarını okuyor
    // Yayın listesini modül grafiğinden türeten araç. Panelin çalışma zamanına
    // ait değil — yayın akışı ve testler çağırıyor, tarayıcı hiç görmüyor.
    "yayin-dosyalari.mjs",
  ]);

  for (const ad of readdirSync(burada)) {
    if (!ad.endsWith(".js") && !ad.endsWith(".mjs")) continue;
    if (ad.endsWith(".test.js")) continue;

    assert.ok(
      BIRLESTIRILEN.includes(ad) || disinda.has(ad),
      `\`${ad}\` ne birleştirmede ne de bilinçli dışında listesinde. Önizlemeye ` +
        `girmesi gerekiyorsa BIRLESTIRILEN'e, gerekmiyorsa bu testteki ` +
        `\`disinda\` kümesine gerekçesiyle eklenmeli.`,
    );
  }
});
