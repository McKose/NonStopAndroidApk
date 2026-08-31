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

/**
 * Birleştirilmiş betik AYRIŞTIRILABİLİYOR olmalı.
 *
 * Önizleme modülleri TEK KAPSAMDA birleştiriyor: `import`/`export` satırları
 * siliniyor ve dosyalar uç uca ekleniyor. Bunun iki sonucu var ve ikisi de
 * gerçek hataya yol açtı:
 *
 *   1. İki modül aynı adı dışa açarsa birleşimde "Identifier has already been
 *      declared" oluşur ve PANELİN TAMAMI yüklenmez.
 *   2. `import { x as y }` biçimindeki takma adlar birleşimde YOK OLUR;
 *      `y` diye bir şey kalmaz ve o adı kullanan kod `y is not defined` ile
 *      düşer — üstelik yalnızca o kod yolu çalıştığında (bir düğmeye
 *      basıldığında), yani açılışta hiçbir belirti vermeden.
 *
 * Üçü de `davet.js` eklenirken yaşandı ve hiçbirini birim testleri, `node
 * --check` ya da tip denetimi yakalamadı — yalnızca tarayıcıda görüldü.
 *
 * `vm.Script` betiği ÇALIŞTIRMADAN ayrıştırıyor: `document` yok, ağ yok,
 * tarayıcı yok. Yinelenen tanım bir sözdizimi hatası olduğu için burada
 * yakalanıyor.
 */
test("birleştirilmiş betik ayrıştırılabiliyor (ad çakışması yok)", async () => {
  const { execFileSync } = await import("node:child_process");
  const { writeFileSync, mkdtempSync } = await import("node:fs");
  const { tmpdir } = await import("node:os");

  const kaynak = BIRLESTIRILEN.map((ad) =>
    readFileSync(join(burada, ad), "utf8")
      .replace(/^import[\s\S]*?;\s*$/gm, "")
      .replace(/^export\s+/gm, ""),
  ).join("\n");

  // MODÜL olarak ayrıştırılıyor, düz betik olarak değil — ve bu ayrım testin
  // işe yarayıp yaramamasını belirliyor.
  //
  // Önizleme betiği `<script type="module">` içinde koşuyor. Düz betikte aynı
  // adlı İKİ FONKSİYON tanımı yasal; modülde hata. İlk yazımda `vm.Script`
  // kullanılmıştı (düz betik) ve gerçek çakışmayı KAÇIRDI: mutasyon testinde
  // hatayı geri koydum, test yeşil kaldı. Uzantısı `.mjs` olan bir dosyaya
  // yazıp `node --check` çalıştırmak modül kipini garanti ediyor.
  const dizin = mkdtempSync(join(tmpdir(), "onizleme-"));
  const dosya = join(dizin, "birlesim.mjs");
  writeFileSync(dosya, kaynak, "utf8");

  try {
    execFileSync(process.execPath, ["--check", dosya], { stdio: "pipe" });
  } catch (e) {
    assert.fail(
      "Birleştirilmiş betik ayrıştırılamıyor. En olası sebep iki modülün aynı " +
        "adı dışa açması — önizlemede tek kapsam olduğu için çakışıyorlar. " +
        "Adlardan birini benzersiz yapın; `import ... as` çözüm değil, " +
        "birleştirmede takma ad kalmıyor.\n\n" +
        String(e.stderr ?? e.message),
    );
  }
});

/**
 * Birleştirilen modüller arasında takma adlı içe alma OLMAMALI.
 *
 * `import { hataMesaji as davetHatasi }` gerçek ES modüllerinde çalışıyor ama
 * birleşimde `davetHatasi` diye bir şey kalmıyor. Sözdizimi hatası da vermiyor
 * — kod o satıra gelene kadar sessiz. Yukarıdaki ayrıştırma testi bunu
 * yakalayamaz, bu yüzden ayrı bir kontrol.
 */
test("birleştirilen modüller arasında takma adlı import yok", () => {
  const sorunlar = [];

  for (const ad of BIRLESTIRILEN) {
    const kaynak = readFileSync(join(burada, ad), "utf8");
    for (const m of kaynak.matchAll(/import\s*\{([^}]*)\}\s*from\s+"\.\/([^"]+)"/g)) {
      if (!BIRLESTIRILEN.includes(m[2])) continue;   // dışarıdan gelen: sorun değil
      for (const parca of m[1].split(",")) {
        if (/\bas\b/.test(parca)) {
          sorunlar.push(`${ad}: ${parca.trim()} (${m[2]})`);
        }
      }
    }
  }

  assert.deepEqual(
    sorunlar, [],
    "Birleştirilen modüller arasında takma adlı import var. Önizlemede takma " +
      "ad kaybolur ve kod çalışma anında `is not defined` ile düşer. " +
      "Kaynaktaki adı benzersiz yapın:\n  " + sorunlar.join("\n  "),
  );
});
