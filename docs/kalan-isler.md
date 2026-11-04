# Kalan işler

Projenin açık uçlarının **tek dizini**. Üç yüzey (Android uygulaması, web
sitesi, sunucu) ve birkaç belgeye dağılmış maddeleri bir arada görmek için.

Burası **dizin, kaynak değil**. Her maddenin gerekçesi ve ayrıntısı kendi
belgesinde duruyor; burada yalnızca tek satırlık durum ve nereye bakılacağı
var. Ayrıntıyı buraya kopyalamak, aynı bilginin iki yerde ayrışması demek
olurdu — bu depoda tam olarak o yaşandı: üç ayrı madde kapandığı hâlde
belgelerde "yapılacak" görünmeye devam etti (`0006` migrasyonu, Room şema
dosyaları, salon fotoğrafı).

Sapma ters yönde de oldu: `SUPABASE_TENANT_ID` "bekliyor" diye listeleniyordu
ama **tanımlıymış**. Bu daha sinsi bir hata — eksik sanılan bir ayar, aslında
çalışan bir akışı "zaten bozuk" sandırıyor. Bir maddenin durumu belgeden
değil, kaynağından doğrulanmalı.

Son gözden geçirme: 4 Kasım 2026, `master` (`08bb178`) + bu dalda bekleyen işler.

---

## Önce: seni bekleyenler

Bunlar bende yapılamaz; hiçbiri kod işi değil.

| # | Ne | Neyi açar | Ayrıntı |
|---|---|---|---|
| 1 | **Yayın anahtarı + 4 gizli anahtar** | İmzalı APK'nın **tek** engeli | `docs/yayin.md` |
| 2 | **KVKK aydınlatma metni için bilgiler** (ticari unvan, adres, saklama süresi) | Sitedeki yasal açığı | `docs/site-plani-kalanlar.md` md. 1 |
| 3 | **En az şifre uzunluğu 6 → 8** (Supabase panelinden) | Sunucuyu istemciyle aynı kurala getirir | `supabase/README.md` → "Panelden açılabilecek iki ayar" |
| 4 | **Sızmış şifre koruması** (Supabase panelinden) | Zayıf şifreleri eler | aynı bölüm |
| 5 | **MX kaydı** (Turhost panelinde) | Kurumsal e-postayı | `docs/site-yonetimi.md` → "E-posta" |
| 6 | **Karşılama videosu** (`kahraman.mp4`) | Banner videosunu (isteğe bağlı) | `web/varliklar/BENIOKU.md` |

1. madde en kritik olanı: uygulama kod olarak hazır ama imzalanamadığı için
dağıtılamıyor.

3. madde **en ucuz olanı ve bu listeye sonradan eklendi** — önce yalnızca
4. madde yazılıydı ve ikisi aynı panel bölümünde olduğu için gözden
kaçmıştı. Uygulama ve panel 8 karakter dayatıyor, sunucu 6'da duruyor; yani
istemci kontrolü atlanırsa 7 karakterli bir şifre geçiyor. **Ücretsiz planda
da yapılabiliyor**, 1 dakika.

4. madde **ücretli plan** gerektiriyor; ücretsiz plandaysan seçenek
görünmez ve bu madde şimdilik kapalı sayılır.

`SUPABASE_TENANT_ID` gizli anahtarı bu listeden **çıktı** ve iki sebebi var:
web'den üye kaydı şimdilik kapatıldı (aşağıya bakın) ve anahtar **zaten
tanımlı** — yayın günlüğünden doğrulandı. Önceden "sende bekliyor" diye
listeleniyordu; yanlıştı.

---

## Android uygulaması

**Kod tarafında açık iş yok.** 13 ekranın 13'ünün de görüntü testi var,
`TODO`/`FIXME` yok, CI `master`ta yeşil. Kalan tek şey yukarıdaki 1. madde.

Yalnızca panelde olan üç bölüm (Duyurular, Üye Hesapları, Personel Erişimi)
eksik değil, **bilinçli kapsam kararı** — gerekçesi `docs/site-yonetimi.md`
"Faz 5" ve `supabase/README.md`.

## Web sitesi

**Web'den üye kaydı KAPALI** (bilinçli karar) ve bu **canlıda yayında**:
`nonstopstudio.tr` 31.10.2026'da `e6a0d8d` ile güncellendi. Üye alanına giriş
çalışıyor; kapanan tek şey ziyaretçinin kendi kendine hesap açması. Geri
açmak `web/uye/kayit.js` içindeki `KAYIT_ACIK` değerini `true` yapmak;
ayrıntı `docs/site-yonetimi.md` → "3b. Üye kayıtları".

Sekiz açık madde, öncelik sırasıyla `docs/site-plani-kalanlar.md` içinde.
İlk ikisi:

1. **KVKK aydınlatma metni** — yasal ve **kaydın kapatılması bunu
   etkilemiyor**: sağlık verisini toplayan yer kayıt formu değil, giriş
   yapmış üyenin panosundaki beyan formu ve orası açık. Formda açık rıza
   var ama aydınlatma metni yok; açık rıza onun yerini tutmuyor.
2. **İletişim / deneme dersi formu** — **yapıldı**, ama canlıya açılması
   1. maddeye bağlı. Form `contact_requests` tablosuna `iletisim-istegi`
   Edge Function'ı üzerinden yazıyor; tabloya doğrudan `anon` yazma yetkisi
   verilmedi (gerekçe `0010`da). **Form şu anda KAPALI** (`FORM_ACIK`);
   açılmadan önce kalan iki şey: **panel ekranı** (talepleri personel
   göremiyor — okunmayan bir form, hiç olmayan bir formdan kötü) ve
   **KVKK metni** (ticari unvan + saklama süresi sende).

**WhatsApp ile temas kapandı** (02.11.2026) ve **canlıda yayında**:
iletişim bölümünde kutu, sayfanın her yerinde sabit düğme, hazır mesajı
deneme dersini soruyor. `nonstopstudio.tr` `cea85a6` ile güncellendi —
yayın günlüğünden doğrulandı (`index.html` ve `site.css` paketlendi,
`Özel alan adı etkin`).

**Lighthouse hedefi kapandı** (02.11.2026): plan mobil performans ≥ 90,
SEO ≥ 95, erişilebilirlik ≥ 90 istiyordu; ölçüldü ve üçü de karşılandı
(91–92 / 100 / 100 / 100). Erişilebilirliği 95'te tutan kontrast ihlali de
düzeltildi. Yerine daha dar bir madde girdi: **görsel ağırlığı** —
`reformer.jpg`ın %70'i boşa gidiyor ve LCP 3.5 s'nin sebebi o.

Kalan altı madde (künye, arama motoru kayıtları, içerik bölümleri, video,
ölçüm, görsel ağırlığı) daha düşük öncelikli.

## Sunucu

Dört madde, hepsi `supabase/README.md` → "Henüz yapılmadı" içinde ve hiçbiri
acil değil:

- **`anon` rolünün tablo yetkileri belgelenen niyetten geniş.** `0005`
  "yalnızca duyurular" diyor, canlıda ise `anon` 15 tablonun hepsinde
  `select`/`insert`/`update` yetkisine sahip (`delete` kapalı — `0008`).
  Veri sızmıyor: kurallar `{authenticated}` kapsamlı, `anon` 0 satır alıyor.
  Hemen yapılmadı çünkü **bu depodaki testlerle doğrulanamıyor** — testler
  boş PostgreSQL'de koşuyor ve orada o yetkiler hiç yok, yani revoke
  eklemek testleri hiç değiştirmez. Gerekçe ve doğru sıra README'de.
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
