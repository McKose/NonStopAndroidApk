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

import { readFileSync, existsSync, readdirSync } from "node:fs";
import { dirname, join, normalize, posix } from "node:path";
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
 * Giriş noktalarını bulur: kökteki ve bir alt dizindeki her `index.html`.
 *
 * Elle sayılmıyor. Site üç yüzeyden oluşuyor (`/`, `/panel/`, `/uye/`) ve
 * dördüncüsü eklendiğinde listeye yazılmayı beklemek, bu dosyanın kapatmak için
 * var olduğu hatanın aynısını geri getirirdi.
 */
export function girisNoktalari(kok = BURADA) {
  const girisler = [];
  if (existsSync(join(kok, "index.html"))) girisler.push("index.html");

  for (const ad of readdirSync(kok, { withFileTypes: true })) {
    if (!ad.isDirectory()) continue;
    if (ad.name === "node_modules" || ad.name.startsWith(".")) continue;
    if (existsSync(join(kok, ad.name, "index.html"))) {
      girisler.push(posix.join(ad.name, "index.html"));
    }
  }
  return girisler.sort();
}

/**
 * Giriş noktalarından ulaşılabilir bütün dosyaları toplar.
 *
 * Genişlik-öncelikli gezinti; aynı dosya iki yerden içe alınsa bile bir kez
 * işleniyor. Döngüsel import (a → b → a) bu yüzden sonsuza gitmiyor.
 *
 * Yollar **referansı içeren dosyanın dizinine göre** çözülüyor. Baştaki `./`yi
 * kırpmak yetmez: `panel/index.html` içindeki `./app.js`, `app.js` değil
 * `panel/app.js` demek. Bu ayrım alt dizinler olmadan görünmüyordu ve site üç
 * yüzeye ayrılınca ortaya çıktı.
 */
export function yayinDosyalari(kok = BURADA, girisler = girisNoktalari(kok)) {
  const bulunan = new Set();
  const uretilen = new Set();
  const eksik = [];
  const kuyruk = [...girisler];

  while (kuyruk.length > 0) {
    const ad = kuyruk.shift();
    if (bulunan.has(ad) || uretilen.has(ad)) continue;

    // Üretilen dosyalar dizinden bağımsız tanınıyor: `config.js` de
    // `panel/config.js` de iş akışının yazdığı dosya.
    if (URETILEN.has(posix.basename(ad))) {
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
    const dizin = posix.dirname(ad);

    for (const m of icerik.matchAll(desen)) {
      const hedef = posix.normalize(posix.join(dizin === "." ? "" : dizin, m[1]));
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
  const { dosyalar, uretilen, eksik } = yayinDosyalari();

  if (eksik.length > 0) {
    console.error("HATA: referans verilen ama bulunamayan dosyalar:");
    for (const e of eksik) console.error(`  - ${e}`);
    process.exit(1);
  }

  if (dosyalar.length === 0) {
    console.error("HATA: hiç dosya bulunamadı — giriş noktası yanlış olabilir.");
    process.exit(1);
  }

  // `--uretilen`: iş akışının YAZMASI gereken dosyalar (bugün yalnızca
  // `config.js`). Hangi dizinlere yazılacağı da türetiliyor; sabit bir yol
  // panel `/panel/` altına taşındığında sessizce yanlış olurdu.
  if (process.argv.includes("--uretilen")) {
    for (const u of uretilen) console.log(u);
  } else if (!process.argv.includes("--kontrol")) {
    for (const d of dosyalar) console.log(d);
  }
}
