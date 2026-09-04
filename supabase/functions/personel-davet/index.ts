// ---------------------------------------------------------------------------
// Personel daveti
// ---------------------------------------------------------------------------
// Bir personele uygulama erişimi verir: Auth hesabını açar, salon yetkisini
// (`gym_users`) yazar ve personel kaydına bağlar.
//
// ### Neden sunucu tarafında
// `gym_users` bütün erişim kurallarının dayanağı. İstemciye yazma açılsaydı,
// yazabilen kişi kendi yetkisini de yükseltebilirdi. Bu yüzden tabloya hiç
// yazma kuralı yok (bkz. migrasyon 0007) ve yazma işini yalnızca burası
// yapıyor: `service_role` anahtarı sunucuda kalıyor, istemciye hiç gitmiyor.
//
// ### Neden SDK yok, düz `fetch` var
// Tek bir `import` yok. Gerekçe: bir SDK sürümü seçmek, o sürümün imzalarına
// bağlanmak demek ve bu fonksiyon yılda bir kez dokunulacak bir yer. Altındaki
// HTTP uçları (GoTrue admin API ve PostgREST) SDK'dan daha yavaş değişiyor.
// Bağımlılık olmayınca sürüm sabitleme, kilit dosyası ve tedarik zinciri
// yüzeyi de olmuyor.
//
// ### Yetkiyi KİM doğruluyor
// `service_role` her şeyi yapabildiği için yetkiyi bu dosya doğrulamak
// zorunda; veritabanı kuralları burada koruma sağlamıyor. İki kontrol var ve
// ikisi de atlanamaz:
//
//   1. Çağıran, HEDEF SALONDA `ADMIN` mi? (başka salonda ADMIN olması yetmez)
//   2. Davet edilen personel kaydı GERÇEKTEN o salona mı ait?
//
// İkincisi olmasaydı bir salonun yöneticisi, başka salonun personel satırına
// kendi seçtiği bir hesabı bağlayabilirdi.
//
// ### Geçici şifre, e-posta değil
// Davet e-postayla gönderilmiyor: e-posta göndermek SMTP kurulumu gerektiriyor
// ve Supabase'in yerleşik göndericisi üretim için ciddi biçimde sınırlı
// (saatte birkaç ileti). Bunun yerine geçici bir şifre üretiliyor ve
// yöneticiye BİR KEZ gösteriliyor; yönetici şifreyi personele yüz yüze
// veriyor. Küçük bir salonda bu hem daha hızlı hem de dış bağımlılığı yok.
//
// Personel giriş yaptıktan sonra şifresini kendisi değiştiriyor: uygulamada
// Ayarlar → "Şifre Değiştir", panelde sağ üstteki "Şifre değiştir". İkisi de
// mevcut şifreyi soruyor. O ekranlar olmadan geçici şifre KALICI hâle
// geliyordu — yönetici tarafından bilinen bir şifreyle çalışılıyordu.
//
// ### Tekrar çalıştırılabilir
// Yarıda kalan bir davet (ağ hatası, kapanan sekme) aynı verilerle tekrar
// denendiğinde tamamlanıyor: var olan hesap `auth_kullanici_id` ile bulunuyor,
// yetki satırı `merge-duplicates` ile üzerine yazılıyor. Bu olmasaydı yarım
// kalan personel KALICI olarak yarım kalırdı ve düzeltmek yine Supabase
// paneli gerektirirdi — yani bu fonksiyonun kapatmak için var olduğu duruma
// geri dönerdi.
// ---------------------------------------------------------------------------

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

/**
 * Tarayıcıdan çağrılabilecek adresler.
 *
 * `*` DEĞİL ve bilinçli: bu uç nokta yetki veriyor. `*` olsaydı herhangi bir
 * sitedeki betik, o siteyi açmış bir yöneticinin tarayıcısı üzerinden davet
 * göndermeye çalışabilirdi. Tarayıcı `Authorization` başlığını kendiliğinden
 * eklemediği için saldırı yine de kolay değil, ama listeyi dar tutmak bedava.
 *
 * Ortam değişkeniyle genişletilebiliyor: adres değişirse fonksiyonu yeniden
 * yayınlamak gerekmesin.
 */
const IZINLI_KAYNAKLAR = new Set(
  (Deno.env.get("IZINLI_KAYNAKLAR") ??
    "https://nonstopstudio.tr,https://www.nonstopstudio.tr,https://mckose.github.io")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean),
);

const ROLLER = new Set(["ADMIN", "MANAGER", "TRAINER"]);

/** Belirsiz karakterler (0/O, 1/l/I) yok: şifre elle okunup elle yazılıyor. */
const SIFRE_ALFABE = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

/**
 * Geçici şifre.
 *
 * `crypto.getRandomValues` kullanılıyor, `Math.random` değil: ikincisi
 * kriptografik değil ve üretilen şifre bir hesabın tek anahtarı.
 *
 * Modulo sapması alfabe 57 karakter olduğu için ihmal edilebilir düzeyde;
 * yine de reddetme örneklemesiyle tamamen kaldırılıyor — 256'nın alfabe
 * uzunluğuna tam bölünmeyen artığı atılıyor.
 */
function geciciSifre(uzunluk = 14): string {
  const n = SIFRE_ALFABE.length;
  const sinir = Math.floor(256 / n) * n;
  const cikti: string[] = [];
  const tampon = new Uint8Array(uzunluk * 2);

  while (cikti.length < uzunluk) {
    crypto.getRandomValues(tampon);
    for (const b of tampon) {
      if (b >= sinir) continue;
      cikti.push(SIFRE_ALFABE[b % n]);
      if (cikti.length === uzunluk) break;
    }
  }
  return cikti.join("");
}

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

const UUID_DESENI =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Kabaca doğru mu: tek `@`, iki yanı dolu, boşluk yok. Kesin doğrulama Auth'ta. */
function epostaGecerli(e: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e);
}

/** PostgREST / GoTrue çağrısı — `service_role` ile. */
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

Deno.serve(async (istek: Request): Promise<Response> => {
  const kaynak = istek.headers.get("Origin");

  if (istek.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: korumaBasliklari(kaynak) });
  }
  if (istek.method !== "POST") {
    return hata("Yalnızca POST", 405, kaynak);
  }
  if (!SUPABASE_URL || !SERVICE_KEY || !ANON_KEY) {
    // Yapılandırma hatası; ayrıntı istemciye yazılmıyor.
    console.error("Ortam değişkenleri eksik.");
    return hata("Sunucu yapılandırması eksik.", 500, kaynak);
  }

  // ── Çağıranın kimliği ────────────────────────────────────────────────────
  const yetkiBasligi = istek.headers.get("Authorization") ?? "";
  const jeton = yetkiBasligi.startsWith("Bearer ")
    ? yetkiBasligi.slice("Bearer ".length)
    : "";
  if (!jeton) return hata("Giriş yapmalısınız.", 401, kaynak);

  const kimlikYaniti = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: ANON_KEY, Authorization: `Bearer ${jeton}` },
  });
  if (!kimlikYaniti.ok) return hata("Oturum geçersiz, yeniden giriş yapın.", 401, kaynak);
  const cagiran = await kimlikYaniti.json();
  if (!cagiran?.id) return hata("Oturum geçersiz, yeniden giriş yapın.", 401, kaynak);

  // ── Girdi ────────────────────────────────────────────────────────────────
  let govde: Record<string, unknown>;
  try {
    govde = await istek.json();
  } catch {
    return hata("Geçersiz istek gövdesi.", 400, kaynak);
  }

  const gymId = String(govde.gym_id ?? "").trim();
  const staffId = String(govde.staff_id ?? "").trim();
  const eposta = String(govde.email ?? "").trim().toLowerCase();
  const rol = String(govde.role ?? "").trim().toUpperCase();

  if (!UUID_DESENI.test(gymId)) return hata("Salon kimliği geçersiz.", 400, kaynak);
  if (!staffId) return hata("Personel kaydı seçilmedi.", 400, kaynak);
  if (!epostaGecerli(eposta)) return hata("E-posta adresi geçersiz.", 400, kaynak);
  if (!ROLLER.has(rol)) return hata("Yetki geçersiz.", 400, kaynak);

  // ── 1) Çağıran BU salonda ADMIN mi ───────────────────────────────────────
  // Başka salonda ADMIN olmak yetmiyor: sorgu iki alanı da eşliyor.
  //
  // Değerler `encodeURIComponent`ten geçiyor. `gymId` zaten uuid desenine
  // uyduğu doğrulandı ve `cagiran.id` Supabase'in kendi yanıtından geliyor —
  // yani ikisi de bugün zararsız. Yine de kodlanıyorlar: bu satırlar bir gün
  // başka bir alanla kopyalanırsa, kodlamanın orada da olması gerekiyor ve
  // "burada gerekmiyordu" istisnası tam olarak böyle unutuluyor.
  const yetkiYaniti = await sunucu(
    `/rest/v1/gym_users?user_id=eq.${encodeURIComponent(cagiran.id)}` +
      `&gym_id=eq.${encodeURIComponent(gymId)}&select=role`,
  );
  if (!yetkiYaniti.ok) {
    console.error("gym_users okunamadı:", yetkiYaniti.status);
    return hata("Yetki kontrol edilemedi.", 500, kaynak);
  }
  const yetkiler = await yetkiYaniti.json();
  if (!Array.isArray(yetkiler) || yetkiler[0]?.role !== "ADMIN") {
    return hata("Bu işlem için salon yöneticisi olmalısınız.", 403, kaynak);
  }

  // ── 2) Personel kaydı bu salona mı ait ───────────────────────────────────
  // `tenant_id` eşlemesi olmadan bir yönetici, başka salonun personel satırına
  // hesap bağlayabilirdi.
  const personelYaniti = await sunucu(
    `/rest/v1/staff?id=eq.${encodeURIComponent(staffId)}` +
      `&tenant_id=eq.${encodeURIComponent(gymId)}&select=id,full_name,auth_user_id`,
  );
  if (!personelYaniti.ok) {
    console.error("staff okunamadı:", personelYaniti.status);
    return hata("Personel kaydı okunamadı.", 500, kaynak);
  }
  const personeller = await personelYaniti.json();
  const personel = Array.isArray(personeller) ? personeller[0] : null;
  if (!personel) return hata("Personel kaydı bu salonda bulunamadı.", 404, kaynak);

  // ── 3) Bu e-postanın hesabı zaten var mı ─────────────────────────────────
  // ÇAĞIRANIN jetonuyla soruluyor, `service_role` ile değil: fonksiyonun
  // yetki kontrolü `auth.uid()`ye dayanıyor ve `service_role` ile çağrılsaydı
  // `auth.uid()` null olur, fonksiyon her zaman null döndürürdü.
  const aramaYaniti = await fetch(`${SUPABASE_URL}/rest/v1/rpc/auth_kullanici_id`, {
    method: "POST",
    headers: {
      apikey: ANON_KEY,
      Authorization: `Bearer ${jeton}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ p_email: eposta }),
  });
  if (!aramaYaniti.ok) {
    console.error("auth_kullanici_id çağrılamadı:", aramaYaniti.status);
    return hata("Hesap sorgulanamadı.", 500, kaynak);
  }
  const mevcutId: string | null = await aramaYaniti.json();

  // ── 4) Hesap: bul ya da aç ───────────────────────────────────────────────
  let kullaniciId = mevcutId;
  let sifre: string | null = null;

  if (!kullaniciId) {
    sifre = geciciSifre();
    const acmaYaniti = await sunucu("/auth/v1/admin/users", {
      method: "POST",
      body: JSON.stringify({
        email: eposta,
        password: sifre,
        // Doğrulama e-postası gönderilmiyor (SMTP yok); hesap doğrudan
        // kullanılabilir olmalı, yoksa personel giriş yapamaz.
        email_confirm: true,
      }),
    });

    if (!acmaYaniti.ok) {
      const ayrinti = await acmaYaniti.text();
      console.error("Hesap açılamadı:", acmaYaniti.status, ayrinti);
      // 422: e-posta zaten kayıtlı. Buraya düşmek, `auth_kullanici_id`nin
      // hesabı bulamadığı ama Auth'un var dediği anlamına geliyor — pratikte
      // aynı e-postayı taşıyan birden çok hesap. Yöneticiye bunu söylemek,
      // "bilinmeyen hata" demekten iyi.
      if (acmaYaniti.status === 422) {
        return hata(
          "Bu e-posta ile zaten bir hesap var ama tek bir hesaba " +
            "eşlenemedi. Farklı bir e-posta kullanın ya da yardım isteyin.",
          409,
          kaynak,
        );
      }
      return hata("Hesap açılamadı.", 502, kaynak);
    }

    const acilan = await acmaYaniti.json();
    kullaniciId = acilan?.id ?? null;
    if (!kullaniciId) {
      console.error("Hesap açıldı ama kimlik dönmedi.");
      return hata("Hesap açıldı ama kimlik alınamadı.", 502, kaynak);
    }
  }

  // ── 5) Personel kaydı başka bir hesaba bağlı olmamalı ────────────────────
  if (personel.auth_user_id && personel.auth_user_id !== kullaniciId) {
    return hata(
      "Bu personel kaydı başka bir hesaba bağlı. Önce mevcut bağlantıyı kaldırın.",
      409,
      kaynak,
    );
  }

  // ── 6) Personel kaydını hesaba bağla ─────────────────────────────────────
  // Yetkiden ÖNCE yapılıyor. İkisinden biri yarıda kalacaksa, "giriş yapabilen
  // ama profili yarım" bir kullanıcıdan çok "henüz giriş yapamayan" biri
  // tercih edilir: ikincisi görünür ve zararsız, birincisi sessiz ve kafa
  // karıştırıcı.
  const baglamaYaniti = await sunucu(
    `/rest/v1/staff?id=eq.${encodeURIComponent(staffId)}` +
      `&tenant_id=eq.${encodeURIComponent(gymId)}`,
    {
      method: "PATCH",
      headers: { Prefer: "return=minimal" },
      body: JSON.stringify({ auth_user_id: kullaniciId }),
    },
  );
  if (!baglamaYaniti.ok) {
    const ayrinti = await baglamaYaniti.text();
    console.error("staff bağlanamadı:", baglamaYaniti.status, ayrinti);
    // Tekil indeks: bu hesap başka bir personel satırına bağlı.
    if (baglamaYaniti.status === 409) {
      return hata(
        "Bu hesap başka bir personel kaydına bağlı. Bir hesap yalnızca bir personele bağlanabilir.",
        409,
        kaynak,
      );
    }
    return hata("Personel kaydı hesaba bağlanamadı.", 502, kaynak);
  }

  // ── 7) Salon yetkisini yaz ───────────────────────────────────────────────
  // `merge-duplicates`: aynı kişi yeniden davet edilirse rolü güncelleniyor,
  // "already exists" ile düşmüyor. Tekrar çalıştırılabilirliğin çekirdeği bu.
  const yetkiYazma = await sunucu("/rest/v1/gym_users", {
    method: "POST",
    headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
    body: JSON.stringify({ user_id: kullaniciId, gym_id: gymId, role: rol }),
  });
  if (!yetkiYazma.ok) {
    const ayrinti = await yetkiYazma.text();
    console.error("gym_users yazılamadı:", yetkiYazma.status, ayrinti);
    return hata("Salon yetkisi verilemedi.", 502, kaynak);
  }

  // Geçici şifre YALNIZCA burada, yalnızca bir kez dönüyor; hiçbir yere
  // kaydedilmiyor ve günlüğe yazılmıyor.
  return yanit(
    {
      durum: sifre ? "hesap_acildi" : "mevcut_hesap_baglandi",
      personel: personel.full_name,
      eposta,
      yetki: rol,
      gecici_sifre: sifre,
    },
    200,
    kaynak,
  );
});
