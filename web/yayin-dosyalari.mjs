// Yayınlanacak dosyaları modül grafiğinden türetir.
//
// Eskiden bu liste iş akışında ELLE sayılıyordu ve tam olarak beklenen şey oldu:
// panele dört yeni modül eklendi (`roller.js`, `stok.js`, `sekmeler.js`,
// `sema.js`), listeye yazılmadı ve yayınlanan panel bomboş açıldı. Sebebi de
// görünmüyordu: bir ES modülü importu 404 alırsa o modülü içe alan dosya
// TAMAMEN yüklenmiyor — yani eksik olan tek dosya bütün paneli düşürüyor.
// Üstelik CI yeşildi, çünkü testler depodaki dosyaları okuyor, yayınlananları
// değil.
//
// Artık liste `index.html`ten başlayıp importları izleyerek hesaplanıyor.
// Unutulacak bir liste kalmadığı için bu hata sınıfı kapandı.
//
// Elle sayılan listenin gerekçesi geçerliydi ve korunuyor: `web/` altında
// testler, `config.example.js` ve önizleme üreticisi de var; onlar yayına ait
// değil. Grafik yaklaşımı bunu kendiliğinden sağlıyor — hiçbir şey onları içe
// almıyor, dolayısıyla listeye girmiyorlar.
//
// Kullanım:
//   node web/yayin-dosyalari.mjs            → dosya adları, satır satır
//   node web/yayin-dosyalari.mjs --kontrol  → yalnızca doğrula, çıktı verme

import { readFileSync, existsSync } from "node:fs";
import { dirname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";

const BURADA = dirname(fileURLToPath(import.meta.url));

/**
 * Yayında ÜRETİLEN, depoda olmayan dosyalar.
 *
 * `config.js` kuruluma özgü ayarları taşıyor ve iş akışı tarafından gizli
 * anahtarlardan yazılıyor (`.gitignore`'da). `index.html` onu `onerror` ile
 * yükliyor: yoksa panel "Kurulum tamamlanmamış" diyor. Bu yüzden eksikliği hata
 * değil — ama listeye de girmemeli, çünkü kopyalanacak bir kaynağı yok.
 */
const URETILEN = new Set(["config.js"]);

/** `import ... from "./x.js"` ve `import "./x.js"` biçimlerini yakalar. */
const IMPORT_DESENI = /(?:from|import)\s+["'](\.\/[^"']+)["']/g;

/** `<script src="x">` ve `<link href="x">` — yalnızca göreli yollar. */
const HTML_DESENI = /(?:src|href)\s*=\s*["'](?!https?:|\/\/|#)([^"']+)["']/g;

/**
 * `index.html`ten başlayarak ulaşılabilir bütün dosyaları toplar.
 *
 * Genişlik-öncelikli gezinti; aynı dosya iki yerden içe alınsa bile bir kez
 * işleniyor. Döngüsel import (a → b → a) bu yüzden sonsuza gitmiyor.
 */
export function yayinDosyalari(kok = BURADA, giris = "index.html") {
  const bulunan = new Set();
  const uretilen = new Set();
  const eksik = [];
  const kuyruk = [giris];

  while (kuyruk.length > 0) {
    const ad = kuyruk.shift();
    if (bulunan.has(ad) || uretilen.has(ad)) continue;

    if (URETILEN.has(ad)) {
      uretilen.add(ad);
      continue;
    }

    const yol = join(kok, ad);
    if (!existsSync(yol)) {
      // Referans var ama dosya yok: bu gerçek bir hata. Sessizce atlanırsa
      // yayınlanan sitede ölü bir bağlantı kalır.
      eksik.push(ad);
      continue;
    }

    bulunan.add(ad);

    const icerik = readFileSync(yol, "utf8");
    const desen = ad.endsWith(".html") ? HTML_DESENI : IMPORT_DESENI;
    desen.lastIndex = 0;
    for (const m of icerik.matchAll(desen)) {
      // `./x.js` → `x.js`; alt dizin de desteklenir (`uye/x.js`).
      const hedef = normalize(m[1].replace(/^\.\//, ""));
      if (hedef.startsWith("..")) {
        eksik.push(`${hedef} (${ad} içinden — kök dizinin dışı)`);
        continue;
      }
      kuyruk.push(hedef);
    }
  }

  return { dosyalar: [...bulunan].sort(), uretilen: [...uretilen].sort(), eksik };
}

// ─── Komut satırı ───────────────────────────────────────────────────────────

if (import.meta.url === `file://${process.argv[1]}`) {
  const { dosyalar, eksik } = yayinDosyalari();

  if (eksik.length > 0) {
    console.error("HATA: referans verilen ama bulunamayan dosyalar:");
    for (const e of eksik) console.error(`  - ${e}`);
    process.exit(1);
  }

  if (dosyalar.length === 0) {
    console.error("HATA: hiç dosya bulunamadı — giriş noktası yanlış olabilir.");
    process.exit(1);
  }

  if (!process.argv.includes("--kontrol")) {
    for (const d of dosyalar) console.log(d);
  }
}
