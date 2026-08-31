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

// Dosya kontrolü gerçek istemciden alınıyor, kopyalanmıyor: demoda kabul edilip
// gerçekte reddedilen (ya da tersi) bir dosya, demonun amacını bozardı.
import { gorselKontrol } from "./supabase.js";

const GUN = 24 * 60 * 60 * 1000;
const simdi = Date.now();

/** Tutarlı bir kimlik üreteci; demo verisinin sabit kalması için. */
const kimlik = (onEk, n) => `${onEk}-demo-${n}`;

const UYELER = [
  ["Ayşe Yılmaz", "+905321112233", 12 * GUN, 8, 240000, "ACTIVE", "ayse@ornek.com"],
  ["Mehmet Kaya", "+905321112244", 45 * GUN, null, 480000, "ACTIVE", "mehmet@ornek.com"],
  ["Zeynep Demir", "+905321112255", 3 * GUN, 2, 180000, "ACTIVE", "zeynep@ornek.com"],
  ["Ali Şahin", "+905321112266", -8 * GUN, 0, 180000, "ACTIVE", null],
  ["Elif Arslan", "+905321112277", 20 * GUN, 10, 300000, "FROZEN", "elif@ornek.com"],
  ["Burak Öztürk", "+905321112288", 6 * GUN, 4, 240000, "ACTIVE", null],
  ["Selin Aydın", "+905321112299", -30 * GUN, 0, 150000, "ARCHIVED", "selin@ornek.com"],
].map(([ad, tel, bitisFark, kalan, odenen, durum, eposta], i) => ({
  id: kimlik("uye", i),
  tenant_id: "demo",
  full_name: ad,
  phone: tel,
  // Panel bu alanda arama yapıyor ve şemada var (`gym_members.email`), ama demo
  // verisi taşımıyordu: önizlemede e-posta aramasını denemek mümkün değildi.
  // Bir kısmı bilinçli olarak `null` — alan şemada isteğe bağlı ve arama boş
  // değerde çökmemeli.
  email: eposta,
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
  // Panel randevu notlarında arama yapıyor; alan şemada isteğe bağlı, o yüzden
  // bir kısmı bilinçli olarak `null` — arama boş değerde çökmemeli.
  notes: [null, "Sakatlık sonrası dönüş", null, "Haber vermeden gelmedi"][i % 4],
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

const URUNLER = [
  ["Su 0.5L", "DRINK", 1500],
  ["Protein Bar", "SNACK", 4500],
  ["Shaker", "EQUIPMENT", 12000],
  ["Havlu", "EQUIPMENT", 8000],
  ["İzotonik", "DRINK", 3000],
].map(([ad, kat, fiyat], i) => ({
  id: kimlik("urun", i),
  tenant_id: "demo",
  name: ad,
  category: kat,
  price_minor: fiyat,
  image_url: null,
  is_active: true,
  created_at_ms: simdi - 150 * GUN,
  updated_at_ms: simdi - 10 * GUN,
  deleted_at_ms: null,
}));

/**
 * Stok hareketleri: her ürün farklı bir duruma denk geliyor.
 *
 * Bilinçli olarak "hepsi bol" değil — ekranın tükenen, azalan ve **negatif**
 * durumları nasıl gösterdiği demo üzerinden görülebilsin. Negatif stok gerçek
 * bir veri sorunu (fazla satış ya da eksik alım kaydı) ve panelin onu gizlemek
 * yerine göstermesi bilinçli bir karar; demoda karşılığı olmasa o karar hiç
 * görünmezdi.
 *
 * `İzotonik`in hiç hareketi yok: hiç hareketi olmayan ürün sıfır sayılıyor.
 */
const STOK_HAREKETLERI = [
  ["urun-demo-0", 48, "PURCHASE", 20],
  ["urun-demo-0", -6, "SALE", 3],
  ["urun-demo-0", -2, "SALE", 1],
  ["urun-demo-1", 24, "PURCHASE", 30],
  ["urun-demo-1", -21, "SALE", 5],
  ["urun-demo-2", 6, "PURCHASE", 40],
  ["urun-demo-2", -6, "SALE", 2],
  ["urun-demo-3", 2, "PURCHASE", 60],
  ["urun-demo-3", -5, "SALE", 4],
].map(([urun, delta, sebep, gunOnce], i) => ({
  id: kimlik("stok", i),
  tenant_id: "demo",
  product_id: urun,
  quantity_delta: delta,
  reason: sebep,
  order_id: sebep === "SALE" ? kimlik("siparis", i % 4) : null,
  note: null,
  occurred_at_ms: simdi - gunOnce * GUN,
  created_at_ms: simdi - gunOnce * GUN,
}));

const SIPARISLER = [
  [0, 9000, 0, "CASH", "PAID", "POST_DELIVERY", 1],
  [1, 4500, 500, "CARD", "PAID", "POST_DELIVERY", 2],
  [2, 12000, 0, "CARD", "PENDING", "PRE_DELIVERY", 4],
  [null, 3000, 0, "CASH", "PAID", "POST_DELIVERY", 6],
].map(([uyeIndex, tutar, indirim, yontem, odeme, teslim, gunOnce], i) => ({
  id: kimlik("siparis", i),
  tenant_id: "demo",
  // Üyesiz satış gerçek bir durum: salona gelen misafir de ürün alıyor.
  member_id: uyeIndex === null ? null : kimlik("uye", uyeIndex),
  total_price_minor: tutar,
  discount_minor: indirim,
  final_price_minor: tutar - indirim,
  payment_method: yontem,
  payment_status: odeme,
  delivery_status: teslim,
  date_ms: simdi - gunOnce * GUN,
  notes: i === 2 ? "Sipariş edildi, gelince teslim" : null,
  created_at_ms: simdi - gunOnce * GUN,
  updated_at_ms: simdi - gunOnce * GUN,
  deleted_at_ms: null,
}));

// Son alan: Auth hesabı kimliği (`null` = hesabı yok).
//
// İkisi bağlı, ikisi değil — bilerek KARIŞIK. Hepsi bağlı olsaydı önizlemede
// "davet et" akışı hiç görünmezdi; hiçbiri bağlı olmasaydı "zaten erişimi var"
// hâli görünmezdi. Bölümün asıl işi bu iki durumu ayırt ettirmek.
const PERSONEL = [
  ["Cağatay Köse", "Salon sahibi", "ADMIN", "Merkez", 0, 0, "cagatay", "auth-demo-0"],
  ["Deniz Yıldız", "Müdür", "MANAGER", "Merkez", 500, 3500000, "deniz", "auth-demo-1"],
  ["Emre Tan", "Eğitmen", "TRAINER", "Merkez", 4000, 0, "emre", null],
  ["Naz Kılıç", "Reformer eğitmeni", "TRAINER", "Merkez", 3500, 1200000, "naz", null],
].map(([ad, unvan, rol, sube, hakedis, maas, takma, hesap], i) => ({
  id: kimlik("personel", i),
  tenant_id: "demo",
  full_name: ad,
  title: unvan,
  role: rol,
  branch: sube,
  commission_basis_points: hakedis,
  monthly_salary_minor: maas,
  phone: `+90532999${String(i).padStart(4, "0")}`,
  nickname: takma,
  auth_user_id: hesap,
  is_active: true,
  created_at_ms: simdi - 300 * GUN,
  updated_at_ms: simdi - 20 * GUN,
  deleted_at_ms: null,
}));

/**
 * Salon yetkileri — kimin uygulamaya girebildiği.
 *
 * `staff.auth_user_id` ile `gym_users` AYRI şeyler ve bu bölüm tam da farkı
 * göstermek için var: birincisi "bu personel hangi hesaba ait", ikincisi "o
 * hesap salona girebilir mi ve hangi yetkiyle". Demo verisinde ikisi tutarlı
 * tutuluyor, çünkü tutarsız hâli (hesabı bağlı ama yetkisi yok) gerçek bir
 * arıza ve önizlemede normalmiş gibi görünmemeli.
 *
 * `role` alanı `staff.role`u DEĞİL kendi değerini taşıyor; gerçek yetkiyi
 * belirleyen bu tablo.
 */
const YETKILER = [
  { user_id: "auth-demo-0", gym_id: "demo", role: "ADMIN" },
  { user_id: "auth-demo-1", gym_id: "demo", role: "MANAGER" },
];

const DUYURULAR = [
  ["Açık Ders: Reformer Tanışma", "Reformer pilatesi hiç denemediyseniz bu seans sizin için. Kontenjan sınırlı.", "EVENT", 3, 10, true],
  ["Yeni Üyelere %20", "Ağustos ayı boyunca ilk üyeliğinizde geçerli.", "AD", 0, 25, true],
  ["Salon Bakımı", "Cumartesi 09:00-12:00 arası fitness alanı kapalı olacak.", "NOTICE", 5, 6, false],
].map(([baslik, metin, tur, baslarGun, biterGun, yayinda], i) => ({
  id: kimlik("duyuru", i),
  tenant_id: "demo",
  title: baslik,
  body: metin,
  kind: tur,
  image_url: null,
  starts_at_ms: simdi + baslarGun * GUN,
  ends_at_ms: simdi + biterGun * GUN,
  is_published: yayinda,
  sort_order: i,
  created_at_ms: simdi - 5 * GUN,
  updated_at_ms: simdi - GUN,
  deleted_at_ms: null,
}));

// İki üyenin hesabı bağlı, kalanların değil: "Bağlı / Bağlı değil" ayrımı
// demoda da görünsün.
const UYE_HESAPLARI = [0, 2].map((uyeIndex) => ({
  member_id: kimlik("uye", uyeIndex),
  tenant_id: "demo",
  auth_user_id: `00000000-0000-0000-0000-00000000000${uyeIndex}`,
  linked_by: null,
  linked_at_ms: simdi - 20 * GUN,
}));

// Bekleyen kayıt istekleri. Biri üyelik kaydıyla telefonu TUTUYOR (eşleştirme
// önerisi görünsün), biri tutmuyor (personelin elle seçtiği hâl de görünsün).
const KAYIT_ISTEKLERI = [
  {
    auth_user_id: "00000000-0000-0000-0000-0000000000f1",
    tenant_id: "demo",
    full_name: UYELER[1].full_name,
    phone: UYELER[1].phone,
    email: UYELER[1].email,
    note: "Salı akşam grubundayım.",
    state: "PENDING",
    created_at_ms: simdi - 2 * GUN,
    updated_at_ms: simdi - 2 * GUN,
  },
  {
    auth_user_id: "00000000-0000-0000-0000-0000000000f2",
    tenant_id: "demo",
    full_name: "Yeni Başvuran",
    phone: "+905320000777",
    email: "yeni@ornek.test",
    note: null,
    state: "PENDING",
    created_at_ms: simdi - 4 * GUN,
    updated_at_ms: simdi - 4 * GUN,
  },
];

const TABLOLAR = {
  gym_members: UYELER,
  gym_packages: PAKETLER,
  appointments: RANDEVULAR,
  ledger_entries: DEFTER,
  products: URUNLER,
  stock_movements: STOK_HAREKETLERI,
  orders: SIPARISLER,
  staff: PERSONEL,
  gym_users: YETKILER,
  announcements: DUYURULAR,
  member_accounts: UYE_HESAPLARI,
  member_link_requests: KAYIT_ISTEKLERI,
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
      // `kesildi` gerçek istemcideki alanın karşılığı ve demoda her zaman
      // `false`: örnek veri sınıra dayanmıyor. Alanın burada da bulunması
      // gerekiyor çünkü app.js iki istemciyi ayırt etmiyor; eksik olsaydı
      // `undefined` gelir ve stok sekmesi demoda gerçekte olmayan bir yoldan
      // geçerdi.
      return { tur: "tamam", satirlar: TABLOLAR[tablo] ?? [], kesildi: false };
    },

    /**
     * Demo modda yazma **kabul ediliyor ama kalıcı değil**.
     *
     * Gerçek istemciyle aynı yüzeyi taşıması şart: app.js hangisiyle
     * çalıştığını bilmiyor ve eksik bir yöntem demoda çökmeye yol açardı.
     *
     * Satır listeye eklenmiyor: demo verisi sabit kalmalı ki ekranı
     * değerlendiren kişi her açtığında aynı şeyi görsün. Formun çalıştığı,
     * "kaydedildi" mesajının görünmesinden anlaşılıyor.
     */
    async yaz() {
      if (!oturum) return { tur: "oturumsuz" };
      return { tur: "tamam" };
    },

    /**
     * Demo modda yükleme yapılmıyor, sahte bir adres dönüyor.
     *
     * Gerçek istemcideki yöntemin karşılığı olması şart — app.js iki istemciyi
     * ayırt etmiyor ve eksik bir yöntem demoyu çökertirdi. Dönen adres
     * depodaki bir dosyaya değil, depoda **bulunmayan** bir yola işaret
     * ediyor: demo ekranını değerlendiren kişi görselin yüklenmediğini görsün,
     * yüklendiğini sanmasın.
     */
    /**
     * Demo modda davet **kabul ediliyor ama kalıcı değil**.
     *
     * Gerçek istemciyle aynı yüzeyi taşıması şart: app.js hangisiyle
     * çalıştığını bilmiyor ve eksik bir yöntem demoyu çökertirdi.
     *
     * Sahte bir geçici şifre dönüyor — ekranın en kritik parçası o kutu
     * (bir kez görünüyor, bir daha alınamıyor) ve önizlemeyi değerlendiren
     * kişinin onu görmesi gerekiyor. Şifre sabit değil, `crypto` ile
     * üretiliyor: sabit olsaydı ekran görüntüsü alan biri onu gerçek bir
     * şifre sanabilirdi.
     */
    async personelDavetEt({ personelId, yetki }) {
      if (!oturum) return { tur: "oturumsuz" };

      const kisi = PERSONEL.find((p) => p.id === personelId);
      const harfler = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
      const rastgele = Array.from(
        crypto.getRandomValues(new Uint8Array(14)),
        (b) => harfler[b % harfler.length],
      ).join("");

      return {
        tur: "tamam",
        yanit: {
          durum: "hesap_acildi",
          personel: kisi?.full_name ?? "Personel",
          yetki,
          gecici_sifre: rastgele,
        },
      };
    },

    async dosyaYukle(dosya) {
      if (!oturum) return { tur: "oturumsuz" };
      const hata = gorselKontrol(dosya);
      if (hata) return { tur: "hata", mesaj: hata };
      return { tur: "tamam", adres: `https://ornek.test/demo/${encodeURIComponent(dosya.name)}` };
    },
  };
}

/** Adres çubuğunda `?demo` var mı. */
export function demoMu() {
  return new URLSearchParams(globalThis.location?.search ?? "").has("demo");
}
