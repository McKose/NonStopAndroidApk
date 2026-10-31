# Kalan işler

Projenin açık uçlarının **tek dizini**. Üç yüzey (Android uygulaması, web
sitesi, sunucu) ve birkaç belgeye dağılmış maddeleri bir arada görmek için.

Burası **dizin, kaynak değil**. Her maddenin gerekçesi ve ayrıntısı kendi
belgesinde duruyor; burada yalnızca tek satırlık durum ve nereye bakılacağı
var. Ayrıntıyı buraya kopyalamak, aynı bilginin iki yerde ayrışması demek
olurdu — bu depoda tam olarak o yaşandı: üç ayrı madde kapandığı hâlde
belgelerde "yapılacak" görünmeye devam etti (`0006` migrasyonu, Room şema
dosyaları, salon fotoğrafı).

Son gözden geçirme: 31 Ekim 2026, `master` (`8a464d7`).

---

## Önce: seni bekleyenler

Bunlar bende yapılamaz; hiçbiri kod işi değil.

| # | Ne | Neyi açar | Ayrıntı |
|---|---|---|---|
| 1 | **Yayın anahtarı + 4 gizli anahtar** | İmzalı APK'nın **tek** engeli | `docs/yayin.md` |
| 2 | **KVKK aydınlatma metni için bilgiler** (ticari unvan, adres, saklama süresi) | Sitedeki yasal açığı | `docs/site-plani-kalanlar.md` md. 1 |
| 3 | **Sızmış şifre koruması** (Supabase panelinden) | Zayıf şifreleri eler | `supabase/README.md` → "Panelden açılabilecek iki ayar" |
| 4 | **MX kaydı** (Turhost panelinde) | Kurumsal e-postayı | `docs/site-yonetimi.md` → "E-posta" |
| 5 | **Karşılama videosu** (`kahraman.mp4`) | Banner videosunu (isteğe bağlı) | `web/varliklar/BENIOKU.md` |

1. madde en kritik olanı: uygulama kod olarak hazır ama imzalanamadığı için
dağıtılamıyor.

3. madde **ücretli plan** gerektiriyor; ücretsiz plandaysan seçenek
görünmez ve bu madde şimdilik kapalı sayılır.

`SUPABASE_TENANT_ID` gizli anahtarı bu listeden **çıktı**: yalnızca web'den
üye kaydı için gerekiyordu ve o akış şimdilik kapatıldı (aşağıya bakın).
Kayıt yeniden açılacağı gün tekrar gerekecek.

---

## Android uygulaması

**Kod tarafında açık iş yok.** 13 ekranın 13'ünün de görüntü testi var,
`TODO`/`FIXME` yok, CI `master`ta yeşil. Kalan tek şey yukarıdaki 1. madde.

Yalnızca panelde olan üç bölüm (Duyurular, Üye Hesapları, Personel Erişimi)
eksik değil, **bilinçli kapsam kararı** — gerekçesi `docs/site-yonetimi.md`
"Faz 5" ve `supabase/README.md`.

## Web sitesi

**Web'den üye kaydı şimdilik KAPALI** (bilinçli karar). Üye alanına giriş
çalışıyor; kapanan tek şey ziyaretçinin kendi kendine hesap açması. Geri
açmak `web/uye/kayit.js` içindeki `KAYIT_ACIK` değerini `true` yapmak;
ayrıntı `docs/site-yonetimi.md` → "3b. Üye kayıtları".

Dokuz açık madde, öncelik sırasıyla `docs/site-plani-kalanlar.md` içinde.
İlk üçü:

1. **KVKK aydınlatma metni** — yasal. Üye kaydı sağlık verisi topluyor;
   formda açık rıza var ama aydınlatma metni yok ve açık rıza onun yerini
   tutmuyor.
2. **WhatsApp ile temas** — planda birincil eylemdi, sitede yok.
3. **İletişim / deneme dersi formu** — planın ana dönüşüm noktasıydı.

Kalan altı madde (künye, arama motoru kayıtları, içerik bölümleri, video,
ölçüm, Lighthouse hedefi) daha düşük öncelikli.

## Sunucu

Üç madde, hepsi `supabase/README.md` → "Henüz yapılmadı" içinde ve hiçbiri
acil değil:

- Personel kaydını **tamamen silme** yolu yok (erişimi kaldırma var). Bilinçli:
  geçmiş satışlar ve hakedişler o satıra bağlı.
- iOS tarafında oturum saklama yok — iOS uygulaması yazılmadığı için sırası
  gelmedi.
- Room'un `MigrationTestHelper` aracı kullanılmıyor; kalan fark küçük ve
  gerekçesi `MigrationsTest` başında yazılı.

---

## Temizlik

`master`a merge edilmiş ama silinmemiş uzak dallar var
(`claude/...`). Zararsız; silinmeleri geçmişi etkilemiyor.
