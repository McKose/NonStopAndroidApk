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

import { yayinDosyalari, girisNoktalari, YAPILANDIRMA } from "./yayin-dosyalari.mjs";

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

/**
 * HTML yorumlarındaki yollar referans sayılmıyor.
 *
 * Yaşanmış durum: karşılama videosunun nasıl açılacağını anlatan açıklama
 * `data-kaynak="varliklar/kahraman.mp4"` örneğini içeriyordu. Tarayıcı ham
 * metin okuduğu için bunu canlı referans sanıp "dosya eksik" dedi ve testi
 * düşürdü — oysa yorumdaki yol için hiçbir tarayıcı istek atmaz.
 *
 * Bu test o davranışı sabitliyor. Olmasaydı yorum ayıklama bir sonraki
 * düzenlemede sessizce geri alınabilir ve kimse fark etmezdi: belirtisi,
 * dokümantasyon yazan birinin testi kırması olurdu.
 */
test("HTML yorumundaki yollar referans sayılmıyor", () => {
  const kaynak = readFileSync(join(BURADA, "index.html"), "utf8");

  // Açıklama gerçekten örnek bir yol içeriyor mu — testin sınadığı durum
  // hâlâ var mı? Açıklama değişirse bu iddia sessizce anlamsızlaşmasın.
  const yorumlar = kaynak.match(/<!--[\s\S]*?-->/g) ?? [];
  const ornekliYorum = yorumlar.some((y) => /data-kaynak\s*=\s*"[^"]+"/.test(y));
  assert.ok(
    ornekliYorum,
    "index.html yorumlarında örnek bir `data-kaynak` yolu bulunamadı — " +
      "bu test o durumu sınıyor, açıklama değiştiyse test de güncellenmeli",
  );

  const { eksik } = yayinDosyalari();
  assert.deepEqual(
    eksik, [],
    `yorum içindeki yol canlı referans sayılmış: ${eksik.join(", ")}`,
  );
});

/**
 * Boş `data-kaynak` listeye girmiyor.
 *
 * Karşılama videosu henüz eklenmedi ve yolu bilerek boş. Boş değer eşleşseydi
 * yayın listesine `""` girer ve dosya arama saçmalardı.
 */
test("boş data-kaynak yayın listesine girmiyor", () => {
  const { dosyalar } = yayinDosyalari();
  assert.ok(!dosyalar.includes(""), "boş yol yayın listesine girmiş");
  assert.ok(
    !dosyalar.some((d) => d.trim() === ""),
    "yalnızca boşluktan oluşan yol yayın listesine girmiş",
  );
});

// ─── Admin paketi (Turhost) ─────────────────────────────────────────────────
//
// Panel iki yere birden yayınlanıyor: GitHub Pages'te `nonstopstudio.tr/panel/`
// altında, Turhost'ta `admin.nonstopstudio.tr` KÖKÜNDE. İkinci pakette
// `panel/` öneki kırpılıyor. Aşağıdaki testler o paketin doğru kurulduğunu
// sabitliyor — yanlış kurulmasının belirtisi, panelin açılmaması olurdu ve
// bunu ancak yayından sonra tarayıcıda görebilirdik.

test("panel yüzeyi tek başına türetilebiliyor", () => {
  const { dosyalar, eksik } = yayinDosyalari(BURADA, ["panel/index.html"]);

  assert.deepEqual(eksik, [], `panel paketinde eksik dosya: ${eksik.join(", ")}`);
  assert.ok(dosyalar.includes("panel/index.html"), "giriş noktası pakette yok");
  assert.ok(dosyalar.includes("panel/app.js"), `app.js pakette yok: ${dosyalar}`);
  assert.ok(dosyalar.includes("panel/styles.css"), `styles.css pakette yok: ${dosyalar}`);

  // Panelin dışındaki yüzeyler bu pakete AİT DEĞİL. Girseydi admin adresine
  // açılış sayfası ve üye alanı da kopyalanır, salonun yönetim adresi
  // gereksizce ikinci bir tam site yayınlardı.
  assert.ok(!dosyalar.includes("index.html"), "açılış sayfası admin paketine girmiş");
  assert.ok(!dosyalar.includes("uye/index.html"), "üye alanı admin paketine girmiş");
});

/**
 * Panelin `../varliklar/...` yazımı iki hedefte de çalışıyor.
 *
 * Pages'te panel `/panel/` altında ve `..` bir üste çıkıp `/varliklar/`e
 * gidiyor. Admin adresinde panel kökte; kökteki `..` atılıyor (RFC 3986) ve
 * yol yine `/varliklar/` oluyor. Yani tek yazım iki yerde de doğru — AMA
 * yalnızca dosya her iki pakete de kopyalanırsa. Bu test onu sabitliyor:
 * varlık pakete girmezse panelin simgesi 404 alır.
 */
test("panel paketinde varlıklar da var", () => {
  const { dosyalar } = yayinDosyalari(BURADA, ["panel/index.html"]);
  assert.ok(
    dosyalar.includes("varliklar/nonstop-gym.svg"),
    `panelin kullandığı varlık pakete girmemiş: ${dosyalar}`,
  );
});

/**
 * `panel/` öneki kırpılınca iki dosya aynı ada düşmemeli.
 *
 * Admin paketi `panel/app.js`i `app.js` yapıyor. Bir gün panelin dışında
 * `app.js` adında bir dosyaya referans verilirse ikisi aynı hedefe yazılır ve
 * biri diğerini sessizce ezer — panel çalışmaya devam ettiği için de fark
 * edilmez. Bugün böyle bir çakışma yok; bu test öyle kalmasını sağlıyor.
 */
test("admin paketinde önek kırpma çakışma üretmiyor", () => {
  const { dosyalar } = yayinDosyalari(BURADA, ["panel/index.html"]);
  const duzlenmis = dosyalar.map((d) =>
    d.startsWith("panel/") ? d.slice("panel/".length) : d,
  );

  const tekil = new Set(duzlenmis);
  assert.equal(
    tekil.size,
    duzlenmis.length,
    `önek kırpıldığında çakışan dosya adı var: ${duzlenmis.join(", ")}`,
  );
});

/** `config.js` admin paketinde de üretilen olarak tanınıyor. */
test("panel paketinde config.js üretilenler arasında", () => {
  const { dosyalar, uretilen } = yayinDosyalari(BURADA, ["panel/index.html"]);
  assert.ok(!dosyalar.includes("panel/config.js"), "config.js kopyalanacak listede olmamalı");
  assert.ok(uretilen.includes("panel/config.js"), `config.js üretilenler arasında olmalı: ${uretilen}`);
});

// ─── Hata sayfası ve yayın yapılandırması ───────────────────────────────────

test("404 sayfası yayın listesinde ve varlıkları da geliyor", () => {
  const { dosyalar, eksik } = yayinDosyalari();

  assert.deepEqual(eksik, [], `eksik dosya: ${eksik.join(", ")}`);
  assert.ok(dosyalar.includes("404.html"), `404.html listede yok: ${dosyalar}`);
  assert.ok(dosyalar.includes("site.css"), "404'ün stili listede yok");
  assert.ok(
    dosyalar.includes("varliklar/nonstop-gym-beyaz.svg"),
    "404'ün logosu listede yok",
  );
});

/**
 * 404 sayfasının yolları KÖKTEN olmalı.
 *
 * Bu dosya herhangi bir adreste servis ediliyor: `nonstopstudio.tr/eski/yol/`
 * isteği de bunu açıyor. Yol göreli olsaydı tarayıcı `eski/yol/site.css`
 * arar, bulamaz ve hata sayfası STİLSİZ çıkardı — sitenin en kötü göründüğü
 * an, tam da bir şeyin ters gittiği an olurdu.
 *
 * Göz ile fark edilmesi de zor: kökten açıldığında (`/404.html`) her iki
 * yazım da doğru çalışıyor. Ancak alt bir yolda denenirse ayrılıyorlar.
 */
test("404 sayfasının bütün yolları kökten", () => {
  const kaynak = readFileSync(join(BURADA, "404.html"), "utf8")
    .replace(/<!--[\s\S]*?-->/g, "");

  const desen = /(?:src|href)\s*=\s*["']([^"']+)["']/g;
  const goreli = [];

  for (const m of kaynak.matchAll(desen)) {
    const yol = m[1];
    if (/^(https?:|\/\/|#|mailto:|tel:)/.test(yol)) continue;
    if (!yol.startsWith("/")) goreli.push(yol);
  }

  assert.deepEqual(
    goreli, [],
    `404.html'de göreli yol var — alt bir adreste açıldığında bozulur: ${goreli.join(", ")}`,
  );
});

/**
 * Kökten yollar listeye BİR kez giriyor.
 *
 * `/site.css` ile `site.css` aynı dosya. Ayırt edilmeseydi ikisi de listeye
 * girer, dosya iki kez kopyalanır ve hedefte `_site//site.css` gibi tuhaf bir
 * yol oluşurdu.
 */
test("kökten yollar ikinci kez listeye girmiyor", () => {
  const { dosyalar } = yayinDosyalari();
  const bolulu = dosyalar.filter((d) => d.startsWith("/"));
  assert.deepEqual(bolulu, [], `kökten yol ham hâliyle listeye girmiş: ${bolulu}`);
});

/**
 * Yayın yapılandırma dosyaları duruyor.
 *
 * Bunlara hiçbir sayfa referans vermiyor, dolayısıyla türetme onları
 * korumuyor. Biri silinse yayın yine yeşil biter ve eksik çıkardı: `CNAME`
 * gidince alan adı, `robots.txt` gidince site haritası bildirimi, `sitemap.xml`
 * gidince `robots.txt`in işaret ettiği hedef kaybolurdu.
 */
test("yayın yapılandırma dosyaları duruyor", () => {
  for (const ad of YAPILANDIRMA) {
    assert.ok(
      existsSync(join(BURADA, ad)),
      `yayın yapılandırma dosyası eksik: ${ad}`,
    );
  }
});

/**
 * `robots.txt` panel ve üye alanını ENGELLEMEMELİ.
 *
 * İkisi de `noindex` ile korunuyor. `Disallow` eklenirse robot sayfayı
 * indirmez, indirmediği için `noindex` etiketini de göremez ve URL yine
 * sonuçlarda çıkabilir — üstelik başlıksız. Yani engellemek korumayı
 * güçlendirmiyor, bozuyor. Bu test o "iyileştirmenin" sessizce yapılmasını
 * engelliyor.
 */
test("robots.txt noindex'li yüzeyleri engellemiyor", () => {
  const kaynak = readFileSync(join(BURADA, "robots.txt"), "utf8");
  const kurallar = kaynak
    .split("\n")
    .filter((s) => !s.trim().startsWith("#"))
    .join("\n");

  assert.ok(
    !/^\s*Disallow:\s*\/(panel|uye)/im.test(kurallar),
    "robots.txt panel/üye alanını engelliyor — noindex'i görünmez kılar",
  );
});

/**
 * Sosyal paylaşım adresleri MUTLAK olmalı.
 *
 * Göreli bir `og:image` tarayıcıda sorunsuz görünüyor ama önizlemeyi üreten
 * robotlar (WhatsApp, Instagram, Facebook) göreli yolu çözmüyor ve görseli
 * atlıyor. Yaşanmış hâli buydu; belirtisi yalnızca bağlantıyı paylaşınca
 * görülüyordu, sitede hiçbir iz yoktu.
 */
test("og etiketleri mutlak adres kullanıyor", () => {
  const kaynak = readFileSync(join(BURADA, "index.html"), "utf8");
  const desen =
    /<meta\s+(?:property|name)=["'](og:image|og:url|twitter:image)["']\s+content=["']([^"']+)["']/g;

  const bulunanlar = [...kaynak.matchAll(desen)];
  assert.ok(bulunanlar.length > 0, "og:image / og:url etiketi bulunamadı");

  for (const [, etiket, deger] of bulunanlar) {
    assert.ok(
      deger.startsWith("https://"),
      `${etiket} göreli yazılmış ("${deger}") — önizleme robotları çözemez`,
    );
  }
});
