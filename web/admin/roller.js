// Hangi rolün hangi sekmeyi göreceği.
//
// Bu tablo, uygulamadaki `AppDestination.visibleTo` kuralının **kopyası**
// (`shared/src/commonMain/kotlin/com/gymapp/data/access/RoleAccess.kt`).
// Kopya olması bir risk ve o risk bu projede bir kez gerçekleşti: aynı karar
// iki ekranda birbirinden habersiz duruyordu ve biri değişince kısıt gerçek bir
// kısıt olmaktan çıkmıştı.
//
// Bu yüzden kopya **sınanıyor**: `roller.test.js` Kotlin dosyasını okuyup
// buradaki tabloyla karşılaştırıyor. Kotlin tarafındaki kural değişirse panel
// testi düşüyor. Yani bu bir "umut edilen kopya" değil, kontrol edilen kopya.
//
// ### Bu bir güvenlik sınırı DEĞİL
// Sunucudaki okuma kuralları (migrasyon `0004`) salona bağlı **her** role bütün
// tabloları açıyor; rol yalnızca yazmayı kısıtlıyor. Buradaki gizleme arayüz
// kararı: eğitmenin işine yaramayan ve yanlış anlaşılması kolay bir ekranı
// yoluna koymamak. Paneli kandıran biri veriyi API'den yine okuyabilir — gerçek
// sınır yazma tarafında ve o sunucuda. Kotlin tarafındaki not da aynı şeyi
// söylüyor; ikisi aynı sebeple aynı kararı veriyor.

/**
 * Sekme → o sekmeyi görebilen roller.
 *
 * Anahtarlar panelin sekme adları, değerler Kotlin'deki `StaffRole` sabitleri.
 * Eşleme [SEKME_HEDEFI] üzerinden Kotlin'deki `AppDestination` adlarına bağlı.
 */
export const SEKME_ROLLERI = {
  ozet: ["ADMIN", "MANAGER", "TRAINER"],
  uyeler: ["ADMIN", "MANAGER", "TRAINER"],
  paketler: ["ADMIN", "MANAGER", "TRAINER"],
  randevular: ["ADMIN", "MANAGER", "TRAINER"],
  market: ["ADMIN", "MANAGER", "TRAINER"],
  satislar: ["ADMIN", "MANAGER", "TRAINER"],
  personel: ["ADMIN", "MANAGER", "TRAINER"],
  // Salonun tüm parası. Eğitmenin günlük işinde karşılığı yok ve tek ekranda
  // salonun tamamının cirosunu göstermek, göstermemekten çok soru doğuruyor.
  finans: ["ADMIN", "MANAGER"],

  // ─── Panele özgü bölümler ───────────────────────────────────────────────
  // Bu ikisinin uygulamada karşılığı YOK ve olması da gerekmiyor; ikisi de
  // web tarafına ait işler. Roller sunucudaki yazma kurallarıyla aynı seviyede
  // tutuluyor (migrasyon 0005: ADMIN + MANAGER) — panel sunucudan daha geniş
  // bir kapı açsaydı kullanıcı "kaydet" deyip sessizce reddedilirdi.
  duyurular: ["ADMIN", "MANAGER"],
  "uye-hesaplari": ["ADMIN", "MANAGER"],

  // ÜÇ ROLE DE AÇIK ve bu, sunucudaki OKUMA kuralıyla aynı seviye
  // (migrasyon `0010`: `tenant_id in (select user_gym_ids())`, yani salona
  // bağlı her rol). Eğitmene de açık olması bilinçli: talebi arayacak kişi
  // eğitmen olabilir ve listeyi görmeyen biri arayamaz.
  //
  // Bölüm şu an SALT OKUNUR. Durum değiştirme (arandı/kapandı) eklendiğinde
  // o denetimlerin yalnız ADMIN + MANAGER'a gösterilmesi gerekecek, çünkü
  // sunucudaki YAZMA kuralı onlara açık. Yukarıdaki not tam bu yüzden var:
  // panel sunucudan geniş bir kapı açarsa eğitmen "arandı" der ve sessizce
  // reddedilir.
  talepler: ["ADMIN", "MANAGER", "TRAINER"],

  // YALNIZCA ADMIN — duyurular/üye-hesapları gibi MANAGER'a da açılmadı.
  //
  // Bu bölüm kimin uygulamaya girebileceğine ve hangi yetkiyle gireceğine
  // karar veriyor; yetki verebilen kişi kendi yetkisini de yükseltebilir.
  // Sunucudaki Edge Function da tam olarak `ADMIN` istiyor (`personel-davet`).
  // Panel daha geniş bir kapı açsaydı müdür formu doldurur, "Davet et" der ve
  // sunucudan 403 alırdı — yapabileceği bir şey olmadan.
  "personel-erisim": ["ADMIN"],
};

/**
 * Uygulamada karşılığı olmayan bölümleri işaretler.
 *
 * [SEKME_HEDEFI] normalde her sekmeyi Kotlin'deki bir `AppDestination`a
 * bağlıyor ve test bu eşleşmeyi zorunlu tutuyor. Panele özgü bir bölüm o
 * kontrolden **gerekçesiyle** muaf tutuluyor: sessizce muaf tutmak, yarın
 * gerçekten unutulmuş bir eşleşmeyi de görünmez yapardı.
 */
export const PANEL_OZEL = "PANEL_OZEL";

/** Panele özgü her bölümün neden uygulamada karşılığı olmadığı. */
export const PANEL_OZEL_GEREKCE = {
  duyurular:
    "Herkese açık sitenin içeriği. Uygulama duyuru göstermiyor ve göstermesi " +
    "için sebep yok — bu bölüm siteyi besliyor.",
  "uye-hesaplari":
    "Üye kaydını bir Supabase hesabına bağlama. Uygulamada üye girişi diye bir " +
    "şey yok; bağ yalnızca web'deki üye alanı için anlamlı.",
  "personel-erisim":
    "Personele Auth hesabı açıp salon yetkisi verme. Uygulamada karşılığı yok " +
    "ve olmamalı: iş sunucudaki `service_role` anahtarıyla yapılıyor ve o " +
    "anahtar hiçbir istemciye — telefona da — konulamaz.",
  talepler:
    "Siteden gelen deneme dersi talepleri. Uygulamada karşılığı yok çünkü " +
    "kaynağı web: formu dolduran kişinin hesabı bile yok. Uygulamaya " +
    "eklenebilir ama ayrı bir karar; bugün talebi gören ve arayan kişi " +
    "panelin başında duruyor.",
};

/**
 * Panel sekmesi → uygulamadaki `AppDestination` sabiti.
 *
 * Testin iki tarafı eşleştirebilmesi için gerekli. Adların birebir aynı olmasını
 * beklemek yerine eşleme açık yazılıyor: panelin sekme adları Türkçe ve
 * kullanıcıya görünüyor, Kotlin sabitleri ise kod adı.
 *
 * `satislar` bilinçli olarak `MARKET`e bağlı: satış market işinin parçası ve
 * uygulamada ayrı bir ekranı yok — market ekranının içinde. Panelde ayrı sekme
 * olması yalnızca liste uzunluğuyla ilgili bir gösterim kararı; görünürlük
 * kuralı market ile aynı olmalı, aksi hâlde panel uygulamanın vermediği bir
 * kararı vermiş olurdu.
 */
export const SEKME_HEDEFI = {
  ozet: "DASHBOARD",
  uyeler: "MEMBERS",
  paketler: "PACKAGES",
  randevular: "CALENDAR",
  market: "MARKET",
  satislar: "MARKET",
  personel: "PERSONNEL",
  finans: "FINANCE",

  // Uygulamada karşılığı yok; gerekçeleri PANEL_OZEL_GEREKCE içinde.
  duyurular: PANEL_OZEL,
  "uye-hesaplari": PANEL_OZEL,
  "personel-erisim": PANEL_OZEL,
  talepler: PANEL_OZEL,
};

/** Bu rol bu sekmeyi görebilir mi? */
export function sekmeGorunur(sekme, rol) {
  const roller = SEKME_ROLLERI[sekme];
  // Tanımsız sekme **gizleniyor**, gösterilmiyor. Yeni bir sekme eklenip
  // buraya yazılmazsa sessizce herkese açılmasın: eksiklik görünür olsun.
  if (!roller) return false;
  return roller.includes(rol);
}

/** Bu rolün görebildiği sekmeler, tanımdaki sırayla. */
export function gorunurSekmeler(rol) {
  return Object.keys(SEKME_ROLLERI).filter((s) => sekmeGorunur(s, rol));
}
