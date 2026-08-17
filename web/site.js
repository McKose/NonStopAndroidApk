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
