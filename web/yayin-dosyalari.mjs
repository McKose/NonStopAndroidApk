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
//   node web/yayin-dosyalari.mjs --uretilen → iş akışının YAZMASI gerekenler
//   node web/yayin-dosyalari.mjs --yapilandirma
//                                           → hiçbir sayfanın referans
//                                             vermediği kök dosyalar
//   node web/yayin-dosyalari.mjs --girisler=panel/index.html
//                                           → yalnızca o yüzeyin dosyaları
//
// `--girisler` neden var: panel artık İKİ yere birden yayınlanıyor — GitHub
// Pages'te `nonstopstudio.tr/panel/`, Turhost'ta `admin.nonstopstudio.tr`
// kökü. İkincisine yalnızca panelin kendisi ve dokunduğu varlıklar gidiyor;
// açılış sayfası ve üye alanı oraya ait değil. Liste yine TÜRETİLİYOR: elle
// sayılsaydı bu dosyanın kapatmak için var olduğu hata sınıfı ikinci bir
// yüzeyde yeniden açılırdı.

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

/**
 * Hiçbir sayfanın REFERANS VERMEDİĞİ ama yayına giren kök dosyalar.
 *
 * Bunlar sayfa varlığı değil, **yayın yapılandırması**: tarayıcı bir sayfadan
 * bağlantıyla değil, doğrudan bilinen adresten istiyor. Türetme onları
 * bulamaz — tanım gereği kimse onlara bağlantı vermiyor.
 *
 * Eskiden yalnızca `CNAME` vardı ve iş akışına ELLE kopyalanıyordu. Liste
 * ikiye çıkınca aynı hata sınıfı geri geliyordu: `robots.txt` eklenir,
 * kopyalama satırı yazılmaz, yayın yeşil biter ve dosya sitede olmaz. Artık
 * tek yerde duruyorlar ve eksik olan biri yayını düşürüyor.
 */
export const YAPILANDIRMA = ["CNAME", "robots.txt", "sitemap.xml"];

/** `import ... from "./x.js"` ve `import "./x.js"` biçimlerini yakalar. */
const IMPORT_DESENI = /(?:from|import)\s+["'](\.\/[^"']+)["']/g;

/**
 * `<script src="x">`, `<link href="x">` ve `data-kaynak="x"` — göreli yollar.
 *
 * `data-kaynak` de taranıyor çünkü karşılama videosu `src` ile DEĞİL onunla
 * veriliyor (yükleme kararı `site.js`te, bkz. index.html). Taranmasaydı iki
 * şey birden kaybolurdu: dosya yayın listesine girmez (yayınlanan sitede 404)
 * ve yanlış yazılmış bir yolu hiçbir test yakalamaz.
 *
 * Boş değer (`data-kaynak=""`) eşleşmiyor — desen en az bir karakter istiyor —
 * yani "video henüz eklenmedi" hâli listeye hiç girmiyor.
 */
const HTML_DESENI =
  /(?:src|href|data-kaynak)\s*=\s*["'](?!https?:|\/\/|#)([^"']+)["']/g;

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

  // Hata sayfası da bir giriş noktası: ona kimse bağlantı vermiyor (sunucu
  // servis ediyor) ama kendi varlıklarını — stil, logo — referans veriyor.
  // Yapılandırma dosyaları gibi düz kopyalansaydı o varlıklar izlenmez,
  // yalnızca başka sayfalar da onları kullandığı için tesadüfen yayına
  // girerlerdi. Tesadüfe bağlı çalışan bir kurulum, ilk düzenlemede bozulur.
  if (existsSync(join(kok, "404.html"))) girisler.push("404.html");

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

    const hamIcerik = readFileSync(yol, "utf8");

    // HTML yorumları taramadan ÇIKARILIYOR.
    //
    // Yorum içindeki bir yol tanım gereği canlı referans değil; tarayıcı onu
    // istemiyor. Çıkarılmasaydı bir dosyayı "şöyle açılır" diye örnekleyen
    // açıklama, olmayan bir dosyayı eksik gibi raporlardı — nitekim karşılama
    // videosunun kullanım örneği tam bunu yaptı.
    //
    // Yalnızca HTML için: JS yorumlarında import benzeri metin bulunması
    // pratikte olmuyor ve `IMPORT_DESENI` zaten `from`/`import` anahtar
    // sözcüğü arıyor.
    const icerik = ad.endsWith(".html")
      ? hamIcerik.replace(/<!--[\s\S]*?-->/g, "")
      : hamIcerik;

    const desen = ad.endsWith(".html") ? HTML_DESENI : IMPORT_DESENI;
    desen.lastIndex = 0;
    const dizin = posix.dirname(ad);

    for (const m of icerik.matchAll(desen)) {
      const ham = m[1];

      // Sayfa içi çapa, e-posta ve telefon bağlantıları dosya değil.
      if (ham.startsWith("#") || ham.startsWith("mailto:") || ham.startsWith("tel:")) continue;

      // `#bolum` PARÇASI dosya adına ait değil, ondan sonra geliyor. 404
      // sayfası ana sayfanın bölümlerine `/#branslar` diye bağlanıyor; parça
      // ayrılmasaydı `#branslar` adında bir dosya aranır ve yayın düşerdi.
      // (Nitekim 404 sayfası eklendiğinde tam bunu yaptı.)
      const hamYol = ham.split("#")[0];

      // Dizine giden bağlantılar (`panel/`, `uye/`, `/`) YÜZEYLER ARASI
      // GEZİNME, varlık değil. Her yüzey zaten kendi giriş noktası olarak
      // taranıyor; burada izlenirlerse betik bir dizini dosya sanıp okumaya
      // çalışıyor ve `EISDIR` ile çöküyor. Bu tam olarak yaşandı: açılış
      // sayfasına "Admin Paneli" düğmesi eklendiği anda.
      if (hamYol === "" || hamYol.endsWith("/")) continue;

      // Kökten yazılmış yollar (`/site.css`) referansın bulunduğu dizine
      // GÖRE ÇÖZÜLMÜYOR — kökten çözülüyor. 404 sayfası bunu gerektiriyor:
      // herhangi bir adreste servis edildiği için yollarını kökten vermek
      // zorunda (bkz. 404.html). Bu ayrım yapılmasaydı yol hem `site.css`
      // hem `/site.css` diye iki kez listeye girer, dosya iki kez kopyalanır
      // ve `_site//site.css` gibi tuhaf bir hedef oluşurdu.
      const hedef = hamYol.startsWith("/")
        ? posix.normalize(hamYol.slice(1))
        : posix.normalize(posix.join(dizin === "." ? "" : dizin, hamYol));
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
  // `--girisler=a,b` verilmezse bütün yüzeyler taranıyor (varsayılan davranış
  // değişmedi). Verilen giriş noktası gerçekten var mı diye BURADA bakılıyor:
  // olmayan bir yol verildiğinde `yayinDosyalari` onu "eksik" sayıp listeye
  // hiç dosya koymaz ve hata "hiç dosya bulunamadı" diye çıkardı — sebebi
  // yazmayan bir mesaj.
  const girisArgumani = process.argv
    .find((a) => a.startsWith("--girisler="))
    ?.slice("--girisler=".length);

  const secilenGirisler = girisArgumani
    ? girisArgumani.split(",").map((g) => g.trim()).filter(Boolean)
    : null;

  if (secilenGirisler) {
    const olmayan = secilenGirisler.filter((g) => !existsSync(join(BURADA, g)));
    if (olmayan.length > 0) {
      console.error(`HATA: giriş noktası bulunamadı: ${olmayan.join(", ")}`);
      process.exit(1);
    }
  }

  const { dosyalar, uretilen, eksik } = secilenGirisler
    ? yayinDosyalari(BURADA, secilenGirisler)
    : yayinDosyalari();

  if (eksik.length > 0) {
    console.error("HATA: referans verilen ama bulunamayan dosyalar:");
    for (const e of eksik) console.error(`  - ${e}`);
    process.exit(1);
  }

  // Yapılandırma dosyaları yalnızca TAM yayında anlamlı. Alt küme (`--girisler`
  // ile üretilen admin paketi) kendi `robots.txt`ini üretiyor ve `CNAME` ona
  // ait değil — orada aramak yanlış olurdu.
  if (!secilenGirisler) {
    const eksikYapilandirma = YAPILANDIRMA.filter(
      (d) => !existsSync(join(BURADA, d)),
    );
    if (eksikYapilandirma.length > 0) {
      console.error(
        `HATA: yayın yapılandırma dosyası eksik: ${eksikYapilandirma.join(", ")}`,
      );
      console.error("Bunlara hiçbir sayfa referans vermiyor; silinirlerse");
      console.error("yayın sessizce eksik çıkardı — bu yüzden burada aranıyor.");
      process.exit(1);
    }
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
  } else if (process.argv.includes("--yapilandirma")) {
    // Hiçbir sayfanın referans vermediği kök dosyalar. Yukarıda varlıkları
    // zaten doğrulandı, burada yalnızca listeleniyor.
    for (const y of YAPILANDIRMA) console.log(y);
  } else if (!process.argv.includes("--kontrol")) {
    for (const d of dosyalar) console.log(d);
  }
}
