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

Son gözden geçirme: 9 Kasım 2026, `master` (`89428a0`).

---

## Planda kalan tek iş (ve o da ücretli plana bağlı)

Salon sahibinin kararı (08.11.2026): **geri kalan her şey plandan çıktı**
(aşağıdaki bölüm). 09.11.2026'da bir madde daha karara bağlandı, aşağıda.

| # | Ne | Neyi açar | Plan |
|---|---|---|---|
| 1 | **Prevent use of leaked passwords** (Supabase paneli → Authentication → Sign In / Providers → Email) | Sızmış şifreleri eler | **Pro ve üstü**; ücretsiz planda görünmez |

**Neden var:** personel şifresini kendisi değiştirebiliyor ve en olası zayıf
şifre, kişinin başka bir sitede zaten kullandığı şifre. Supabase bunu
HaveIBeenPwned listesine karşı denetliyor. **Canlıda ölçüldü (08.11.2026):
kapalı** — proje danışmanı `auth_leaked_password_protection` uyarısını hâlâ
veriyor. Ücretsiz plandaysan seçenek görünmez, yani bu madde fiilen kapalı ve
**planda yapılacak iş kalmıyor.**

### En az şifre uzunluğu: OLDUĞU GİBİ KALIYOR (karar, 09.11.2026)

Bu madde listenin başındaydı ("sunucuyu istemciyle aynı kurala getirir") ve
**plandan çıktı.** Karar, kodda izlenen bir ölçümle verildi — insanın şifre
seçtiği her yol tarandı:

| Yol | Şifreyi kim seçiyor | Kaç karakter |
|---|---|---|
| Personel hesabı açılışı (`personel-davet`) | kimse — sunucu üretiyor | **14**, kriptografik rastgele |
| Şifre değiştirme (uygulama + panel) | personel | **en az 8**, istemci dayatıyor |
| Personelin kendi kaydı | **yok** | — |
| Web'den üye kaydı (`web/uye/index.html`) | üye | **en az 6** (`minlength="6"`); akış kapalı |

**Personel tarafında** 8'in altına düşebilecek tek kişi, uygulamayı ve paneli
atlayıp Auth API'sine kendi jetonuyla istek atan personel — kendi hesabını
kendi eliyle zayıflatan kişi.

**Üye tarafında durum farklı.** Kayıt 31.10.2026'da kapatıldı ama o güne kadar
açıktı ve form 6 karakter kabul ediyordu; canlıdaki **2 üye hesabı** o
dönemden. Yani 6–7 karakterlik bir üye şifresi mevcut olabilir.
**Sunucu minimumunu 8 yapmak bunu düzeltmezdi** — minimum geriye dönük
çalışmıyor, var olan şifre olduğu gibi kalıyor. Yani kararın o hesaplar için
bedeli yok.

Etkisi olan şey sunucu ayarı değil, **formun kendisi**: `minlength="6"`.
`KAYIT_ACIK` bir gün açılırsa 6'dan insan şifresi girilen canlı bir yol
doğar — ve bu, depodaki "istemci şifre kuralları uygulamayla aynı" kuralına
aykırı (panel 8, uygulama 8, üye formu 6). **Ayrı bir madde ve karar
bekliyor.**

Bu belge daha önce yalnızca "istemci kontrolü atlanırsa 7 karakterli şifre
geçer" diyordu; doğru ama eksikti. Tam ölçüm: `supabase/README.md` →
"Panelden açılabilecek ayarlar".

> **Aynı ekrandaki üçüncü ayarı AÇMA:** *Require current password when
> changing password*. Doğru fikir ama açıldığında `PUT /auth/v1/user` isteği
> gövdesinde `current_password` bekliyor; uygulama ve panel bunu göndermiyor,
> yani **şifre değiştirme akışının tamamı kırılır.** Aynı korumayı bugün
> istemci sağlıyor. Ayrıntı: `supabase/README.md` → "Panelden açılabilecek
> ayarlar".

Panelde ayrıca **4 adet "Signed-In Users Can Execute SECURITY DEFINER
Function"** uyarısı göreceksin. Bunlar yeni iş değil, **bilinçli**: o dört
fonksiyon satır bazlı güvenlik kurallarının içinde çağrılıyor, yani
`authenticated` rolünün onları çalıştırabilmesi zorunlu. `anon` erişimi
`0009` ile kapatıldı. Gerekçe `supabase/README.md`de.

---

## Plan dışı — ertelendi (salon sahibinin kararı, 08.11.2026)

> "diğer kalanlar acil değil şimdilik bunları iş planından çıkart"

Aşağıdakiler **kapanmadı, plandan çıktı.** Aradaki fark önemli: hiçbiri
"yarım kalmış iş" değil ve yeniden ele alınması **yeni bir karar**
gerektiriyor. Gerekçeleri ve ölçümleri kendi belgelerinde **silinmedi**,
çünkü yeniden açılırsa işin yarısı o analiz.

| Ne | Nerede duruyor |
|---|---|
| **KVKK aydınlatma metni için bilgiler** (ticari unvan, adres, saklama süresi) | `docs/site-plani-kalanlar.md` md. 1 |
| **MX kaydı** (Turhost panelinde) — kurumsal e-posta | `docs/site-yonetimi.md` → "E-posta" |
| **Karşılama videosu** (`kahraman.mp4`) — dosyayı sen veriyorsun | `web/varliklar/BENIOKU.md` |
| Web sitesinin kalan altı maddesi (künye, arama motoru kayıtları, içerik bölümleri, **video**, ölçüm, görsel ağırlığı) | `docs/site-plani-kalanlar.md` |

Listedeki **video iki satırda görünüyor ve bu bir kopya değil**: aynı işin iki
yarısı. Dosyayı salon veriyor (üçüncü satır), siteye bağlamak kod işi
(dördüncü satırın içindeki "video"). Dosya gelmeden kod yarısının yapılacak
bir şeyi yok.
| Sunucunun dört maddesi (`anon` yetkileri, personel silme, iOS oturumu, `MigrationTestHelper`) | `supabase/README.md` → "Henüz yapılmadı" |

**İkisinin bedeli ötekilerden farklı ve bu not o yüzden burada** — karar
salon sahibinin, ama "acil değil" ikisi için "bedelsiz" demiyor:

- **KVKK.** Canlıda ölçüldü (04.11.2026): **girilmiş 1 sağlık beyanı** var ve
  aydınlatma metni yok. Beyan formunda açık rıza kutusu var ama açık rıza
  aydınlatmanın yerini tutmuyor. Yani bu, ileride doğabilecek bir risk değil,
  bir kez olmuş bir veri toplama.
- **`anon` rolünün tablo yetkileri.** Belgelenen niyetten geniş: `0005`
  "yalnızca duyurular" diyor, canlıda `anon` 15 tablonun hepsinde
  `select`/`insert`/`update` yetkisine sahip. **Veri sızmıyor** — kurallar
  `{authenticated}` kapsamlı ve `anon` 0 satır alıyor — ama yetki katmanı
  kural katmanından geniş durdukça, ileride yazılacak geniş bir kural bunu
  sessizce açabilir.

Ötekiler (MX, video, künye, arama motoru, içerik, ölçüm, görsel ağırlığı,
personel silme, iOS, `MigrationTestHelper`) gerçekten bedelsiz bekliyor.

**Daha önce bu listeden çıkanlar** (kayıt olarak duruyor): **yayın anahtarı +
4 gizli anahtar** — uygulama mağazada yayınlanmayacağı için *gereksizleşti*
(04.11.2026, `docs/kararlar.md` → "Mağazada yayınlanmayacak").
**`SUPABASE_TENANT_ID`** — zaten tanımlıydı, "bekliyor" diye listelenmesi
yanlıştı.

---

## Android uygulaması

**Açık iş yok — ne kodda ne dağıtımda.** 13 ekranın 13'ünün de görüntü testi
var, `TODO`/`FIXME` yok, CI `master`ta yeşil.

Dağıtım da artık beklemiyor: uygulama **mağazada yayınlanmayacak** (karar,
04.11.2026) ve kurulabilir APK her koşuda üretiliyor — 90 gün saklanıyor,
indirme bağlantısı koşu özetinin başında. Nasıl kurulacağı
`docs/deneme.md` md. 2'de.

Dağıtılan APK `dagitim` yapı tipinden geliyor ve hata ayıklamaya **kapalı**
(05.11.2026). Önceden `debug` yapısı dağıtılıyordu; o `debuggable` olduğu için
telefona fiziksel erişimi olan biri üye verisini okuyabiliyordu. Her koşu bunu
APK'nın içinden doğruluyor. Gerekçe: `docs/kararlar.md` → "Dağıtım yapısı hata
ayıklamaya kapalı".

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

**Sitede planda duran iş kalmadı** (08.11.2026 kararı). Sekiz madde
`docs/site-plani-kalanlar.md` içinde duruyor ama hepsi **plan dışı**; liste
bir kuyruk değil, yeniden açılırsa okunacak bir kayıt. İlk ikisinin durumu
burada, çünkü ikisi de "yapılacak iş" değil birer karar:

1. **KVKK aydınlatma metni** — **ertelendi** (salon sahibinin kararı,
   04.11.2026, 08.11.2026'da yeniden onaylandı) ama **kapanmadı**.
   Gerekçe "web'den üyelik almayacağız" idi;
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
ölçüm, görsel ağırlığı) **plan dışı**.

## Sunucu

Planda duran iş yok. Supabase panelindeki **sızmış şifre koruması** (en üstteki
bölüm) sunucu tarafı ama kod işi değil ve ücretli plana bağlı; en az şifre
uzunluğu ise karara bağlandı, olduğu gibi kalıyor.

Dört madde `supabase/README.md` → "Henüz yapılmadı" içinde duruyor ve hepsi
**plan dışı** (08.11.2026). Yalnızca ilki bedelsiz değil:

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
