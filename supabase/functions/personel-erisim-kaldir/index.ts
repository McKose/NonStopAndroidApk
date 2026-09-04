// ---------------------------------------------------------------------------
// Personel erişimini kaldırma
// ---------------------------------------------------------------------------
// `personel-davet`in tersi: bir personelin salon yetkisini (`gym_users`) siler
// ve personel kaydının hesap bağını (`staff.auth_user_id`) boşaltır. İşten
// ayrılan biri için yapılacak şey bu.
//
// ### Neden sunucu tarafında
// Aynı gerekçe davet tarafındakiyle birebir: `gym_users` bütün erişim
// kurallarının dayanağı. İstemciye yazma açılsaydı, yazabilen kişi kendi
// yetkisini de yükseltebilirdi. Tabloya hiç yazma kuralı yok (bkz. `0007`) ve
// `delete` yetkisi yalnızca `service_role`de (bkz. `0008`); anahtar sunucuda
// kalıyor, istemciye hiç gitmiyor.
//
// ### Hesap SİLİNMİYOR
// `auth.users` satırına dokunulmuyor. Sebebi somut: aynı hesap başka bir
// salonda da çalışıyor olabilir ve orada hâlâ geçerli. Silinen şey yalnızca BU
// salona bağlılık. Kişinin hesabı duruyor, giriş yapabiliyor, ama artık bu
// salonun hiçbir verisini görmüyor.
//
// Aynı sebeple `staff` satırı da silinmiyor: geçmiş satışlar, randevular ve
// hakedişler o satıra bağlı. Silinseydi kişinin bütün geçmişi rapordan
// düşerdi — ayrılan biri, hiç çalışmamış birine dönerdi.
//
// ### Kendi erişimini kaldıramıyor
// Tek ve yeterli kilitlenme koruması bu. Bu ucu yalnızca salonun ADMIN'i
// çağırabiliyor; çağıran kendini kaldıramadığına göre salonda her zaman en az
// bir ADMIN kalıyor. "Son yönetici mi" diye ayrıca saymaya gerek yok — sayım
// yarışa açık olurdu (iki yönetici aynı anda birbirini kaldırırsa ikisi de
// "diğeri var" görür), bu kontrol değil.
//
// ### Sıra: önce yetki, sonra bağ
// İkisi tek işlem değil (iki ayrı PostgREST çağrısı) ve arada kopabilir.
// Sıra buna göre seçildi:
//
//   - Önce `gym_users` silinir. Burada durursa kişinin ERİŞİMİ GİTMİŞTİR —
//     istenen sonuç zaten bu. Geriye kalan tek şey `staff` satırındaki artık
//     bağ ve o, panelde "Hesabı var, yetkisi yok" olarak GÖRÜNÜYOR.
//   - Ters sırada olsaydı: bağ boşalır, yetki silinemez ve kişi salonun
//     verilerini görmeye DEVAM ederdi — üstelik panelde "Hesabı yok" yazardı.
//     Yani yarım kalış, sessizce yanlış bir ekranla birleşirdi.
//
// ### Tekrar çalıştırılabilir
// Yarıda kalan bir kaldırma aynı verilerle tekrar denendiğinde tamamlanıyor:
// olmayan `gym_users` satırını silmek hata değil, `staff.auth_user_id`yi ikinci
// kez boşaltmak da öyle. Bu olmasaydı yarım kalan durumu düzeltmek yine
// Supabase paneli gerektirirdi — yani bu fonksiyonun kapatmak için var olduğu
// duruma geri dönülürdü.
// ---------------------------------------------------------------------------

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

/**
 * Tarayıcıdan çağrılabilecek adresler.
 *
 * `*` DEĞİL ve bilinçli: bu uç nokta yetki kaldırıyor. Liste `personel-davet`
 * ile aynı ve aynı ortam değişkeninden okunuyor — ikisi ayrışsaydı panelin bir
 * düğmesi çalışır, diğeri CORS'ta sessizce dururdu.
 */
const IZINLI_KAYNAKLAR = new Set(
  (Deno.env.get("IZINLI_KAYNAKLAR") ??
    "https://nonstopstudio.tr,https://www.nonstopstudio.tr,https://mckose.github.io")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean),
);

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

/** PostgREST çağrısı — `service_role` ile. */
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

  if (!UUID_DESENI.test(gymId)) return hata("Salon kimliği geçersiz.", 400, kaynak);
  if (!staffId) return hata("Personel kaydı seçilmedi.", 400, kaynak);

  // ── 1) Çağıran BU salonda ADMIN mi ───────────────────────────────────────
  // Başka salonda ADMIN olmak yetmiyor: sorgu iki alanı da eşliyor.
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
  // `tenant_id` eşlemesi olmadan bir yönetici, başka salonun personelinin
  // erişimini kaldırabilirdi.
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

  // ── 3) Kaldıracak bir şey var mı ─────────────────────────────────────────
  const kullaniciId: string | null = personel.auth_user_id ?? null;
  if (!kullaniciId) {
    // Hata DEĞİL: istenen son durum zaten bu. 200 dönmek, yarıda kalmış bir
    // isteğin tekrar denenmesini de sorunsuz kılıyor.
    return yanit(
      { durum: "zaten_yok", personel: personel.full_name },
      200,
      kaynak,
    );
  }

  // ── 4) Kendi erişimini kaldıramaz ────────────────────────────────────────
  // Salonun kilitlenmesini engelleyen tek kontrol bu; gerekçesi dosyanın
  // başındaki nota yazılı.
  if (kullaniciId === cagiran.id) {
    return hata(
      "Kendi erişiminizi kaldıramazsınız. Bunu salonun başka bir yöneticisi yapmalı.",
      409,
      kaynak,
    );
  }

  // ── 5) Salon yetkisini sil ───────────────────────────────────────────────
  // Bağdan ÖNCE. Gerekçe dosyanın başında: burada durulursa erişim gitmiş
  // olur ve kalan artık bağ panelde görünür. Ters sıra, erişimi duran birini
  // "hesabı yok" diye gösterirdi.
  const silmeYaniti = await sunucu(
    `/rest/v1/gym_users?user_id=eq.${encodeURIComponent(kullaniciId)}` +
      `&gym_id=eq.${encodeURIComponent(gymId)}`,
    { method: "DELETE", headers: { Prefer: "return=minimal" } },
  );
  if (!silmeYaniti.ok) {
    const ayrinti = await silmeYaniti.text();
    console.error("gym_users silinemedi:", silmeYaniti.status, ayrinti);
    return hata("Salon yetkisi kaldırılamadı.", 502, kaynak);
  }

  // ── 6) Personel kaydının hesap bağını boşalt ─────────────────────────────
  // Bu adım olmadan panel kişiyi "Hesabı var, yetkisi yok" diye gösterirdi ve
  // o satır orada bir ARIZA uyarısı üretiyor ("giriş yapıyor ama boş ekran
  // görüyor"). Kasıtlı bir kaldırmadan sonra o uyarı yanlış olurdu.
  const bagYaniti = await sunucu(
    `/rest/v1/staff?id=eq.${encodeURIComponent(staffId)}` +
      `&tenant_id=eq.${encodeURIComponent(gymId)}`,
    {
      method: "PATCH",
      headers: { Prefer: "return=minimal" },
      body: JSON.stringify({ auth_user_id: null }),
    },
  );
  if (!bagYaniti.ok) {
    const ayrinti = await bagYaniti.text();
    console.error("staff bağı boşaltılamadı:", bagYaniti.status, ayrinti);
    // Yetki SİLİNDİ; kişi artık veri göremiyor. Kalan artık bağ zararsız ve
    // aynı isteği tekrarlamak düzeltiyor — mesaj bunu söylüyor.
    return hata(
      "Erişim kaldırıldı ama personel kaydındaki hesap bağı temizlenemedi. " +
        "İşlemi bir kez daha çalıştırın.",
      502,
      kaynak,
    );
  }

  return yanit(
    { durum: "kaldirildi", personel: personel.full_name },
    200,
    kaynak,
  );
});
