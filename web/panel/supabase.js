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

    if (!yanit.ok) {
      const metin = await yanit.text().catch(() => "");
      return { tur: "hata", mesaj: `Kaydedilemedi (${yanit.status}): ${metin.slice(0, 200)}` };
    }

    return { tur: "tamam" };
  }
}
