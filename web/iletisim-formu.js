// İletişim / deneme dersi formunun KARAR MANTIĞI.
//
// DOM'a dokunmuyor ve ağa çıkmıyor; tek işi "bu girdi gönderilebilir mi, nasıl
// normalleşir" sorusunu yanıtlamak. Bu depodaki desen bu: saf mantık ayrı
// modülde ve Node testiyle sınanıyor, DOM'a bağlı kısım Chromium'da sürülüyor.
//
// ### Telefon normalleştirmesi neden BURADA, sunucuda değil
//
// Sunucu (`iletisim-istegi` Edge Function) yalnızca KANONİK biçimi kabul
// ediyor: `+90` ve ardından 10 hane. Normalleştirme — insanın yazdığı
// `0541 971 50 95`, `+90 541…`, `541…` gibi biçimleri o kanonik hâle
// çevirmek — burada yapılıyor ve sebebi sınanabilirlik: Edge Function'lar
// Deno'da koşuyor ve bu depoda onlar için test koşucusu yok, yalnızca `tsc`
// denetimi var. Normalleştirmeyi oraya koymak, en ince kuralı testsiz
// bırakmak olurdu.
//
// Mantık İKİ YERDE DEĞİL: normalleştirme yalnız burada, kanonik biçim
// doğrulaması yalnız sunucuda. İkisi aynı işi yapmıyor. Formu atlayıp
// doğrudan uca istek atan biri kanonik olmayan bir numara gönderirse sunucu
// reddediyor — yani yetkili taraf hâlâ sunucu.
//
// ### Baştaki SIFIR bu dosyanın en kritik satırı
//
// `wa.me/0541…` sessizce yanlış çalışıyor (WhatsApp sıfırı ülke kodu sanıyor)
// ve aynı tuzak kaydedilen numarada da var: `+900541…` diye kaydedilen bir
// numara aranmıyor, üstelik ekranda doğru GÖRÜNÜYOR. Bu yüzden sıfır ayrıca
// sınanıyor — son 10 hane eşitliği bu hatayı YAKALAMIYOR, sıfırlı hâlde de
// son 10 hane aynı.

/**
 * Form ziyaretçiye gösterilsin mi.
 *
 * **ŞU AN KAPALI ve sebebi teknik değil, KVKK.** Form ad, telefon ve
 * e-posta topluyor; bunlar kişisel veri ve KVKK md. 10 bir aydınlatma metni
 * istiyor (veri sorumlusunun kimliği, işleme amacı, aktarım, haklar,
 * saklama süresi). Formun içinde amacı ve paylaşılmamayı söyleyen kısa bir
 * satır var ama metnin tamamı değil, ve eksik kalan iki bilgi salondan
 * gelmeli: **ticari unvan** ve **saklama süresi**.
 *
 * Yani kapalı olması bir arıza değil, sıralama: metin hazır olmadan kişisel
 * veri toplamaya başlamamak için. Sunucu tarafı (tablo, uç, hız sınırı)
 * tamamen hazır ve çalışıyor.
 *
 * **Açmak:** bu değeri `true` yapmak. Başka hiçbir şey gerekmiyor —
 * `docs/site-plani-kalanlar.md` md. 1 ve 2.
 *
 * Desen `web/uye/kayit.js` içindeki `KAYIT_ACIK`ın aynısı ve bilinçli: aynı
 * soruyu ("bu akış ziyaretçiye açık mı") iki farklı biçimde yanıtlamak,
 * ikisinden birinin gözden kaçması demekti.
 */
export const FORM_ACIK = false;

/**
 * Formun gerçek durumu.
 *
 * İki sebep var ve BİRLEŞTİRİLMEMELİ, çünkü yapılacak şey farklı:
 *
 *  - `kapali`     — bilinçli karar (yukarıdaki KVKK sıralaması). Ziyaretçi
 *                   telefon ve WhatsApp'tan ulaşıyor; ortada arıza yok.
 *  - `ayar_eksik` — `SUPABASE_TENANT_ID` tanımsız. Bu bir KURULUM eksiği ve
 *                   düzeltmesi salonun elinde.
 *
 * İkisini tek "kapalı" hâlinde toplamak, form açıldığı gün gerçek bir
 * yapılandırma eksiğini görünmez kılardı.
 *
 * @param acik ürün kararı; varsayılanı [FORM_ACIK]
 * @param tenantId `window.NONSTOP_CONFIG.tenantId`
 * @returns `{ acik: true }` ya da `{ acik: false, sebep }`
 */
export function formDurumu({ acik = FORM_ACIK, tenantId = null } = {}) {
  if (!acik) return { acik: false, sebep: "kapali" };
  if (!tenantId) return { acik: false, sebep: "ayar_eksik" };
  return { acik: true };
}

/** Sunucunun kabul ettiği tek biçim. */
export const KANONIK_TELEFON = /^\+90[0-9]{10}$/;

export const SINIRLAR = {
  adSoyad: { enAz: 2, enCok: 80 },
  mesaj: { enCok: 500 },
  eposta: { enCok: 120 },
};

/**
 * İnsanın yazdığı telefonu `+90XXXXXXXXXX` hâline çevirir.
 *
 * Çeviremezse `null` döndürüyor — istisna atmıyor, çünkü çağıran yer bir form
 * ve kullanıcıya mesaj göstermesi gerekiyor.
 */
export function telefonuNormalle(ham) {
  if (typeof ham !== "string") return null;

  // Boşluk, parantez, tire, nokta, bölü: hepsi atılıyor. `+` yalnızca BAŞTA
  // anlamlı; ortada geçen bir `+` numarayı geçersiz kılıyor.
  const temiz = ham.trim().replace(/[\s()\-./]/g, "");
  if (!/^\+?[0-9]+$/.test(temiz)) return null;

  const haneler = temiz.replace(/^\+/, "");

  let ulusal;
  if (haneler.length === 10) {
    // `5419715095` — ülke kodu yok.
    ulusal = haneler;
  } else if (haneler.length === 11 && haneler.startsWith("0")) {
    // `05419715095` — BAŞTAKİ SIFIR ATILIYOR. Korunsaydı `+900541…` çıkardı:
    // 14 karakter, kanonik desene uymaz; uysaydı da aranmayan bir numara
    // kaydedilmiş olurdu.
    ulusal = haneler.slice(1);
  } else if (haneler.length === 12 && haneler.startsWith("90")) {
    // `905419715095` ya da `+905419715095`.
    ulusal = haneler.slice(2);
  } else if (haneler.length === 13 && haneler.startsWith("090")) {
    // `0905419715095` — uluslararası çıkış kodunu da yazanlar oluyor.
    ulusal = haneler.slice(3);
  } else {
    return null;
  }

  // İlk hane 2–5 arası olmalı. Türkiye'de coğrafi numaralar 2xx–4xx, mobil
  // 5xx. SABİT HAT BİLEREK KABUL EDİLİYOR: bu bir "beni arayın" formu ve
  // sabit hat veren gerçek bir talebi reddetmek, sabit hat kaydetmekten daha
  // kötü. 0, 1, 6–9 ile başlayan 10 hane geçerli bir Türkiye numarası değil.
  if (!/^[2-5][0-9]{9}$/.test(ulusal)) return null;

  return `+90${ulusal}`;
}

/** Boş/yalnızca-boşluk değerleri `null`a indirger. */
const kirp = (d) => {
  if (typeof d !== "string") return null;
  const k = d.trim();
  return k === "" ? null : k;
};

/**
 * Formu doğrular ve gönderilecek yükü üretir.
 *
 * Hata hâlinde alan adı → mesaj eşlemesi veriyor; form her alanın altına
 * kendi mesajını yazabiliyor.
 */
export function formuDogrula(girdi = {}) {
  const hatalar = {};

  const adSoyad = kirp(girdi.adSoyad);
  if (!adSoyad) {
    hatalar.adSoyad = "Adınızı yazın.";
  } else if (adSoyad.length < SINIRLAR.adSoyad.enAz) {
    hatalar.adSoyad = "Adınız çok kısa.";
  } else if (adSoyad.length > SINIRLAR.adSoyad.enCok) {
    hatalar.adSoyad = `Adınız en fazla ${SINIRLAR.adSoyad.enCok} karakter olabilir.`;
  }

  const hamTelefon = kirp(girdi.telefon);
  const telefon = hamTelefon ? telefonuNormalle(hamTelefon) : null;
  if (!hamTelefon) {
    hatalar.telefon = "Telefon numaranızı yazın.";
  } else if (!telefon) {
    hatalar.telefon = "Numarayı kontrol edin (örnek: 0541 971 50 95).";
  }

  // E-POSTA İSTEĞE BAĞLI ve bu bilinçli: zorunlu olsaydı telefonla
  // ulaşılmasını isteyen biri formu bırakırdı. Doğrulaması da kasten gevşek —
  // `@` ve bir nokta arıyor. Sıkı bir desen gerçek adresleri reddetmesiyle
  // ünlü ve buradaki bedeli yüksek: reddedilen her adres kaybedilen bir talep.
  const eposta = kirp(girdi.eposta);
  if (eposta) {
    if (eposta.length > SINIRLAR.eposta.enCok) {
      hatalar.eposta = "E-posta adresi çok uzun.";
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(eposta)) {
      hatalar.eposta = "E-posta adresini kontrol edin.";
    }
  }

  const mesaj = kirp(girdi.mesaj);
  if (mesaj && mesaj.length > SINIRLAR.mesaj.enCok) {
    hatalar.mesaj = `Mesaj en fazla ${SINIRLAR.mesaj.enCok} karakter olabilir.`;
  }

  if (Object.keys(hatalar).length > 0) return { gecerli: false, hatalar };

  return {
    gecerli: true,
    yuk: {
      adSoyad,
      telefon,
      // Boş alanlar `null` gidiyor, boş dize değil: tablodaki kolonlar `null`
      // kabul ediyor ve boş dize "yazılmış ama boş" gibi görünürdü.
      eposta: eposta ?? null,
      mesaj: mesaj ?? null,
    },
  };
}

/**
 * Tuzak alanı dolu mu (bot mu).
 *
 * Alan ekranda görünmüyor, yani insan onu dolduramıyor; dolduran şey formdaki
 * bütün girdileri tarayıp yazan bir bot. Dolu geldiğinde form BAŞARILI gibi
 * davranıyor ama hiçbir şey göndermiyor: bota "başarısız" demek, denemeye
 * devam etmesi için bilgi vermek olurdu.
 *
 * Tek başına yeterli DEĞİL — bu formu bilerek hedefleyen bir betik alanı boş
 * bırakır. Asıl koruma sunucudaki hız sınırı; bu yalnız ucuz olan kısmı.
 */
export function tuzagaDustu(deger) {
  return kirp(deger) !== null;
}
