import { test } from "node:test";
import assert from "node:assert/strict";
import {
  metinSadelestir,
  metinEslesir,
  gunBasi,
  gunSonu,
  tarihAraliginda,
  suz,
} from "./suzme.js";

// ─── Türkçe harf katlaması ──────────────────────────────────────────────────
//
// Bu bölüm süzmenin en kolay sessizce yanlış olan yeri. JavaScript'in
// varsayılan büyük/küçük harf dönüşümü Türkçe'de doğru sonuç vermiyor ve hata
// "arama çalışmıyor" diye değil, "bazı isimler bulunamıyor" diye görünürdü.

test("büyük harf, küçük harfle aynı metne indirgeniyor", () => {
  assert.equal(metinSadelestir("AYŞE"), metinSadelestir("ayşe"));
  assert.equal(metinSadelestir("Ayşe Yılmaz"), "ayse yilmaz");
});

test("aksansız yazım da eşleşiyor", () => {
  // Salon sahibi telefonda hızlıca "ayse" yazıyor; "Ayşe"yi bulmalı.
  assert.equal(metinSadelestir("ayse"), metinSadelestir("Ayşe"));
  assert.equal(metinSadelestir("gulsah"), metinSadelestir("Gülşah"));
  assert.equal(metinSadelestir("cigdem"), metinSadelestir("Çiğdem"));
});

test("noktalı ve noktasız i ayrımı aramada kaldırılıyor", () => {
  // JavaScript'in tuzağı: "I".toLowerCase() === "i" ama Türkçe'de "ı"
  // beklenir; "İ".toLowerCase() ise birleşen nokta bırakır. İkisi de tek
  // harfe indirgenmezse "Işıl" araması "isil" ile sonuç vermez.
  assert.equal(metinSadelestir("Işıl"), "isil");
  assert.equal(metinSadelestir("İşil"), "isil");
  assert.equal(metinSadelestir("IŞIL"), metinSadelestir("işil"));
});

test("ayrışık (decomposed) yazım da eşleşiyor", () => {
  // macOS'tan kopyalanan metin çoğu zaman NFD biçiminde geliyor: "ş" tek
  // karakter değil, "s" + birleşen çengel. Harf eşlemesi yalnızca birleşik
  // biçimi tanıdığı için aksan ayrıştırma adımı OLMADAN bu satır eşleşmez ve
  // kullanıcı "aynı ismi yazdım ama bulmuyor" derdi.
  const ayrisik = "Ays\u0327e";          // s + birleşen çengel
  const birlesik = "Ayşe";
  assert.notEqual(ayrisik, birlesik, "kurgu bozuk: iki metin zaten aynı");
  assert.equal(metinSadelestir(ayrisik), metinSadelestir(birlesik));
  assert.equal(metinSadelestir(ayrisik), "ayse");

  // Türkçe dışı aksanlar da sadeleşmeli (ör. yabancı uyruklu üye adı).
  assert.equal(metinSadelestir("José"), "jose");
});

test("birleşen nokta metinde kalmıyor", () => {
  // Regresyon koruması: NFD adımı atlanırsa burada görünmez bir birleşen
  // karakter kalır, uzunluk beklenenden büyük olur ve `includes` tutmaz.
  const sade = metinSadelestir("İstanbul");
  assert.equal(sade, "istanbul");
  assert.equal(sade.length, 8);
});

// ─── Metin araması ──────────────────────────────────────────────────────────

const UYE = { full_name: "Ayşe Yılmaz", phone: "+905321112233", email: "ayse@ornek.com" };

test("boş sorgu her satırı geçiriyor", () => {
  // "Arama yapılmadı" ile "hiçbir şey eşleşmedi" farklı durumlar.
  assert.equal(metinEslesir(UYE, ["full_name"], ""), true);
  assert.equal(metinEslesir(UYE, ["full_name"], "   "), true);
});

test("birden çok alanda aranıyor", () => {
  assert.equal(metinEslesir(UYE, ["full_name", "phone"], "0532"), true);
  assert.equal(metinEslesir(UYE, ["full_name", "phone"], "ayse"), true);
});

test("aranmayan alandaki eşleşme sayılmıyor", () => {
  // `email` alanlar listesinde yok; içeriği eşleşse bile satır gelmemeli.
  assert.equal(metinEslesir(UYE, ["full_name", "phone"], "ornek.com"), false);
});

test("boşlukla ayrılmış parçaların hepsi aranıyor", () => {
  // Kullanıcı "ayşe 0532" yazdığında ad ve telefon AYRI alanlarda; tek parça
  // olarak arasaydık hiçbir alanda böyle bir metin olmadığı için boş dönerdi.
  assert.equal(metinEslesir(UYE, ["full_name", "phone"], "ayse 0532"), true);
  // Parçalardan biri tutmuyorsa satır elenmeli.
  assert.equal(metinEslesir(UYE, ["full_name", "phone"], "ayse 0555"), false);
});

test("eksik alan çökmeye yol açmıyor", () => {
  assert.equal(metinEslesir({ full_name: null }, ["full_name", "phone"], "ayse"), false);
  assert.equal(metinEslesir({}, ["full_name"], ""), true);
});

// ─── Tarih sınırları ────────────────────────────────────────────────────────

test("gün başı yerel gece yarısı", () => {
  const ms = gunBasi("2026-08-15");
  const d = new Date(ms);
  assert.equal(d.getFullYear(), 2026);
  assert.equal(d.getMonth(), 7); // Ağustos
  assert.equal(d.getDate(), 15);
  assert.equal(d.getHours(), 0);
  assert.equal(d.getMinutes(), 0);
});

test("gün sonu aynı günün son milisaniyesi", () => {
  const d = new Date(gunSonu("2026-08-15"));
  assert.equal(d.getDate(), 15, "gün sonu bir sonraki güne taşmamalı");
  assert.equal(d.getHours(), 23);
  assert.equal(d.getMinutes(), 59);
  assert.equal(d.getSeconds(), 59);
  assert.equal(d.getMilliseconds(), 999);
});

test("gün sonu, gün başından tam bir gün eksi 1 ms sonra", () => {
  const fark = gunSonu("2026-08-15") - gunBasi("2026-08-15");
  assert.equal(fark, 24 * 60 * 60 * 1000 - 1);
});

test("gün başı YEREL saate göre, UTC'ye göre değil", () => {
  // Bu iddia yalnızca UTC DIŞINDA anlamlı: UTC'de yerel ve UTC hesap
  // birbirinin aynısı çıkar ve `Date.UTC` kullanan bozuk bir sürüm de
  // testlerden geçerdi. CI bu yüzden TZ=Europe/Istanbul ile koşuyor.
  const ofsetDk = new Date(2026, 7, 15).getTimezoneOffset();
  if (ofsetDk === 0) {
    // Sessizce geçmek yerine sebebini söyleyerek atlanıyor.
    console.log("      ATLANDI: saat dilimi UTC; TZ=Europe/Istanbul ile koşturun");
    return;
  }
  assert.notEqual(
    gunBasi("2026-08-15"),
    Date.UTC(2026, 7, 15),
    "gün başı UTC gece yarısına eşit olmamalı — yerel gün kullanılmalı",
  );
  assert.equal(gunBasi("2026-08-15"), new Date(2026, 7, 15).getTime());
});

test("geçersiz tarih null dönüyor", () => {
  for (const kotu of ["", null, undefined, "15/08/2026", "2026-8-15", "abc", "2026-13-01"]) {
    assert.equal(gunBasi(kotu), null, `gunBasi(${JSON.stringify(kotu)})`);
    assert.equal(gunSonu(kotu), null, `gunSonu(${JSON.stringify(kotu)})`);
  }
});

test("olmayan takvim günü reddediliyor", () => {
  // JavaScript 31 Nisan'ı sessizce 1 Mayıs'a kaydırır; bunu kabul etmek
  // kullanıcının hiç yazmadığı bir tarihle süzmek olurdu.
  assert.equal(gunBasi("2026-04-31"), null);
  assert.equal(gunBasi("2026-02-30"), null);
  // Gerçek artık yıl günü geçerli kalmalı.
  assert.notEqual(gunBasi("2024-02-29"), null);
});

// ─── Tarih aralığı ──────────────────────────────────────────────────────────

test("aralık her iki uçta da dahil", () => {
  const bas = gunBasi("2026-08-01");
  const bit = gunSonu("2026-08-15");

  // Bitiş gününün akşamı yazılmış bir kayıt aralığa GİRMELİ. En kolay yapılan
  // hata bu: gün sonu yerine gün başı kullanılırsa bu satır dışarıda kalır.
  const aksam = new Date(2026, 7, 15, 22, 30).getTime();
  assert.equal(tarihAraliginda({ t: aksam }, "t", bas, bit), true);

  const ilkAn = new Date(2026, 7, 1, 0, 0, 0, 0).getTime();
  assert.equal(tarihAraliginda({ t: ilkAn }, "t", bas, bit), true);

  const ertesiGun = new Date(2026, 7, 16, 0, 0).getTime();
  assert.equal(tarihAraliginda({ t: ertesiGun }, "t", bas, bit), false);
});

test("tek uçlu aralıklar çalışıyor", () => {
  const t = new Date(2026, 7, 10).getTime();
  assert.equal(tarihAraliginda({ t }, "t", gunBasi("2026-08-01"), null), true);
  assert.equal(tarihAraliginda({ t }, "t", gunBasi("2026-09-01"), null), false);
  assert.equal(tarihAraliginda({ t }, "t", null, gunSonu("2026-08-31")), true);
  assert.equal(tarihAraliginda({ t }, "t", null, gunSonu("2026-07-31")), false);
});

test("aralık yoksa her satır geçiyor", () => {
  assert.equal(tarihAraliginda({ t: null }, "t", null, null), true);
});

test("tarihsiz satır, aralık verildiğinde eleniyor", () => {
  // Süresiz üyelikte bitiş tarihi yok; "şu tarihler arasında bitenler"
  // sorusunun cevabı değil.
  const bas = gunBasi("2026-08-01");
  assert.equal(tarihAraliginda({ t: null }, "t", bas, null), false);
  assert.equal(tarihAraliginda({ t: "" }, "t", bas, null), false);
  assert.equal(tarihAraliginda({}, "t", bas, null), false);
});

// ─── Birleşik süzme ─────────────────────────────────────────────────────────

const SATIRLAR = [
  { full_name: "Ayşe Yılmaz", phone: "+905321112233", end_date_ms: new Date(2026, 7, 10).getTime() },
  { full_name: "Mehmet Öz", phone: "+905339998877", end_date_ms: new Date(2026, 8, 20).getTime() },
  { full_name: "Işıl Kaya", phone: "+905301234567", end_date_ms: new Date(2026, 7, 14).getTime() },
  { full_name: "Süresiz Üye", phone: "+905300000000", end_date_ms: null },
];

test("süzgeç yoksa liste aynen kalıyor", () => {
  assert.equal(suz(SATIRLAR).length, 4);
  assert.equal(suz(SATIRLAR, { sorgu: "", alanlar: ["full_name"] }).length, 4);
});

test("metin ve tarih VE ile birleşiyor", () => {
  const sonuc = suz(SATIRLAR, {
    sorgu: "isil",
    alanlar: ["full_name", "phone"],
    tarihAlani: "end_date_ms",
    baslangic: "2026-08-01",
    bitis: "2026-08-31",
  });
  assert.deepEqual(sonuc.map((s) => s.full_name), ["Işıl Kaya"]);

  // Aynı sorgu, aralık dışına alındığında hiçbir şey dönmemeli. VEYA ile
  // birleştirilseydi bu satır yine gelirdi.
  const bos = suz(SATIRLAR, {
    sorgu: "isil",
    alanlar: ["full_name"],
    tarihAlani: "end_date_ms",
    baslangic: "2026-09-01",
    bitis: "2026-09-30",
  });
  assert.equal(bos.length, 0);
});

test("tarih aralığı süresiz üyeyi eliyor ama süzgeçsizken elemiyor", () => {
  const hepsi = suz(SATIRLAR, { tarihAlani: "end_date_ms" });
  assert.equal(hepsi.length, 4, "aralık verilmediyse tarihsiz satır kalmalı");

  const araliktakiler = suz(SATIRLAR, {
    tarihAlani: "end_date_ms",
    baslangic: "2026-08-01",
    bitis: "2026-08-31",
  });
  assert.deepEqual(araliktakiler.map((s) => s.full_name), ["Ayşe Yılmaz", "Işıl Kaya"]);
});

test("geçersiz tarih girdisi süzgeci sessizce kapatıyor", () => {
  // Kullanıcı tarihi yazarken ara durumlar geçersiz oluyor ("2026-0…").
  // Bunlarda liste boşalsaydı ekran yazarken titrer, kullanıcı yazmayı
  // bırakırdı.
  const sonuc = suz(SATIRLAR, { tarihAlani: "end_date_ms", baslangic: "2026-0" });
  assert.equal(sonuc.length, 4);
});

test("özgün liste değiştirilmiyor", () => {
  const kopya = [...SATIRLAR];
  suz(SATIRLAR, { sorgu: "ayse", alanlar: ["full_name"] });
  assert.deepEqual(SATIRLAR, kopya);
});
