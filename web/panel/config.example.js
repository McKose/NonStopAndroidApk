// Panelin bağlanacağı proje.
//
// Bu dosyayı `config.js` adıyla kopyalayıp değerleri doldurun. `config.js`
// depoya işlenmiyor (.gitignore): değerler kuruluma özgü, kaynak koda değil.
//
// `anonKey` gizli bir şey değil — tarayıcıda görünmesi normaldir ve tek başına
// hiçbir veriye erişemez; her sorgu giriş yapan kullanıcıya göre süzülür.
// `service_role` anahtarı ise **asla** buraya konmaz: o anahtar bütün erişim
// kurallarını baypas eder ve panelde görünür olması tüm verinin herkese açık
// olması demektir.
// `tenantId` salonun kimliği (`public.gyms.id`). YALNIZCA üye kayıt akışı
// kullanıyor: kayıt isteği bir salona ait olmak zorunda ve giriş yapmamış bir
// ziyaretçi salon listesini okuyamıyor (okuyabilseydi bütün salonlar herkese
// açık olurdu). Değeri panelde "Üye Hesapları" sekmesinin altında yazıyor.
//
// Boş bırakılırsa panel ve üye girişi çalışır, yalnızca KAYIT kapalı kalır ve
// ekranda bunu söyler.
window.NONSTOP_CONFIG = {
  url: "https://<proje-ref>.supabase.co",
  anonKey: "<anon public anahtarı>",
  tenantId: "<salon kimliği — panelden kopyalayın>",
};
