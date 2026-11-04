// Açılış sayfasının tek dinamik parçası: etkinlik ve duyurular.
//
// Sayfanın geri kalanı sabit HTML. Bu bilinçli: tanıtım metni, branşlar ve
// iletişim bilgisi sunucuya bağlı olmamalı — Supabase ulaşılamadığında ya da
// ayar dosyası olmadığında site yine de açılıp salonu anlatmalı.
//
// ### Giriş yapılmadan okunuyor
// `announcements` tablosunda yalnızca YAYINLANMIŞ ve yayın penceresi açık
// satırlar `anon` rolüne açık (migrasyon 0005). Süzme sunucuda: taslak ya da
// süresi geçmiş bir duyuru istemciye hiç gelmiyor. Yalnızca burada süzülseydi
// adresi bilen biri yayınlanmamış kampanyayı API'den okuyabilirdi.

import { formDurumu, formuDogrula, tuzagaDustu } from "./iletisim-formu.js";

const $ = (id) => document.getElementById(id);

const ayar = window.NONSTOP_CONFIG;

/** Bugünün yılı alt bilgide; elle güncellenen bir yıl er geç eskiyor. */
$("yil").textContent = String(new Date().getFullYear());

/**
 * Duyuruları çeker.
 *
 * Hata durumunda **sessiz**: bölüm gizli kalıyor. Tanıtım sayfasında ziyaretçiye
 * "duyurular yüklenemedi" demek bir işe yaramıyor — yapabileceği bir şey yok ve
 * sayfanın asıl işi (salonu anlatmak, girişe yönlendirmek) etkilenmiyor.
 * Panelde tam tersi geçerli ve orada hatalar açıkça yazılıyor.
 */
async function duyurulariYukle() {
  if (!ayar?.url || !ayar?.anonKey) return;

  const adres =
    `${ayar.url}/rest/v1/announcements` +
    `?select=id,title,body,kind,image_url,starts_at_ms` +
    `&order=sort_order.asc,starts_at_ms.desc` +
    `&limit=12`;

  const yanit = await fetch(adres, {
    headers: { apikey: ayar.anonKey },
  }).catch(() => null);

  if (!yanit || !yanit.ok) return;

  const satirlar = await yanit.json().catch(() => null);
  if (!Array.isArray(satirlar) || satirlar.length === 0) return;

  ciz(satirlar);
}

function ciz(satirlar) {
  const kap = $("duyuru-listesi");

  for (const satir of satirlar) {
    const kart = document.createElement("article");
    kart.className = "kart";

    // Görsel yalnızca varsa. `image_url` boşken kırık bir resim simgesi
    // göstermek, duyuruyu olduğundan özensiz gösterirdi.
    if (satir.image_url) {
      const gorsel = document.createElement("img");
      gorsel.className = "kart-gorsel";
      gorsel.src = satir.image_url;
      gorsel.alt = "";
      gorsel.loading = "lazy";
      kart.appendChild(gorsel);
    }

    const tarih = tarihYaz(satir.starts_at_ms);
    if (tarih) {
      const etiket = document.createElement("span");
      etiket.className = "kart-tarih";
      etiket.textContent = `${turEtiketi(satir.kind)} · ${tarih}`;
      kart.appendChild(etiket);
    } else {
      const etiket = document.createElement("span");
      etiket.className = "kart-tarih";
      etiket.textContent = turEtiketi(satir.kind);
      kart.appendChild(etiket);
    }

    const baslik = document.createElement("h3");
    // `textContent`, `innerHTML` değil: başlık ve metin panelden giriliyor ve
    // HTML olarak yorumlanmamalı.
    baslik.textContent = satir.title ?? "";
    kart.appendChild(baslik);

    const metin = document.createElement("p");
    metin.textContent = satir.body ?? "";
    kart.appendChild(metin);

    kap.appendChild(kart);
  }

  $("etkinlikler").hidden = false;
}

function turEtiketi(tur) {
  switch (tur) {
    case "EVENT": return "Etkinlik";
    case "AD": return "Kampanya";
    case "NOTICE": return "Duyuru";
    default: return "Duyuru";
  }
}

/** Epoch milisaniyeyi tarihe çevirir; okunamayan değer `null`. */
function tarihYaz(ms) {
  if (ms === null || ms === undefined) return null;
  const sayi = Number(ms);
  if (!Number.isFinite(sayi)) return null;
  return new Date(sayi).toLocaleDateString("tr-TR", {
    day: "2-digit",
    month: "long",
    year: "numeric",
  });
}

duyurulariYukle();

// ─── Karşılama: video ve kaydırma animasyonu ──────────────────────────────
//
// Bu bölümün tamamı İSTEĞE BAĞLI süs. Hiçbiri çalışmazsa banner yine de
// fotoğraf + metin olarak tam görünüyor; CSS'teki varsayılan hâl bu.
// Bu yüzden burada hata yakalama sessiz: ziyaretçinin yapabileceği bir şey yok.

/** Kullanıcı hareket azaltma istiyor mu. */
const hareketAzalt =
  window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;

/**
 * Videoyu arka planda yükler, oynayabilir hâle gelince gösterir.
 *
 * ### Sıra neden böyle
 * Fotoğraf `fetchpriority="high"` ile hemen iniyor ve banner anında doluyor.
 * Video ise `preload="none"` ile bekliyor; yüklemeyi BURADAN başlatıyoruz.
 * Tersi olsaydı tarayıcı ikisini birden çeker, fotoğraf gecikir ve ziyaretçi
 * ilk saniyede boş bir alan görürdü.
 *
 * ### Neden `canplaythrough` değil de `canplay`
 * `canplaythrough` videonun SONUNA kadar kesintisiz oynayabileceğini bekliyor;
 * yavaş bağlantıda bu olay hiç gelmeyebiliyor ve video sonsuza kadar gizli
 * kalıyordu. `canplay` "oynatmaya başlanabilir" demek ve döngüye alınmış
 * sessiz bir arka plan videosu için doğru eşik — takılırsa altındaki fotoğraf
 * zaten duruyor.
 *
 * ### Otomatik oynatma reddedilebilir
 * `play()` bir Promise döndürüyor ve tarayıcı reddedebiliyor (iOS'ta düşük
 * güç modu, bazı veri tasarrufu ayarları). Reddedilirse video GÖSTERİLMİYOR:
 * donmuş tek kare, hareketli fotoğraftan daha kötü görünürdü.
 */
function kahramanVideosu() {
  const video = document.getElementById("kahraman-video");
  if (!video) return;

  // Yol boşsa video henüz eklenmemiş demektir; fotoğrafta kalınıyor ve
  // HİÇBİR istek atılmıyor. (Neden `src` değil de `data-kaynak`: index.html.)
  const kaynak = video.dataset.kaynak?.trim();
  if (!kaynak) return;

  // Hareket azaltma tercihinde video hiç yüklenmiyor: hem tercih gereği hem
  // de boşuna indirilen birkaç megabayt olmasın diye.
  if (hareketAzalt) return;

  video.addEventListener("canplay", () => {
    video.play().then(
      () => video.classList.add("hazir"),
      () => { /* otomatik oynatma reddedildi; fotoğrafta kalınıyor */ },
    );
  }, { once: true });

  // Dosya yoksa (henüz eklenmedi) ya da ağ hatasında sessizce fotoğrafta
  // kalınıyor. `error` olayı `<source>` üzerinde tetikleniyor.
  video.addEventListener("error", () => {}, { once: true });

  video.preload = "auto";
  video.src = kaynak;
  video.load();
}

/**
 * Kaydırma animasyonunun JS yedeği.
 *
 * YALNIZCA `animation-timeline: view()` desteklenmiyorsa devreye giriyor.
 * Destekleniyorsa animasyon tamamen CSS'te koşuyor ve ana iş parçacığına
 * hiç dokunmuyor — kaydırma akıcılığı için bu fark gözle görülür.
 *
 * `.js-kaydirma` sınıfı GÖVDEYE burada ekleniyor, HTML'de değil: sınıf
 * baştan HTML'de olsaydı ve bu betik yüklenmeseydi (ağ hatası, `type=module`
 * desteklemeyen tarayıcı) içerik `opacity: 0` ile gizli kalır ve banner boş
 * görünürdü.
 */
function kahramanKaydirma() {
  if (hareketAzalt) return;
  if (CSS.supports?.("animation-timeline: view()")) return;
  if (!("IntersectionObserver" in window)) return;

  const icerik = document.querySelector(".kahraman-icerik");
  if (!icerik) return;

  // Sıralı geliş: her çocuk bir öncekinden 90 ms sonra.
  [...icerik.children].forEach((cocuk, sira) => {
    cocuk.style.setProperty("--gecikme", `${sira * 90}ms`);
  });

  document.body.classList.add("js-kaydirma");

  const gozlemci = new IntersectionObserver((girisler) => {
    for (const giris of girisler) {
      if (!giris.isIntersecting) continue;
      giris.target.classList.add("gorunur");
      gozlemci.unobserve(giris.target);
    }
  }, { threshold: 0.15 });

  gozlemci.observe(icerik);
}

// ─── Deneme dersi formu ───────────────────────────────────────────────────
/*
  Formun tek işi yükü üretip uca göndermek. Doğrulama ve normalleştirme
  `iletisim-formu.js`te ve orada Node testleriyle sınanıyor; buradaki kod
  DOM'a bağlı olduğu için Chromium'da sürülüyor.

  Yazma doğrudan tabloya DEĞİL, `iletisim-istegi` Edge Function'ına gidiyor.
  Gerekçe `0010`un başında: `anon` yayınlanmış anahtarla çalışıyor ve tabloya
  `insert` açmak "internetteki herkes istediği hızda satır yazabilir" demek
  olurdu. Uç ayrıca mükerrer bastırma ve hız sınırı yapıyor.
*/

/** Alan altındaki hata metnini yazar ya da gizler. */
function alanHatasi(ad, mesaj) {
  const kutu = $(`deneme-${ad}-hata`);
  if (!kutu) return;
  kutu.textContent = mesaj ?? "";
  kutu.hidden = !mesaj;
}

const HATA_ALANLARI = ["ad", "telefon", "eposta", "mesaj"];

function denemeFormu() {
  const sarmal = $("deneme-sarmal");
  const form = $("deneme-form");
  if (!sarmal || !form) return;

  // FORM İKİ KOŞULDAN BİRİ SAĞLANMAZSA HİÇ AÇILMIYOR.
  //
  //  1. Ürün kararı (`FORM_ACIK`). Şu an kapalı ve sebebi KVKK sıralaması:
  //     form kişisel veri topluyor ve aydınlatma metni henüz tamam değil.
  //     Ayrıntı `iletisim-formu.js`te.
  //  2. Sunucu ayarı. Yoksa form GÖNDERİLEMEZ, ve gönderilemeyen bir form
  //     tuzaktır: ziyaretçi doldurur, düğmeye basar, hiçbir şey olmaz ve
  //     salona ulaştığını sanır.
  //
  // İki hâlde de telefon ve WhatsApp hemen yukarıda duruyor, yani kapalı
  // kalmak ziyaretçiyi çıkışsız bırakmıyor.
  if (!formDurumu({ tenantId: ayar?.tenantId ?? null }).acik) return;
  if (!ayar?.url || !ayar?.anonKey) return;
  sarmal.hidden = false;

  const dugme = $("deneme-gonder");
  const durum = $("deneme-durum");

  form.addEventListener("submit", async (olay) => {
    olay.preventDefault();
    if (dugme.disabled) return;

    for (const a of HATA_ALANLARI) alanHatasi(a, null);
    durum.textContent = "";

    // Tuzak EN BAŞTA: dolu geldiğinde hiçbir şey gönderilmiyor ama form
    // başarılı gibi davranıyor. Bota "başarısız" demek, denemeye devam
    // etmesi için bilgi vermek olurdu.
    if (tuzagaDustu($("deneme-tuzak").value)) {
      form.reset();
      durum.textContent = "Teşekkürler, en kısa sürede size dönüyoruz.";
      return;
    }

    const sonuc = formuDogrula({
      adSoyad: $("deneme-ad").value,
      telefon: $("deneme-telefon").value,
      eposta: $("deneme-eposta").value,
      mesaj: $("deneme-mesaj").value,
    });

    if (!sonuc.gecerli) {
      // Hatalar TOPLUCA gösteriliyor: tek tek gösterilse ziyaretçi formu
      // üç kez göndermek zorunda kalırdı.
      alanHatasi("ad", sonuc.hatalar.adSoyad);
      alanHatasi("telefon", sonuc.hatalar.telefon);
      alanHatasi("eposta", sonuc.hatalar.eposta);
      alanHatasi("mesaj", sonuc.hatalar.mesaj);
      durum.textContent = "Lütfen işaretli alanları kontrol edin.";
      return;
    }

    dugme.disabled = true;
    durum.textContent = "Gönderiliyor…";

    const yanit = await fetch(`${ayar.url}/functions/v1/iletisim-istegi`, {
      method: "POST",
      headers: {
        apikey: ayar.anonKey,
        Authorization: `Bearer ${ayar.anonKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ tenantId: ayar.tenantId, ...sonuc.yuk }),
    }).catch(() => null);

    dugme.disabled = false;

    if (!yanit) {
      // Ağ hatası: ziyaretçinin yapabileceği bir şey var, o yüzden
      // duyurulardaki "sessiz hata" deseni BURADA GEÇERLİ DEĞİL.
      durum.textContent =
        "Bağlantı kurulamadı. Telefonla ya da WhatsApp'tan ulaşabilirsiniz.";
      return;
    }

    if (yanit.ok) {
      form.reset();
      durum.textContent = "Teşekkürler, en kısa sürede size dönüyoruz.";
      return;
    }

    // Ucun mesajı varsa O gösteriliyor: hız sınırı yanıtı ziyaretçiyi
    // WhatsApp ve telefona yönlendiriyor ve o yönlendirme kaybolmamalı.
    const govde = await yanit.json().catch(() => null);
    durum.textContent =
      govde?.hata ??
      "Şu anda gönderilemedi. Telefonla ya da WhatsApp'tan ulaşabilirsiniz.";
  });
}

kahramanVideosu();
kahramanKaydirma();
denemeFormu();
