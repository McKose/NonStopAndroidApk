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

kahramanVideosu();
kahramanKaydirma();
