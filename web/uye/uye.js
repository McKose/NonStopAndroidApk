// Üye alanı: giriş, paket durumu, ölçümler, sağlık beyanı.
//
// ### Panelden ayrı bir oturum
// Oturum `nonstop.uye.session` altında saklanıyor, panelin `nonstop.session`
// anahtarının altında değil. İkisi aynı anahtarı paylaşsaydı aynı tarayıcıda
// üye girişi yapmak panelin oturumunu ezerdi (ve tersi): panel üyenin jetonuyla
// açılmaya çalışır, "salonsuz" der ve kullanıcı sebebini anlamazdı.
//
// ### Neden panelin istemcisi kullanılmıyor
// `panel/supabase.js` girişten sonra salonu `gym_users` üzerinden çözüyor —
// personel için doğru, üye için yanlış: üyenin `gym_users` satırı YOK ve
// olmamalı (bkz. migrasyon 0005). Üyenin kimliği `member_accounts` üzerinden
// çözülüyor.
//
// Gösterim yardımcıları paylaşılıyor: tutar ve tarih biçimlendirmesinin iki
// kopyası olsaydı aynı veri iki yüzeyde farklı görünürdü.

import { tutarYaz, tarihYaz, uyelikDurumu, durumEtiketi } from "../panel/domain.js";

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

function goster(bolum) {
  for (const id of ["giris", "pano", "ayar-eksik", "baglanmamis"]) {
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
    return goster("baglanmamis");
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
