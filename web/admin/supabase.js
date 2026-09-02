// Supabase ile konuşan ince katman.
//
// `supabase-js` kullanılmıyor. Sebep: paket, derleme adımı ve CDN bağımlılığı
// getiriyor; buradaki ihtiyaç ise iki uç nokta (giriş ve okuma) ve düz `fetch`.
// Uygulama tarafında da aynı tercih yapıldı ve gerekçesi orada yazılı.
//
// Anahtar (`anon`) burada görünür olmak zorunda ve bu **sorun değil**: tek
// başına hiçbir veriye erişemiyor, her sorgu giriş yapan kullanıcıya göre
// süzülüyor. Panelde gösterilen her satırı sunucudaki erişim kuralları
// belirliyor — panelin kodu değil.

/** Oturumun tarayıcıda saklandığı anahtar. */
const OTURUM_ANAHTARI = "nonstop.session";

export class SupabaseClient {
  constructor(url, anonKey) {
    this.url = String(url || "").trim().replace(/\/$/, "");
    this.anonKey = String(anonKey || "").trim();
  }

  get yapilandirildiMi() {
    return this.url.length > 0 && this.anonKey.length > 0;
  }

  // ─── Oturum ──────────────────────────────────────────────────────────────

  oturumOku() {
    try {
      const ham = localStorage.getItem(OTURUM_ANAHTARI);
      if (!ham) return null;
      const oturum = JSON.parse(ham);
      // Yarım bir oturum (jetonu olan ama süresi bilinmeyen) her isteği sessizce
      // başarısız kılardı; okunamayan kayıt yok sayılıyor.
      if (!oturum.access_token || !oturum.expires_at_ms) return null;
      return oturum;
    } catch {
      return null;
    }
  }

  oturumYaz(oturum) {
    localStorage.setItem(OTURUM_ANAHTARI, JSON.stringify(oturum));
  }

  oturumSil() {
    localStorage.removeItem(OTURUM_ANAHTARI);
  }

  /**
   * E-posta ve şifreyle giriş.
   *
   * Sonuç uygulamadaki ayrımın aynısını taşıyor: kimlik hatası, salona bağlı
   * olmama ve diğer hatalar ayrı. Tek bir "giriş başarısız" mesajı, sorun
   * sunucudaki eksik bir `gym_users` satırıyken kullanıcıyı şifresini aramaya
   * gönderirdi.
   */
  async girisYap(eposta, sifre) {
    let yanit;
    try {
      yanit = await fetch(`${this.url}/auth/v1/token?grant_type=password`, {
        method: "POST",
        headers: { apikey: this.anonKey, "Content-Type": "application/json" },
        body: JSON.stringify({ email: eposta, password: sifre }),
      });
    } catch (e) {
      return { tur: "hata", mesaj: `Sunucuya ulaşılamadı: ${e.message}` };
    }

    const govde = await yanit.json().catch(() => ({}));

    if (!yanit.ok) {
      const mesaj =
        govde.error_description || govde.msg || govde.message || govde.error ||
        `Beklenmeyen yanıt (${yanit.status})`;
      if (yanit.status === 400 || yanit.status === 401) {
        return { tur: "kimlik", mesaj };
      }
      return { tur: "hata", mesaj };
    }

    // Geçerlilik anı yerel saate göre hesaplanıyor: sunucunun mutlak değeri
    // kullanılsaydı, saati sapan bir bilgisayarda jeton ya erken çöpe atılır ya
    // da süresi dolmuş hâliyle gönderilirdi.
    const oturum = {
      access_token: govde.access_token,
      refresh_token: govde.refresh_token,
      expires_at_ms: Date.now() + (Number(govde.expires_in) || 0) * 1000,
      email: govde.user?.email || eposta,
      user_id: govde.user?.id,
    };

    const salon = await this.salonBul(oturum.access_token);
    if (salon.tur !== "tamam") return salon;

    oturum.gym_id = salon.gymId;
    oturum.gym_name = salon.gymName;
    oturum.role = salon.role;
    this.oturumYaz(oturum);
    return { tur: "tamam", oturum };
  }

  /**
   * Kullanıcının salonunu okur.
   *
   * Sorguda `user_id` koşulu yok: erişim kuralları zaten yalnızca bu
   * kullanıcının satırlarını döndürüyor. Koşulu ayrıca yazmak, yalıtımı
   * sağlayan şeyin sorgu olduğu izlenimini verirdi.
   */
  async salonBul(jeton) {
    const yanit = await fetch(
      `${this.url}/rest/v1/gym_users?select=gym_id,role,gyms(name)`,
      { headers: { apikey: this.anonKey, Authorization: `Bearer ${jeton}` } },
    ).catch(() => null);

    if (!yanit) return { tur: "hata", mesaj: "Salon bilgisi alınamadı." };
    if (!yanit.ok) {
      return { tur: "hata", mesaj: `Salon bilgisi alınamadı (${yanit.status}).` };
    }

    const satirlar = await yanit.json().catch(() => []);
    if (!Array.isArray(satirlar) || satirlar.length === 0) {
      return {
        tur: "salonsuz",
        mesaj:
          "Hesabınız bir salona bağlı değil. Yöneticinizin sizi Supabase " +
          "panelinden salona eklemesi gerekiyor.",
      };
    }
    if (satirlar.length > 1) {
      return {
        tur: "hata",
        mesaj: "Hesabınız birden fazla salona bağlı; bu sürüm tek salon destekliyor.",
      };
    }

    const satir = satirlar[0];
    return {
      tur: "tamam",
      gymId: satir.gym_id,
      gymName: satir.gyms?.name || "Salon",
      role: satir.role,
    };
  }

  // ─── Veri okuma ──────────────────────────────────────────────────────────

  /**
   * Tablodan satır okur.
   *
   * Salon süzgeci **yazılmıyor**: erişim kuralları zaten yalnızca kullanıcının
   * salonunun satırlarını döndürüyor. Panel yanlış yazılsa bile başka salonun
   * verisi gelmez — yalıtımı sağlayan şey kural, sorgu değil.
   */
  async oku(tablo, { order = null, limit = 500 } = {}) {
    const oturum = this.oturumOku();
    if (!oturum) return { tur: "oturumsuz" };

    // Jetonun süresi dolduysa okumayı hiç denemiyoruz: 401 alıp kullanıcıya
    // anlamsız bir hata göstermek yerine doğrudan girişe yönlendiriyoruz.
    if (Date.now() >= oturum.expires_at_ms) {
      this.oturumSil();
      return { tur: "oturumsuz" };
    }

    let adres = `${this.url}/rest/v1/${tablo}?select=*&limit=${limit}`;
    if (order) adres += `&order=${order}`;

    const yanit = await fetch(adres, {
      headers: { apikey: this.anonKey, Authorization: `Bearer ${oturum.access_token}` },
    }).catch(() => null);

    if (!yanit) return { tur: "hata", mesaj: "Sunucuya ulaşılamadı." };
    if (yanit.status === 401) {
      this.oturumSil();
      return { tur: "oturumsuz" };
    }
    if (!yanit.ok) {
      const govde = await yanit.text().catch(() => "");
      return { tur: "hata", mesaj: `${tablo} okunamadı (${yanit.status}): ${govde.slice(0, 200)}` };
    }

    const satirlar = await yanit.json().catch(() => []);

    // `kesildi`: sunucu sınıra dayandı mı, yani daha fazla satır olabilir mi?
    //
    // Liste göstermek için önemsiz (birkaç yüz satırdan sonrasını kimse
    // okumuyor), ama TOPLAMA girecek veri için kritik: 500 hareketin ilk
    // 500'ünden hesaplanmış bir stok sayısı, doğru bir stok sayısından ayırt
    // edilemez. Çağıran taraf bunu bilmeden toplam alırsa sessizce yanlış bir
    // sayı gösterir. Stok sekmesi bu yüzden kesildiğinde sayı yerine "?"
    // gösteriyor.
    return { tur: "tamam", satirlar, kesildi: satirlar.length >= limit };
  }

  /**
   * Satır ekler ya da günceller.
   *
   * ### Panel neden artık yazıyor
   * Panel bilinçli olarak salt okunurdu ve gerekçesi geçerliliğini koruyor:
   * uygulamadaki yazma yolları iş kuralları taşıyor (hakediş, seans düşme,
   * defter kaydı) ve o kurallar ortak Kotlin modülünde. Panelden yazmak
   * onların ikinci bir kopyasını burada tutmak olurdu.
   *
   * Bu yöntem **yalnızca** o kuralların hiç bulunmadığı iki tabloda
   * kullanılıyor: `announcements` (herkese açık sitenin içeriği) ve
   * `member_accounts` (üye ↔ hesap bağı). İkisi de web'e ait; uygulama
   * ikisini de bilmiyor, okumuyor, yazmıyor. Yani kopyalanan bir kural yok.
   *
   * Üye, paket, randevu, defter gibi tablolara buradan yazılmıyor ve
   * yazılmamalı.
   *
   * (`member_link_requests` de bu listeye katıldı: üyenin kendi yazdığı kayıt
   * isteğini personel yalnızca "bağlandı/reddedildi" olarak işaretliyor. İş
   * kuralı yok, erişim kararı var.)
   *
   * @param {string} tablo hedef tablo
   * @param {object} govde yazılacak alanlar
   * @param {?string} eslesme `id=eq.xxx` gibi bir süzgeç; verilirse GÜNCELLEME
   */
  async yaz(tablo, govde, eslesme = null) {
    const oturum = this.oturumOku();
    if (!oturum) return { tur: "oturumsuz" };
    if (Date.now() >= oturum.expires_at_ms) {
      this.oturumSil();
      return { tur: "oturumsuz" };
    }

    const adres = eslesme
      ? `${this.url}/rest/v1/${tablo}?${eslesme}`
      : `${this.url}/rest/v1/${tablo}`;

    const yanit = await fetch(adres, {
      method: eslesme ? "PATCH" : "POST",
      headers: {
        apikey: this.anonKey,
        Authorization: `Bearer ${oturum.access_token}`,
        "Content-Type": "application/json",
        // `return=minimal`: yanıt gövdesi gerekmiyor, yazma başarısı durum
        // kodundan anlaşılıyor.
        Prefer: "return=minimal",
      },
      body: JSON.stringify(govde),
    }).catch(() => null);

    if (!yanit) return { tur: "hata", mesaj: "Sunucuya ulaşılamadı." };
    if (yanit.status === 401) {
      this.oturumSil();
      return { tur: "oturumsuz" };
    }

    // 403: sunucudaki erişim kuralı reddetti. Ayrı ele alınıyor çünkü
    // kullanıcının yapabileceği şey farklı — "tekrar dene" değil, "bu işlem
    // senin rolünde yok".
    if (yanit.status === 403) {
      return { tur: "yetkisiz", mesaj: "Bu işlem için yetkiniz yok." };
    }

    // 409: tekillik kısıtı. Ayrı ele alınıyor çünkü tek başına bir HATA
    // olmayabilir — hesap bağlama akışında bunun anlamı "bu bağ zaten var",
    // yani işlem yarım kalmış bir denemeden sonra tekrarlanıyor. Ham PostgREST
    // metni ("duplicate key value violates unique constraint …") kullanıcıya
    // gösterilecek bir şey değil.
    if (yanit.status === 409) {
      return { tur: "cakisma", mesaj: "Bu kayıt zaten var." };
    }

    if (!yanit.ok) {
      const metin = await yanit.text().catch(() => "");
      return { tur: "hata", mesaj: `Kaydedilemedi (${yanit.status}): ${metin.slice(0, 200)}` };
    }

    return { tur: "tamam" };
  }

  /**
   * Duyuru görselini Supabase Storage'a yükler ve herkese açık adresini döndürür.
   *
   * ### Neden dosya adı korunmuyor
   * Yüklenen ad tamamen atılıyor, yalnızca uzantısı alınıyor. Sebep dosya
   * adlarının nesne yolunun parçası olması: Türkçe harf, boşluk ve `..` gibi
   * şeyler ya adresi bozar ya da yolu oynatır. Üstelik iki kişi aynı gün
   * "etkinlik.jpg" yüklerse ikincisi birincisinin görselini ezerdi — sitedeki
   * duyuru sessizce başka bir resme dönerdi.
   *
   * ### Neden kova salon klasörüne ayrılıyor
   * Yol `<salon>/<zaman>-<rastgele>.<uzantı>`. Bu bir GÜVENLİK sınırı değil —
   * kova herkese açık ve kuralları yola bakmıyor (bkz. migrasyon 0006) — sadece
   * düzen: hangi görselin hangi salona ait olduğu dosya listesinden görünüyor.
   *
   * @param {File} dosya `<input type=file>` üzerinden seçilen dosya
   * @param {string} salonId nesne yolunun ilk parçası
   */
  /**
   * Personele uygulama erişimi verir.
   *
   * İşi PANEL YAPMIYOR: istek sunucudaki `personel-davet` Edge Function'ına
   * gidiyor. Sebep `gym_users` tablosu — bütün erişim kurallarının dayanağı ve
   * ona istemciden yazma açılsaydı, yazabilen kişi kendi yetkisini de
   * yükseltebilirdi. Fonksiyon `service_role` anahtarıyla koşuyor ve o anahtar
   * hiçbir zaman tarayıcıya inmiyor.
   *
   * `gym_id` OTURUMDAN alınıyor, formdan değil. Formdan gelseydi paneli
   * değiştiren biri başka salonun kimliğini yazmayı deneyebilirdi — sunucu
   * yine reddederdi (yetkiyi kendi doğruluyor), ama reddedilecek bir isteği
   * hiç göndermemek daha iyi.
   *
   * Hata durumunda ham durum kodu ve gövde dönüyor; okunur mesaja çeviren yer
   * `davet.js` (orada sınanabiliyor). Ağ hatası `kod: 0` ile ayrı tutuluyor:
   * yapılacak şey farklı — tekrar denemek güvenli, davet tekrar edilebilir
   * olacak şekilde yazıldı.
   */
  async personelDavetEt({ personelId, eposta, yetki }) {
    const oturum = this.oturumOku();
    if (!oturum) return { tur: "oturumsuz" };
    if (Date.now() >= oturum.expires_at_ms) {
      this.oturumSil();
      return { tur: "oturumsuz" };
    }

    const yanit = await fetch(`${this.url}/functions/v1/personel-davet`, {
      method: "POST",
      headers: {
        apikey: this.anonKey,
        Authorization: `Bearer ${oturum.access_token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        gym_id: oturum.gym_id,
        staff_id: personelId,
        email: eposta,
        role: yetki,
      }),
    }).catch(() => null);

    if (!yanit) return { tur: "hata", kod: 0, govde: null };

    const govde = await yanit.json().catch(() => null);
    if (!yanit.ok) return { tur: "hata", kod: yanit.status, govde };
    return { tur: "tamam", yanit: govde };
  }

  /**
   * Giriş şifresini değiştirir — **önce mevcut şifreyi doğrulayarak**.
   *
   * ### Neden yeniden giriş yapılıyor
   * Supabase'in `PUT /auth/v1/user` ucu mevcut şifreyi sormuyor; geçerli bir
   * jeton yetiyor. Tek başına kullanılsaydı açık somut olurdu: salonun ortak
   * bilgisayarında açık unutulmuş bir panelde şifre değiştirilip hesap sahibi
   * kilitlenebilirdi. Jeton "bu kişi giriş yapmıştı" diyor, "bu kişi şu anda
   * burada" demiyor.
   *
   * Doğrulama gerçek bir giriş denemesiyle yapılıyor — mevcut şifreyi bilmenin
   * başka bir kanıtı yok. Yan etkisi bilinçli: [girisYap] başarılı olduğunda
   * oturum tazeleniyor ve saklanıyor, dolayısıyla yazma taze jetonla gidiyor.
   *
   * ### Sıra: önce doğrula, sonra yaz
   * Ters sırada, yanlış mevcut şifre girildiğinde şifre çoktan değişmiş
   * olurdu — yani korumanın kendisi zararı üretirdi.
   *
   * Hata durumunda ham durum kodu ve gövde dönüyor; okunur mesaja çeviren yer
   * `sifre.js` (orada sınanabiliyor).
   */
  async sifreDegistir({ mevcut, yeni }) {
    const oturum = this.oturumOku();
    if (!oturum) return { tur: "oturumsuz" };

    // Kimlik kanıtı. `girisYap` başarılıysa oturumu da tazeleyip saklıyor.
    const dogrulama = await this.girisYap(oturum.email, mevcut);
    if (dogrulama.tur === "kimlik") {
      return { tur: "yanlis-sifre", mesaj: "Mevcut şifreniz yanlış." };
    }
    if (dogrulama.tur !== "tamam") return dogrulama;

    const yanit = await fetch(`${this.url}/auth/v1/user`, {
      method: "PUT",
      headers: {
        apikey: this.anonKey,
        Authorization: `Bearer ${dogrulama.oturum.access_token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ password: yeni }),
    }).catch(() => null);

    if (!yanit) return { tur: "hata", kod: 0, govde: null };

    if (!yanit.ok) {
      const ham = await yanit.json().catch(() => ({}));
      // Sunucunun kendi metni tek bir alana toplanıyor: GoTrue sürüme göre
      // `msg`, `message` ya da `error_description` kullanıyor ve üçünü de
      // çağıran tarafta aramak, birinin unutulmasıyla mesajı kaybettirirdi.
      const mesaj = ham.msg || ham.message || ham.error_description || ham.error || "";
      return { tur: "hata", kod: yanit.status, govde: { mesaj } };
    }

    // Yanıt gövdesi güncellenmiş kullanıcı; okunacak bir şey yok. Jeton
    // üretilmiyor ve mevcut oturum geçerli kalıyor.
    return { tur: "tamam" };
  }

  async dosyaYukle(dosya, salonId) {
    const oturum = this.oturumOku();
    if (!oturum) return { tur: "oturumsuz" };
    if (Date.now() >= oturum.expires_at_ms) {
      this.oturumSil();
      return { tur: "oturumsuz" };
    }

    const kontrol = gorselKontrol(dosya);
    if (kontrol) return { tur: "hata", mesaj: kontrol };

    const yol =
      `${temizParca(salonId)}/${Date.now()}-` +
      `${Math.random().toString(36).slice(2, 8)}.${uzanti(dosya.name)}`;

    const yanit = await fetch(`${this.url}/storage/v1/object/${KOVA}/${yol}`, {
      method: "POST",
      headers: {
        apikey: this.anonKey,
        Authorization: `Bearer ${oturum.access_token}`,
        // Tarayıcının seçtiği tür olduğu gibi gönderiliyor; Storage bunu
        // saklıyor ve görsel indirilirken aynı başlıkla dönüyor.
        "Content-Type": dosya.type || "application/octet-stream",
      },
      body: dosya,
    }).catch(() => null);

    if (!yanit) return { tur: "hata", mesaj: "Sunucuya ulaşılamadı." };
    if (yanit.status === 401) {
      this.oturumSil();
      return { tur: "oturumsuz" };
    }
    if (yanit.status === 403) {
      return {
        tur: "yetkisiz",
        mesaj: "Görsel yükleme yetkiniz yok (yönetici veya müdür gerekiyor).",
      };
    }
    if (yanit.status === 404) {
      // Kova yoksa 404 dönüyor ve bu, kullanıcının düzeltebileceği bir şey
      // değil: migrasyon 0006 Supabase'e uygulanmamış demek.
      return {
        tur: "hata",
        mesaj: `"${KOVA}" kovası bulunamadı — sunucuda 0006 migrasyonu uygulanmamış olabilir.`,
      };
    }
    if (!yanit.ok) {
      const metin = await yanit.text().catch(() => "");
      return { tur: "hata", mesaj: `Yüklenemedi (${yanit.status}): ${metin.slice(0, 200)}` };
    }

    return { tur: "tamam", adres: `${this.url}/storage/v1/object/public/${KOVA}/${yol}` };
  }
}

/** Duyuru görsellerinin kovası (migrasyon 0006). */
const KOVA = "duyuru-gorselleri";

/** Kabul edilen uzantılar. Liste dar tutuluyor: kova herkese açık. */
const GORSEL_UZANTILARI = new Set(["jpg", "jpeg", "png", "webp", "avif", "gif"]);

/** 5 MB. Sitede kart görseli olarak kullanılıyor; daha büyüğü sayfayı yavaşlatır. */
const EN_BUYUK_BAYT = 5 * 1024 * 1024;

const uzanti = (ad) => String(ad || "").split(".").pop().toLowerCase();

/** Yol parçasındaki tehlikeli karakterleri eler (`/`, `..`, boşluk …). */
const temizParca = (s) => String(s || "salon").replace(/[^a-zA-Z0-9_-]/g, "") || "salon";

/**
 * Dosya kabul edilebilir mi; değilse **sebebi** döner.
 *
 * Sunucuya gitmeden bakılıyor: 20 MB'lık bir dosyayı yükleyip reddedilmesini
 * beklemek, hatayı öğrenmenin en yavaş yolu olurdu.
 */
function gorselKontrol(dosya) {
  if (!dosya) return "Dosya seçilmedi.";
  if (!GORSEL_UZANTILARI.has(uzanti(dosya.name))) {
    return `Yalnızca görsel yüklenebilir (${[...GORSEL_UZANTILARI].join(", ")}).`;
  }
  if (dosya.size > EN_BUYUK_BAYT) {
    const mb = (dosya.size / 1024 / 1024).toFixed(1);
    return `Dosya çok büyük (${mb} MB). En fazla 5 MB olmalı.`;
  }
  return null;
}

export { gorselKontrol, KOVA };
