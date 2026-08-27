// Üye alanı: giriş, paket durumu, ölçümler, sağlık beyanı.
//
// ### Panelden ayrı bir oturum
// Oturum `nonstop.uye.session` altında saklanıyor, panelin `nonstop.session`
// anahtarının altında değil. İkisi aynı anahtarı paylaşsaydı aynı tarayıcıda
// üye girişi yapmak panelin oturumunu ezerdi (ve tersi): panel üyenin jetonuyla
// açılmaya çalışır, "salonsuz" der ve kullanıcı sebebini anlamazdı.
//
// ### Neden panelin istemcisi kullanılmıyor
// `admin/supabase.js` girişten sonra salonu `gym_users` üzerinden çözüyor —
// personel için doğru, üye için yanlış: üyenin `gym_users` satırı YOK ve
// olmamalı (bkz. migrasyon 0005). Üyenin kimliği `member_accounts` üzerinden
// çözülüyor.
//
// Gösterim yardımcıları paylaşılıyor: tutar ve tarih biçimlendirmesinin iki
// kopyası olsaydı aynı veri iki yüzeyde farklı görünürdü.

import { tutarYaz, tarihYaz, uyelikDurumu, durumEtiketi } from "../admin/domain.js";

const $ = (id) => document.getElementById(id);
const ayar = window.NONSTOP_CONFIG;

const OTURUM_ANAHTARI = "nonstop.uye.session";

/** Giriş yapan üyenin kaydı; beyan gönderirken gerekiyor. */
let uye = null;

// ─── Oturum ─────────────────────────────────────────────────────────────────

function oturumOku() {
  try {
    const ham = localStorage.getItem(OTURUM_ANAHTARI);
    if (!ham) return null;
    const o = JSON.parse(ham);
    // Yarım bir oturum (jetonu olan ama süresi bilinmeyen) her isteği sessizce
    // başarısız kılardı.
    if (!o.access_token || !o.expires_at_ms) return null;
    if (Date.now() >= o.expires_at_ms) return null;
    return o;
  } catch {
    return null;
  }
}

const oturumYaz = (o) => localStorage.setItem(OTURUM_ANAHTARI, JSON.stringify(o));
const oturumSil = () => localStorage.removeItem(OTURUM_ANAHTARI);

// ─── Sunucu ─────────────────────────────────────────────────────────────────

async function oku(yol) {
  const o = oturumOku();
  if (!o) return { tur: "oturumsuz" };

  const yanit = await fetch(`${ayar.url}/rest/v1/${yol}`, {
    headers: { apikey: ayar.anonKey, Authorization: `Bearer ${o.access_token}` },
  }).catch(() => null);

  if (!yanit) return { tur: "hata", mesaj: "Sunucuya ulaşılamadı." };
  if (yanit.status === 401) { oturumSil(); return { tur: "oturumsuz" }; }
  if (!yanit.ok) {
    const g = await yanit.text().catch(() => "");
    return { tur: "hata", mesaj: `Okunamadı (${yanit.status}): ${g.slice(0, 160)}` };
  }
  return { tur: "tamam", satirlar: await yanit.json().catch(() => []) };
}

/** Satır yazar (POST) ya da günceller (PATCH). */
async function yaz(yol, govde, yontem = "POST") {
  const o = oturumOku();
  if (!o) return { tur: "oturumsuz" };

  const yanit = await fetch(`${ayar.url}/rest/v1/${yol}`, {
    method: yontem,
    headers: {
      apikey: ayar.anonKey,
      Authorization: `Bearer ${o.access_token}`,
      "Content-Type": "application/json",
      Prefer: "return=minimal",
    },
    body: JSON.stringify(govde),
  }).catch(() => null);

  if (!yanit) return { tur: "hata", mesaj: "Sunucuya ulaşılamadı." };
  if (yanit.status === 401) { oturumSil(); return { tur: "oturumsuz" }; }
  if (yanit.ok) return { tur: "tamam" };

  const g = await yanit.text().catch(() => "");
  return { tur: "hata", mesaj: `Kaydedilemedi (${yanit.status}): ${g.slice(0, 160)}` };
}

function goster(bolum) {
  for (const id of ["giris", "kayit", "pano", "ayar-eksik", "baglanmamis"]) {
    $(id).hidden = id !== bolum;
  }
}

function hataYaz(alanId, mesaj) {
  const a = $(alanId);
  a.textContent = mesaj ?? "";
  a.hidden = !mesaj;
}

// ─── Giriş ──────────────────────────────────────────────────────────────────

$("giris-formu").addEventListener("submit", async (olay) => {
  olay.preventDefault();
  const dugme = $("giris-dugmesi");
  if (dugme.disabled) return;

  dugme.disabled = true;
  dugme.textContent = "Giriş yapılıyor…";
  hataYaz("giris-hata", null);

  const veri = new FormData(olay.target);
  const yanit = await fetch(`${ayar.url}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: { apikey: ayar.anonKey, "Content-Type": "application/json" },
    body: JSON.stringify({
      email: String(veri.get("eposta")).trim(),
      password: String(veri.get("sifre")),
    }),
  }).catch(() => null);

  dugme.disabled = false;
  dugme.textContent = "Giriş yap";

  if (!yanit) return hataYaz("giris-hata", "Sunucuya ulaşılamadı.");

  const govde = await yanit.json().catch(() => ({}));
  if (!yanit.ok) {
    // Sunucunun mesajı olduğu gibi aktarılıyor: "Invalid login credentials" ile
    // "Email not confirmed" kullanıcı açısından tamamen farklı iki iş.
    const mesaj = govde.error_description || govde.msg || govde.message || "Giriş yapılamadı.";
    return hataYaz("giris-hata", mesaj);
  }

  oturumYaz({
    access_token: govde.access_token,
    expires_at_ms: Date.now() + (Number(govde.expires_in) || 0) * 1000,
    email: govde.user?.email ?? "",
  });

  panoyuAc();
});

$("cikis").addEventListener("click", () => {
  oturumSil();
  uye = null;
  $("cikis").hidden = true;
  $("uye-adi").hidden = true;
  goster("giris");
});

// ─── Kayıt ──────────────────────────────────────────────────────────────────

$("kayda-git").addEventListener("click", (olay) => {
  olay.preventDefault();
  goster("kayit");
});
$("girise-git").addEventListener("click", (olay) => {
  olay.preventDefault();
  goster("giris");
});

/**
 * Kayıt: Supabase hesabı açar, sonra bağlanma isteğini yazar.
 *
 * ### İki adım neden ayrı
 * Kayıt hesabı açıyor, istek ise "beni şu salona bağlayın" diyor. Supabase
 * e-posta doğrulaması AÇIKSA kayıt oturum döndürmüyor — o hâlde istek burada
 * yazılamaz, çünkü satırı yazacak olan kullanıcının kendisi (`auth.uid()`).
 * Bu yüzden istek, oturum varsa hemen; yoksa kullanıcı e-postasını doğrulayıp
 * ilk kez giriş yaptığında `baglanmamis` ekranındaki formdan yazılıyor.
 *
 * Girilen ad ve telefon tarayıcıda SAKLANMIYOR: doğrulama başka bir cihazda
 * (telefonda gelen bağlantı, masaüstünde kayıt) tamamlanabilir ve orada
 * bulunmayan bir kopyaya güvenmek, bilginin sessizce kaybolması olurdu.
 * İkinci ekranda tekrar soruluyor.
 */
$("kayit-formu").addEventListener("submit", async (olay) => {
  olay.preventDefault();
  const dugme = $("kayit-dugmesi");
  if (dugme.disabled) return;

  const veri = new FormData(olay.target);
  const ad = String(veri.get("ad")).trim();
  const telefon = String(veri.get("telefon")).trim();
  const eposta = String(veri.get("eposta")).trim();
  const not = String(veri.get("not")).trim();

  hataYaz("kayit-hata", null);
  hataYaz("kayit-bilgi", null);

  if (!ayar.tenantId) {
    // Kayıt isteği bir salona ait olmak zorunda (`tenant_id not null`). Ayar
    // eksikse kaydı sessizce yarım bırakmak yerine hiç başlatmıyoruz:
    // hesap açılıp istek yazılamasaydı kullanıcı "kayıt oldum" sanırdı ve
    // salonun listesinde hiç görünmezdi.
    return hataYaz("kayit-hata",
      "Kayıt şu anda kapalı (salon ayarı eksik). Lütfen salonla iletişime geçin.");
  }

  dugme.disabled = true;
  dugme.textContent = "Kayıt yapılıyor…";

  const yanit = await fetch(`${ayar.url}/auth/v1/signup`, {
    method: "POST",
    headers: { apikey: ayar.anonKey, "Content-Type": "application/json" },
    body: JSON.stringify({ email: eposta, password: String(veri.get("sifre")) }),
  }).catch(() => null);

  dugme.disabled = false;
  dugme.textContent = "Kayıt ol";

  if (!yanit) return hataYaz("kayit-hata", "Sunucuya ulaşılamadı.");

  const govde = await yanit.json().catch(() => ({}));
  if (!yanit.ok) {
    // Sunucunun mesajı olduğu gibi aktarılıyor: "User already registered" ile
    // "Password should be at least 6 characters" kullanıcı açısından tamamen
    // farklı iki iş.
    return hataYaz("kayit-hata",
      govde.error_description || govde.msg || govde.message || "Kayıt yapılamadı.");
  }

  // Oturum dönmediyse e-posta doğrulaması açık demek.
  if (!govde.access_token) {
    olay.target.reset();
    return hataYaz("kayit-bilgi",
      `${eposta} adresine bir doğrulama bağlantısı gönderildi. Bağlantıya ` +
      "tıkladıktan sonra buradan giriş yapın; bilgileriniz orada sorulacak.");
  }

  oturumYaz({
    access_token: govde.access_token,
    expires_at_ms: Date.now() + (Number(govde.expires_in) || 0) * 1000,
    email: govde.user?.email ?? eposta,
  });

  const istekSonuc = await istekYaz({ ad, telefon, eposta, not });
  if (istekSonuc.tur !== "tamam") {
    // Hesap AÇILDI ama istek yazılamadı. Bunu gizlemek, kullanıcıyı hiç
    // görünmeyeceği bir bekleyişe göndermek olurdu; `baglanmamis` ekranı formu
    // tekrar sunuyor ve oradan yeniden denenebiliyor.
    hataYaz("kayit-hata", istekSonuc.mesaj ?? "Bilgileriniz kaydedilemedi.");
  }

  panoyuAc();
});

/** Bağlanma isteğini yazar (yeni satır ya da bekleyen satırın güncellenmesi). */
async function istekYaz({ ad, telefon, eposta, not }, guncelle = false) {
  const o = oturumOku();
  if (!o) return { tur: "oturumsuz" };

  const kimlik = await kullaniciKimligi();
  if (!kimlik) return { tur: "hata", mesaj: "Oturum bilgisi okunamadı." };

  const simdi = Date.now();
  const govde = {
    tenant_id: ayar.tenantId,
    full_name: ad,
    phone: telefon,
    email: eposta || null,
    note: not || null,
    updated_at_ms: simdi,
  };

  if (guncelle) {
    return yaz(
      `member_link_requests?auth_user_id=eq.${encodeURIComponent(kimlik)}`,
      govde,
      "PATCH",
    );
  }
  return yaz("member_link_requests", {
    auth_user_id: kimlik,
    created_at_ms: simdi,
    ...govde,
  });
}

/**
 * Oturumdaki kullanıcının kimliği.
 *
 * Jetonun içinden değil, sunucudan (`/auth/v1/user`) okunuyor. JWT'yi istemcide
 * çözmek çalışırdı ama kimliği İSTEMCİNİN söylediği bir şey hâline getirirdi;
 * satırın anahtarı bu değer ve tek doğru kaynağı sunucu. (Yazma kuralı zaten
 * `auth_user_id = auth.uid()` diyor, yani yanlış bir değer reddedilirdi —
 * ama o hâlde hata anlaşılmaz olurdu.)
 */
async function kullaniciKimligi() {
  const o = oturumOku();
  if (!o) return null;
  if (o.user_id) return o.user_id;

  const yanit = await fetch(`${ayar.url}/auth/v1/user`, {
    headers: { apikey: ayar.anonKey, Authorization: `Bearer ${o.access_token}` },
  }).catch(() => null);

  if (!yanit || !yanit.ok) return null;
  const govde = await yanit.json().catch(() => ({}));
  if (!govde.id) return null;

  oturumYaz({ ...o, user_id: govde.id });
  return govde.id;
}

// ─── Pano ───────────────────────────────────────────────────────────────────

async function panoyuAc() {
  // Üyenin kendi kaydı. Erişim kuralı yalnızca kendi satırını döndürüyor, o
  // yüzden sorguda süzgeç YOK — yalıtımı sağlayan şey kural, sorgu değil.
  const uyeSonuc = await oku("gym_members?select=*");
  if (uyeSonuc.tur === "oturumsuz") return goster("giris");
  if (uyeSonuc.tur !== "tamam") {
    goster("pano");
    return hataYaz("pano-hata", uyeSonuc.mesaj);
  }

  const satirlar = (uyeSonuc.satirlar ?? []).filter((s) => !s.deleted_at_ms);

  // Hiç satır yoksa hesap bir üyeliğe bağlanmamış demek. Boş bir pano
  // göstermek kullanıcıya verisinin kaybolduğunu düşündürürdü.
  if (satirlar.length === 0) {
    $("cikis").hidden = false;
    goster("baglanmamis");
    return baglantiDurumunuCiz();
  }

  uye = satirlar[0];
  $("uye-adi").textContent = uye.full_name ?? "";
  $("uye-adi").hidden = false;
  $("cikis").hidden = false;
  goster("pano");

  paketiCiz();
  olcumleriCiz();
  saglikFormunuCiz();
  beyanlariCiz();
}

// ─── Bağlanma isteği ────────────────────────────────────────────────────────

/**
 * "Hesabınız bağlanmamış" ekranının gövdesi.
 *
 * Üç hâl var ve kullanıcının yapması gereken şey her birinde farklı:
 *
 *   - istek yok        → bilgilerini bırakacağı form
 *   - istek bekliyor   → bilgi + düzeltme imkânı
 *   - istek reddedildi → salonla iletişim
 *
 * Üçünü tek bir metinle anlatmak, en az ikisinde yanlış yönlendirmek olurdu:
 * "salon sizi bağlayacak" diyen bir ekran, isteğini hiç göndermemiş kişiyi
 * sonsuza kadar bekletir.
 */
async function baglantiDurumunuCiz() {
  const kap = $("baglanti-durumu");
  kap.replaceChildren(paragraf("Bilgileriniz kontrol ediliyor…", "alt"));

  // Erişim kuralı yalnızca kendi satırını döndürüyor, o yüzden sorguda süzgeç
  // yok — yalıtımı sağlayan şey kural, sorgu değil.
  const sonuc = await oku("member_link_requests?select=*");
  if (sonuc.tur === "oturumsuz") return goster("giris");

  if (sonuc.tur !== "tamam") {
    // Okunamadıysa form GÖSTERİLMİYOR: var olan bir isteğin üzerine ikinci
    // kez yazmayı denemek 409 ile düşerdi ve kullanıcı sebebini anlamazdı.
    return kap.replaceChildren(paragraf(
      "Bilgileriniz şu anda okunamadı. Sayfayı yenileyin ya da salonla " +
      "iletişime geçin.", "hata"));
  }

  const istek = (sonuc.satirlar ?? [])[0];

  if (!istek) return kap.replaceChildren(istekFormu(null));

  if (istek.state === "REJECTED") {
    return kap.replaceChildren(paragraf(
      "Bağlantı isteğiniz onaylanmadı. Bilgilerinizde bir uyuşmazlık olabilir — " +
      "aşağıdaki bağlantıdan bize ulaşın.", "alt"));
  }

  if (istek.state === "LINKED") {
    // Beklenmedik: istek "bağlandı" ama üyelik kaydı görünmüyor. Genelde bağın
    // başka bir salona kurulmuş olması demek. Sessizce boş ekran göstermek
    // yerine durumu söylüyoruz.
    return kap.replaceChildren(paragraf(
      "Hesabınız bağlı görünüyor ancak üyelik kaydınıza ulaşılamadı. " +
      "Salonla iletişime geçin.", "hata"));
  }

  const bilgi = paragraf(
    `Bilgileriniz salona iletildi (${tarihYaz(istek.created_at_ms)}). ` +
    "Onaylandığında paket durumunuz ve ölçümleriniz burada görünecek. " +
    "Yanlış bir bilgi varsa aşağıdan düzeltebilirsiniz.", "alt");
  kap.replaceChildren(bilgi, istekFormu(istek));
}

function paragraf(metin, sinif) {
  const p = document.createElement("p");
  p.className = sinif;
  p.style.maxWidth = "56ch";
  p.textContent = metin;
  return p;
}

/** Bilgi bırakma / düzeltme formu. `mevcut` doluysa güncelleme yapıyor. */
function istekFormu(mevcut) {
  const form = document.createElement("form");
  form.className = "kart uye-form";

  const alanlar = {};
  const ekle = (ad, etiket, deger, tur = "text") => {
    const l = document.createElement("label");
    l.textContent = etiket;
    const i = tur === "textarea" ? document.createElement("textarea") : document.createElement("input");
    if (tur === "textarea") i.rows = 2;
    else i.type = tur;
    i.value = deger ?? "";
    l.appendChild(i);
    form.appendChild(l);
    alanlar[ad] = i;
  };

  ekle("ad", "Ad Soyad", mevcut?.full_name);
  ekle("telefon", "Telefon — salondaki kaydınızla aynı olmalı", mevcut?.phone, "tel");
  ekle("not", "Eklemek istedikleriniz (isteğe bağlı)", mevcut?.note, "textarea");

  const dugme = document.createElement("button");
  dugme.type = "submit";
  dugme.className = "dugme dugme-birincil";
  dugme.textContent = mevcut ? "Bilgilerimi güncelle" : "Bilgilerimi gönder";
  form.appendChild(dugme);

  const durum = document.createElement("p");
  durum.className = "alt";
  form.appendChild(durum);

  form.onsubmit = async (olay) => {
    olay.preventDefault();
    if (dugme.disabled) return;

    const ad = alanlar.ad.value.trim();
    const telefon = alanlar.telefon.value.trim();
    // İkisi de sunucuda `not null`. Boş gönderim, anlaşılmaz bir 400 olurdu.
    if (!ad || !telefon) {
      durum.textContent = "Ad ve telefon gerekli.";
      return;
    }
    if (!ayar.tenantId) {
      durum.textContent =
        "Kayıt şu anda kapalı (salon ayarı eksik). Lütfen salonla iletişime geçin.";
      return;
    }

    dugme.disabled = true;
    durum.textContent = "Gönderiliyor…";

    const sonuc = await istekYaz(
      { ad, telefon, eposta: oturumOku()?.email ?? "", not: alanlar.not.value.trim() },
      Boolean(mevcut),
    );

    dugme.disabled = false;
    if (sonuc.tur === "oturumsuz") return goster("giris");
    if (sonuc.tur === "tamam") return baglantiDurumunuCiz();
    durum.textContent = sonuc.mesaj ?? "Gönderilemedi.";
  };

  return form;
}

function paketiCiz() {
  const durum = uyelikDurumu(uye, Date.now());
  $("paket-kutulari").replaceChildren(...kutular([
    ["Durum", durumEtiketi(durum)],
    ["Üyelik bitişi", tarihYaz(uye.end_date_ms)],
    ["Kalan seans", uye.remaining_sessions ?? "Sınırsız"],
    ["Ödenen", tutarYaz(uye.price_paid_minor)],
  ]));
}

function kutular(ciftler) {
  return ciftler.map(([baslik, deger]) => {
    const k = document.createElement("div");
    k.className = "kutu";
    const b = document.createElement("p");
    b.className = "alt";
    b.textContent = baslik;
    const d = document.createElement("strong");
    d.textContent = String(deger);
    k.append(b, d);
    return k;
  });
}

async function olcumleriCiz() {
  const sonuc = await oku("measurements?select=*&order=date_ms.desc&limit=24");
  const kap = $("olcumler");

  if (sonuc.tur !== "tamam") {
    kap.textContent = "Ölçümler okunamadı.";
    return;
  }
  const satirlar = (sonuc.satirlar ?? []).filter((s) => !s.deleted_at_ms);
  if (satirlar.length === 0) {
    kap.innerHTML = `<p class="alt">Henüz ölçüm kaydı yok.</p>`;
    return;
  }

  kap.replaceChildren(tablo(
    ["Tarih", "Kilo", "Boy", "Göğüs", "Bel", "Kalça", "Kol", "Bacak"],
    satirlar.map((o) => [
      tarihYaz(o.date_ms), o.weight, o.height, o.chest, o.waist, o.hips, o.arm, o.leg,
    ]),
  ));
}

function tablo(basliklar, satirlar) {
  const t = document.createElement("table");
  const ust = document.createElement("thead");
  const us = document.createElement("tr");
  for (const b of basliklar) {
    const th = document.createElement("th");
    th.textContent = b;
    us.appendChild(th);
  }
  ust.appendChild(us);
  t.appendChild(ust);

  const g = document.createElement("tbody");
  for (const hucreler of satirlar) {
    const tr = document.createElement("tr");
    for (const h of hucreler) {
      const td = document.createElement("td");
      // `textContent`: değerler sunucudan geliyor, HTML olarak yorumlanmamalı.
      td.textContent = h ?? "—";
      tr.appendChild(td);
    }
    g.appendChild(tr);
  }
  t.appendChild(g);

  const sar = document.createElement("div");
  sar.className = "tablo-sar";
  sar.appendChild(t);
  return sar;
}

// ─── Sağlık beyanı ──────────────────────────────────────────────────────────

const BEYAN_ALANLARI = [
  ["conditions", "Rahatsızlıklar", "Örn. tansiyon, astım, diyabet"],
  ["medications", "Kullandığınız ilaçlar", "Düzenli kullandıklarınız"],
  ["injuries", "Sakatlıklar", "Geçmiş ya da güncel sakatlıklar"],
  ["note", "Eklemek istedikleriniz", ""],
];

function saglikFormunuCiz() {
  const form = $("saglik-formu");
  form.replaceChildren();

  const alanlar = {};
  for (const [ad, etiket, ipucu] of BEYAN_ALANLARI) {
    const l = document.createElement("label");
    l.textContent = etiket;
    const i = document.createElement("textarea");
    i.rows = 2;
    if (ipucu) i.placeholder = ipucu;
    l.appendChild(i);
    form.appendChild(l);
    alanlar[ad] = i;
  }

  // KVKK: sağlık verisi özel nitelikli kişisel veri ve açık rıza gerektiriyor.
  // Onay kutusu işaretlenmeden gönderim yapılmıyor; rıza zamanı da kayda
  // yazılıyor (`consent_at_ms`), çünkü hangi beyanın hangi rızayla verildiği
  // sonradan belirsizleşmemeli.
  const rizaL = document.createElement("label");
  rizaL.className = "riza";
  const riza = document.createElement("input");
  riza.type = "checkbox";
  rizaL.append(riza, document.createTextNode(
    " Sağlık bilgilerimin antrenman programımın güvenli şekilde planlanması " +
    "amacıyla salon tarafından işlenmesine açık rıza veriyorum.",
  ));
  form.appendChild(rizaL);

  const dugme = document.createElement("button");
  dugme.type = "submit";
  dugme.className = "dugme dugme-birincil";
  dugme.textContent = "Gönder";
  form.appendChild(dugme);

  const durum = document.createElement("p");
  durum.className = "alt";
  form.appendChild(durum);

  form.onsubmit = async (olay) => {
    olay.preventDefault();
    if (dugme.disabled) return;

    if (!riza.checked) {
      durum.textContent = "Göndermek için açık rıza onayı gerekiyor.";
      return;
    }
    if (!BEYAN_ALANLARI.some(([ad]) => alanlar[ad].value.trim())) {
      durum.textContent = "En az bir alan doldurun.";
      return;
    }

    dugme.disabled = true;
    durum.textContent = "Gönderiliyor…";

    const o = oturumOku();
    const simdi = Date.now();
    const yanit = await fetch(`${ayar.url}/rest/v1/member_health_updates`, {
      method: "POST",
      headers: {
        apikey: ayar.anonKey,
        Authorization: `Bearer ${o.access_token}`,
        "Content-Type": "application/json",
        Prefer: "return=minimal",
      },
      body: JSON.stringify({
        id: `beyan-${simdi}-${Math.random().toString(36).slice(2, 8)}`,
        tenant_id: uye.tenant_id,
        member_id: uye.id,
        reported_at_ms: simdi,
        conditions: alanlar.conditions.value.trim() || null,
        medications: alanlar.medications.value.trim() || null,
        injuries: alanlar.injuries.value.trim() || null,
        note: alanlar.note.value.trim() || null,
        consent_at_ms: simdi,
        created_at_ms: simdi,
      }),
    }).catch(() => null);

    dugme.disabled = false;

    if (yanit && yanit.ok) {
      durum.textContent = "Gönderildi. Eğitmeniniz görebilecek.";
      saglikFormunuCiz();
      beyanlariCiz();
      return;
    }
    durum.textContent = yanit ? `Gönderilemedi (${yanit.status}).` : "Sunucuya ulaşılamadı.";
  };
}

/**
 * Geçmiş beyanlar.
 *
 * Tablo yalnızca eklenen: üye dün bildirdiğini silemiyor, düzeltme yeni kayıtla
 * yapılıyor. Geçmişi göstermek bunu görünür kılıyor — aksi hâlde kullanıcı
 * bildirimlerinin nereye gittiğini bilemezdi.
 */
async function beyanlariCiz() {
  const sonuc = await oku("member_health_updates?select=*&order=reported_at_ms.desc&limit=12");
  const kap = $("beyan-gecmisi");
  if (sonuc.tur !== "tamam") return kap.replaceChildren();

  const satirlar = sonuc.satirlar ?? [];
  if (satirlar.length === 0) return kap.replaceChildren();

  const baslik = document.createElement("h3");
  baslik.textContent = "Önceki bildirimleriniz";
  kap.replaceChildren(baslik, tablo(
    ["Tarih", "Rahatsızlıklar", "İlaçlar", "Sakatlıklar", "Not"],
    satirlar.map((b) => [
      tarihYaz(b.reported_at_ms), b.conditions, b.medications, b.injuries, b.note,
    ]),
  ));
}

// ─── Açılış ─────────────────────────────────────────────────────────────────

if (!ayar?.url || !ayar?.anonKey) {
  goster("ayar-eksik");
} else if (oturumOku()) {
  panoyuAc();
} else {
  goster("giris");
}
