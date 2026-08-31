// Panelin tek dosyalık önizlemesini üretir.
//
// Panelin kendisinin derleme adımı YOK ve olmayacak; bu betik yalnızca
// paylaşılabilir bir önizleme çıktısı üretiyor. Amaç, küçük bir değişikliği
// göstermek için karşı tarafın depo klonlaması ve sunucu çalıştırması
// gerekmesin.
//
// Çıktı demo moduna sabit: önizlemede sunucuya gidilmiyor.
//
// Kullanım:  node web/onizleme.mjs > /tmp/onizleme.html

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const kok = dirname(fileURLToPath(import.meta.url));
const oku = (ad) => readFileSync(join(kok, ad), "utf8");

/**
 * Modülleri tek parçaya birleştirir.
 *
 * `import`/`export` satırları çıkarılıyor; sıra bağımlılık sırası. Gerçek bir
 * paketleyici değil ve olmasına gerek yok — dosyalar birbirini yalnızca isimle
 * kullanıyor ve hepsi aynı kapsamda birleşince çalışıyor. Bir gün karmaşıklaşırsa
 * doğru yol bu betiği büyütmek değil, gerçek bir paketleyiciye geçmek olur.
 */
function birlestir(dosyalar) {
  return dosyalar
    .map((ad) =>
      oku(ad)
        .replace(/^import[\s\S]*?;\s*$/gm, "")
        .replace(/^export (?=(function|const|class|let|var))/gm, ""),
    )
    .join("\n");
}

const govde = oku("index.html")
  .replace(/^[\s\S]*<main id="uygulama">/, '<main id="uygulama">')
  .replace(/<\/main>[\s\S]*$/, "</main>");

/**
 * Birleştirmeye giren dosyalar, bağımlılık sırasıyla.
 *
 * `app.js` en sonda: diğerlerinin tanımlarını kullanıyor.
 *
 * `supabase.js` önizlemede hiç **çağrılmıyor** (demo moduna sabitlenmiş) ama
 * listeye giriyor: `app.js` onu import ediyor ve dışarıda bırakmak, önizlemenin
 * yalnızca üçlü operatörün o dalı hiç değerlendirilmediği için çalışması demek
 * olurdu. Şansa bağlı çalışan bir şey, çalışmıyor sayılır.
 *
 * Bu liste elle tutuluyor ve **eksik kalması sessiz bir hata**: unutulan modülün
 * fonksiyonları önizlemede tanımsız olur, sayfa da yalnızca o sekmeye
 * basıldığında bozulur. `onizleme.test.js` bu yüzden listeyi koddaki
 * `import`larla karşılaştırıyor — yeni bir modül eklenip buraya yazılmazsa test
 * düşüyor. (Bu tam olarak `stok.js` ve `roller.js` eklenirken yaşandı.)
 */
export const BIRLESTIRILEN = [
  "supabase.js",
  "domain.js",
  "ozet.js",
  "suzme.js",
  "roller.js",
  "sekmeler.js",
  "stok.js",
  "davet.js",
  "demo.js",
  "app.js",
];

const betik = birlestir(BIRLESTIRILEN)
  // Önizleme her zaman demo: `demoMu()` adres çubuğuna bakıyor, burada
  // bakılacak bir adres yok.
  .replace(/function demoMu\(\)[\s\S]*?\n}/, "function demoMu() {\n  return true;\n}");

process.stdout.write(`<title>NonStop Studio Paneli</title>
<style>
${oku("styles.css")}
</style>

<div class="onizleme-not">
  Bu bir <strong>önizleme</strong>: veriler örnek, sunucuya bağlanmıyor.
  Giriş ekranında herhangi bir e-posta ve şifre kabul edilir.
</div>

${govde}

<script type="module">
${betik}
</script>
`);
