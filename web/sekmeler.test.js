// Sekme tanımlarındaki kolon adları gerçek mi?
//
// Tanımlar 20'den fazla kolon adı taşıyor ve üç yazım hatası türü de **sessiz**:
//
//   - `order`da hata → sunucu 400 döndürür, sekme boş açılır
//   - `ara`da hata → arama o alanda çalışmaz ve hiçbir hata görünmez
//   - `tarihAlani`nda hata → tarih süzgeci hiçbir şeyi süzmez
//
// Üçü de yalnızca o sekmeye basıp elle deneyerek fark edilir; testsiz kalırsa
// yeni bir sekme eklendiğinde kimse fark etmez.
//
// Karşılaştırma **SQL migrasyonlarına** karşı yapılıyor, demo verisine değil.
// Sebebi: demo verisi de bir kopya ve kopyayı kopyayla karşılaştırmak ikisinin
// birlikte yanlış olmasını yakalamıyor. Panelin sorguları sunucuya gidiyor, yani
// doğruyu söyleyen şey `supabase/migrations`.
//
// Demo verisi ayrıca sınanıyor ama farklı bir soru için: önizlemenin panelin
// gerçekten okuduğu alanları gösterebiliyor olması.

import test from "node:test";
import assert from "node:assert/strict";

import { SEKME_VERISI, siraKolonu, tablolari } from "./sekmeler.js";
import { SEKME_ROLLERI } from "./roller.js";
import { demoIstemcisi } from "./demo.js";
import { sunucuSemasi } from "./sema.js";

const SEMA = sunucuSemasi();

/** Sunucu şemasındaki kolonlar; tablo yoksa test açıkça düşüyor. */
function semaKolonlari(tablo) {
  const kolonlar = SEMA[tablo];
  assert.ok(
    kolonlar && kolonlar.size > 0,
    `\`${tablo}\` tablosu SQL migrasyonlarında bulunamadı. Tablo adı yanlış ` +
      `yazılmışsa panel 404 alır; şema ayrıştırıcısı bozulmuşsa test onarılmalı.`,
  );
  return kolonlar;
}

/** Demo verisini tablo adına göre okur. */
async function demoSatirlari() {
  const istemci = demoIstemcisi();
  await istemci.girisYap("test@ornek.com");

  const tablolar = {};
  for (const tanim of Object.values(SEKME_VERISI)) {
    for (const tablo of tablolari(tanim)) {
      if (!tablolar[tablo]) tablolar[tablo] = (await istemci.oku(tablo)).satirlar;
    }
  }
  return tablolar;
}

/** Bir tablodaki bütün satırlarda görülen kolon adları. */
function kolonlar(satirlar) {
  const kume = new Set();
  for (const satir of satirlar) for (const k of Object.keys(satir)) kume.add(k);
  return kume;
}

test("her sekmenin demo verisi var", async () => {
  const tablolar = await demoSatirlari();

  for (const [sekme, tanim] of Object.entries(SEKME_VERISI)) {
    for (const tablo of tablolari(tanim)) {
      assert.ok(
        tablolar[tablo] && tablolar[tablo].length > 0,
        `\`${sekme}\` sekmesi \`${tablo}\` tablosunu okuyor ama demo verisi yok — ` +
          `önizlemede boş görünür.`,
      );
    }
  }
});

test("sıralama kolonları gerçek", () => {
  for (const [sekme, tanim] of Object.entries(SEKME_VERISI)) {
    if (!tanim.order) continue;
    const kolon = siraKolonu(tanim.order);
    assert.ok(kolon, `\`${sekme}\` sekmesinin \`order\` değeri okunamadı: ${tanim.order}`);
    assert.ok(
      semaKolonlari(tanim.tablo).has(kolon),
      `\`${sekme}\` sekmesi \`${kolon}\` kolonuna göre sıralıyor ama \`${tanim.tablo}\` ` +
        `tablosunda böyle bir kolon yok. Sunucu 400 döndürür ve sekme boş açılır.`,
    );
  }
});

test("arama kolonları gerçek", () => {
  for (const [sekme, tanim] of Object.entries(SEKME_VERISI)) {
    if (!tanim.ara) continue;
    const mevcut = semaKolonlari(tanim.tablo);

    for (const alan of tanim.ara) {
      assert.ok(
        mevcut.has(alan),
        `\`${sekme}\` sekmesi \`${alan}\` alanında arıyor ama \`${tanim.tablo}\` ` +
          `tablosunda böyle bir kolon yok. Arama sessizce o alanı atlar — hiçbir ` +
          `hata görünmez, sonuç sadece eksik çıkar.`,
      );
    }
  }
});

test("tarih süzgeci kolonları gerçek", () => {
  for (const [sekme, tanim] of Object.entries(SEKME_VERISI)) {
    if (!tanim.tarihAlani) continue;
    assert.ok(
      semaKolonlari(tanim.tablo).has(tanim.tarihAlani),
      `\`${sekme}\` sekmesi \`${tanim.tarihAlani}\` kolonuna göre süzüyor ama ` +
        `\`${tanim.tablo}\` tablosunda yok. Süzgeç sessizce hiçbir şeyi süzmez.`,
    );
  }
});

test("tarih alanı olan her sekmenin etiketi var", () => {
  for (const [sekme, tanim] of Object.entries(SEKME_VERISI)) {
    if (!tanim.tarihAlani) continue;
    assert.ok(
      tanim.tarihEtiketi,
      `\`${sekme}\` sekmesinde tarih süzgeci var ama etiketi yok; kullanıcı hangi ` +
        `tarihe göre süzdüğünü bilemez.`,
    );
  }
});

/**
 * Tarih alanları gerçekten tarih mi taşıyor?
 *
 * Kolonun var olması yetmiyor: süzgeç epoch ms bekliyor. Metin ya da tarih
 * olmayan bir kolona bağlanırsa süzgeç sessizce boş sonuç verir.
 */
test("tarih alanları epoch ms değer taşıyor", async () => {
  const tablolar = await demoSatirlari();
  const enErken = Date.parse("2000-01-01");
  const enGec = Date.parse("2100-01-01");

  for (const [sekme, tanim] of Object.entries(SEKME_VERISI)) {
    if (!tanim.tarihAlani) continue;

    const degerler = tablolar[tanim.tablo]
      .map((s) => s[tanim.tarihAlani])
      .filter((d) => d !== null && d !== undefined);

    assert.ok(degerler.length > 0, `\`${sekme}\`: \`${tanim.tarihAlani}\` hep boş.`);

    for (const deger of degerler) {
      assert.equal(
        typeof deger, "number",
        `\`${sekme}\`: \`${tanim.tarihAlani}\` sayı değil (${typeof deger}) — epoch ms olmalı.`,
      );
      assert.ok(
        deger > enErken && deger < enGec,
        `\`${sekme}\`: \`${tanim.tarihAlani}\` makul bir tarih değil (${deger}). ` +
          `Saniye cinsinden bir damga milisaniye sanılıyorsa 1970'e düşer.`,
      );
    }
  }
});

/**
 * Sekme listeleri iki dosyada ve aynı olmalı.
 *
 * `roller.js` görünürlüğü, `sekmeler.js` veriyi tanımlıyor. Biri güncellenip
 * diğeri unutulursa: `roller.js`te olmayan bir sekme **gizli** kalır (sessizce
 * erişilemez), `sekmeler.js`te olmayan bir sekme ise tanımsız veriyle açılır.
 */
test("roller.js ile sekmeler.js aynı sekmeleri tanıyor", () => {
  assert.deepEqual(
    Object.keys(SEKME_VERISI).sort(),
    Object.keys(SEKME_ROLLERI).sort(),
    "İki dosyadaki sekme listesi ayrışmış.",
  );
});

// ─── Demo verisi ────────────────────────────────────────────────────────────
//
// Yukarıdaki testler panelin sunucuyla uyumunu sınıyor. Bunlar farklı bir soru
// soruyor: önizleme, panelin gerçekten okuduğu şeyi gösterebiliyor mu?

/**
 * Demo satırları şemada olmayan bir alan taşımamalı.
 *
 * Taşırsa ters yönde bir tuzak doğuyor: panel o alanı okuyacak şekilde yazılır,
 * demoda çalışır, gerçek veride `undefined` gelir. Demo verisinin değeri
 * sunucudan gelen biçimin aynısı olmasında; saptığı anda yanlış güven veriyor.
 */
test("demo satırları şemada olmayan alan taşımıyor", async () => {
  const tablolar = await demoSatirlari();
  const fazlalar = [];

  for (const [tablo, satirlar] of Object.entries(tablolar)) {
    const semada = semaKolonlari(tablo);
    for (const alan of kolonlar(satirlar)) {
      if (!semada.has(alan)) fazlalar.push(`${tablo}.${alan}`);
    }
  }

  assert.deepEqual(
    fazlalar,
    [],
    "Demo verisinde şemada olmayan alanlar var; panel bunlara güvenirse gerçek " +
      `veride tanımsız gelir:\n  ${fazlalar.join("\n  ")}`,
  );
});

/**
 * Panelin okuduğu her alan demo verisinde de olmalı.
 *
 * Olmazsa o özellik önizlemede **denenemez**: arama kutusuna e-posta yazan biri
 * sonuç alamaz ve bunun sebebi panelin hatası mı demo verisinin eksikliği mi
 * belli olmaz. `email` ve `notes` alanları tam olarak bu durumdaydı.
 */
test("demo verisi panelin aradığı alanları taşıyor", async () => {
  const tablolar = await demoSatirlari();
  const eksikler = [];

  for (const tanim of Object.values(SEKME_VERISI)) {
    if (!tanim.ara) continue;
    const mevcut = kolonlar(tablolar[tanim.tablo]);
    for (const alan of tanim.ara) {
      if (!mevcut.has(alan)) eksikler.push(`${tanim.tablo}.${alan}`);
    }
  }

  assert.deepEqual(
    eksikler,
    [],
    `Bu alanlar demo verisinde yok, önizlemede aranamaz:\n  ${eksikler.join("\n  ")}`,
  );
});
