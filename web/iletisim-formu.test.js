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
  tuzagaDustu,
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
