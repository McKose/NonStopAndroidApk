// ---------------------------------------------------------------------------
// İletişim / deneme dersi isteği
// ---------------------------------------------------------------------------
// Siteden gelen formun tek yazma yolu. `contact_requests` tablosuna
// `service_role` ile yazıyor.
//
// ### Neden sunucu tarafında — tabloya `anon` yazma kuralı yazmak yerine
//
// Gerekçenin tamamı `0010`un başında; özeti: `anon` YAYINLANMIŞ anahtarla
// çalışıyor (anahtar uygulamanın içinde ve panelin kaynağında), yani tabloya
// `insert` açmak "internetteki herkes istediği hızda satır yazabilir" demek.
// Sızacak veri yok ama bir betik salonun kotasını tüketip gerçek talepleri
// gömebilir. Üstelik tablo kuralı HIZ SINIRI KOYAMIYOR: `with check` yalnız
// satıra bakıyor, "son bir saatte kaç satır geldi" sorusunu soramıyor.
//
// Yani bu fonksiyonun var oluş sebebi yalnız yetki değil, ÜÇ KONTROL:
// doğrulama, mükerrer bastırma ve hız sınırı.
//
// ### ÇAĞIRAN KİŞİNİN HESABI YOK — `personel-*` uçlarından ayrıldığı yer
//
// Öteki iki fonksiyon çağıranın kimliğini `/auth/v1/user` ile doğruluyor ve
// yetkisine bakıyor. Burada öyle bir kimlik yok: formu dolduran ziyaretçi.
// Supabase uçları yine de çıplak değil — `verify_jwt` varsayılan olarak açık
// ve `anon` anahtarı geçerli bir JWT olduğu için istek o anahtarla geliyor.
// Bu kaba bir kapı: anahtarsız çağrıyı eler, ama anahtar herkesin elinde
// olduğundan HİÇBİR koruma sayılmaz. Korumayı aşağıdaki üç kontrol sağlıyor.
//
// ### Mükerrer ve hız sınırında yanıt neden "başarılı" olabiliyor
//
// Aynı numaradan 10 dakika içinde ikinci istek gelirse yeni satır
// YAZILMIYOR ama istemciye BAŞARILI dönüyor. Sebebi somut: insanlar
// gönder düğmesine iki kez basıyor ve "zaten gönderdiniz" demek, gönderip
// göndermediğinden emin olmayan birini telefona sarılmaktan alıkoyar.
// Panelde de aynı talebin iki kopyası, personelin aynı kişiyi iki kez
// aramasına yol açardı.
//
// ### Hız sınırının bedeli bilinçli olarak kabul edildi
//
// Sınır SALON BAZINDA saatte 20 istek. Gerçek bir salon günde bir elin
// parmakları kadar talep alıyor, yani 20 fazlasıyla geniş. Ama bir saldırgan
// sınırı doldurursa o saat içindeki GERÇEK talepler de reddedilir — bu,
// sınırsız yazmaya izin vermenin alternatifi ve daha az kötü olanı.
//
// Bedeli yumuşatan şey, reddin ÇIKIŞSIZ OLMAMASI: yanıt ziyaretçiyi
// WhatsApp'a ve telefona yönlendiriyor, ikisi de sayfada duruyor. Yani
// saldırı altında bile salona ulaşmanın yolu kapanmıyor, yalnız form
// kapanıyor.
// ---------------------------------------------------------------------------

// Bu dosyayı MODÜL yapıyor; gerekçesi `personel-davet/index.ts`te yazılı.
// Kısaca: `import`/`export` içermeyen bir dosyayı TypeScript global betik
// sayıyor ve denetim bütün fonksiyonları tek `tsc` çağrısına verdiği için iki
// dosyanın tepe seviye adları çakışıyor.
export {};

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

/**
 * Tarayıcıdan çağrılabilecek adresler.
 *
 * `*` DEĞİL ve liste öteki iki fonksiyonla aynı ortam değişkeninden okunuyor —
 * ayrışsalar sitenin bir düğmesi çalışır, diğeri CORS'ta sessizce dururdu.
 */
const IZINLI_KAYNAKLAR = new Set(
  (Deno.env.get("IZINLI_KAYNAKLAR") ??
    "https://nonstopstudio.tr,https://www.nonstopstudio.tr,https://mckose.github.io")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean),
);

/** Salon bazında saatlik üst sınır. Gerekçesi dosyanın başında. */
const SAATLIK_SINIR = 20;

/** Aynı numaradan tekrar: bu süre içinde yeni satır yazılmıyor. */
const MUKERRER_PENCERE_MS = 10 * 60 * 1000;

const UUID_DESENI =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Kabul edilen TEK telefon biçimi.
 *
 * Normalleştirme burada DEĞİL: insanın yazdığı biçimleri (`0541…`, `+90 541…`)
 * bu hâle çeviren yer `web/iletisim-formu.js` ve orada Node testleriyle
 * sınanıyor. Sebebi, Edge Function'lar için bu depoda test koşucusu
 * olmaması — en ince kuralı (baştaki sıfır) testsiz bırakmak olurdu.
 *
 * Buradaki iş farklı ve basit: formu atlayıp doğrudan bu uca istek atan biri
 * kanonik olmayan bir numara gönderirse reddetmek. Yani yetkili taraf hâlâ
 * sunucu.
 */
const KANONIK_TELEFON = /^\+90[0-9]{10}$/;

// Tablodaki `check` kısıtlarıyla AYNI sayılar (bkz. `0010`). Burada da
// doğrulanıyorlar ki istemciye anlaşılır bir mesaj dönsün; tablo son durak.
const AD_EN_AZ = 2;
const AD_EN_COK = 80;
const EPOSTA_EN_COK = 120;
const MESAJ_EN_COK = 500;

function korumaBasliklari(kaynak: string | null): Record<string, string> {
  const basliklar: Record<string, string> = {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
  };
  if (kaynak && IZINLI_KAYNAKLAR.has(kaynak)) {
    basliklar["Access-Control-Allow-Origin"] = kaynak;
    basliklar["Vary"] = "Origin";
    basliklar["Access-Control-Allow-Headers"] = "authorization, content-type";
    basliklar["Access-Control-Allow-Methods"] = "POST, OPTIONS";
    basliklar["Access-Control-Max-Age"] = "3600";
  }
  return basliklar;
}

function yanit(gövde: unknown, durum: number, kaynak: string | null): Response {
  return new Response(JSON.stringify(gövde), {
    status: durum,
    headers: korumaBasliklari(kaynak),
  });
}

function hata(mesaj: string, durum: number, kaynak: string | null): Response {
  return yanit({ hata: mesaj }, durum, kaynak);
}

/** PostgREST çağrısı — `service_role` ile. Anahtar istemciye hiç gitmiyor. */
async function sunucu(
  yol: string,
  secenek: RequestInit = {},
): Promise<Response> {
  return await fetch(`${SUPABASE_URL}${yol}`, {
    ...secenek,
    headers: {
      apikey: SERVICE_KEY,
      Authorization: `Bearer ${SERVICE_KEY}`,
      "Content-Type": "application/json",
      ...(secenek.headers ?? {}),
    },
  });
}

/** Gövdeden kırpılmış dize ya da `null`. */
function metin(deger: unknown): string | null {
  if (typeof deger !== "string") return null;
  const k = deger.trim();
  return k === "" ? null : k;
}

Deno.serve(async (istek: Request): Promise<Response> => {
  const kaynak = istek.headers.get("Origin");

  if (istek.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: korumaBasliklari(kaynak) });
  }
  if (istek.method !== "POST") {
    return hata("Yalnızca POST", 405, kaynak);
  }
  if (!SUPABASE_URL || !SERVICE_KEY) {
    console.error("Ortam değişkenleri eksik.");
    return hata("Sunucu yapılandırması eksik.", 500, kaynak);
  }

  let govde: Record<string, unknown>;
  try {
    govde = await istek.json();
  } catch {
    return hata("Geçersiz istek gövdesi.", 400, kaynak);
  }

  // ── Tuzak ────────────────────────────────────────────────────────────────
  // En başta, her şeyden önce: tuzağa düşen bir istek için veritabanına hiç
  // dokunulmuyor. BAŞARILI dönülüyor — bota "başarısız" demek, denemeye devam
  // etmesi için bilgi vermek olurdu.
  if (metin(govde.tuzak) !== null) {
    return yanit({ tamam: true }, 200, kaynak);
  }

  // ── Girdi ────────────────────────────────────────────────────────────────
  const tenantId = String(govde.tenantId ?? "").trim();
  if (!UUID_DESENI.test(tenantId)) {
    return hata("Salon kimliği geçersiz.", 400, kaynak);
  }

  const adSoyad = metin(govde.adSoyad);
  if (!adSoyad || adSoyad.length < AD_EN_AZ || adSoyad.length > AD_EN_COK) {
    return hata("Adınızı kontrol edin.", 400, kaynak);
  }

  const telefon = metin(govde.telefon);
  if (!telefon || !KANONIK_TELEFON.test(telefon)) {
    return hata("Telefon numarası geçersiz.", 400, kaynak);
  }

  const eposta = metin(govde.eposta);
  if (eposta && eposta.length > EPOSTA_EN_COK) {
    return hata("E-posta adresi çok uzun.", 400, kaynak);
  }

  const mesaj = metin(govde.mesaj);
  if (mesaj && mesaj.length > MESAJ_EN_COK) {
    return hata("Mesaj çok uzun.", 400, kaynak);
  }

  const simdi = Date.now();

  // ── Hız sınırı ───────────────────────────────────────────────────────────
  // Sayım salon bazında ve son bir saat için. `Prefer: count=exact` ile satır
  // gövdesi hiç çekilmiyor; yalnız `Content-Range` başlığındaki sayı okunuyor.
  // Satırlar çekilseydi saldırı altında fonksiyon kendi kendini yavaşlatırdı.
  const saatOnce = simdi - 60 * 60 * 1000;
  const sayimYaniti = await sunucu(
    `/rest/v1/contact_requests?tenant_id=eq.${encodeURIComponent(tenantId)}` +
      `&created_at_ms=gte.${saatOnce}&select=id`,
    { headers: { Prefer: "count=exact", Range: "0-0" } },
  );
  if (!sayimYaniti.ok) {
    console.error("İstek sayımı okunamadı:", sayimYaniti.status);
    return hata("Şu anda işleme alınamadı, lütfen tekrar deneyin.", 500, kaynak);
  }
  // `Content-Range` biçimi: `0-0/12` ya da satır yoksa `*/0`.
  const aralik = sayimYaniti.headers.get("Content-Range") ?? "";
  const toplam = Number.parseInt(aralik.split("/")[1] ?? "", 10);
  if (Number.isFinite(toplam) && toplam >= SAATLIK_SINIR) {
    // 429 ve ÇIKIŞ GÖSTEREN mesaj: ikisi de sayfada duran kanallar.
    return hata(
      "Şu anda çok fazla istek alıyoruz. WhatsApp'tan ya da telefonla " +
        "doğrudan ulaşabilirsiniz.",
      429,
      kaynak,
    );
  }

  // ── Mükerrer bastırma ────────────────────────────────────────────────────
  // Aynı salonda aynı numaradan son 10 dakikada istek varsa yeni satır
  // yazılmıyor ve BAŞARILI dönülüyor (gerekçe dosyanın başında).
  const pencere = simdi - MUKERRER_PENCERE_MS;
  const mukerrerYaniti = await sunucu(
    `/rest/v1/contact_requests?tenant_id=eq.${encodeURIComponent(tenantId)}` +
      `&phone=eq.${encodeURIComponent(telefon)}` +
      `&created_at_ms=gte.${pencere}&select=id&limit=1`,
  );
  if (!mukerrerYaniti.ok) {
    console.error("Mükerrer kontrolü okunamadı:", mukerrerYaniti.status);
    return hata("Şu anda işleme alınamadı, lütfen tekrar deneyin.", 500, kaynak);
  }
  const mukerrer = await mukerrerYaniti.json();
  if (Array.isArray(mukerrer) && mukerrer.length > 0) {
    return yanit({ tamam: true, mukerrer: true }, 200, kaynak);
  }

  // ── Yazma ────────────────────────────────────────────────────────────────
  const yazmaYaniti = await sunucu("/rest/v1/contact_requests", {
    method: "POST",
    headers: { Prefer: "return=minimal" },
    body: JSON.stringify({
      tenant_id: tenantId,
      full_name: adSoyad,
      phone: telefon,
      email: eposta,
      message: mesaj,
      source: "WEB",
      created_at_ms: simdi,
      updated_at_ms: simdi,
    }),
  });

  if (!yazmaYaniti.ok) {
    const ayrinti = await yazmaYaniti.text();
    // Ayrıntı GÜNLÜĞE, istemciye değil: PostgREST hata gövdesi kolon ve kısıt
    // adlarını sızdırıyor.
    console.error("İstek yazılamadı:", yazmaYaniti.status, ayrinti);
    // Yabancı anahtar ihlali = salon kimliği gerçek bir salona işaret
    // etmiyor. Ayrı mesaj veriliyor çünkü bu bir YAPILANDIRMA hatası
    // (`SUPABASE_TENANT_ID` yanlış) ve ziyaretçinin yazdığıyla ilgisi yok.
    if (yazmaYaniti.status === 409 || ayrinti.includes("foreign key")) {
      return hata("Salon kimliği tanınmıyor.", 400, kaynak);
    }
    return hata("Şu anda işleme alınamadı, lütfen tekrar deneyin.", 500, kaynak);
  }

  return yanit({ tamam: true }, 201, kaynak);
});
