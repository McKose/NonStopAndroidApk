// Kurulumsuz deneme verisi.
//
// Panele `?demo` ekleyerek açıldığında sunucuya hiç gidilmiyor; buradaki örnek
// satırlar gösteriliyor. Amaç, ekranı değerlendirmek için Supabase ayarı,
// hesap ve internet gerekmesin — küçük değişikliklerde bakıp geri bildirim
// vermek kolay olsun.
//
// Veri **sunucudan gelen biçimin aynısı**: snake_case kolonlar, kuruş cinsinden
// tutarlar, epoch ms tarihler, tombstone alanı. Farklı olsaydı demo ekranı
// gerçekte hiç görülmeyecek bir şeyi gösterirdi — ve ekranı ona göre
// ayarlamak, gerçek veride bozuk görünmesine yol açardı.

const GUN = 24 * 60 * 60 * 1000;
const simdi = Date.now();

/** Tutarlı bir kimlik üreteci; demo verisinin sabit kalması için. */
const kimlik = (onEk, n) => `${onEk}-demo-${n}`;

const UYELER = [
  ["Ayşe Yılmaz", "+905321112233", 12 * GUN, 8, 240000, "ACTIVE"],
  ["Mehmet Kaya", "+905321112244", 45 * GUN, null, 480000, "ACTIVE"],
  ["Zeynep Demir", "+905321112255", 3 * GUN, 2, 180000, "ACTIVE"],
  ["Ali Şahin", "+905321112266", -8 * GUN, 0, 180000, "ACTIVE"],
  ["Elif Arslan", "+905321112277", 20 * GUN, 10, 300000, "FROZEN"],
  ["Burak Öztürk", "+905321112288", 6 * GUN, 4, 240000, "ACTIVE"],
  ["Selin Aydın", "+905321112299", -30 * GUN, 0, 150000, "ARCHIVED"],
].map(([ad, tel, bitisFark, kalan, odenen, durum], i) => ({
  id: kimlik("uye", i),
  tenant_id: "demo",
  full_name: ad,
  phone: tel,
  status: durum,
  end_date_ms: simdi + bitisFark,
  start_date_ms: simdi + bitisFark - 90 * GUN,
  remaining_sessions: kalan,
  price_paid_minor: odenen,
  payment_status: i % 3 === 0 ? "PENDING" : "PAID",
  created_at_ms: simdi - 120 * GUN,
  updated_at_ms: simdi - GUN,
  deleted_at_ms: null,
}));

const PAKETLER = [
  ["Aylık Fitness", "FITNESS", "INDIVIDUAL", 30, 12, 240000],
  ["Reformer 8 Ders", "REFORMER", "DUET", 60, 8, 480000],
  ["Sınırsız Abonman", "FITNESS", "INDIVIDUAL", 365, null, 1800000],
  ["Fonksiyonel Grup", "FUNCTIONAL", "GROUP", 30, 16, 180000],
].map(([ad, tur, kat, gun, seans, fiyat], i) => ({
  id: kimlik("paket", i),
  tenant_id: "demo",
  name: ad,
  type: tur,
  category: kat,
  validity_days: gun,
  session_count: seans,
  base_price_minor: fiyat,
  is_active: true,
  created_at_ms: simdi - 200 * GUN,
  updated_at_ms: simdi - 30 * GUN,
  deleted_at_ms: null,
}));

const RANDEVULAR = Array.from({ length: 8 }, (_, i) => ({
  id: kimlik("randevu", i),
  tenant_id: "demo",
  member_id: kimlik("uye", i % UYELER.length),
  staff_id: kimlik("personel", 0),
  training_type: ["FITNESS", "REFORMER", "FUNCTIONAL"][i % 3],
  start_time_ms: simdi - i * GUN,
  end_time_ms: simdi - i * GUN + 60 * 60 * 1000,
  state: ["COMPLETED", "COMPLETED", "SCHEDULED", "NO_SHOW"][i % 4],
  session_value_minor: 20000,
  settled_at_ms: i % 4 < 2 ? simdi - i * GUN : null,
  created_at_ms: simdi - (i + 5) * GUN,
  updated_at_ms: simdi - i * GUN,
  deleted_at_ms: null,
}));

const DEFTER = [
  ["PAYMENT", "MEMBERSHIP", 240000, "CASH", "Ayşe Yılmaz — aylık paket", 2],
  ["PAYMENT", "MARKET", 4500, "CARD", "Su + protein bar", 1],
  ["EXPENSE", "RENT", 1500000, "CARD", "Salon kirası", 5],
  ["PAYMENT", "MEMBERSHIP", 480000, "CARD", "Mehmet Kaya — reformer", 8],
  ["EXPENSE", "COMMISSION", 96000, "CASH", "Eğitmen hakedişi", 3],
  ["CHARGE", "MEMBERSHIP", 180000, "CASH", "Zeynep Demir — paket borcu", 10],
].map(([tur, kat, tutar, yontem, aciklama, gunOnce], i) => ({
  id: kimlik("defter", i),
  tenant_id: "demo",
  type: tur,
  category: kat,
  amount_minor: tutar,
  payment_method: yontem,
  description: aciklama,
  occurred_at_ms: simdi - gunOnce * GUN,
  created_at_ms: simdi - gunOnce * GUN,
  reverses_id: null,
}));

const TABLOLAR = {
  gym_members: UYELER,
  gym_packages: PAKETLER,
  appointments: RANDEVULAR,
  ledger_entries: DEFTER,
};

/**
 * Sunucu yerine örnek veri döndüren istemci.
 *
 * `SupabaseClient` ile **aynı yüzeyi** taşıyor; app.js hangisiyle çalıştığını
 * bilmiyor. Ayrı bir kod yolu olsaydı demo ekranı gerçek ekrandan sapabilir ve
 * demoda düzgün görünen bir şey gerçekte bozuk olabilirdi.
 */
export function demoIstemcisi() {
  // Demo **çıkış yapmış** başlıyor: giriş ekranı da değerlendirilecek şeyin
  // parçası. Doğrudan panele girseydi giriş ekranındaki alan adları, hata
  // mesajları ve düğme durumları hiç görülmezdi.
  let oturum = null;

  const yeniOturum = (eposta) => ({
    access_token: "demo",
    expires_at_ms: Date.now() + 60 * 60 * 1000,
    email: eposta || "demo@nonstopstudio.tr",
    gym_id: "demo",
    gym_name: "NonStop Studio (demo)",
    role: "ADMIN",
  });

  return {
    yapilandirildiMi: true,

    oturumOku: () => oturum,
    oturumYaz: (yeni) => { oturum = yeni; },
    oturumSil: () => { oturum = null; },

    /** Demo modda şifre kontrolü yok; her deneme kabul ediliyor. */
    async girisYap(eposta) {
      oturum = yeniOturum(eposta);
      return { tur: "tamam", oturum };
    },

    async oku(tablo) {
      if (!oturum) return { tur: "oturumsuz" };
      return { tur: "tamam", satirlar: TABLOLAR[tablo] ?? [] };
    },
  };
}

/** Adres çubuğunda `?demo` var mı. */
export function demoMu() {
  return new URLSearchParams(globalThis.location?.search ?? "").has("demo");
}
