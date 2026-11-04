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

Son gözden geçirme: 4 Kasım 2026, `master` (`17f0e42`).

---

## Önce: seni bekleyenler

Bunlar bende yapılamaz; hiçbiri kod işi değil.

| # | Ne | Neyi açar | Ayrıntı |
|---|---|---|---|
| 1 | **KVKK aydınlatma metni için bilgiler** (ticari unvan, adres, saklama süresi) — *ertelendi, aşağıya bakın* | Sitedeki yasal açığı | `docs/site-plani-kalanlar.md` md. 1 |
| 2 | **En az şifre uzunluğu 6 → 8** (Supabase panelinden) | Sunucuyu istemciyle aynı kurala getirir | `supabase/README.md` → "Panelden açılabilecek iki ayar" |
| 3 | **Sızmış şifre koruması** (Supabase panelinden) | Zayıf şifreleri eler | aynı bölüm |
| 4 | **MX kaydı** (Turhost panelinde) | Kurumsal e-postayı | `docs/site-yonetimi.md` → "E-posta" |
| 5 | **Karşılama videosu** (`kahraman.mp4`) | Banner videosunu (isteğe bağlı) | `web/varliklar/BENIOKU.md` |

2. madde **en ucuz olanı ve bu listeye sonradan eklendi** — önce yalnızca
3. madde yazılıydı ve ikisi aynı panel bölümünde olduğu için gözden
kaçmıştı. Uygulama ve panel 8 karakter dayatıyor, sunucu 6'da duruyor; yani
istemci kontrolü atlanırsa 7 karakterli bir şifre geçiyor. **Ücretsiz planda
da yapılabiliyor**, 1 dakika.

3. madde **ücretli plan** gerektiriyor; ücretsiz plandaysan seçenek
görünmez ve bu madde şimdilik kapalı sayılır.

**Yayın anahtarı + 4 gizli anahtar bu listeden ÇIKTI** (salon sahibinin
kararı, 04.11.2026) ve listenin en kritik maddesiydi. Gerekçe: uygulama
mağazada yayınlanmayacak, dağıtım kurulabilir APK ile olacak. O APK'yı
üretmek için anahtar gerekmiyor — her CI koşusu depodaki sabit hata ayıklama
anahtarıyla imzalanmış bir APK üretiyor ve indirme bağlantısı koşu özetinde
duruyor (`docs/deneme.md` md. 2). Bu madde kapanmadı, **gereksizleşti**;
kararın tamamı `docs/kararlar.md` → "Mağazada yayınlanmayacak".

`SUPABASE_TENANT_ID` gizli anahtarı bu listeden **çıktı** ve iki sebebi var:
web'den üye kaydı şimdilik kapatıldı (aşağıya bakın) ve anahtar **zaten
tanımlı** — yayın günlüğünden doğrulandı. Önceden "sende bekliyor" diye
listeleniyordu; yanlıştı.

---

## Android uygulaması

**Açık iş yok — ne kodda ne dağıtımda.** 13 ekranın 13'ünün de görüntü testi
var, `TODO`/`FIXME` yok, CI `master`ta yeşil.

Dağıtım da artık beklemiyor: uygulama **mağazada yayınlanmayacak** (karar,
04.11.2026) ve kurulabilir APK her koşuda üretiliyor — 90 gün saklanıyor,
indirme bağlantısı koşu özetinin başında. Nasıl kurulacağı
`docs/deneme.md` md. 2'de.

Bir uyarı: "imzasız APK" diye bir dağıtım yolu **yok**. Android imzası
olmayan bir paketi kurmuyor (`INSTALL_PARSE_FAILED_NO_CERTIFICATES`), yani
`:app:assembleRelease`in anahtar yokken ürettiği `app-release-unsigned.apk`
telefona kurulamaz. Mağazasız dağıtımın doğru karşılığı hata ayıklama
anahtarıyla imzalı APK ve zaten üretilen o.

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

1. **KVKK aydınlatma metni** — **ertelendi** (salon sahibinin kararı,
   04.11.2026) ama **kapanmadı**. Gerekçe "web'den üyelik almayacağız" idi;
   o gerekçe kayıt formu için doğru, bu madde için değil. Sağlık verisini
   toplayan yer kayıt formu DEĞİL, giriş yapmış üyenin panosundaki beyan
   formu — ve orası açık. Canlıda ölçüldü (04.11.2026): 2 bağlı hesap ve
   **girilmiş 1 sağlık beyanı**. Formda açık rıza var ama aydınlatma yok;
   açık rıza onun yerini tutmuyor.
2. **İletişim / deneme dersi formu** — **yapıldı**, ama canlıya açılması
   1. maddeye bağlı. Form `contact_requests` tablosuna `iletisim-istegi`
   Edge Function'ı üzerinden yazıyor; tabloya doğrudan `anon` yazma yetkisi
   verilmedi (gerekçe `0010`da). Panel ekranı da **yapıldı** (Talepler
   sekmesi: liste, tıkla-ara, durum değiştirme).

   **Form KAPALI KALACAK — salon sahibinin kararı (04.11.2026).** Bekleyen
   bir iş değil; açılması yeni bir karar gerektiriyor. Açmak tek satır
   (`FORM_ACIK`) ve o gün zincirin tamamı hazır.

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
