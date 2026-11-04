// İletişim formu karar mantığı testleri.
//
// Bu testin asıl hedefi TELEFON NORMALLEŞTİRMESİ, çünkü oradaki hata sınıfı
// sessiz: yanlış normalleşmiş bir numara ekranda doğru görünüyor, form
// başarıyla gönderiliyor, satır veritabanına düşüyor — ve yalnızca salon
// aradığında kimse cevap vermiyor. Yani hatanın tek belirtisi, kaybedilmiş
// bir müşteri.
//
// Baştaki sıfır ayrıca sınanıyor. `05419715095` → `+905419715095` olmalı;
// sıfır korunursa `+9005419715095` çıkar. SON 10 HANE EŞİTLİĞİ BU HATAYI
// YAKALAMIYOR (iki hâlde de aynı), o yüzden tam dize karşılaştırılıyor.

import test from "node:test";
import assert from "node:assert/strict";

import {
  telefonuNormalle,
  formuDogrula,
  formDurumu,
  tuzagaDustu,
  FORM_ACIK,
  KANONIK_TELEFON,
  SINIRLAR,
} from "./iletisim-formu.js";

// ─── Telefon ────────────────────────────────────────────────────────────────

test("insanın yazabileceği bütün biçimler aynı kanonik hâle çıkıyor", () => {
  const beklenen = "+905419715095";
  for (const yazim of [
    "5419715095",
    "05419715095",
    "905419715095",
    "+905419715095",
    "0905419715095",
    "0541 971 50 95",
    "0541-971-50-95",
    "(0541) 971 50 95",
    "+90 541 971 50 95",
    "  05419715095  ",
    "0541.971.50.95",
  ]) {
    assert.equal(
      telefonuNormalle(yazim),
      beklenen,
      `"${yazim}" yanlış normalleşti`,
    );
  }
});

test("baştaki sıfır ülke kodunun parçası olmuyor", () => {
  // Bu testin ayrı durmasının sebebi: yukarıdaki test geçerken bu düşebilir
  // ve düştüğünde sebebi tek bir cümleyle okunur olmalı.
  const sonuc = telefonuNormalle("05419715095");
  assert.equal(sonuc, "+905419715095");
  assert.notEqual(sonuc, "+9005419715095", "sıfır ülke kodunun içine girdi");
  assert.equal(sonuc.length, 13, "kanonik numara 13 karakter olmalı");
});

test("kanonik çıktı sunucunun desenine uyuyor", () => {
  // Sunucu `KANONIK_TELEFON`un AYNISINI uyguluyor. Bu test iki tarafın
  // anlaşmasını sabitliyor: normalleştirme buradaki deseni üretmezse
  // sunucu her gönderimi reddeder ve form hiç çalışmaz.
  for (const yazim of ["5419715095", "0212 555 44 33", "+90 312 444 55 66"]) {
    const n = telefonuNormalle(yazim);
    assert.ok(n, `"${yazim}" normalleşemedi`);
    assert.match(n, KANONIK_TELEFON, `"${n}" kanonik desene uymuyor`);
  }
});

test("sabit hat bilerek kabul ediliyor", () => {
  // "Beni arayın" formunda sabit hat veren gerçek bir talebi reddetmek,
  // sabit hat kaydetmekten daha kötü.
  assert.equal(telefonuNormalle("0212 555 44 33"), "+902125554433");
  assert.equal(telefonuNormalle("0312 444 55 66"), "+903124445566");
});

test("geçersiz numaralar reddediliyor", () => {
  for (const kotu of [
    "",
    "   ",
    "abc",
    "0541",                 // çok kısa
    "054197150955555",      // çok uzun
    "1111111111",           // 1 ile başlıyor — geçerli Türkiye numarası değil
    "6419715095",           // 6 ile başlıyor
    "9419715095",           // 9 ile başlıyor
    "0041971509",           // 0 ile başlayan ulusal kısım
    "+1 555 123 4567",      // Türkiye dışı
    "0541+9715095",         // ortada `+`
    "05419715O95",          // harf O
    null,
    undefined,
    12345,
  ]) {
    assert.equal(
      telefonuNormalle(kotu),
      null,
      `${JSON.stringify(kotu)} kabul edildi; reddedilmeliydi`,
    );
  }
});

// ─── Form doğrulama ─────────────────────────────────────────────────────────

const gecerliGirdi = {
  adSoyad: "Ayşe Yılmaz",
  telefon: "0541 971 50 95",
  eposta: "ayse@ornek.com",
  mesaj: "Deneme dersi istiyorum",
};

test("geçerli form normalleşmiş yük üretiyor", () => {
  const s = formuDogrula(gecerliGirdi);
  assert.equal(s.gecerli, true, JSON.stringify(s.hatalar));
  assert.deepEqual(s.yuk, {
    adSoyad: "Ayşe Yılmaz",
    telefon: "+905419715095",
    eposta: "ayse@ornek.com",
    mesaj: "Deneme dersi istiyorum",
  });
});

test("yalnız ad ve telefon yeterli", () => {
  // E-posta ve mesaj zorunlu OLMAMALI: zorunlu olsalardı telefonla
  // ulaşılmasını isteyen biri formu bırakırdı.
  const s = formuDogrula({ adSoyad: "Ali Veli", telefon: "5419715095" });
  assert.equal(s.gecerli, true, JSON.stringify(s.hatalar));
  assert.equal(s.yuk.eposta, null);
  assert.equal(s.yuk.mesaj, null);
});

test("boş alanlar boş dize değil `null` gidiyor", () => {
  const s = formuDogrula({ ...gecerliGirdi, eposta: "   ", mesaj: "" });
  assert.equal(s.gecerli, true, JSON.stringify(s.hatalar));
  assert.equal(s.yuk.eposta, null, "boş e-posta boş dize olarak gitti");
  assert.equal(s.yuk.mesaj, null, "boş mesaj boş dize olarak gitti");
});

test("ad ve telefon eksikse ikisi birden bildiriliyor", () => {
  // Alanlar tek tek değil TOPLUCA bildiriliyor: her gönderimde yalnız bir
  // hata gösterilse kullanıcı formu üç kez göndermek zorunda kalırdı.
  const s = formuDogrula({});
  assert.equal(s.gecerli, false);
  assert.ok(s.hatalar.adSoyad, "ad hatası yok");
  assert.ok(s.hatalar.telefon, "telefon hatası yok");
});

test("geçersiz telefon örnekli mesaj veriyor", () => {
  const s = formuDogrula({ adSoyad: "Ali Veli", telefon: "123" });
  assert.equal(s.gecerli, false);
  assert.match(s.hatalar.telefon, /0541/, "mesajda örnek numara yok");
});

test("uzunluk sınırları tabloyla aynı", () => {
  // Tablodaki `check` kısıtları son durak; buradaki sınırlar onlarla AYNI
  // olmalı, yoksa form kabul ettiği bir değeri sunucuya gönderir ve sunucu
  // anlaşılmaz bir veritabanı hatası döndürür.
  const uzunAd = "a".repeat(SINIRLAR.adSoyad.enCok + 1);
  const s1 = formuDogrula({ ...gecerliGirdi, adSoyad: uzunAd });
  assert.equal(s1.gecerli, false);
  assert.ok(s1.hatalar.adSoyad);

  const tamAd = "a".repeat(SINIRLAR.adSoyad.enCok);
  assert.equal(formuDogrula({ ...gecerliGirdi, adSoyad: tamAd }).gecerli, true);

  const uzunMesaj = "m".repeat(SINIRLAR.mesaj.enCok + 1);
  const s2 = formuDogrula({ ...gecerliGirdi, mesaj: uzunMesaj });
  assert.equal(s2.gecerli, false);
  assert.ok(s2.hatalar.mesaj);

  assert.equal(SINIRLAR.adSoyad.enAz, 2, "tablodaki alt sınır 2");
  assert.equal(SINIRLAR.adSoyad.enCok, 80, "tablodaki üst sınır 80");
  assert.equal(SINIRLAR.mesaj.enCok, 500, "tablodaki mesaj sınırı 500");
  assert.equal(SINIRLAR.eposta.enCok, 120, "tablodaki e-posta sınırı 120");
});

test("tek karakterli ad reddediliyor", () => {
  const s = formuDogrula({ ...gecerliGirdi, adSoyad: "A" });
  assert.equal(s.gecerli, false);
  assert.ok(s.hatalar.adSoyad);
});

test("bozuk e-posta reddediliyor, makul olanlar geçiyor", () => {
  for (const kotu of ["ayse", "ayse@", "@ornek.com", "ayse@ornek", "a b@c.com"]) {
    const s = formuDogrula({ ...gecerliGirdi, eposta: kotu });
    assert.equal(s.gecerli, false, `"${kotu}" kabul edildi`);
    assert.ok(s.hatalar.eposta);
  }
  // Gevşek doğrulamanın amacı bunlar: sıkı desenlerin yanlışlıkla reddettiği
  // gerçek adresler.
  for (const iyi of [
    "ayse+deneme@ornek.com",
    "a.y@alt.ornek.com.tr",
    "ayse_yilmaz@ornek.co",
  ]) {
    const s = formuDogrula({ ...gecerliGirdi, eposta: iyi });
    assert.equal(s.gecerli, true, `"${iyi}" reddedildi: ${JSON.stringify(s.hatalar)}`);
  }
});

// ─── Tuzak ──────────────────────────────────────────────────────────────────

test("tuzak alanı yalnız doluyken tetikleniyor", () => {
  assert.equal(tuzagaDustu(""), false);
  assert.equal(tuzagaDustu("   "), false);
  assert.equal(tuzagaDustu(null), false);
  assert.equal(tuzagaDustu(undefined), false);
  assert.equal(tuzagaDustu("bot"), true);
  assert.equal(tuzagaDustu("  x  "), true);
});

// ─── İşaretleme varsayılanları ve iki dosyanın anlaşması ────────────────────
//
// Buradan aşağısı `index.html`i ve `site.js`i OKUYOR. Gerekçe, bu depoda
// yaşanmış iki hata sınıfı:
//
//   1. Varsayılanın yanlış tarafta olması. Form `hidden` BAŞLAMALI ve onu
//      yalnızca JavaScript, yalnızca sunucu ayarı varken açmalı. Görünür
//      başlasa ve ayar eksik olsa, ziyaretçi dolduramadığı bir formu
//      doldurup düğmeye basar, hiçbir şey olmaz ve salona ULAŞTIĞINI SANIR.
//      Telefon ve WhatsApp hemen yukarıda duruyor, yani gizli kalmak dürüst
//      olanı.
//
//   2. İki dosyanın sessizce ayrışması. `site.js` alanları kimlikle buluyor
//      (`$("deneme-ad")`); işaretlemede bir kimlik değişirse `$()` `null`
//      dönüyor, gönderme işleyicisi istisna atıyor ve form HİÇBİR ŞEY
//      SÖYLEMEDEN ölüyor. Konsola bakmayan kimse farkı göremez.

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const BURADA = dirname(fileURLToPath(import.meta.url));
const INDEX = readFileSync(join(BURADA, "index.html"), "utf8");
const SITE_JS = readFileSync(join(BURADA, "site.js"), "utf8");

test("form sarmalı işaretlemede `hidden` başlıyor", () => {
  const m = INDEX.match(/<div class="deneme-sarmal"[^>]*>/);
  assert.ok(m, "`.deneme-sarmal` sarmalı bulunamadı");
  assert.match(
    m[0],
    /\shidden[\s>]/,
    "form sarmalı `hidden` olmadan başlıyor — ayar eksikken gönderilemeyen " +
      "bir form görünür kalır",
  );
});

test("tuzak alanı hem gözden hem ekran okuyucudan gizli", () => {
  const m = INDEX.match(/<div class="deneme-tuzak"[^>]*>/);
  assert.ok(m, "tuzak sarmalı bulunamadı");
  assert.match(m[0], /\shidden[\s>]/, "tuzak `hidden` değil");
  assert.match(
    m[0],
    /aria-hidden="true"/,
    "tuzak `aria-hidden` değil — ekran okuyucu kullanan ziyaretçi onu " +
      "doldurabilir ve bot sayılırdı",
  );

  const girdi = INDEX.match(/<input id="deneme-tuzak"[\s\S]*?>/);
  assert.ok(girdi, "tuzak girdisi bulunamadı");
  assert.match(
    girdi[0],
    /autocomplete="off"/,
    "tuzak girdisinde `autocomplete=\"off\"` yok — tarayıcı onu otomatik " +
      "doldurursa gerçek ziyaretçi bot sayılır",
  );
  assert.match(
    girdi[0],
    /tabindex="-1"/,
    "tuzak girdisi klavye sırasında — sekme ile gezinen biri ona düşerdi",
  );
});

test("`site.js`in aradığı her kimlik işaretlemede var", () => {
  // Asıl korunan şey bu: iki dosya kimlikler üzerinden anlaşıyor ve
  // ayrıştıklarında form sessizce ölüyor.
  const kimlikler = [...SITE_JS.matchAll(/\$\(\s*["'`](deneme-[a-z-]+)["'`]\s*\)/g)]
    .map((m) => m[1]);
  // Şablonla kurulan hata kimlikleri (`deneme-${ad}-hata`) ayrıca ekleniyor.
  const hataKimlikleri = ["ad", "telefon", "eposta", "mesaj"]
    .map((a) => `deneme-${a}-hata`);

  const hepsi = [...new Set([...kimlikler, ...hataKimlikleri])];
  assert.ok(hepsi.length >= 7, `yalnızca ${hepsi.length} kimlik bulundu`);

  const eksik = hepsi.filter((k) => !INDEX.includes(`id="${k}"`));
  assert.deepEqual(
    eksik,
    [],
    `site.js bu kimlikleri arıyor ama index.html'de yok: ${eksik.join(", ")}`,
  );
});

test("`maxlength` değerleri sınırlarla aynı", () => {
  // İşaretleme sınırı daha gevşek olsaydı ziyaretçi kabul edilmeyecek bir
  // metni yazabilir ve ancak gönderince öğrenirdi.
  const al = (id) => {
    const m = INDEX.match(new RegExp(`<(?:input|textarea) id="${id}"[\\s\\S]*?>`));
    assert.ok(m, `${id} bulunamadı`);
    const ml = m[0].match(/maxlength="(\d+)"/);
    assert.ok(ml, `${id} içinde maxlength yok`);
    return Number(ml[1]);
  };
  assert.equal(al("deneme-ad"), SINIRLAR.adSoyad.enCok);
  assert.equal(al("deneme-eposta"), SINIRLAR.eposta.enCok);
  assert.equal(al("deneme-mesaj"), SINIRLAR.mesaj.enCok);
});

test("`site.js` modülü içe alıyor (yoksa yayın listesine girmiyor)", () => {
  // `yayin-dosyalari.mjs` yayın listesini importları izleyerek türetiyor.
  // İçe alma kaldırılsa `iletisim-formu.js` sitede 404 olur ve bir ES modülü
  // importu 404 alırsa onu içe alan dosya TAMAMEN yüklenmiyor — yani
  // `site.js`in tamamı, karşılama videosu ve duyurular dahil, ölürdü.
  assert.match(
    SITE_JS,
    /import\s*\{[^}]*\}\s*from\s*["']\.\/iletisim-formu\.js["']/,
    "site.js `./iletisim-formu.js`i içe almıyor",
  );
});

// ─── Anahtar ────────────────────────────────────────────────────────────────
//
// Form şu an KAPALI ve sebebi teknik değil, KVKK sıralaması: kişisel veri
// topluyor ve aydınlatma metni henüz tamam değil (eksik iki bilgi salondan
// gelmeli). Sunucu tarafı tamamen hazır; açmak tek satır.

test("form varsayılan olarak KAPALI", () => {
  // Varsayılanın yanlış tarafa kaymasını yakalayan test bu. Açık olsaydı
  // metin hazır olmadan kişisel veri toplanmaya başlardı.
  assert.equal(FORM_ACIK, false);
  assert.deepEqual(formDurumu(), { acik: false, sebep: "kapali" });
});

test("kapalı ve ayar eksik sebepleri AYRI", () => {
  // Birleştirilseler, form açıldığı gün gerçek bir yapılandırma eksiği
  // görünmez kalırdı: ekran "kapalı" der, kimse anahtarın eksik olduğunu
  // anlamazdı.
  assert.equal(formDurumu({ acik: false, tenantId: "x" }).sebep, "kapali");
  assert.equal(formDurumu({ acik: true, tenantId: null }).sebep, "ayar_eksik");
  assert.deepEqual(formDurumu({ acik: true, tenantId: "x" }), { acik: true });
});

test("`site.js` formu anahtara bağlıyor", () => {
  // Anahtar dosyada dursun ama `site.js` ona bakmasın — o hâlde anahtar
  // hiçbir şey yapmaz ve form açık kalır. Sessiz ve tam yanlış yönde.
  assert.match(
    SITE_JS,
    /formDurumu\(/,
    "site.js `formDurumu()` çağırmıyor — anahtar hiçbir şeyi kapatmaz",
  );
});
