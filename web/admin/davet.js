// Personel erişimi bölümünün KARAR mantığı — çizim yok, ağ yok.
//
// Ayrı dosya olmasının sebebi bu panelde birkaç kez kanıtlandı: `app.js`
// içindeki mantık ancak tarayıcıda, gerçek bir sunucuya bağlıyken
// denenebiliyor. Buradaki her şey saf fonksiyon, dolayısıyla `davet.test.js`
// hepsini doğrudan sınayabiliyor.
//
// ### Neden doğrulama İKİ yerde
// Aynı kurallar sunucudaki `personel-davet` fonksiyonunda da var ve orada
// olması ŞART: istemci doğrulaması atlanabilir. Burada da olmasının sebebi
// güvenlik değil, geri bildirim — yönetici e-postayı yanlış yazdığında
// sunucuya gidip 400 ile dönmek yerine anında görsün.

/**
 * Sunucunun kabul ettiği yetkiler. Sıra ekranda da bu sırayla görünüyor.
 *
 * Ad `DAVET_` önekli: `demo.js` de `YETKILER` adında bir sabit tutuyor ve
 * önizleme üreticisi modülleri tek kapsamda birleştirdiği için iki tanım
 * çakışıyor. Takma adla içe almak çözüm DEĞİL — birleştirmede `import ... as`
 * diye bir şey kalmıyor.
 */
export const DAVET_YETKILERI = ["TRAINER", "MANAGER", "ADMIN"];

/** Yetkinin Türkçe adı ve ne anlama geldiği. */
export const YETKI_ACIKLAMA = {
  TRAINER: "Eğitmen — günlük iş: üye, randevu, satış, ölçüm",
  MANAGER: "Müdür — eğitmenin yaptıkları + fiyat listesi ve finans",
  ADMIN: "Yönetici — hepsi + personel ve erişim yönetimi",
};

/**
 * Bir personelin erişim durumu.
 *
 * ÜÇ hâl var ve üçü de farklı bir eylem gerektiriyor. İkiye indirmek
 * ("erişimi var / yok") ortadaki hâli gizlerdi: hesabı açılmış ama salon
 * yetkisi yazılmamış biri giriş yapabiliyor ve HİÇBİR VERİ göremiyor —
 * uygulamada hata da çıkmıyor. Bölümün var oluş sebebi tam olarak bu hâl.
 *
 * @param personel `staff` satırı (`auth_user_id` taşıyor)
 * @param yetkiler `gym_users` satırları
 */
export function erisimDurumu(personel, yetkiler) {
  const hesap = personel?.auth_user_id ?? null;
  if (!hesap) {
    return { durum: "hesap_yok", yetki: null };
  }

  const satir = (yetkiler ?? []).find((y) => y?.user_id === hesap);
  if (!satir) {
    return { durum: "yetki_yok", yetki: null };
  }
  return { durum: "erisim_var", yetki: satir.role ?? null };
}

/**
 * Durumun ekranda görünen etiketi.
 *
 * Adı `durumEtiketi` DEĞİL: `domain.js` üyelik durumu için o adı kullanıyor.
 * `import ... as` ile çakışmayı çözmek yetmiyor — önizleme üreticisi
 * (`onizleme.mjs`) modülleri TEK KAPSAMDA birleştiriyor ve orada iki tanım
 * yan yana gelip "Identifier has already been declared" ile panelin tamamını
 * düşürüyor. Bu tam olarak yaşandı ve yalnızca tarayıcıda görüldü: birim
 * testleri, `node --check` ve tip denetimi hiçbiri yakalamadı, çünkü gerçek
 * ES modüllerinde takma ad çalışıyor.
 */
export function erisimEtiketi(durum) {
  switch (durum) {
    case "erisim_var": return "Erişimi var";
    case "yetki_yok": return "Hesabı var, yetkisi yok";
    case "hesap_yok": return "Hesabı yok";
    default: return "Bilinmiyor";
  }
}

/**
 * `yetki_yok` neden ayrıca anlatılıyor.
 *
 * Yönetici bu satırı gördüğünde "zaten hesabı var, sorun yok" diye
 * düşünebilir. Oysa o kişi giriş yapıp boş bir uygulama görüyor ve sebebini
 * kimse söylemiyor. Metin bunu açıkça yazıyor.
 */
export const YETKI_YOK_ACIKLAMA =
  "Bu kişi giriş yapabiliyor ama salona bağlı olmadığı için hiçbir veri " +
  "göremiyor — uygulamada hata da çıkmıyor, ekranlar boş kalıyor. " +
  "Davet ederek yetkisini yazın.";

/**
 * Form girdisini sunucuya göndermeden önce doğrular.
 *
 * @returns `{ gecerli: true, deger }` ya da `{ gecerli: false, mesaj }`
 */
export function davetiDogrula({ personelId, eposta, yetki } = {}) {
  const p = String(personelId ?? "").trim();
  if (!p) return { gecerli: false, mesaj: "Personel seçilmedi." };

  // Küçük harfe çevriliyor: sunucu da öyle yapıyor ve `auth.users` araması
  // harf duyarsız. İkisi ayrışsaydı yönetici "Ali@..." yazıp "hesap yok"
  // cevabı alır, sonra "ali@..." yazıp "hesap var" alırdı.
  const e = String(eposta ?? "").trim().toLowerCase();
  if (!e) return { gecerli: false, mesaj: "E-posta adresi girin." };
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e)) {
    return { gecerli: false, mesaj: "E-posta adresi geçerli görünmüyor." };
  }

  const y = String(yetki ?? "").trim().toUpperCase();
  if (!DAVET_YETKILERI.includes(y)) return { gecerli: false, mesaj: "Yetki seçilmedi." };

  return { gecerli: true, deger: { personelId: p, eposta: e, yetki: y } };
}

/**
 * Sunucu yanıtını okunur bir mesaja çevirir.
 *
 * Ham durum kodu göstermek ("Hata 409") yöneticiye hiçbir şey anlatmıyor;
 * her kodun karşılığı ne yapılması gerektiğini söylüyor. Bilinmeyen kodlar
 * için sunucunun kendi mesajı kullanılıyor — uydurulmuş bir açıklama,
 * anlaşılmaz bir koddan kötüdür.
 */
export function davetHataMesaji(durumKodu, govde) {
  const sunucudan = typeof govde?.hata === "string" ? govde.hata : "";

  switch (durumKodu) {
    case 401:
      return "Oturumunuz sona ermiş. Çıkıp yeniden giriş yapın.";
    case 403:
      return "Bu işlem için salon yöneticisi olmanız gerekiyor.";
    case 404:
      return "Personel kaydı bulunamadı. Sayfayı yenileyip tekrar deneyin.";
    case 409:
      return sunucudan || "Bu hesap ya da personel zaten başka bir kayda bağlı.";
    case 0:
      // Ağ hatası: yanıt hiç gelmedi. Ayrı tutuluyor çünkü yapılacak şey
      // farklı — bağlantıyı kontrol edip TEKRAR DENEMEK güvenli, davet
      // tekrar edilebilir olacak şekilde yazıldı.
      return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip tekrar deneyin.";
    default:
      return sunucudan || `Davet gönderilemedi (${durumKodu}).`;
  }
}

/** Başarılı yanıtın ekranda görünecek özeti. */
export function davetBasariMesaji(yanit) {
  const ad = yanit?.personel ? `${yanit.personel} ` : "";
  return yanit?.durum === "mevcut_hesap_baglandi"
    ? `${ad}için mevcut hesap salona bağlandı. Şifresi değişmedi.`
    : `${ad}için hesap açıldı.`;
}

// ─── Erişimi kaldırma ───────────────────────────────────────────────────────
//
// Davetin tersi. Aynı kurallar sunucudaki `personel-erisim-kaldir` fonksiyonunda
// da var ve orada olması ŞART — buradakiler yalnızca geri bildirim için:
// yönetici, sunucuya gidip 409 ile dönmek yerine düğmenin neden kapalı
// olduğunu anında görsün.

/**
 * Bu personelin erişimi kaldırılabilir mi.
 *
 * @param durum `erisimDurumu()` sonucundaki `durum`
 * @param personelAuthId `staff.auth_user_id`
 * @param oturumKullaniciId oturumu açık olan kişinin `user_id`si
 * @returns `{ olur: true }` ya da `{ olur: false, sebep }`
 */
export function erisimKaldirilabilirMi({
  durum,
  personelAuthId = null,
  oturumKullaniciId = null,
} = {}) {
  if (durum === "hesap_yok" || !personelAuthId) {
    return { olur: false, sebep: "Bu personelin kaldırılacak bir erişimi yok." };
  }

  // Kendi erişimini kaldıramıyor. Salonun kilitlenmesini engelleyen tek kontrol
  // bu ve yeterli: bu ekranı yalnızca ADMIN görüyor, çağıran kendini
  // kaldıramadığına göre salonda her zaman en az bir ADMIN kalıyor.
  //
  // "Son yönetici mi" diye ayrıca saymak DAHA İYİ DEĞİL, daha kötü olurdu:
  // sayım yarışa açık — iki yönetici aynı anda birbirini kaldırırsa ikisi de
  // "diğeri duruyor" görür ve salon yöneticisiz kalır.
  if (personelAuthId === oturumKullaniciId) {
    return {
      olur: false,
      sebep: "Kendi erişiminizi kaldıramazsınız. Bunu salonun başka bir yöneticisi yapmalı.",
    };
  }

  // `yetki_yok` de kaldırılabilir ve edilmeli: orada `gym_users` satırı zaten
  // yok ama `staff.auth_user_id` duruyor. O artık bağ panelde bir ARIZA uyarısı
  // üretiyor ("giriş yapıyor, boş ekran görüyor") ve kaldırma onu temizliyor.
  return { olur: true };
}

/**
 * Onay metni.
 *
 * Ne olacağını ve ne OLMAYACAĞINI birlikte söylüyor. İkincisi olmadan yönetici
 * düğmeye basmaktan çekinirdi: "erişimi kaldır" ifadesi, kişinin geçmişinin de
 * silineceği izlenimini verebiliyor.
 */
export function kaldirmaOnayMetni(personelAdi) {
  const ad = personelAdi || "Bu personel";
  return (
    `${ad} artık bu salonun hiçbir verisini göremeyecek.\n\n` +
    "Personel kaydı ve geçmişi (satışlar, randevular, hakedişler) DURUYOR — " +
    "raporlardan düşmüyor. Hesabı da silinmiyor; yalnızca bu salona bağlılığı " +
    "kaldırılıyor.\n\nDevam edilsin mi?"
  );
}

/** Sunucu yanıtını okunur bir mesaja çevirir. */
export function kaldirmaHataMesaji(durumKodu, govde) {
  const sunucudan = typeof govde?.hata === "string" ? govde.hata : "";

  switch (durumKodu) {
    case 401:
      return "Oturumunuz sona ermiş. Çıkıp yeniden giriş yapın.";
    case 403:
      return "Bu işlem için salon yöneticisi olmanız gerekiyor.";
    case 404:
      return "Personel kaydı bulunamadı. Sayfayı yenileyip tekrar deneyin.";
    case 409:
      // Tek 409 sebebi kendi erişimini kaldırma denemesi; sunucunun mesajı
      // zaten bunu anlatıyor ve uydurmaktan iyi.
      return sunucudan || "Bu erişim kaldırılamaz.";
    case 502:
      // Yarım kalmış olabilir ve yapılacak şey belli: tekrar çalıştırmak.
      // Sunucunun mesajı bunu söylüyor, o yüzden öne alınıyor.
      return sunucudan || "İşlem yarıda kaldı. Bir kez daha deneyin.";
    case 0:
      return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip tekrar deneyin.";
    default:
      return sunucudan || `Erişim kaldırılamadı (${durumKodu}).`;
  }
}

/** Başarılı yanıtın ekranda görünecek özeti. */
export function kaldirmaBasariMesaji(yanit) {
  const ad = yanit?.personel ? `${yanit.personel} ` : "";
  return yanit?.durum === "zaten_yok"
    ? `${ad}için zaten bir erişim yoktu; personel kaydı temizlendi.`
    : `${ad}artık bu salonun verilerini göremiyor.`;
}
