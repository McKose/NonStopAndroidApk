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
window.NONSTOP_CONFIG = {
  url: "https://<proje-ref>.supabase.co",
  anonKey: "<anon public anahtarı>",
};
