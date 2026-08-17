// Panelin akışı: giriş, sekmeler, listeler.
//
// Panel **salt okunur**. Yazma bilinçli olarak yok: uygulamadaki her yazma yolu
// aynı transaction içinde gönderim kuyruğuna kayıt bırakıyor ve iş kuralları
// (hakediş, seans düşme, defter kaydı) ortak Kotlin modülünde. Panelden yazmak,
// o kuralların ikinci bir kopyasını burada tutmak demek olurdu — ve iki kopya
// er geç birbirinden sapardı.

import { SupabaseClient } from "./supabase.js";
import { tutarYaz, tarihYaz, uyelikDurumu, durumEtiketi, silinmemisler } from "./domain.js";
import { ayBasi, uyeDagilimi, yaklasanBitisler, defterToplami } from "./ozet.js";
import { demoIstemcisi, demoMu } from "./demo.js";
import { suz } from "./suzme.js";
import { stokHaritasi, stokYaz, stokUyarilari, stokDurumu } from "./stok.js";
import { sekmeGorunur } from "./roller.js";
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

function kutular(ciftler) {
  const kap = document.createElement("div");
  kap.className = "kutular";
  for (const [baslik, deger] of ciftler) {
    const kutu = document.createElement("div");
    kutu.className = "kutu";
    const b = document.createElement("p");
    b.className = "alt";
    b.textContent = baslik;
    const d = document.createElement("strong");
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
      if (hucre && typeof hucre === "object" && hucre.rozet) {
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

function finansiCiz(satirlar) {
  return tabloYap(
    ["Tarih", "Tür", "Kategori", "Tutar", "Yöntem", "Açıklama"],
    satirlar.map((k) => [
      tarihYaz(k.occurred_at_ms), k.type, k.category,
      tutarYaz(k.amount_minor), k.payment_method, k.description,
    ]),
  );
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
