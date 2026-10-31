# Web sitesi: ilk plandan kalanlar

Sitenin ilk planı Haziran 2026'da ayrı bir çalışmada yazıldı
(`McKose/McKose` deposunda `PLAN.md`, taslak PR McKose/McKose#1). Site sonradan
bu depoda, `web/` altında yapıldı ve plan oradan hiç güncellenmedi. Bu belge o
planı buraya taşıyor: neyin yapıldığını, neyin bilerek farklı yapıldığını ve
neyin hâlâ açık olduğunu tek yerde tutmak için. Eski plan artık kaynak değil.

Karşılaştırma tarihi: 31 Ekim 2026, `master` (`89ec450`).

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

Öncelik sırasıyla. Birincisi yasal, ikinci ve üçüncüsü planın ana hedefi
olan "ziyaretçiyi temasa geçirme" ile ilgili.

1. **KVKK aydınlatma metni.** Üye kayıt formu (`web/uye/`) ad, telefon,
   e-posta ve sağlık bilgisi topluyor. Formda açık rıza kutusu var, ama
   **aydınlatma metni** yok (KVKK md. 10: veri sorumlusunun kimliği, işleme
   amacı, aktarım, haklar). Açık rıza aydınlatmanın yerini tutmuyor. Bir
   `kvkk` sayfası ve formdan ona bağlantı gerekiyor. Metnin içeriği
   (unvan, adres, saklama süresi) salondan gelmeli.
2. **WhatsApp ile temas.** Planda birincil eylemdi (`wa.me` bağlantısı, sabit
   buton). Sitede yok; temas yalnızca telefon ve Instagram üzerinden.
3. **İletişim / deneme dersi formu.** Planda ana dönüşüm noktasıydı. Sitede
   "Bize ulaşın" düğmesi yalnızca iletişim bilgilerine kaydırıyor. Form
   eklenirse 1. madde onun için de geçerli olur ve spam koruması gerekir.
4. **İşletme künyesi.** Alt bilgide ticari unvan yok (plan §9). Aydınlatma
   metniyle aynı bilgi, birlikte eklenebilir.
5. **Arama motoru kayıtları.** Google Search Console ve Google İşletme
   Profili. Depoda doğrulama izi yok; yapıldıysa bu madde kapanır.
   `sitemap.xml` ve yapısal veri hazır.
6. **İçerik bölümleri.** Haftalık ders programı, eğitmen kadrosu, galeri ve
   üye yorumları. Hepsi salondan içerik gerektiriyor; kod tarafı küçük.
7. **Karşılama videosu.** Zaten izleniyor: `web/varliklar/BENIOKU.md`.
8. **Ölçüm.** Analytics yok. Bu bilinçli olabilir: analytics yokken çerez
   izni penceresi de gerekmiyor. Eklenirse ikisi birlikte eklenmeli.
9. **Performans hedefi.** Planın hedefi mobil Lighthouse performans ≥ 90,
   SEO ≥ 95, erişilebilirlik ≥ 90 idi. Ölçülmedi.

## Kapanan

Planın geri kalanı yapıldı ya da artık geçerli değil: alan adı ve kanonik
adres, karşılama bölümü, branşlar, tıkla-ara telefon, harita bağlantısı,
çalışma saatleri, Instagram, Open Graph ve X kartları, `HealthClub`
yapısal verisi, `sitemap.xml`, `robots.txt`, 404 sayfası,
`prefers-reduced-motion` desteği.
