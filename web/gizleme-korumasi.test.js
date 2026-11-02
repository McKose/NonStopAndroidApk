// `hidden` niteliğinin gizleme gücü hiçbir yazar kuralına yenilmemeli.
//
// Sitede ve üye alanında bölümler `hidden` ile açılıp kapanıyor; bu, akışın
// taşıyıcısı, süsü değil. Üye alanında BÜTÜN bölümler `hidden` başlıyor ve
// hangisinin görüneceğine JavaScript karar veriyor — yani gizleme bozulursa
// sayfa "biraz yanlış" görünmüyor, aynı anda birbirini dışlayan bölümler
// birden görünüyor.
//
// Tarayıcının kendi `[hidden] { display: none }` kuralı HERHANGİ bir yazar
// `display` bildirimine yeniliyor, bu yüzden `site.css` ve
// `admin/styles.css` gizlemeyi `[hidden] { display: none !important }` ile
// koruyor.
//
// AMA O KORUMA DA YENİLEBİLİR. `[hidden]` özgüllüğü (0,1,0) ve eşit
// özgüllükte iki `!important` çarpıştığında SIRA kazanıyor — sonra yüklenen
// stil dosyası. `uye.css` tam olarak bunu yapıyordu:
//
//     .riza { display: flex !important; }   /* (0,1,0), site.css'ten SONRA */
//
// `hidden` verilmiş bir `.riza` gizlenmiyordu. Chromium'da ölçüldü: düz bir
// `hidden` etiket `none` olurken `.riza` `flex` kalıyordu. O kural artık
// `.uye-form label.riza` (0,2,1) ile `!important`sız kazanıyor.
//
// Bu testin sınadığı kural: `display: ... !important` YALNIZCA `[hidden]`
// kuralında olabilir. Başka her yerde en iyi hâlde gereksiz, en kötü hâlde
// gizlemeyi sessizce bozuyor. Gerçekten gerekirse bu testi değiştirmek
// gerekiyor — amaç tam olarak o: kararın farkında olunarak verilmesi.
//
// CI'da tarayıcı yok, o yüzden sınama metin üzerinde. Davranış tarafı
// Chromium'da elle ölçüldü ve yukarıda yazılı.

import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const BURADA = dirname(fileURLToPath(import.meta.url));

/** `web/` altındaki bütün stil dosyaları. Elle liste tutulmuyor: yeni bir
 *  stil dosyası eklendiğinde testin kapsamı kendiliğinden genişlemeli. */
function stilDosyalari(kok) {
  const bulunan = [];
  for (const ad of readdirSync(kok)) {
    const yol = join(kok, ad);
    if (statSync(yol).isDirectory()) bulunan.push(...stilDosyalari(yol));
    else if (ad.endsWith(".css")) bulunan.push(yol);
  }
  return bulunan.sort();
}

const DOSYALAR = stilDosyalari(BURADA);

/** Yorumlar çıkarılıyor: bu dosyalarda `!important`ın NEDEN gerektiği
 *  yorumlarda anlatılıyor ve o metin bildirim sanılmamalı. */
const yorumsuz = (metin) => metin.replace(/\/\*[\s\S]*?\*\//g, "");

/**
 * `display` bildirimlerinden `!important` taşıyanları, ait oldukları
 * seçiciyle birlikte döndürüyor.
 *
 * Süslü parantezler sayılarak en içteki seçici bulunuyor; `@media` gibi
 * sarmalayıcılar da bu yüzden doğru ele alınıyor (düz bir "seçici { ... }"
 * kalıbı `@media` içinde yanlış seçici verirdi).
 */
function onemliDisplayKurallari(metin) {
  const bulunan = [];
  const yigin = [];
  let parca = "";

  for (const harf of metin) {
    if (harf === "{") {
      yigin.push(parca.trim().replace(/\s+/g, " "));
      parca = "";
    } else if (harf === "}") {
      bildirimleriTara(parca, yigin, bulunan);
      yigin.pop();
      parca = "";
    } else if (harf === ";") {
      bildirimleriTara(parca, yigin, bulunan);
      parca = "";
    } else {
      parca += harf;
    }
  }
  return bulunan;
}

function bildirimleriTara(parca, yigin, bulunan) {
  if (!/^\s*display\s*:/.test(parca)) return;
  if (!/!\s*important/.test(parca)) return;
  bulunan.push({
    secici: yigin[yigin.length - 1] ?? "(seçici yok)",
    sarmal: yigin.slice(0, -1),
    bildirim: parca.trim().replace(/\s+/g, " "),
  });
}

test("stil dosyası bulundu (yol yanlışsa test boşa geçmesin)", () => {
  assert.ok(
    DOSYALAR.length >= 3,
    `web/ altında yalnızca ${DOSYALAR.length} stil dosyası bulundu; ` +
      "yol yanlış olabilir",
  );
});

test("`display: !important` yalnızca `[hidden]` kuralında olabilir", () => {
  const izinsiz = [];
  for (const yol of DOSYALAR) {
    const metin = yorumsuz(readFileSync(yol, "utf8"));
    for (const kural of onemliDisplayKurallari(metin)) {
      if (kural.secici === "[hidden]" && kural.sarmal.length === 0) continue;
      izinsiz.push(
        `${relative(BURADA, yol)}: ${kural.secici} { ${kural.bildirim} }`,
      );
    }
  }
  assert.deepEqual(
    izinsiz,
    [],
    "Bu bildirimler `[hidden]` korumasını yenebilir; `display`i " +
      "`!important` yerine özgüllükle kazanın:\n  " + izinsiz.join("\n  "),
  );
});

test("`[hidden]` koruması her stil dosyasının yüklendiği sayfada var", () => {
  // Koruma yalnızca `site.css` ve `admin/styles.css` içinde; `uye.css`
  // kendi başına yüklenmiyor, `site.css` ile BİRLİKTE yükleniyor. Yani
  // sınanması gereken şey sayfalarda: `hidden` kullanan her sayfa, korumayı
  // taşıyan bir stil dosyası da yüklemeli.
  const sayfalar = ["index.html", "404.html", "uye/index.html",
    "admin/index.html", "panel/index.html"];
  const korumali = new Set();
  for (const yol of DOSYALAR) {
    const metin = yorumsuz(readFileSync(yol, "utf8"));
    if (/\[hidden\]\s*\{[^}]*display\s*:\s*none\s*!\s*important/.test(metin)) {
      korumali.add(relative(BURADA, yol));
    }
  }
  assert.ok(korumali.size > 0, "hiçbir stil dosyasında `[hidden]` koruması yok");

  for (const sayfa of sayfalar) {
    const kaynak = readFileSync(join(BURADA, sayfa), "utf8");
    if (!/\shidden[\s>=]/.test(kaynak)) continue; // `hidden` kullanmıyor

    // Yol, SAYFANIN bulunduğu dizine göre çözülüyor ve `join` `..`yı
    // kendisi normalleştiriyor: `uye/index.html` içindeki `../site.css`
    // kökteki `site.css`e denk geliyor.
    const sayfaDizini = dirname(sayfa);
    const cozulmus = [...kaynak.matchAll(/<link[^>]+href="([^"]+\.css)"/g)]
      .map((m) => relative(BURADA, join(BURADA, sayfaDizini, m[1])));

    assert.ok(
      cozulmus.some((s) => korumali.has(s)),
      `${sayfa} \`hidden\` kullanıyor ama yüklediği stil dosyalarının ` +
        `(${cozulmus.join(", ")}) hiçbirinde \`[hidden]\` koruması yok`,
    );
  }
});

test("rıza satırı özgüllükle kazanıyor, bare `.riza` ile değil", () => {
  // Yenmesi gereken kural `.uye-form label { display: grid }` (0,1,1).
  // Seçici yalnız `.riza`ya (0,1,0) indirilirse onay kutusu metnin ÜSTÜNE
  // çıkıyor — görünür bir bozulma ama sessiz: hata vermiyor, yalnız çirkin
  // duruyor. `!important` ile düzeltme yolu kapalı (yukarıdaki test), o
  // yüzden kalan tek doğru yol özgüllük ve burada sabitleniyor.
  const metin = yorumsuz(readFileSync(join(BURADA, "uye/uye.css"), "utf8"));
  const kural = metin.match(/([^{}]*\.riza[^{}]*)\{([^}]*display[^}]*)\}/);
  assert.ok(kural, "uye.css içinde `.riza` için `display` kuralı yok");

  const secici = kural[1].trim().replace(/\s+/g, " ");
  assert.match(
    secici,
    /\.uye-form\b/,
    `rıza satırının seçicisi "${secici}" — \`.uye-form\` içermiyor, yani ` +
      "`.uye-form label` kuralını yenemez",
  );
});
