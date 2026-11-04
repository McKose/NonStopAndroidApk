# Web sitesi: ilk plandan kalanlar

Sitenin ilk planı Haziran 2026'da ayrı bir çalışmada yazıldı
(`McKose/McKose` deposunda `PLAN.md`, taslak PR McKose/McKose#1). Site sonradan
bu depoda, `web/` altında yapıldı ve plan oradan hiç güncellenmedi. Bu belge o
planı buraya taşıyor: neyin yapıldığını, neyin bilerek farklı yapıldığını ve
neyin hâlâ açık olduğunu tek yerde tutmak için. Eski plan artık kaynak değil.

Karşılaştırma tarihi: 2 Kasım 2026, `master` (`cea85a6`).

## Plandan farklı yapılanlar (bilinçli)

| Plan | Yapılan | Neden önemli değil |
|---|---|---|
| Next.js + Tailwind + shadcn/ui | Düz HTML/CSS/JS, derleme adımı yok | Tanıtım sayfası tek sayfa; panelle aynı "derlemesiz, statik sunucuya olduğu gibi" deseni |
| Vercel | GitHub Pages + `web/CNAME` (`nonstopstudio.tr`), kanonik adres yazılı | SSL ve CDN Pages'ten geliyor |
| Üye paneli v2'de | Üye alanı (`web/uye/`) ve yönetim paneli (`web/admin/`) yapıldı | Planın kapsamından geniş |
| İçerik kod içinde, CMS v2'de | Duyurular panelden giriliyor (`announcements`) | Salon sahibi duyuruyu kendisi güncelliyor; geri kalan içerik sabit |

## Plandaki açık kararların durumu

| # | Karar | Durum |
|---|---|---|
| 1 | Fiyatlar sitede görünsün mü | **Verildi: görünmüyor.** JSON-LD'de `priceRange` bilerek yok |
| 2 | Tek dil mi, TR/EN mi | **Fiilen TR** (`lang="tr"`, `og:locale` `tr_TR`). İngilizce açık bir karar olarak hiç sorulmadı |
| 3 | CMS gerekli mi | **Kısmen:** duyurular panelden. Program, eğitmen, galeri gibi içerik için gerek doğmadı |
| 4 | Blog v1'de mi | Açık. Öncelik düşük |
| 5 | Online rezervasyon | Açık. Uygulamada randevu altyapısı var, siteye açılmadı |
| 6 | Kurumsal e-posta | `docs/site-yonetimi.md` → "E-posta: ne bozuk, ne bozuk değil" bölümünde ele alınıyor |

## Hâlâ açık olan maddeler

Bütün yüzeylerin açık işleri için: [`kalan-isler.md`](kalan-isler.md).
Aşağısı **site** tarafının ayrıntısı.

Öncelik sırasıyla. Birincisi yasal, ikinci ve üçüncüsü planın ana hedefi
olan "ziyaretçiyi temasa geçirme" ile ilgili.

1. **KVKK aydınlatma metni.** Sağlık verisi **özel nitelikli kişisel veri**
   ve üye panosundaki sağlık beyanı formu (`web/uye/`, giriş yapıldıktan
   sonra) onu topluyor. Formda açık rıza kutusu var ve rıza zamanı kayda
   yazılıyor (`consent_at_ms`), ama **aydınlatma metni** yok (KVKK md. 10:
   veri sorumlusunun kimliği, işleme amacı, aktarım, haklar). Açık rıza
   aydınlatmanın yerini tutmuyor. Bir `kvkk` sayfası ve formdan ona
   bağlantı gerekiyor. Metnin içeriği (unvan, adres, saklama süresi)
   salondan gelmeli.

   **Üye kaydının kapatılması bu maddeyi etkilemiyor.** Kayıt formu sağlık
   verisi toplamıyordu (ad, telefon, e-posta, şifre, not); sağlık verisini
   toplayan yer giriş yapmış üyenin panosu ve orası açık. İlk yazımda bu
   iki form birbirine karışmıştı.

   **ERTELENDİ — salon sahibinin kararı (04.11.2026).** Gerekçe "web'den
   üyelik almayacağız" idi. O gerekçe kayıt formu için doğru ama **bu madde
   kayıt formuyla ilgili değil**; aynı karışıklık burada ikinci kez yaşandı,
   o yüzden ayrıca yazılıyor.

   Maddenin kapsamı ölçüldü (04.11.2026, canlı veritabanı): 2 bağlı üye
   hesabı ve **girilmiş 1 sağlık beyanı** var. Yani veri toplanması teorik
   değil, bir kez olmuş. Beyan formunda açık rıza kutusu var ve rıza zamanı
   kayda yazılıyor; eksik olan **aydınlatma**.

   Madde listede kalıyor çünkü kapanmadı — yalnızca sırası geriye alındı.
   Yeniden ele alınırsa bilinmesi gereken: ticari unvan ve saklama süresi
   olmadan md. 10'un tamamı yazılamaz, ama veri sorumlusu / amaç /
   paylaşılmama söyleyen kısa bir bilgilendirme o iki bilgi olmadan da
   eklenebilir ve bugünkü hâlden iyidir.
2. **İletişim / deneme dersi formu — YAPILDI, canlıya açılması 1. maddeye
   bağlı.** Planın ana dönüşüm noktasıydı. Form iletişim bölümünde,
   `contact_requests` tablosuna `iletisim-istegi` Edge Function'ı üzerinden
   yazıyor.

   Tabloya doğrudan `anon` yazma yetkisi **verilmedi**: `anon` yayınlanmış
   anahtarla çalışıyor ve tabloya `insert` açmak "internetteki herkes
   istediği hızda satır yazabilir" demekti. Uç ayrıca doğrulama, mükerrer
   bastırma (aynı numara, 10 dk) ve hız sınırı (salon başına saatte 20)
   yapıyor — tablo kuralının yapamadığı şeyler. Spam koruması bu üçü artı
   tuzak alanı.

   **Form KAPALI KALACAK — salon sahibinin kararı (04.11.2026).**
   (`FORM_ACIK = false`, `web/iletisim-formu.js`.) Bekleyen bir iş değil:
   "metin gelince açılacak" diye kuyrukta durmuyor, açılması yeni bir karar
   gerektiriyor. Açmak tek satır; işletme tarafı
   `docs/site-yonetimi.md` → "3a. Deneme dersi formu".

   Kapalı kaldıkça ziyaretçi çıkışsız değil: telefon, WhatsApp ve Instagram
   formun hemen üstünde ve WhatsApp'ın hazır mesajı deneme dersini soruyor.

   **Panel ekranı YAPILDI** (Talepler sekmesi): liste, tıkla-ara ve durum
   değiştirme (arandı/kapandı) çalışıyor. Yani form açıldığı gün zincirin
   tamamı hazır — talep panelde görünür ve işaretlenebilir.
3. **İşletme künyesi.** Alt bilgide ticari unvan yok (plan §9). Aydınlatma
   metniyle aynı bilgi, birlikte eklenebilir.
4. **Arama motoru kayıtları.** Google Search Console ve Google İşletme
   Profili. Depoda doğrulama izi yok; yapıldıysa bu madde kapanır.
   `sitemap.xml` ve yapısal veri hazır.
5. **İçerik bölümleri.** Haftalık ders programı, eğitmen kadrosu, galeri ve
   üye yorumları. Hepsi salondan içerik gerektiriyor; kod tarafı küçük.
6. **Karşılama videosu.** Zaten izleniyor: `web/varliklar/BENIOKU.md`.
7. **Ölçüm.** Analytics yok. Bu bilinçli olabilir: analytics yokken çerez
   izni penceresi de gerekmiyor. Eklenirse ikisi birlikte eklenmeli.
8. **Görsel ağırlığı.** `reformer.jpg` 1200×1600 ve 359 KiB, ama sayfada
   çok daha küçük duruyor: Lighthouse **%70'inin boşa gittiğini** söylüyor.
   Mobil veriyle açan ziyaretçi için gerçek bir maliyet. Doğru çözüm
   `<picture>` ile WebP ve küçültülmüş `srcset`.

   `salon-ic.jpg` için **ana dosya küçültülmemeli**: yalnız %34 fazla ve
   aynı zamanda `og:image`, yapısal veri görseli ve video afişi. Ona
   dokunmak sosyal paylaşım kartını ve arama sonucunu bozar; ek WebP
   türevi verilebilir, aslı kalmalı.

   Eklenen her türev `panel-yayin.yml`deki yayın listesine de girmeli:
   `<picture>` içindeki bir `<source>` 404 verirse tarayıcı `<img>`e
   DÖNMÜYOR, kırık görsel gösteriyor.

## Kapanan

Planın geri kalanı yapıldı ya da artık geçerli değil: alan adı ve kanonik
adres, karşılama bölümü, branşlar, tıkla-ara telefon, harita bağlantısı,
çalışma saatleri, Instagram, Open Graph ve X kartları, `HealthClub`
yapısal verisi, `sitemap.xml`, `robots.txt`, 404 sayfası,
`prefers-reduced-motion` desteği.

**WhatsApp ile temas** (02.11.2026): planın birincil eylemiydi ve iki yerde
birden eklendi — iletişim bölümünde kutu, sayfanın her yerinde sabit düğme.
Hazır mesaj deneme dersini soruyor, yani planın dönüşüm hedefini taşıyor.
Numara artık üç yerde (`tel:`, yapısal veri, `wa.me`) ve üçünün eşitliği
`web/iletisim.test.js` ile sınanıyor.

Canlıda yayında: `cea85a6` ile `https://nonstopstudio.tr/`. Bu oturumdan
siteye doğrudan istek atılamıyor (alan adı ağ izin listesinde değil), o
yüzden doğrulama yayın günlüğünden yapıldı: değişen iki dosya pakete girdi
ve dağıtım `Özel alan adı etkin` ile tamamlandı.

**Performans hedefi** (02.11.2026): plan mobil Lighthouse performans ≥ 90,
SEO ≥ 95, erişilebilirlik ≥ 90 istiyordu. Ölçüldü ve **üçü de karşılandı**.

| Kategori | Puan | Hedef |
|---|---|---|
| Performans | 91–92 | ≥ 90 ✔ |
| Erişilebilirlik | 100 | ≥ 90 ✔ |
| İyi uygulamalar | 100 | — |
| SEO | 100 | ≥ 95 ✔ |

Ölçüm Lighthouse 12.8.2, mobil kip (412×823 @1.75), üç koşu. Erişilebilirlik
ilk koşuda 95'ti; düşüren tek şey altbilgideki yapımcı satırının kontrastıydı
(4.44:1, gereken 4.5:1) ve düzeltildi → 100.

**Ölçümün nasıl yineleneceği önemli, çünkü yanlış kurulum sahte bulgu
üretiyor.** Yerel sunucu GitHub Pages'i taklit etmeli:

- **gzip** ve **`Cache-Control: max-age=600`** göndermeli. Python'un
  `http.server`ı ikisini de yapmıyor ve bu, "metin sıkıştırması kapalı"
  ile "önbellek ömrü kısa" diye İKİ SAHTE bulgu üretiyordu.
- **`web/config.js` yerinde olmalı.** Dosya `.gitignore`da ve canlıda yayın
  adımı gizli anahtarlardan yazıyor; yerelde yokken 404 veriyor ve "iyi
  uygulamalar" puanını konsol hatası yüzünden 96'ya düşürüyordu. Yerel
  ölçüm için boş değerli bir taslak yeterli — **gerçek anahtar konmamalı**.

Bu iki kurulum hatasıyla alınan ilk ölçüm 90 / 95 / 96 / 100 diyordu; ikisi
de düzeltilince 91 / 100 / 100 / 100. Yani puanların üçü ölçüm hatasıydı.

Geriye kalan gerçek zayıflık **LCP 3.4–3.5 s** ve sebebi aşağıdaki 8. madde.
