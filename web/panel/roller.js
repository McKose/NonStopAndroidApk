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
