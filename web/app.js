// Panelin akışı: giriş, sekmeler, listeler.
//
// Panel **salt okunur**. Yazma bilinçli olarak yok: uygulamadaki her yazma yolu
// aynı transaction içinde gönderim kuyruğuna kayıt bırakıyor ve iş kuralları
// (hakediş, seans düşme, defter kaydı) ortak Kotlin modülünde. Panelden yazmak,
// o kuralların ikinci bir kopyasını burada tutmak demek olurdu — ve iki kopya
// er geç birbirinden sapardı.

import { SupabaseClient } from "./supabase.js";
import { tutarYaz, tarihYaz, uyelikDurumu, durumEtiketi, silinmemisler } from "./domain.js";

const $ = (id) => document.getElementById(id);

const ayar = window.NONSTOP_CONFIG;
const istemci = new SupabaseClient(ayar?.url, ayar?.anonKey);

let aktifSekme = "uyeler";

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
  goster("panel");
  sekmeYukle(aktifSekme);
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

const SEKMELER = {
  uyeler: { tablo: "gym_members", order: "full_name.asc", ciz: uyeleriCiz },
  paketler: { tablo: "gym_packages", order: "name.asc", ciz: paketleriCiz },
  randevular: { tablo: "appointments", order: "start_time_ms.desc", ciz: randevulariCiz },
  finans: { tablo: "ledger_entries", order: "occurred_at_ms.desc", ciz: finansiCiz },
};

async function sekmeYukle(ad) {
  const tanim = SEKMELER[ad];
  $("icerik").innerHTML = "";
  hataYaz("panel-hata", null);
  $("yukleniyor").hidden = false;

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

  const satirlar = silinmemisler(sonuc.satirlar);
  if (satirlar.length === 0) {
    $("icerik").innerHTML = `<p class="alt">Kayıt yok.</p>`;
    return;
  }
  $("icerik").appendChild(tanim.ciz(satirlar));
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
