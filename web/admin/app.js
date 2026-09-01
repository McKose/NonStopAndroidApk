// Panelin akışı: giriş, sekmeler, listeler.
//
// Panel **salt okunur**. Yazma bilinçli olarak yok: uygulamadaki her yazma yolu
// aynı transaction içinde gönderim kuyruğuna kayıt bırakıyor ve iş kuralları
// (hakediş, seans düşme, defter kaydı) ortak Kotlin modülünde. Panelden yazmak,
// o kuralların ikinci bir kopyasını burada tutmak demek olurdu — ve iki kopya
// er geç birbirinden sapardı.

import { SupabaseClient } from "./supabase.js";
import {
  tutarYaz,
  tarihYaz,
  uyelikDurumu,
  durumEtiketi,
  silinmemisler,
  iptalEdilenKimlikler,
} from "./domain.js";
import { ayBasi, uyeDagilimi, yaklasanBitisler, defterToplami } from "./ozet.js";
import {
  FINANS_YONTEM_ETIKETLERI,
  defterOzeti,
  kategoriKirilimi,
  yontemKirilimi,
  aylikSeyir,
  kayitIptalDurumu,
  kategoriEtiketi,
} from "./finans.js";
import { demoIstemcisi, demoMu } from "./demo.js";
import { suz } from "./suzme.js";
import { stokHaritasi, stokYaz, stokUyarilari, stokDurumu } from "./stok.js";
import { sekmeGorunur } from "./roller.js";
// Adların `domain.js` ile ÇAKIŞMAMASI gerekiyor; `import ... as` yetmiyor.
// Önizleme üreticisi modülleri tek kapsamda birleştiriyor ve orada takma ad
// diye bir şey kalmıyor — çakışan iki tanım panelin tamamını düşürüyor.
// Bu yüzden `davet.js` kendi adlarını benzersiz seçiyor (`erisimEtiketi`).
import {
  DAVET_YETKILERI,
  YETKI_ACIKLAMA,
  YETKI_YOK_ACIKLAMA,
  erisimDurumu,
  erisimEtiketi,
  davetiDogrula,
  davetHataMesaji,
  davetBasariMesaji,
} from "./davet.js";
import { SEKME_VERISI } from "./sekmeler.js";

const $ = (id) => document.getElementById(id);

// `?demo` ile açıldığında sunucuya hiç gidilmiyor: ekranı değerlendirmek için
// Supabase ayarı, hesap ve internet gerekmesin. Demo istemcisi gerçeğiyle aynı
// yüzeyi taşıyor, dolayısıyla aşağıdaki kodun hangisiyle çalıştığından haberi yok.
const ayar = window.NONSTOP_CONFIG;
const istemci = demoMu() ? demoIstemcisi() : new SupabaseClient(ayar?.url, ayar?.anonKey);

let aktifSekme = "ozet";

/** Giriş yapan kişinin rolü; sekme görünürlüğü buna bağlı. */
let aktifRol = null;

/** Giriş yapan kişinin salonu; yeni satır yazarken `tenant_id` buradan. */
let aktifTenant = null;

/**
 * Sunucudan gelen ham satırlar, açık olan sekme için.
 *
 * Süzme bunun üzerinde yapılıyor; her tuş vuruşunda sunucuya gidilmiyor.
 * Salonun ölçeğinde (birkaç yüz üye) bu anında sonuç veriyor. Sekme
 * değiştiğinde tazeleniyor, dolayısıyla bayat veri birikmiyor.
 */
let acikSatirlar = [];

// ─── Görünüm geçişleri ──────────────────────────────────────────────────────

function goster(bolum) {
  for (const id of ["giris", "panel", "ayar-eksik"]) {
    $(id).hidden = id !== bolum;
  }
}

function hataYaz(alanId, mesaj) {
  const alan = $(alanId);
  alan.textContent = mesaj ?? "";
  alan.hidden = !mesaj;
}

// ─── Giriş ──────────────────────────────────────────────────────────────────

$("giris-formu").addEventListener("submit", async (olay) => {
  olay.preventDefault();
  const dugme = $("giris-dugmesi");
  if (dugme.disabled) return; // çift tıklama koruması

  dugme.disabled = true;
  dugme.textContent = "Giriş yapılıyor…";
  hataYaz("giris-hata", null);

  const veri = new FormData(olay.target);
  const sonuc = await istemci.girisYap(
    String(veri.get("eposta")).trim(),
    String(veri.get("sifre")),
  );

  dugme.disabled = false;
  dugme.textContent = "Giriş yap";

  // Her başarısızlık türü kullanıcıyı farklı bir işe yönlendiriyor; tek bir
  // "giriş başarısız" mesajı çoğu zaman yanlış yönlendirirdi.
  if (sonuc.tur === "kimlik") return hataYaz("giris-hata", `E-posta veya şifre hatalı. (${sonuc.mesaj})`);
  if (sonuc.tur === "salonsuz") return hataYaz("giris-hata", sonuc.mesaj);
  if (sonuc.tur !== "tamam") return hataYaz("giris-hata", sonuc.mesaj);

  paneliAc(sonuc.oturum);
});

$("cikis").addEventListener("click", () => {
  istemci.oturumSil();
  goster("giris");
});

function paneliAc(oturum) {
  $("salon-adi").textContent = oturum.gym_name || "Salon";
  $("kullanici").textContent = oturum.email || "";
  $("rol").textContent = rolEtiketi(oturum.role);
  aktifRol = oturum.role;
  aktifTenant = oturum.gym_id;
  sekmeleriRoleGoreAyarla();
  goster("panel");
  sekmeYukle(aktifSekme);
}

/**
 * Rolün görmediği sekmeleri gizler.
 *
 * Kural `roller.js`te ve orası uygulamadaki `AppDestination` kuralının
 * sınanan kopyası: panel kendi kararını vermiyor. Daha önce veriyordu — Finans
 * sekmesi her role açıktı, oysa uygulama onu eğitmene göstermiyor. Aynı ürün
 * iki farklı cevap veriyordu.
 *
 * Gizleme bir güvenlik sınırı DEĞİL: sunucu okumayı salona bağlı her role açıyor
 * (migrasyon `0004`) ve paneli kandıran biri veriyi API'den yine okuyabilir.
 * Gerçek sınır yazma tarafında ve o sunucuda.
 */
function sekmeleriRoleGoreAyarla() {
  let aktifGizlendi = false;

  for (const dugme of $("sekmeler").querySelectorAll("button[data-sekme]")) {
    const gorunur = sekmeGorunur(dugme.dataset.sekme, aktifRol);
    dugme.hidden = !gorunur;
    if (!gorunur && dugme.dataset.sekme === aktifSekme) aktifGizlendi = true;
  }

  // Açık sekme bu rolde görünmüyorsa ilk görünür sekmeye dönülüyor. Aksi hâlde
  // rol değişen bir oturumda (ya da eski bir sekme hatırlandığında) panel boş
  // kalır ve kullanıcı sebebini anlamaz.
  if (aktifGizlendi) {
    const ilk = [...$("sekmeler").querySelectorAll("button[data-sekme]")]
      .find((d) => !d.hidden);
    aktifSekme = ilk ? ilk.dataset.sekme : "ozet";
    for (const d of $("sekmeler").querySelectorAll("button[data-sekme]")) {
      d.classList.toggle("secili", d.dataset.sekme === aktifSekme);
    }
  }
}

function rolEtiketi(rol) {
  switch (rol) {
    case "ADMIN": return "Yönetici";
    case "MANAGER": return "Müdür";
    case "TRAINER": return "Eğitmen";
    default: return rol || "—";
  }
}

// ─── Sekmeler ───────────────────────────────────────────────────────────────

$("sekmeler").addEventListener("click", (olay) => {
  const dugme = olay.target.closest("button[data-sekme]");
  if (!dugme) return;
  for (const d of $("sekmeler").querySelectorAll("button")) {
    d.classList.toggle("secili", d === dugme);
  }
  aktifSekme = dugme.dataset.sekme;
  sekmeYukle(aktifSekme);
});

/**
 * Sekmenin veri tanımı + çizim işi.
 *
 * Veri kısmı (tablo, sıra, aranan kolonlar) `sekmeler.js`te ve orada olmasının
 * sebebi test: o tanımlar 20'den fazla kolon adı taşıyor, bir yazım hatası
 * sessizce arama ya da süzgeci bozuyor ve `sekmeler.test.js` her adı SQL
 * şemasıyla karşılaştırıyor. Buradaki `ciz`/`ozel` işleri DOM'a dokunduğu için
 * Node'da koşamıyor, o yüzden ayrı duruyor.
 *
 * Çizim tablosu ile veri tablosunun aynı sekmeleri tanıdığı da sınanıyor
 * (aşağıdaki kontrol): biri güncellenip diğeri unutulursa sekme ya tanımsız
 * veriyle açılır ya hiç çizilmez.
 */
const CIZIMLER = {
  ozet: { ozel: ozetYukle },
  uyeler: { ciz: uyeleriCiz },
  paketler: { ciz: paketleriCiz },
  randevular: { ciz: randevulariCiz },
  market: { ozel: marketYukle },
  satislar: { ciz: satislariCiz },
  personel: { ciz: personeliCiz },
  finans: { ciz: finansiCiz },
  duyurular: { ozel: duyurulariYukle },
  "uye-hesaplari": { ozel: uyeHesaplariYukle },
  "personel-erisim": { ozel: personelErisimYukle },
};

const SEKMELER = Object.fromEntries(
  Object.entries(SEKME_VERISI).map(([ad, veri]) => [ad, { ...veri, ...CIZIMLER[ad] }]),
);

// İki tablonun ayrışması sessiz bir hata olurdu: çizimi olmayan sekme boş açılır,
// verisi olmayan sekme tanımsız tabloyu okumaya çalışır. Açılışta bir kez
// kontrol ediliyor — testte de var, ama burada olması gerçek bir kurulumda
// (ör. birleştirilmiş önizlemede) de yakalanmasını sağlıyor.
for (const ad of Object.keys(SEKME_VERISI)) {
  if (!CIZIMLER[ad]) console.error(`Sekme çizimi eksik: ${ad}`);
}
for (const ad of Object.keys(CIZIMLER)) {
  if (!SEKME_VERISI[ad]) console.error(`Sekme verisi eksik: ${ad}`);
}

async function sekmeYukle(ad) {
  const tanim = SEKMELER[ad];

  // Düğmeyi gizlemek yeterli değil: gizli bir düğme DOM'dan tetiklenebilir ve
  // sekme adı başka bir yoldan da (ör. hatırlanan durum) gelebilir. Kural tek
  // yerde uygulanmalı, yoksa "gizli ama çalışan" bir yol kalır — bu projede
  // daha önce tam olarak bu olmuştu: bir ekranda görünmeyen düğme, başka bir
  // ekrandan herkese açıktı.
  if (!tanim || !sekmeGorunur(ad, aktifRol)) {
    $("icerik").innerHTML = "";
    hataYaz("panel-hata", "Bu bölüm rolünüzde açık değil.");
    $("yukleniyor").hidden = true;
    return;
  }

  $("icerik").innerHTML = "";
  hataYaz("panel-hata", null);
  $("yukleniyor").hidden = false;

  if (tanim.ozel) {
    await tanim.ozel(ad);
    $("yukleniyor").hidden = true;
    return;
  }

  const sonuc = await istemci.oku(tanim.tablo, { order: tanim.order });
  $("yukleniyor").hidden = true;

  if (sonuc.tur === "oturumsuz") {
    hataYaz("giris-hata", "Oturumunuzun süresi doldu, tekrar giriş yapın.");
    return goster("giris");
  }
  if (sonuc.tur !== "tamam") return hataYaz("panel-hata", sonuc.mesaj);

  // Sekme bu arada değişmiş olabilir; geç gelen yanıt yeni sekmenin üzerine
  // yazmamalı.
  if (ad !== aktifSekme) return;

  acikSatirlar = silinmemisler(sonuc.satirlar);
  if (acikSatirlar.length === 0) {
    $("icerik").innerHTML = `<p class="alt">Kayıt yok.</p>`;
    return;
  }

  const suzgec = suzgecCubugu(tanim, () => tabloyuTazele(tanim, suzgec));
  $("icerik").appendChild(suzgec.kok);
  $("icerik").appendChild(suzgec.sonucKabi);
  tabloyuTazele(tanim, suzgec);
}

/**
 * Süzgeç değerlerini uygulayıp tabloyu yeniden çizer.
 *
 * Yalnızca sonuç kabı yenileniyor, süzgeç çubuğu değil: çubuk da yeniden
 * çizilseydi arama kutusu her tuşta odağı kaybederdi.
 */
function tabloyuTazele(tanim, suzgec) {
  const suzulen = suz(acikSatirlar, {
    sorgu: suzgec.sorgu(),
    alanlar: tanim.ara ?? [],
    tarihAlani: tanim.tarihAlani ?? null,
    baslangic: suzgec.baslangic(),
    bitis: suzgec.bitis(),
  });

  suzgec.sayac.textContent = suzulen.length === acikSatirlar.length
    ? `${acikSatirlar.length} kayıt`
    : `${suzulen.length} / ${acikSatirlar.length} kayıt`;

  suzgec.sonucKabi.innerHTML = "";
  if (suzulen.length === 0) {
    const bos = document.createElement("p");
    bos.className = "alt";
    // "Kayıt yok"tan farklı bir cümle: veri var ama süzgeç eliyor. Aynı
    // mesajı kullanmak, kullanıcıya verisinin kaybolduğunu düşündürürdü.
    bos.textContent = "Süzgece uyan kayıt yok.";
    suzgec.sonucKabi.appendChild(bos);
    return;
  }
  suzgec.sonucKabi.appendChild(tanim.ciz(suzulen));
}

/** Arama kutusu, tarih aralığı ve sayaçtan oluşan çubuk. */
function suzgecCubugu(tanim, degisti) {
  const kok = document.createElement("div");
  kok.className = "suzgec";

  const arama = document.createElement("input");
  arama.type = "search";
  arama.className = "suzgec-arama";
  arama.placeholder = "Ara…";
  // `search` türü tarayıcının temizleme düğmesini getiriyor; `input` olayı o
  // düğmeyle temizlemede de tetikleniyor, `change` tetiklenmezdi.
  arama.addEventListener("input", degisti);
  arama.setAttribute("aria-label", "Listede ara");
  kok.appendChild(arama);

  let bas = null;
  let bit = null;
  if (tanim.tarihAlani) {
    const grup = document.createElement("div");
    grup.className = "suzgec-tarih";

    const etiket = document.createElement("span");
    etiket.className = "alt";
    etiket.textContent = `${tanim.tarihEtiketi}:`;
    grup.appendChild(etiket);

    bas = tarihKutusu(`${tanim.tarihEtiketi} başlangıcı`, degisti);
    bit = tarihKutusu(`${tanim.tarihEtiketi} bitişi`, degisti);
    grup.append(bas, arasiMetni(), bit);
    kok.appendChild(grup);
  }

  const temizle = document.createElement("button");
  temizle.type = "button";
  // Kendi sınıfı da var: `ikincil` görünüm sınıfı ve sayfada başka
  // düğmeler de (ör. Çıkış) taşıyor. Süzgeci temizleme düğmesini seçmek
  // isteyen kodun ona denk gelmesi gerekiyor.
  temizle.className = "ikincil suzgec-temizle";
  temizle.textContent = "Temizle";
  temizle.addEventListener("click", () => {
    arama.value = "";
    if (bas) bas.value = "";
    if (bit) bit.value = "";
    degisti();
  });
  kok.appendChild(temizle);

  const sayac = document.createElement("span");
  sayac.className = "alt suzgec-sayac";
  kok.appendChild(sayac);

  return {
    kok,
    sayac,
    sonucKabi: suzgecSonucKabi(),
    sorgu: () => arama.value,
    baslangic: () => (bas ? bas.value : null),
    bitis: () => (bit ? bit.value : null),
  };
}

function suzgecSonucKabi() {
  const kap = document.createElement("div");
  kap.className = "suzgec-sonuc";
  return kap;
}

function tarihKutusu(etiket, degisti) {
  const kutu = document.createElement("input");
  kutu.type = "date";
  kutu.setAttribute("aria-label", etiket);
  kutu.addEventListener("input", degisti);
  return kutu;
}

function arasiMetni() {
  const s = document.createElement("span");
  s.className = "alt";
  s.textContent = "–";
  return s;
}

// ─── Özet ───────────────────────────────────────────────────────────────────

/**
 * Özet sekmesi: iki tablo okunuyor ve sayılıyor.
 *
 * Sayma ve düz toplama dışında hesap yok. Üye bazında bakiye ve hakediş
 * bilinçli olarak burada değil — ikisi de ortak Kotlin modülünde tanımlı gerçek
 * iş kuralları ve kopyalanmaları panelde farklı, uygulamada farklı rakam
 * üretirdi.
 */
async function ozetYukle(ad) {
  const [uyeSonuc, defterSonuc] = await Promise.all([
    istemci.oku("gym_members", { order: "end_date_ms.asc" }),
    istemci.oku("ledger_entries", { order: "occurred_at_ms.desc" }),
  ]);

  for (const sonuc of [uyeSonuc, defterSonuc]) {
    if (sonuc.tur === "oturumsuz") {
      hataYaz("giris-hata", "Oturumunuzun süresi doldu, tekrar giriş yapın.");
      return goster("giris");
    }
    if (sonuc.tur !== "tamam") return hataYaz("panel-hata", sonuc.mesaj);
  }

  if (ad !== aktifSekme) return;

  const simdi = Date.now();
  const uyeler = silinmemisler(uyeSonuc.satirlar);
  const defter = defterSonuc.satirlar; // defter append-only; tombstone yok
  const dagilim = uyeDagilimi(uyeler, simdi);
  const buAy = defterToplami(defter, ayBasi(simdi), simdi);
  const yaklasan = yaklasanBitisler(uyeler, simdi);

  const kap = document.createElement("div");
  kap.appendChild(kutular([
    ["Aktif üye", String(dagilim.AKTIF + dagilim.SURESIZ)],
    ["Süresi dolmuş", String(dagilim.SURESI_DOLDU)],
    ["Dondurulmuş", String(dagilim.DONDURULDU)],
    ["Bu ay tahsilat", tutarYaz(buAy.PAYMENT)],
    ["Bu ay gider", tutarYaz(buAy.EXPENSE)],
  ]));

  const baslik = document.createElement("h2");
  baslik.textContent = "14 gün içinde bitecek üyelikler";
  kap.appendChild(baslik);

  if (yaklasan.length === 0) {
    const bos = document.createElement("p");
    bos.className = "alt";
    bos.textContent = "Yaklaşan bitiş yok.";
    kap.appendChild(bos);
  } else {
    kap.appendChild(tabloYap(
      ["Ad Soyad", "Telefon", "Bitiş", "Kalan seans"],
      yaklasan.map((u) => [u.full_name, u.phone, tarihYaz(u.end_date_ms), u.remaining_sessions ?? "Sınırsız"]),
    ));
  }

  $("icerik").appendChild(kap);
}

// ─── Market ─────────────────────────────────────────────────────────────────

/**
 * Market sekmesi: ürünler ve **hareketlerden türeyen** eldeki stok.
 *
 * Ürün tablosunda stok kolonu yok ve bu bilinçli: mutlak bir sayaç olsaydı iki
 * cihaz aynı anda satış yaptığında bir satış sessizce kaybolurdu. Stok,
 * `stock_movements` toplamı — kural uygulamadaki `StockMovementDao.onHand` ile
 * birebir aynı ve `stok.js` içinde yazılı.
 *
 * ### Kesik liste durumunda sayı gösterilmiyor
 * Hareket sayısı okuma sınırına dayanırsa toplam eksik hesaplanır ve sonuç
 * tamamen makul bir sayı gibi görünür — 500 hareketin ilk 500'ünden çıkan bir
 * stok, doğru stoktan ayırt edilemez. O yüzden kesildiğinde sayı yerine "?"
 * yazıyor ve sebebi ekranda açıkça söyleniyor. Sınır ürün tablosundan çok daha
 * yüksek tutuluyor: bir ürün yüzlerce hareket taşıyabiliyor.
 */
async function marketYukle(ad) {
  const [urunSonuc, stokSonuc] = await Promise.all([
    istemci.oku("products", { order: "name.asc" }),
    istemci.oku("stock_movements", { order: "occurred_at_ms.desc", limit: 10000 }),
  ]);

  for (const sonuc of [urunSonuc, stokSonuc]) {
    if (sonuc.tur === "oturumsuz") {
      hataYaz("giris-hata", "Oturumunuzun süresi doldu, tekrar giriş yapın.");
      return goster("giris");
    }
    if (sonuc.tur !== "tamam") return hataYaz("panel-hata", sonuc.mesaj);
  }

  if (ad !== aktifSekme) return;

  const urunler = silinmemisler(urunSonuc.satirlar);
  // Hareketlerde tombstone yok (tablo yalnızca eklenen); süzmek ölü kod olurdu.
  const harita = stokHaritasi(stokSonuc.satirlar);
  const hepsiOkundu = !stokSonuc.kesildi;

  const kap = document.createElement("div");

  if (!hepsiOkundu) {
    const uyari = document.createElement("p");
    uyari.className = "hata";
    uyari.textContent =
      "Stok hareketleri okuma sınırına dayandı; eldeki stok eksik hesaplanacağı " +
      "için sayı gösterilmiyor.";
    kap.appendChild(uyari);
  }

  // Sayaçlar ve tablodaki rozetler AYNI sınıflandırmadan geliyor (`stokDurumu`).
  // Ayrı yazıldıklarında gerçekten ayrıştılar: sayaç negatif stoğu "tükendi"
  // sayarken tablo ona ayrı bir rozet veriyordu ve kutudaki sayı tablodaki rozet
  // sayısıyla tutmuyordu.
  const gruplar = stokUyarilari(urunler, harita);
  const say = (ad) => (hepsiOkundu ? String(gruplar[ad].length) : "?");

  kap.appendChild(kutular([
    ["Ürün", String(urunler.length)],
    ["Tükendi", say("tukendi")],
    ["Azalıyor", say("azaliyor")],
    ["Eksi stok", say("eksi")],
    ["Bilinmiyor", say("bilinmiyor")],
  ]));

  if (urunler.length === 0) {
    const bos = document.createElement("p");
    bos.className = "alt";
    bos.textContent = "Ürün yok.";
    kap.appendChild(bos);
    $("icerik").appendChild(kap);
    return;
  }

  kap.appendChild(tabloYap(
    ["Ürün", "Kategori", "Fiyat", "Eldeki stok", "Durum"],
    urunler.map((u) => [
      u.name,
      u.category,
      tutarYaz(u.price_minor),
      stokYaz(harita, u.id, hepsiOkundu),
      stokRozeti(harita, u.id, hepsiOkundu),
    ]),
  ));

  $("icerik").appendChild(kap);
}

/**
 * Stok durumunun rozeti.
 *
 * Negatif stok "tükendi" değil **ayrı** bir durum: fazla satış ya da eksik alım
 * kaydı demek, yani veri sorunu. Aynı etiketi vermek onu sıradan bir tükenme
 * gibi gösterir ve kimse sebebini araştırmaz.
 */
const STOK_ETIKETLERI = {
  yeterli: "Yeterli",
  azaliyor: "Azalıyor",
  tukendi: "Tükendi",
  eksi: "Eksi stok",
  bilinmiyor: "Bilinmiyor",
};

function stokRozeti(harita, urunId, hepsiOkundu) {
  const durum = hepsiOkundu ? stokDurumu(harita, urunId) : "bilinmiyor";
  return { rozet: `stok-${durum}`, metin: STOK_ETIKETLERI[durum] };
}

function satislariCiz(satirlar) {
  return tabloYap(
    ["Tarih", "Tutar", "İndirim", "Ödenen", "Yöntem", "Ödeme", "Teslim"],
    satirlar.map((s) => [
      tarihYaz(s.date_ms),
      tutarYaz(s.total_price_minor),
      // Sıfır indirim "—" değil "₺0,00": indirim yok demek, bilinmiyor demek değil.
      tutarYaz(s.discount_minor),
      tutarYaz(s.final_price_minor),
      s.payment_method,
      s.payment_status,
      s.delivery_status === "PRE_DELIVERY" ? "Teslim edilmedi" : "Teslim edildi",
    ]),
  );
}

/**
 * Personel listesi.
 *
 * Maaş ve hakediş oranı gösteriliyor. Bu yeni bir açığa çıkarma **değil**:
 * uygulamadaki personel kartı da ikisini gösteriyor (`PersonnelScreen`) ve o
 * ekran eğitmene açık (`AppDestination.PERSONNEL`). Panel burada uygulamadan
 * farklı bir karar vermiyor — verseydi aynı ürün iki farklı cevap verirdi.
 *
 * Hakediş sunucuda on binde (`basis_points`) tutuluyor; yüzdeye çevrim yalnızca
 * gösterim anında, tam sayı bölmesiyle değil.
 */
function personeliCiz(satirlar) {
  return tabloYap(
    ["Ad Soyad", "Ünvan", "Rol", "Şube", "Hakediş", "Maaş", "Telefon"],
    satirlar.map((p) => [
      p.full_name,
      p.title,
      rolEtiketi(p.role),
      p.branch,
      hakedisYaz(p.commission_basis_points),
      tutarYaz(p.monthly_salary_minor),
      p.phone,
    ]),
  );
}

/** On binde cinsinden oranı yüzde olarak yazar. */
function hakedisYaz(basisPoints) {
  const sayi = Number(basisPoints);
  if (!Number.isFinite(sayi)) return "—";
  // 4000 → %40 ; 350 → %3,5
  return `%${(sayi / 100).toLocaleString("tr-TR", { maximumFractionDigits: 2 })}`;
}

// ─── Duyurular ──────────────────────────────────────────────────────────────

/**
 * Duyurular: herkese açık sitenin etkinlik vitrinini besleyen bölüm.
 *
 * Panelin **yazan** ilk bölümü. Panel bilinçli olarak salt okunurdu ve gerekçe
 * hâlâ geçerli: uygulamadaki yazma yolları iş kuralları taşıyor ve o kurallar
 * ortak Kotlin modülünde. Burada kopyalanan bir kural YOK — `announcements`
 * tablosunu uygulama hiç bilmiyor, okumuyor, yazmıyor. Ayrım `supabase.js`
 * içindeki `yaz` yönteminde de yazılı.
 */
async function duyurulariYukle(ad) {
  const sonuc = await istemci.oku("announcements", { order: "sort_order.asc" });
  if (sonuc.tur === "oturumsuz") {
    hataYaz("giris-hata", "Oturumunuzun süresi doldu, tekrar giriş yapın.");
    return goster("giris");
  }
  if (sonuc.tur !== "tamam") return hataYaz("panel-hata", sonuc.mesaj);
  if (ad !== aktifSekme) return;

  const kap = document.createElement("div");
  kap.appendChild(duyuruFormu());

  const satirlar = silinmemisler(sonuc.satirlar);
  const baslik = document.createElement("h2");
  baslik.textContent = "Mevcut duyurular";
  kap.appendChild(baslik);

  if (satirlar.length === 0) {
    const bos = document.createElement("p");
    bos.className = "alt";
    bos.textContent = "Henüz duyuru yok. Yukarıdaki formla ekleyebilirsiniz.";
    kap.appendChild(bos);
  } else {
    kap.appendChild(tabloYap(
      ["Başlık", "Tür", "Yayın", "Başlangıç", "Bitiş", ""],
      satirlar.map((d) => [
        d.title,
        turEtiketi(d.kind),
        d.is_published
          ? { rozet: "aktif", metin: "Yayında" }
          : { rozet: "donduruldu", metin: "Taslak" },
        tarihYaz(d.starts_at_ms),
        tarihYaz(d.ends_at_ms),
        yayinDugmesi(d),
      ]),
    ));
  }

  $("icerik").appendChild(kap);
}

/** Yeni duyuru formu. */
function duyuruFormu() {
  const form = document.createElement("form");
  form.className = "kart";
  form.style.marginBottom = "24px";

  const baslik = document.createElement("h2");
  baslik.textContent = "Yeni duyuru";
  form.appendChild(baslik);

  const alanlar = {};
  const ekle = (ad, etiket, tur = "text") => {
    const l = document.createElement("label");
    l.textContent = etiket;
    const i = tur === "textarea" ? document.createElement("textarea") : document.createElement("input");
    if (tur !== "textarea") i.type = tur;
    if (tur === "textarea") i.rows = 3;
    l.appendChild(i);
    form.appendChild(l);
    alanlar[ad] = i;
  };

  ekle("title", "Başlık");
  ekle("body", "Metin", "textarea");

  const turL = document.createElement("label");
  turL.textContent = "Tür";
  const tur = document.createElement("select");
  for (const [deger, metin] of [["EVENT", "Etkinlik"], ["AD", "Kampanya"], ["NOTICE", "Duyuru"]]) {
    const o = document.createElement("option");
    o.value = deger;
    o.textContent = metin;
    tur.appendChild(o);
  }
  turL.appendChild(tur);
  form.appendChild(turL);
  alanlar.kind = tur;

  ekle("starts", "Başlangıç tarihi (boş bırakılabilir)", "date");
  ekle("ends", "Bitiş tarihi — bu tarihte siteden kendiliğinden kalkar", "date");
  ekle("image_url", "Görsel adresi (isteğe bağlı)");
  form.appendChild(gorselYukleyici(alanlar.image_url));

  // `styles.css` etiketleri IZGARA yapıyor (`label { display: grid }`), yani
  // `flex-direction` hiçbir işe yaramıyordu: onay kutusu metnin ÜSTÜNDE, ortada
  // duruyordu ve hangi metne ait olduğu belli değildi. `display` burada açıkça
  // `flex`e çevriliyor.
  const yayinL = document.createElement("label");
  yayinL.style.display = "flex";
  yayinL.style.alignItems = "center";
  yayinL.style.gap = "8px";
  const yayin = document.createElement("input");
  yayin.type = "checkbox";
  yayinL.append(yayin, document.createTextNode(" Hemen yayınla"));
  form.appendChild(yayinL);
  alanlar.is_published = yayin;

  const dugme = document.createElement("button");
  dugme.type = "submit";
  dugme.textContent = "Kaydet";
  form.appendChild(dugme);

  const durum = document.createElement("p");
  durum.className = "alt";
  form.appendChild(durum);

  form.addEventListener("submit", async (olay) => {
    olay.preventDefault();
    if (dugme.disabled) return;

    // Başlıksız duyuru sitede boş bir kart olurdu.
    if (!alanlar.title.value.trim()) {
      durum.textContent = "Başlık gerekli.";
      return;
    }

    dugme.disabled = true;
    durum.textContent = "Kaydediliyor…";

    const simdi = Date.now();
    const sonuc = await istemci.yaz("announcements", {
      // Kimlik istemcide üretiliyor; tablo `text primary key` ve uygulamanın
      // geri kalanında da desen bu.
      id: `duyuru-${simdi}-${Math.random().toString(36).slice(2, 8)}`,
      tenant_id: aktifTenant,
      title: alanlar.title.value.trim(),
      body: alanlar.body.value.trim(),
      kind: alanlar.kind.value,
      image_url: alanlar.image_url.value.trim() || null,
      starts_at_ms: tariheCevir(alanlar.starts.value),
      ends_at_ms: tariheCevir(alanlar.ends.value, true),
      is_published: alanlar.is_published.checked,
      sort_order: 0,
      created_at_ms: simdi,
      updated_at_ms: simdi,
    });

    dugme.disabled = false;

    if (sonuc.tur === "tamam") {
      durum.textContent = "Kaydedildi.";
      return sekmeYukle(aktifSekme);
    }
    durum.textContent = sonuc.mesaj ?? "Kaydedilemedi.";
  });

  return form;
}

/**
 * Görsel yükleme kutusu: dosya seç → Supabase Storage'a yükle → adresi
 * yukarıdaki "Görsel adresi" alanına yaz.
 *
 * ### Adres alanı neden duruyor
 * Yükleme onun yerini almıyor, onu DOLDURUYOR. Görsel başka bir yerde
 * barındırılıyorsa (Instagram gönderisi, tasarımcının verdiği bağlantı)
 * adresi elle yapıştırmak hâlâ mümkün olmalı. Alanı gizleyip yalnızca yükleme
 * bırakmak, var olan bir görseli kullanmayı imkânsız kılardı.
 *
 * ### Yüklenen görsel neden herkese açık
 * Duyuru kartı açılış sayfasında, giriş yapılmadan gösteriliyor. Süreli imzalı
 * adres üretmek sunucu tarafında bir bileşen gerektirirdi ve burada öyle bir şey
 * yok. Bunun bedeli açık: bu kovaya **yalnızca** duyuru görseli konmalı — üye
 * fotoğrafı, sağlık belgesi gibi şeyler değil.
 */
function gorselYukleyici(adresAlani) {
  const kap = document.createElement("div");
  kap.style.marginBottom = "12px";

  const etiket = document.createElement("label");
  etiket.textContent = "…ya da bilgisayarınızdan yükleyin";
  const dosya = document.createElement("input");
  dosya.type = "file";
  dosya.accept = "image/*";
  etiket.appendChild(dosya);
  kap.appendChild(etiket);

  const dugme = document.createElement("button");
  // `type=button`: varsayılan `submit` olurdu ve düğme, duyuruyu yükleme
  // bitmeden kaydederdi.
  dugme.type = "button";
  dugme.className = "ikincil";
  dugme.textContent = "Görseli yükle";
  dugme.disabled = true;
  kap.appendChild(dugme);

  const durum = document.createElement("p");
  durum.className = "alt";
  kap.appendChild(durum);

  const onizleme = document.createElement("img");
  onizleme.hidden = true;
  onizleme.alt = "Yüklenen görsel";
  onizleme.style.maxHeight = "150px";
  onizleme.style.marginTop = "10px";
  onizleme.style.borderRadius = "8px";
  kap.appendChild(onizleme);

  dosya.addEventListener("change", () => {
    dugme.disabled = !dosya.files?.length;
    durum.textContent = "";
  });

  dugme.addEventListener("click", async () => {
    const secilen = dosya.files?.[0];
    if (!secilen) return;

    dugme.disabled = true;
    durum.textContent = "Yükleniyor…";

    const sonuc = await istemci.dosyaYukle(secilen, aktifTenant);

    if (sonuc.tur === "tamam") {
      adresAlani.value = sonuc.adres;
      onizleme.src = sonuc.adres;
      onizleme.hidden = false;
      durum.textContent = "Yüklendi. Adres yukarıdaki alana yazıldı.";
      // Aynı dosyanın ikinci kez yüklenmesini engellemek için seçim
      // temizleniyor; düğme yeni bir dosya seçilene kadar kapalı kalıyor.
      dosya.value = "";
      return;
    }

    dugme.disabled = false;
    if (sonuc.tur === "oturumsuz") {
      hataYaz("giris-hata", "Oturumunuzun süresi doldu, tekrar giriş yapın.");
      return goster("giris");
    }
    durum.textContent = sonuc.mesaj ?? "Yüklenemedi.";
  });

  return kap;
}

/** Yayına alma / yayından kaldırma düğmesi. */
function yayinDugmesi(duyuru) {
  const dugme = document.createElement("button");
  dugme.className = "ikincil";
  dugme.textContent = duyuru.is_published ? "Yayından kaldır" : "Yayınla";
  dugme.addEventListener("click", async () => {
    dugme.disabled = true;
    const sonuc = await istemci.yaz(
      "announcements",
      { is_published: !duyuru.is_published, updated_at_ms: Date.now() },
      `id=eq.${encodeURIComponent(duyuru.id)}`,
    );
    if (sonuc.tur === "tamam") return sekmeYukle(aktifSekme);
    dugme.disabled = false;
    hataYaz("panel-hata", sonuc.mesaj ?? "Değiştirilemedi.");
  });
  return dugme;
}

/**
 * `<input type=date>` değerini epoch ms'ye çevirir.
 *
 * Bitiş tarihinde günün SONU alınıyor: kullanıcı "31 Aralık" yazdığında
 * etkinliğin 31 Aralık günü boyunca görünmesini bekliyor, o günün 00:00'ında
 * kaybolmasını değil.
 */
function tariheCevir(deger, gunSonu = false) {
  if (!deger) return null;
  const d = new Date(deger);
  if (Number.isNaN(d.getTime())) return null;
  if (gunSonu) d.setHours(23, 59, 59, 999);
  return d.getTime();
}

function turEtiketi(tur) {
  switch (tur) {
    case "EVENT": return "Etkinlik";
    case "AD": return "Kampanya";
    case "NOTICE": return "Duyuru";
    default: return tur ?? "—";
  }
}

// ─── Personel erişimi ───────────────────────────────────────────────────────

/**
 * Personele uygulama erişimi verir.
 *
 * ### Neden ayrı bir bölüm, `personel` sekmesinin içi değil
 * `personel` sekmesi üç role de açık; erişim vermek yalnızca ADMIN'in işi
 * (sunucudaki `personel-davet` fonksiyonu da öyle davranıyor). Panelin rol
 * kapısı sekme düzeyinde çalışıyor, sekme içi eleman gizleme deseni yok —
 * yani formu oraya koymak ya müdüre de göstermek ya da yeni bir gizleme
 * mekanizması icat etmek olurdu.
 *
 * ### Üç durum gösteriliyor, iki değil
 * "Hesabı var ama yetkisi yok" hâli ayrı duruyor ve bunun sebebi bu bölümün
 * var oluş sebebiyle aynı: o kişi giriş yapabiliyor, uygulama açılıyor, hiçbir
 * hata çıkmıyor ve HİÇBİR VERİ gelmiyor. İki hâlli bir liste onu "erişimi var"
 * tarafına yazar ve yönetici sorunu hiç göremezdi.
 */
async function personelErisimYukle(ad) {
  const [personelSonuc, yetkiSonuc] = await Promise.all([
    istemci.oku("staff", { order: "full_name.asc" }),
    istemci.oku("gym_users"),
  ]);

  for (const sonuc of [personelSonuc, yetkiSonuc]) {
    if (sonuc.tur === "oturumsuz") {
      hataYaz("giris-hata", "Oturumunuzun süresi doldu, tekrar giriş yapın.");
      return goster("giris");
    }
    if (sonuc.tur !== "tamam") return hataYaz("panel-hata", sonuc.mesaj);
  }
  if (ad !== aktifSekme) return;

  const personeller = silinmemisler(personelSonuc.satirlar);
  const yetkiler = yetkiSonuc.satirlar;
  const durumlar = personeller.map((p) => ({ p, ...erisimDurumu(p, yetkiler) }));

  const say = (d) => durumlar.filter((x) => x.durum === d).length;

  const kap = document.createElement("div");
  kap.appendChild(kutular([
    ["Personel", String(personeller.length)],
    ["Erişimi var", String(say("erisim_var"))],
    ["Yetkisi eksik", String(say("yetki_yok"))],
    ["Hesabı yok", String(say("hesap_yok"))],
  ]));

  // Yetkisi eksik biri varsa bu bir ARIZA ve görünür olmalı: o kişi her gün
  // giriş yapıp boş ekran görüyor olabilir.
  if (say("yetki_yok") > 0) {
    const uyari = document.createElement("p");
    uyari.className = "hata";
    uyari.textContent = YETKI_YOK_ACIKLAMA;
    kap.appendChild(uyari);
  }

  kap.appendChild(davetFormu(durumlar));

  const baslik = document.createElement("h2");
  baslik.textContent = "Personel";
  kap.appendChild(baslik);

  kap.appendChild(tabloYap(
    ["Ad Soyad", "Unvan", "Durum", "Uygulama yetkisi"],
    durumlar.map(({ p, durum, yetki }) => [
      p.full_name,
      p.title,
      durum === "erisim_var"
        ? { rozet: "aktif", metin: erisimEtiketi(durum) }
        : { rozet: "donduruldu", metin: erisimEtiketi(durum) },
      // `gym_users.role` — `staff.role` DEĞİL. İkisi ayrışabiliyor ve gerçek
      // yetkiyi belirleyen bu. `staff.role` gösterilseydi yönetici birine
      // ADMIN verdiğini sanırken kişi eğitmen yetkisiyle gezebilirdi.
      yetki ?? "—",
    ]),
  ));

  $("icerik").appendChild(kap);
}

/** Davet formu: personel seç, e-posta ve yetki gir, gönder. */
function davetFormu(durumlar) {
  const form = document.createElement("form");
  form.className = "kart";

  const baslik = document.createElement("h2");
  baslik.textContent = "Erişim ver";
  form.appendChild(baslik);

  const aciklama = document.createElement("p");
  aciklama.className = "alt";
  aciklama.textContent =
    "Hesap açılır, salon yetkisi yazılır ve personel kaydına bağlanır. " +
    "Geçici şifre bir kez gösterilir — kaydetmezseniz bir daha alınamaz.";
  form.appendChild(aciklama);

  // Erişimi zaten olanlar da listede: yetkisini değiştirmek (ör. eğitmenden
  // müdüre) aynı akışla yapılıyor. Listeden çıkarsalardı yetki yükseltmek
  // yine Supabase paneli gerektirirdi.
  const kisi = document.createElement("select");
  kisi.required = true;
  for (const { p, durum } of durumlar) {
    const secenek = document.createElement("option");
    secenek.value = p.id;
    secenek.textContent = `${p.full_name} — ${erisimEtiketi(durum)}`;
    kisi.appendChild(secenek);
  }
  form.appendChild(etiketli("Personel", kisi));

  const eposta = document.createElement("input");
  eposta.type = "email";
  eposta.required = true;
  eposta.placeholder = "personel@ornek.com";
  form.appendChild(etiketli("E-posta", eposta));

  const yetki = document.createElement("select");
  for (const y of DAVET_YETKILERI) {
    const secenek = document.createElement("option");
    secenek.value = y;
    secenek.textContent = YETKI_ACIKLAMA[y];
    yetki.appendChild(secenek);
  }
  form.appendChild(etiketli("Yetki", yetki));

  const dugme = document.createElement("button");
  dugme.type = "submit";
  dugme.textContent = "Erişim ver";
  form.appendChild(dugme);

  const sonucAlani = document.createElement("div");
  form.appendChild(sonucAlani);

  form.addEventListener("submit", async (olay) => {
    olay.preventDefault();
    sonucAlani.replaceChildren();

    const kontrol = davetiDogrula({
      personelId: kisi.value,
      eposta: eposta.value,
      yetki: yetki.value,
    });
    if (!kontrol.gecerli) {
      sonucAlani.appendChild(mesajSatiri("hata", kontrol.mesaj));
      return;
    }

    // Düğme kilitleniyor: davet tekrar edilebilir olsa da iki kez göndermek
    // yöneticiye iki farklı geçici şifre gösterir ve hangisinin geçerli
    // olduğu belirsizleşir.
    dugme.disabled = true;
    dugme.textContent = "Gönderiliyor…";

    const sonuc = await istemci.personelDavetEt(kontrol.deger);

    dugme.disabled = false;
    dugme.textContent = "Erişim ver";

    if (sonuc.tur === "oturumsuz") {
      hataYaz("giris-hata", "Oturumunuzun süresi doldu, tekrar giriş yapın.");
      return goster("giris");
    }
    if (sonuc.tur !== "tamam") {
      sonucAlani.appendChild(
        mesajSatiri("hata", davetHataMesaji(sonuc.kod, sonuc.govde)),
      );
      return;
    }

    sonucAlani.appendChild(mesajSatiri("alt", davetBasariMesaji(sonuc.yanit)));

    if (sonuc.yanit?.gecici_sifre) {
      sonucAlani.appendChild(sifreKutusu(sonuc.yanit.gecici_sifre));
      // LİSTE TAZELENMİYOR ve bu bilinçli.
      //
      // İlk yazımda tazeleniyordu ve ekranın en kritik parçasını siliyordu:
      // `sekmeYukle` bölümü baştan çiziyor, geçici şifre kutusu da onun
      // içinde. Sonuç, hesabın açılması ama şifreyi KİMSENİN görememesiydi —
      // ve şifre bir daha alınamıyor. Tarayıcıda akış sürülene kadar
      // görünmedi; birim testleri, sözdizimi ve tip denetimi hepsi temizdi.
      //
      // Durum sütunu bu satır yüzünden eski kalıyor. Kabul edildi: bir kez
      // görülebilen şifreyi korumak, güncel bir tablodan önemli. Yönetici
      // sekmeye tekrar bastığında liste zaten tazeleniyor.
      const not = document.createElement("p");
      not.className = "alt";
      not.textContent =
        "Şifreyi kaydettikten sonra listeyi görmek için sekmeye tekrar basın.";
      sonucAlani.appendChild(not);
      return;
    }

    // Şifre yoksa (mevcut hesap bağlandı) korunacak bir şey de yok; liste
    // tazeleniyor ki durum sütunu doğru olsun.
    sekmeYukle(aktifSekme);
  });

  return form;
}

/**
 * Geçici şifreyi gösteren kutu.
 *
 * Şifre `<input readonly>` içinde, düz metin değil: yönetici tek dokunuşla
 * seçip kopyalayabilsin. Uzun ve belirsiz karakterler içermeyen bir şifreyi
 * ekrandan okuyup elle yazmak hata kaynağı.
 */
function sifreKutusu(sifre) {
  const kap = document.createElement("div");
  kap.className = "kart";
  kap.style.marginTop = "12px";

  const baslik = document.createElement("p");
  baslik.className = "alt";
  baslik.textContent = "Geçici şifre — bu kutu bir daha gösterilmeyecek:";

  const alan = document.createElement("input");
  alan.readOnly = true;
  alan.value = sifre;
  alan.style.fontFamily = "monospace";
  alan.addEventListener("focus", () => alan.select());

  const not = document.createElement("p");
  not.className = "alt";
  not.textContent =
    "Şifreyi personele siz iletin. Kendi şifresini değiştirebileceği ekran " +
    "henüz yok, yani bu şifre kalıcı — güvenli bir yerde saklayın.";

  kap.append(baslik, alan, not);
  return kap;
}

function etiketli(metin, alan) {
  const etiket = document.createElement("label");
  const yazi = document.createElement("span");
  yazi.textContent = metin;
  etiket.append(yazi, alan);
  return etiket;
}

function mesajSatiri(sinif, metin) {
  const p = document.createElement("p");
  p.className = sinif;
  p.textContent = metin;
  return p;
}

// ─── Üye hesapları ──────────────────────────────────────────────────────────

/**
 * Üye kaydını bir Supabase hesabına bağlar.
 *
 * Bağ olmadan üye giriş yapsa bile HİÇBİR veri göremiyor — erişim kuralları
 * `member_accounts` üzerinden çalışıyor (migrasyon 0005). Bu bilinçli: bağı
 * kurmak bir erişim kararı ve salon veriyor.
 *
 * ### Hesap kimliği artık elle girilmiyor
 * Üye `/uye/` üzerinden kayıt olurken KENDİSİ bir istek satırı yazıyor
 * (`member_link_requests`, migrasyon 0006) ve o satır hesap kimliğini taşıyor.
 * Personel yalnızca "bu istek şu üyelik kaydına ait" diyor.
 *
 * Eşleştirme yine de OTOMATİK DEĞİL ve olmayacak: e-postayla otomatik bağlamak
 * reddedildi çünkü `gym_members.email` hem boş olabiliyor hem de tekil değil —
 * bir yazım hatası başka birinin sağlık verisini açardı. Telefon eşleşmesi
 * yalnızca bir ÖNERİ olarak sunuluyor, kararı insan veriyor.
 */
async function uyeHesaplariYukle(ad) {
  const [bagSonuc, uyeSonuc, istekSonuc] = await Promise.all([
    istemci.oku("member_accounts", { order: "linked_at_ms.desc" }),
    istemci.oku("gym_members", { order: "full_name.asc" }),
    istemci.oku("member_link_requests", { order: "created_at_ms.asc" }),
  ]);

  for (const sonuc of [bagSonuc, uyeSonuc, istekSonuc]) {
    if (sonuc.tur === "oturumsuz") {
      hataYaz("giris-hata", "Oturumunuzun süresi doldu, tekrar giriş yapın.");
      return goster("giris");
    }
    if (sonuc.tur !== "tamam") return hataYaz("panel-hata", sonuc.mesaj);
  }
  if (ad !== aktifSekme) return;

  const baglar = bagSonuc.satirlar;
  const uyeler = silinmemisler(uyeSonuc.satirlar);
  const bagliIdler = new Set(baglar.map((b) => b.member_id));
  const bekleyenler = istekSonuc.satirlar.filter((i) => i.state === "PENDING");

  const kap = document.createElement("div");
  kap.appendChild(kutular([
    ["Üye", String(uyeler.length)],
    ["Hesabı bağlı", String(bagliIdler.size)],
    ["Bağlı değil", String(uyeler.filter((u) => !bagliIdler.has(u.id)).length)],
    ["Bekleyen istek", String(bekleyenler.length)],
  ]));

  const aciklama = document.createElement("p");
  aciklama.className = "alt";
  aciklama.textContent =
    "Üye önce nonstopstudio.tr/uye/ adresinden hesap açıp bilgilerini bildirir; " +
    "istek aşağıda görünür. Siz bağlayana kadar hiçbir veri göremez.";
  kap.appendChild(aciklama);

  kap.appendChild(bekleyenIstekler(bekleyenler, uyeler, bagliIdler));

  const baslik = document.createElement("h2");
  baslik.textContent = "Üyeler";
  kap.appendChild(baslik);

  kap.appendChild(tabloYap(
    ["Ad Soyad", "Telefon", "E-posta", "Hesap durumu"],
    uyeler.map((u) => [
      u.full_name,
      u.phone,
      u.email,
      bagliIdler.has(u.id)
        ? { rozet: "aktif", metin: "Bağlı" }
        : { rozet: "donduruldu", metin: "Bağlı değil" },
    ]),
  ));

  kap.appendChild(salonKimligi());

  $("icerik").appendChild(kap);
}

/**
 * Salon kimliğini gösterir.
 *
 * Sitedeki kayıt formunun ayarı (`tenantId`) bu değer ve başka bir yerden
 * kolay bulunamıyor: giriş yapmamış ziyaretçi salon listesini OKUYAMIYOR
 * (okuyabilseydi bütün salonlar herkese açık olurdu), yani site kendi salonunu
 * çalışma zamanında çözemez. Değeri Supabase panelinden SQL yazarak bulmak
 * mümkün ama gereksiz — burada zaten oturumda duruyor.
 */
function salonKimligi() {
  const kap = document.createElement("details");
  kap.style.marginTop = "24px";

  const baslik = document.createElement("summary");
  baslik.textContent = "Site kayıt ayarı (salon kimliği)";
  baslik.className = "alt";
  kap.appendChild(baslik);

  const aciklama = document.createElement("p");
  aciklama.className = "alt";
  aciklama.textContent =
    "Sitedeki üye kaydının çalışması için bu değer depo gizli anahtarlarına " +
    "SUPABASE_TENANT_ID adıyla eklenmeli (Settings → Secrets → Actions):";
  kap.appendChild(aciklama);

  const deger = document.createElement("code");
  deger.textContent = aktifTenant ?? "—";
  deger.style.userSelect = "all";
  kap.appendChild(deger);

  return kap;
}

/**
 * Bekleyen kayıt istekleri listesi.
 *
 * Her satırda üye seçici + "Bağla" + "Reddet". Seçicide **yalnızca hesabı
 * bağlanmamış** üyeler var: bağlı bir üyeyi seçmek zaten sunucuda tekillik
 * kısıtına takılırdı, listede durması boşuna bir denemeye davet olurdu.
 */
function bekleyenIstekler(istekler, uyeler, bagliIdler) {
  const kap = document.createElement("div");

  const baslik = document.createElement("h2");
  baslik.textContent = "Bekleyen kayıt istekleri";
  baslik.style.marginTop = "28px";
  kap.appendChild(baslik);

  if (istekler.length === 0) {
    const bos = document.createElement("p");
    bos.className = "alt";
    bos.textContent = "Bekleyen istek yok.";
    kap.appendChild(bos);
    return kap;
  }

  const bagsizlar = uyeler.filter((u) => !bagliIdler.has(u.id));

  // Seçici ve düğmeler AYNI hücrede. Ayrı sütunlar olsaydı tablo yedi sütuna
  // çıkar ve "Bağla" düğmesi dizüstü genişliğinde yatay kaydırmanın ardında
  // kalırdı — ekranın tek eylemi görünmez olurdu.
  kap.appendChild(tabloYap(
    ["Ad Soyad", "Telefon", "E-posta", "Not", "İstek tarihi", "Üyelik kaydı ve işlem"],
    istekler.map((istek) => {
      const secici = uyeSecici(bagsizlar, istek);
      return [
        istek.full_name,
        istek.phone,
        istek.email,
        istek.note,
        tarihYaz(istek.created_at_ms),
        istekEylemleri(istek, secici),
      ];
    }),
  ));

  return kap;
}

/**
 * İsteği bir üyelik kaydıyla eşleştiren açılır liste.
 *
 * Telefon eşleşmesi önceden seçiliyor. `gym_members.phone` kiracı bazında tekil
 * olduğu için bu en güvenilir ipucu — ama yalnızca ipucu: personel değiştirebilir
 * ve eşleşme yoksa seçim boş kalıyor, kendiliğinden bir tahmin yapılmıyor.
 */
function uyeSecici(uyeler, istek) {
  const secici = document.createElement("select");

  const bos = document.createElement("option");
  bos.value = "";
  bos.textContent = "— üye seçin —";
  secici.appendChild(bos);

  const normal = (t) => String(t ?? "").replace(/\D/g, "");
  const istekTel = normal(istek.phone);

  for (const u of uyeler) {
    const o = document.createElement("option");
    o.value = u.id;
    o.textContent = `${u.full_name} — ${u.phone ?? "telefonsuz"}`;
    // Eşleşme boş telefonlar üzerinden kurulamaz: `normal("")` iki tarafta da
    // `""` döner ve telefonu olmayan her üye "eşleşmiş" görünürdü.
    if (istekTel && normal(u.phone) === istekTel) o.selected = true;
    secici.appendChild(o);
  }

  return secici;
}

/** Bir isteğin üye seçicisi ve "Bağla" / "Reddet" düğmeleri. */
function istekEylemleri(istek, secici) {
  const kap = document.createElement("div");
  kap.style.display = "flex";
  kap.style.flexWrap = "wrap";
  kap.style.alignItems = "center";
  kap.style.gap = "8px";
  kap.appendChild(secici);

  const durum = document.createElement("span");
  durum.className = "alt";

  const bagla = document.createElement("button");
  bagla.textContent = "Bağla";
  bagla.addEventListener("click", async () => {
    const uyeId = secici.value;
    if (!uyeId) {
      durum.textContent = "Önce üye seçin.";
      return;
    }

    bagla.disabled = true;
    durum.textContent = "Bağlanıyor…";

    const simdi = Date.now();

    // Sıra önemli: önce BAĞ kuruluyor, sonra istek işaretleniyor.
    //
    // Tersi yapılsaydı ve ikinci adım düşseydi, istek "bağlandı" görünüp
    // listeden kaybolur, üye ise hiçbir şey göremezdi — kimsenin fark
    // etmeyeceği bir kayıp. Bu sırayla yarım kalırsa istek listede DURUYOR,
    // yani hata görünür kalıyor.
    const bagSonuc = await istemci.yaz("member_accounts", {
      member_id: uyeId,
      tenant_id: aktifTenant,
      auth_user_id: istek.auth_user_id,
      linked_at_ms: simdi,
    });

    if (bagSonuc.tur !== "tamam" && bagSonuc.tur !== "cakisma") {
      bagla.disabled = false;
      durum.textContent = bagSonuc.mesaj ?? "Bağlanamadı.";
      return;
    }
    // `cakisma`: bağ zaten var — önceki yarım kalmış bir denemeden. İşaretleme
    // adımına devam etmek doğru olan, çünkü eksik olan tam da o.
    if (bagSonuc.tur === "cakisma") {
      durum.textContent = "Bağ zaten vardı; istek işaretleniyor…";
    }

    const isaretSonuc = await istemci.yaz(
      "member_link_requests",
      { state: "LINKED", updated_at_ms: simdi },
      `auth_user_id=eq.${encodeURIComponent(istek.auth_user_id)}`,
    );

    if (isaretSonuc.tur !== "tamam") {
      bagla.disabled = false;
      // Kullanıcının bilmesi gereken şey burada bağın KURULDUĞU: tekrar
      // denerse "zaten var" görecek ve neden olduğunu bilmezse paniğe kapılır.
      durum.textContent =
        `Bağ kuruldu ama istek işaretlenemedi: ${isaretSonuc.mesaj ?? "bilinmeyen hata"}`;
      return;
    }

    sekmeYukle(aktifSekme);
  });

  const reddet = document.createElement("button");
  reddet.className = "ikincil";
  reddet.textContent = "Reddet";
  reddet.addEventListener("click", async () => {
    // Reddetmek isteği listeden düşürüyor ama hesabı silmiyor — hesabı silmek
    // `service_role` gerektirir ve o anahtar buraya asla gelmiyor. Kişi tekrar
    // başvurmak isterse bilgilerini güncelleyebilir.
    if (!confirm(`"${istek.full_name}" isteği reddedilsin mi?`)) return;

    reddet.disabled = true;
    durum.textContent = "Reddediliyor…";

    const sonuc = await istemci.yaz(
      "member_link_requests",
      { state: "REJECTED", updated_at_ms: Date.now() },
      `auth_user_id=eq.${encodeURIComponent(istek.auth_user_id)}`,
    );

    if (sonuc.tur === "tamam") return sekmeYukle(aktifSekme);
    reddet.disabled = false;
    durum.textContent = sonuc.mesaj ?? "Reddedilemedi.";
  });

  kap.append(bagla, reddet, durum);
  return kap;
}

/**
 * Özet kutuları.
 *
 * Üçüncü eleman isteğe bağlı bir renk sınıfı (`deger-iyi`, `deger-hata`, …).
 * Renk tek başına bilgi TAŞIMIYOR — negatif tutar zaten eksi işaretiyle
 * yazılıyor — yalnızca gözü doğru kutuya götürüyor; renk körlüğünde de sayı
 * okunabilir kalıyor.
 */
function kutular(ciftler) {
  const kap = document.createElement("div");
  kap.className = "kutular";
  for (const [baslik, deger, sinif] of ciftler) {
    const kutu = document.createElement("div");
    kutu.className = "kutu";
    const b = document.createElement("p");
    b.className = "alt";
    b.textContent = baslik;
    const d = document.createElement("strong");
    if (sinif) d.className = sinif;
    d.textContent = deger;
    kutu.append(b, d);
    kap.appendChild(kutu);
  }
  return kap;
}

// ─── Tablolar ───────────────────────────────────────────────────────────────

function tabloYap(basliklar, satirlar) {
  const tablo = document.createElement("table");
  const ust = document.createElement("thead");
  const ustSatir = document.createElement("tr");
  for (const b of basliklar) {
    const th = document.createElement("th");
    th.textContent = b;
    ustSatir.appendChild(th);
  }
  ust.appendChild(ustSatir);
  tablo.appendChild(ust);

  const govde = document.createElement("tbody");
  for (const hucreler of satirlar) {
    const tr = document.createElement("tr");
    for (const hucre of hucreler) {
      const td = document.createElement("td");
      // `textContent`, `innerHTML` değil: sunucudan gelen isimler ve notlar
      // kullanıcı girdisi ve HTML olarak yorumlanmamalı.
      // Hazır bir DOM düğümü (ör. satır içi düğme) doğrudan yerleştiriliyor.
      if (hucre instanceof Node) {
        td.appendChild(hucre);
      } else if (hucre && typeof hucre === "object" && hucre.rozet) {
        const span = document.createElement("span");
        span.className = `rozet rozet-${hucre.rozet}`;
        span.textContent = hucre.metin;
        td.appendChild(span);
      } else {
        td.textContent = hucre ?? "—";
      }
      tr.appendChild(td);
    }
    govde.appendChild(tr);
  }
  tablo.appendChild(govde);
  return tablo;
}

function uyeleriCiz(satirlar) {
  const simdi = Date.now();
  return tabloYap(
    ["Ad Soyad", "Telefon", "Durum", "Bitiş", "Kalan seans", "Ödenen"],
    satirlar.map((u) => {
      const durum = uyelikDurumu(u, simdi);
      return [
        u.full_name,
        u.phone,
        { rozet: durum.toLowerCase(), metin: durumEtiketi(durum) },
        tarihYaz(u.end_date_ms),
        u.remaining_sessions ?? "Sınırsız",
        tutarYaz(u.price_paid_minor),
      ];
    }),
  );
}

function paketleriCiz(satirlar) {
  return tabloYap(
    ["Ad", "Tür", "Kategori", "Süre (gün)", "Seans", "Fiyat"],
    satirlar.map((p) => [
      p.name, p.type, p.category, p.validity_days,
      p.session_count ?? "Sınırsız", tutarYaz(p.base_price_minor),
    ]),
  );
}

function randevulariCiz(satirlar) {
  return tabloYap(
    ["Tarih", "Tür", "Durum", "Hakediş matrahı"],
    satirlar.map((r) => [
      tarihYaz(r.start_time_ms), r.training_type, r.state,
      tutarYaz(r.session_value_minor),
    ]),
  );
}

/**
 * Finans panosu: özet kutuları, aylık seyir, kırılımlar ve kayıt listesi.
 *
 * ### Neden düz bir tablo yetmiyordu
 * Bölüm yalnızca ham satırları listeliyordu: ne "bu dönemde ne kadar tahsilat",
 * ne "nereye gitti", ne de "geçen aya göre ne oldu" görülebiliyordu. Bu sayılar
 * defterde zaten duruyordu ama okumak için satırları elle toplamak gerekiyordu
 * — yani rapor vardı, raporun kendisi yoktu.
 *
 * ### Bütün sayılar SÜZGECE bağlı
 * Buraya gelen `satirlar` üstteki arama ve tarih aralığından geçmiş hâli.
 * Bu bilinçli: tarih aralığını daraltmak bütün kutuları, kırılımları ve
 * seyri birlikte değiştiriyor. Bazı sayıları süzgeçten muaf tutmak, aynı
 * ekranda iki farklı "dönem" tanımı yaratırdı ve hangi sayının neye ait
 * olduğu anlaşılmazdı. Kullanıcıya da yazıyor.
 *
 * ### İptal edilmiş kayıtlar
 * Listede duruyorlar (denetim izi) ama hiçbir toplama girmiyorlar ve
 * rozetle işaretleniyorlar. Uygulamadaki finans ekranı da aynısını yapıyor;
 * ikisi ayrışsaydı aynı defter iki yerde iki farklı ciro gösterirdi.
 */
function finansiCiz(satirlar) {
  const kap = document.createElement("div");
  const ozet = defterOzeti(satirlar);
  const iptaller = iptalEdilenKimlikler(satirlar);

  kap.appendChild(kutular([
    ["Tahsilat", tutarYaz(ozet.tahsilat), "deger-iyi"],
    ["Gider", tutarYaz(ozet.gider), "deger-hata"],
    ["Net", tutarYaz(ozet.net), ozet.net < 0 ? "deger-hata" : "deger-iyi"],
    // Tahakkuk ayrı kutuda: gelire eklenseydi ciro, kasaya hiç girmemiş
    // borçla şişerdi.
    ["Bekleyen tahsilat", tutarYaz(ozet.tahakkuk), "deger-uyari"],
    ["Kayıt", `${ozet.kayitSayisi}`],
    ["İptal edilen", `${ozet.iptalSayisi}`],
  ]));

  const not = document.createElement("p");
  not.className = "alt";
  not.textContent =
    "Bütün sayılar üstteki arama ve tarih süzgecine göre. " +
    "İptal edilen kayıtlar listede kalır ama hiçbir toplama girmez.";
  kap.appendChild(not);

  kap.appendChild(bolumBasligi("Aylık seyir (son 6 ay)"));
  kap.appendChild(seyirTablosu(aylikSeyir(satirlar, Date.now(), 6)));

  kap.appendChild(bolumBasligi("Kategoriye göre"));
  const kategoriler = kategoriKirilimi(satirlar);
  kap.appendChild(kategoriler.length === 0 ? bosSatir() : tabloYap(
    ["Kategori", "Tahsilat", "Gider", "Bekleyen"],
    kategoriler.map((s) => [
      s.etiket, tutarYaz(s.tahsilat), tutarYaz(s.gider), tutarYaz(s.tahakkuk),
    ]),
  ));

  kap.appendChild(bolumBasligi("Tahsilat yöntemi"));
  const yontemler = yontemKirilimi(satirlar);
  kap.appendChild(yontemler.length === 0 ? bosSatir() : tabloYap(
    ["Yöntem", "Tahsilat", "Pay"],
    yontemler.map((s) => [
      s.etiket, tutarYaz(s.tahsilat), `%${Math.round(s.oran * 100)}`,
    ]),
  ));

  kap.appendChild(bolumBasligi("Kayıtlar"));
  kap.appendChild(tabloYap(
    ["Tarih", "Tür", "Kategori", "Tutar", "Yöntem", "Durum", "Açıklama"],
    satirlar.map((k) => {
      const durum = kayitIptalDurumu(k, iptaller);
      return [
        tarihYaz(k.occurred_at_ms),
        defterTuruEtiketi(k.type),
        kategoriEtiketi(k.category),
        // İptal edilmiş satırın tutarı solgun: rozet tek başına yeterli değil,
        // gözün ilk gittiği yer tutarın kendisi.
        durum === "normal" ? tutarYaz(k.amount_minor)
          : soluk(tutarYaz(k.amount_minor)),
        FINANS_YONTEM_ETIKETLERI[k.payment_method] ?? k.payment_method,
        finansDurumRozeti(durum),
        k.description,
      ];
    }),
  ));

  return kap;
}

/** Defter türünün Türkçe karşılığı; ham `PAYMENT` kullanıcıya bir şey anlatmıyor. */
function defterTuruEtiketi(tur) {
  switch (tur) {
    case "PAYMENT": return "Tahsilat";
    case "EXPENSE": return "Gider";
    case "CHARGE": return "Tahakkuk";
    default: return tur ?? "—";
  }
}

/** İptal durumunun tablo hücresi; yaşayan kayıtta rozet yok, tire var. */
function finansDurumRozeti(durum) {
  if (durum === "iptal") return { rozet: "finans-iptal", metin: "İptal edildi" };
  if (durum === "ters") return { rozet: "finans-ters", metin: "İptal kaydı" };
  return "—";
}

/** Solgun metin düğümü — `tabloYap` hazır DOM düğümlerini olduğu gibi yerleştiriyor. */
function soluk(metin) {
  const span = document.createElement("span");
  span.className = "deger-soluk";
  span.textContent = metin;
  return span;
}

function bolumBasligi(metin) {
  const h = document.createElement("h2");
  h.textContent = metin;
  return h;
}

function bosSatir(metin = "Bu aralıkta kayıt yok.") {
  const p = document.createElement("p");
  p.className = "alt";
  p.textContent = metin;
  return p;
}

/**
 * Aylık seyir — çubuklu bir tablo.
 *
 * Ayrı bir grafik düzeni değil, `tabloYap` üzerine kurulu: çubuk yalnızca bir
 * hücrenin içeriği. Böylece sayılar okunur kalıyor ve dar ekranda tablo
 * düzeninin kendisi devreye giriyor.
 *
 * Çubuklar tek başına bir şey ANLATMIYOR — tutarlar yanlarında yazılı olarak
 * duruyor. Yalnızca çubuk çizilseydi rakamlar üstüne gelmeden okunamazdı ve
 * dokunmatik ekranda "üstüne gelmek" diye bir şey yok.
 */
function seyirTablosu(aylar) {
  // Ölçek: en yüksek tahsilat ya da gider. Sıfıra bölmemek için en az 1.
  const enBuyuk = Math.max(1, ...aylar.flatMap((a) => [a.tahsilat, a.gider]));

  return tabloYap(
    ["Ay", "Seyir", "Tahsilat", "Gider", "Net"],
    aylar.map((a) => [
      a.etiket,
      cubukHucresi(a, enBuyuk),
      tutarYaz(a.tahsilat),
      tutarYaz(a.gider),
      a.net < 0 ? renkli(tutarYaz(a.net), "deger-hata") : renkli(tutarYaz(a.net), "deger-iyi"),
    ]),
  );
}

function cubukHucresi(ay, enBuyuk) {
  const kap = document.createElement("span");
  kap.className = "seyir-cubuklar";
  for (const [deger, sinif, etiket] of [
    [ay.tahsilat, "seyir-gelir", "Tahsilat"],
    [ay.gider, "seyir-gider", "Gider"],
  ]) {
    // Sıfır tutarda çubuk HİÇ çizilmiyor. Çubukların en küçük bir genişliği
    // var (çok küçük tutarlar görünmez olmasın diye) ve sıfır da o genişlikte
    // çizilseydi hareketsiz ay, küçük bir gider varmış gibi görünürdü.
    // Ayın boş olduğu zaten komşu hücrelerdeki "₺0,00" ile yazılı.
    if (deger <= 0) continue;

    const cubuk = document.createElement("span");
    cubuk.className = `seyir-cubuk ${sinif}`;
    cubuk.style.width = `${(deger / enBuyuk) * 100}%`;
    // Ekran okuyucu için: görsel uzunluk tek başına bilgi taşımıyor.
    cubuk.title = `${etiket}: ${tutarYaz(deger)}`;
    kap.appendChild(cubuk);
  }
  return kap;
}

function renkli(metin, sinif) {
  const span = document.createElement("span");
  span.className = sinif;
  span.textContent = metin;
  return span;
}

// ─── Açılış ─────────────────────────────────────────────────────────────────

if (!istemci.yapilandirildiMi) {
  goster("ayar-eksik");
} else {
  const oturum = istemci.oturumOku();
  // Süresi dolmuş oturumla panel açılsaydı her sekme 401 alır ve kullanıcı
  // sebebini anlamadan boş ekranlar görürdü.
  if (oturum && Date.now() < oturum.expires_at_ms) {
    paneliAc(oturum);
  } else {
    istemci.oturumSil();
    goster("giris");
  }
}
