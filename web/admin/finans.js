// Finans panosunun HESAPLARI — çizim yok, DOM yok.
//
// Ayrı dosya olmasının sebebi bu panelde birkaç kez kanıtlandı: `app.js`
// içindeki mantık ancak tarayıcıda, gerçek bir sunucuya bağlıyken
// denenebiliyor. Buradaki her şey saf fonksiyon, dolayısıyla `finans.test.js`
// hepsini doğrudan sınayabiliyor.
//
// ### Panelde neden hesap YAPILIYOR
// `ozet.js`in başındaki kural hâlâ geçerli: üye bakiyesi ve hakediş burada
// hesaplanmıyor, çünkü ikisi de ortak Kotlin modülünde tanımlı gerçek iş
// kuralları ve kopyalanmaları sessiz sapma üretir.
//
// Buradakiler o sınıfa girmiyor: hepsi **aynı satırların düz toplamı**.
// "Ağustosta ne kadar tahsilat" sorusunun tek bir doğru cevabı var ve o cevap
// defterin kendisinde duruyor; bir iş kuralı yorumu gerektirmiyor. Tek kural
// iptal edilmiş kayıtların elenmesi ve o da `domain.js`te, tek yazımla.

import { aktifDefterKayitlari, iptalEdilenKimlikler } from "./domain.js";

/**
 * Kategori kodlarının Türkçe karşılığı.
 *
 * Ad `FINANS_` önekli: `app.js` ve `stok.js` de etiket sözlükleri tutuyor ve
 * önizleme üreticisi modülleri tek kapsamda birleştirdiği için önseksiz adlar
 * çakışırdı. Takma adla içe almak çözüm DEĞİL — birleştirmede `import ... as`
 * diye bir şey kalmıyor.
 */
export const FINANS_KATEGORI_ETIKETLERI = {
  MEMBERSHIP: "Üyelik",
  MARKET: "Market",
  COMMISSION: "Hakediş",
  SALARY: "Maaş",
  RENT: "Kira",
  BILL: "Fatura",
  PURCHASE: "Alım",
  OTHER: "Diğer",
};

/** Ödeme yöntemlerinin Türkçe karşılığı. */
export const FINANS_YONTEM_ETIKETLERI = {
  CASH: "Nakit",
  CARD: "Kart",
  MULTISPORT: "Multisport",
};

/** Ay kısaltmaları; seyir grafiğinin etiketleri. */
const AY_KISA = [
  "Oca", "Şub", "Mar", "Nis", "May", "Haz",
  "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara",
];

/** Sayıya çevrilemeyen tutar toplamı bozmasın: tek bozuk satır raporu NaN yapmamalı. */
function tutar(kayit) {
  const sayi = Number(kayit?.amount_minor);
  return Number.isFinite(sayi) ? sayi : 0;
}

/** Kaydın gerçekleştiği an; okunamıyorsa `null`. */
function an(kayit) {
  const sayi = Number(kayit?.occurred_at_ms);
  return Number.isFinite(sayi) ? sayi : null;
}

/**
 * Bir kaydın iptal durumu — tablodaki rozet bunun karşılığı.
 *
 * Üç hâl var ve üçü de farklı şey anlatıyor:
 *  - `normal`  — yaşayan kayıt, toplamlara giriyor
 *  - `iptal`   — iptal EDİLMİŞ asıl kayıt
 *  - `ters`    — iptali YAPAN kayıt
 *
 * İkisini tek "iptal" hâlinde birleştirmek yanıltıcı olurdu: listede aynı
 * tutar iki kez görünüyor ve hangisinin asıl, hangisinin düzeltme olduğu
 * ancak bu ayrımla anlaşılıyor.
 */
export function kayitIptalDurumu(kayit, iptalEdilenler) {
  if (kayit?.reverses_id) return "ters";
  if (iptalEdilenler?.has(kayit?.id)) return "iptal";
  return "normal";
}

/**
 * Üst satırdaki özet kutuların sayıları.
 *
 * ### `CHARGE` neden gelire eklenmiyor
 * Tahakkuk, üyeye açılmış bir **alacak** — para henüz kasaya girmedi.
 * Tahsilata eklenseydi ciro, tahsil edilmemiş borçla şişerdi. Ayrı kutuda
 * duruyor çünkü kendi başına da bir bilgi: "bu dönemde ne kadar borç doğdu".
 *
 * @returns `{ tahsilat, gider, net, tahakkuk, kayitSayisi, iptalSayisi }`
 *   — tutarlar kuruş
 */
export function defterOzeti(kayitlar) {
  const liste = kayitlar ?? [];
  const aktifler = aktifDefterKayitlari(liste);

  let tahsilat = 0;
  let gider = 0;
  let tahakkuk = 0;
  for (const k of aktifler) {
    if (k.type === "PAYMENT") tahsilat += tutar(k);
    else if (k.type === "EXPENSE") gider += tutar(k);
    else if (k.type === "CHARGE") tahakkuk += tutar(k);
  }

  return {
    tahsilat,
    gider,
    net: tahsilat - gider,
    tahakkuk,
    kayitSayisi: aktifler.length,
    // İptal edilen ÇİFT sayısı, satır sayısı değil: bir iptal iki satır
    // üretiyor ve "4 kayıt iptal edildi" demek kullanıcının yaptığı iki
    // işlemi dört gösterirdi.
    iptalSayisi: iptalEdilenKimlikler(liste).size,
  };
}

/**
 * Kategori kırılımı — hangi kalemden ne kadar girdi, ne kadar çıktı.
 *
 * Tek satırda hem tahsilat hem gider olabiliyor (ör. MARKET: satış geliri ve
 * ürün alımı gideri) ve ikisini tek "net" sayıya indirmek bilgiyi yok ederdi:
 * 10.000 gelir ve 9.000 gider ile 1.000 gelir ve 0 gider aynı görünürdü.
 *
 * Sıralama toplam hacme göre: en çok para hareketi olan kalem üstte.
 */
export function kategoriKirilimi(kayitlar) {
  const harita = new Map();

  for (const k of aktifDefterKayitlari(kayitlar ?? [])) {
    const kod = k.category ?? "OTHER";
    if (!harita.has(kod)) {
      harita.set(kod, { kod, etiket: kategoriEtiketi(kod), tahsilat: 0, gider: 0, tahakkuk: 0 });
    }
    const satir = harita.get(kod);
    if (k.type === "PAYMENT") satir.tahsilat += tutar(k);
    else if (k.type === "EXPENSE") satir.gider += tutar(k);
    else if (k.type === "CHARGE") satir.tahakkuk += tutar(k);
  }

  return [...harita.values()].sort(
    (a, b) => (b.tahsilat + b.gider + b.tahakkuk) - (a.tahsilat + a.gider + a.tahakkuk),
  );
}

/**
 * Ödeme yöntemi kırılımı — yalnızca TAHSİLAT.
 *
 * Gider dışarıda: "nakit mi kart mı" sorusu kasa sayımı için soruluyor ve
 * kiranın karttan ödenmiş olması o soruyu ilgilendirmiyor. Tahakkukta ise
 * ödeme yöntemi henüz belli değil — kayıttaki değer yalnızca üyenin varsayılan
 * tercihi, gerçekleşmiş bir işlem değil.
 *
 * `oran`: toplam tahsilat içindeki pay (0–1). Toplam sıfırsa 0 — bölme
 * yapılmıyor, aksi hâlde her satır `NaN` olurdu.
 */
export function yontemKirilimi(kayitlar) {
  const harita = new Map();
  let toplam = 0;

  for (const k of aktifDefterKayitlari(kayitlar ?? [])) {
    if (k.type !== "PAYMENT") continue;
    const kod = k.payment_method ?? "CASH";
    harita.set(kod, (harita.get(kod) ?? 0) + tutar(k));
    toplam += tutar(k);
  }

  return [...harita.entries()]
    .map(([kod, deger]) => ({
      kod,
      etiket: FINANS_YONTEM_ETIKETLERI[kod] ?? kod,
      tahsilat: deger,
      oran: toplam > 0 ? deger / toplam : 0,
    }))
    .sort((a, b) => b.tahsilat - a.tahsilat);
}

/**
 * Aylık seyir — son [ayAdedi] ay, eskiden yeniye.
 *
 * ### Boş aylar neden atlanmıyor
 * Hiç hareket olmayan ay da sıfırla listede duruyor. Atlansaydı grafik
 * yanıltıcı olurdu: iki dolu ay yan yana çizilir ve aradaki ölü ay hiç
 * görünmezdi — düşüşü göstermek grafiğin var oluş sebebi.
 *
 * Aylar YEREL saate göre ayrılıyor (`new Date(...)` yerel). Salonun ayı takvim
 * ayı; UTC'ye göre bölünseydi ayın ilk ve son günündeki akşam kayıtları komşu
 * aya düşerdi.
 */
export function aylikSeyir(kayitlar, simdiMs, ayAdedi = 6) {
  const adet = Math.max(1, Math.floor(ayAdedi));
  const simdi = new Date(simdiMs);

  const aylar = [];
  const dizin = new Map();
  for (let i = adet - 1; i >= 0; i -= 1) {
    const bas = new Date(simdi.getFullYear(), simdi.getMonth() - i, 1);
    const satir = {
      yil: bas.getFullYear(),
      ay: bas.getMonth(),
      etiket: `${AY_KISA[bas.getMonth()]} ${bas.getFullYear()}`,
      tahsilat: 0,
      gider: 0,
      net: 0,
    };
    aylar.push(satir);
    dizin.set(`${satir.yil}-${satir.ay}`, satir);
  }

  for (const k of aktifDefterKayitlari(kayitlar ?? [])) {
    const zaman = an(k);
    if (zaman === null) continue;
    const t = new Date(zaman);
    const satir = dizin.get(`${t.getFullYear()}-${t.getMonth()}`);
    if (!satir) continue; // pencerenin dışında
    if (k.type === "PAYMENT") satir.tahsilat += tutar(k);
    else if (k.type === "EXPENSE") satir.gider += tutar(k);
  }

  for (const satir of aylar) satir.net = satir.tahsilat - satir.gider;
  return aylar;
}

/** Kategori kodunun görünen adı; tanınmayan kod olduğu gibi gösteriliyor. */
export function kategoriEtiketi(kod) {
  return FINANS_KATEGORI_ETIKETLERI[kod] ?? kod ?? "—";
}
