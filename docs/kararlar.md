# Mimari kararlar

Verilen kararlar ve gerekçeleri. Amaç, "bu neden böyle yapılmış?" sorusunun
cevabının kod arkeolojisi gerektirmemesi.

## iOS arayüzü: Compose Multiplatform

**Karar:** iOS arayüzü SwiftUI ile sıfırdan yazılmayacak; mevcut Compose ekranları
`:shared` modülüne taşınarak iki platformda da aynı kod çizecek.

**Neden:** Uygulamada 14 ekran var ve hepsi Compose. SwiftUI seçilseydi bunlar
Swift'te yeniden yazılacak, bundan sonraki her arayüz değişikliği iki yerde
yapılacaktı. Tek geliştiricili bir projede bu maliyet kalıcı.

**Bedeli:** iOS'ta his tam yerel olmayacak; Compose/iOS ekosistemi Android kadar
olgun değil. Ekranlardaki Android'e özgü parçaların (tercihler, kaynaklar) KMP'ye
çevrilmesi gerekiyor.

**Ne zaman gözden geçirilmeli:** iOS tarafında performans ya da his sorunu
kullanıcı şikâyetine dönüşürse, tek tek ekranları SwiftUI'a taşımak mümkün —
iş kuralları zaten ortak kodda olduğu için bu bir arayüz kararı olarak kalır.

## Senkronizasyon: gönderim kuyruğu (outbox)

**Karar:** Sunucuya gönderilecek değişiklikler `sync_outbox` tablosunda açık bir
kuyrukta tutuluyor. Kuyruk kaydı veri kopyası değil, **işaretçi** (tablo + satır
kimliği).

**Neden kuyruk:** Alternatif olan "son senkronizasyondan sonra değişenleri bul"
sorgusu cihaz saatine güvenir. Saat geri alınırsa o aralıktaki değişiklikler
sessizce atlanır ve bir daha hiç gönderilmez. Kuyrukta kayıt gönderilene kadar
durur.

**Neden işaretçi:** Son yazan kazanan bir modelde ara durumların değeri yok. Bir
satır arka arkaya üç kez değişince kuyrukta tek kayıt kalır ve gönderim anında
satırın güncel hâli okunur — üç ayrı yazma yerine bir tane. Ayrıca her entity'yi
JSON'a çevirmek gerekmiyor.

**Kural:** Kuyruğa alma, satırı değiştiren yazmayla **aynı transaction** içinde
olmalı. Ayrı olsalardı satır yazılıp kuyruğa girmeyebilir (değişiklik sessizce
kaybolur) ya da tersi olabilirdi (var olmayan değişiklik gönderilmeye çalışılır).

**İlgili kural:** Transaction'ı **giriş noktası** açar. Repository'ler birbirini
çağırıyor (üye → defter, sipariş → defter, randevu → defter); iç çağrılar kendi
transaction'ını açmaz.

## Kimlik doğrulama: Supabase Auth, her personele ayrı hesap

**Karar:** Kimlik doğrulama Supabase Auth'a ait; her personelin kendi e-posta +
şifre hesabı var. Uygulamaya gömülen `anon` anahtarı tek başına hiçbir veriye
erişemez.

**Reddedilen alternatif:** Uygulamaya tek bir servis anahtarı gömmek. APK bir zip
dosyasıdır; anahtarı çıkarmak dakikalar sürer ve özel bir beceri gerektirmez. O
anahtarı ele geçiren kişi bütün salonların bütün verisine erişirdi. Ayrıca
"kullanıcı yalnızca kendi salonunu görsün" kuralını yazacak bir yer kalmazdı:
sunucu açısından tüm istekler aynı kimlikten gelirdi.

**Neden kişi başına hesap, ortak hesap değil:** Salonda hakediş ve tahsilat kaydı
tutuluyor. Kimin hangi kaydı girdiğinin ayırt edilebilmesi, para söz konusu
olduğunda ihtiyari bir ayrıntı değil.

**Sonucu:** Uygulamadaki yerel `staff.password` (varsayılanı `"123"` olan, düz
metin saklanan alan) ortadan kalkıyor. Sunucudaki `staff` tablosunda şifre kolonu
bilinçli olarak yok.

## Salon (kiracı) modeli

**Karar:** Bugün tek salon, ama şema baştan çok salonlu. Her satırda `tenant_id`
var ve `gyms.id`'yi gösteriyor.

**Neden şimdi:** İkinci salon eklendiğinde şema değişikliği ve veri taşıma
gerekmiyor — bugün `gyms` tablosunda bir satır var, yarın on satır. Sonradan
eklemek, canlı veriyle taşıma yapmak demekti.

**Erişim kuralı:** "Kullanıcı yalnızca bağlı olduğu salonun satırlarına erişir."
Kuralın `with check` yarısı en az `using` kadar önemli ve unutulması kolay:
olmasaydı istemci başka salonun `tenant_id`'siyle satır yazabilirdi — okuyamayacağı
ama bozabileceği veriye. Bu, CI'da her koşuda sınanıyor.

## Senkronizasyon: geçici ve kalıcı hata ayrımı

**Karar:** Gönderim sonucu üç durumlu — başarı, geçici hata, kalıcı hata.

**Geçici hatada tur durur** (ağ yok, 5xx, zaman aşımı). Sıradaki kayıtlar da
başarısız olacağı için hepsini denemek deneme sayaçlarını boş yere şişirir ve
geri çekilme süresini yanlış yere uzatır.

**Kalıcı hatada tur devam eder** (sunucu kaydı reddetti). Tek bozuk kayıt
arkasındaki her şeyi süresiz bekletmemeli. Kayıt kuyrukta kalır ve `lastError`
ile işaretlenir — sessizce silmek hem veri hem teşhis kaybı olurdu.

**Sıra korunur.** Satırlar arasında bağımlılık var (sipariş üyeye, randevu
eğitmene bakıyor); sırayı bozmak sunucuda henüz var olmayan bir satıra referans
göndermek demek.

**Uzak uç arayüzü satırın içeriğini taşımaz**, yalnızca "hangi tablodaki hangi
satır" der. Gönderilecek verinin şekli sunucu ve kimlik doğrulama seçimine bağlı;
sınır oradan geçince motor o karar verilmeden yazılabildi.

## İndirme: okunamayan satır su işaretini kilitler

**Karar:** Sunucudan gelen bir satır yerel biçime çevrilemiyorsa, o tablonun su
işareti o satırın zaman damgasını **aşmıyor** ve indirme orada duruyor.

**Neden:** İlk kurulumda su işareti okunamayan satırı da geçiyordu. Satır bir kez
sayılıyor, sonra bir daha hiç istenmiyordu — sorgu "su işaretinden yenisini ver"
dediği için o satır sonsuza kadar isteğin dışında kalıyordu. Uygulamanın sonraki
sürümü onu okuyabilir hâle gelse bile sunucudaki kayıt cihaza inmiyordu. Üstelik
sayı hiçbir yerde gösterilmiyordu: ekranda "12 kayıt indirildi" yazıyor, düşen üç
satırdan hiç söz edilmiyordu. Yani hem kayıp hem sessizdi.

**Bedeli:** O tablo, satır okunabilir hâle gelene kadar olduğu yerde duruyor;
arkasındaki satırlar da inmiyor. Bu bilinçli bir takas: hata artık gürültülü,
ekranda sebebiyle görünüyor ve düzeltilmeyi bekliyor. Alternatifi bir satırı
sessizce kaybedip her şeyin yolunda göründüğü bir kurulumdu.

**Sınır en küçük damga, ilk görülen değil.** Sayfa damga sırasında geliyor ama
buna güvenilmiyor: sıra bozuksa ikisi farklı olur ve aradaki satırlar sessizce
atlanırdı.

**Duruş sebebi iki ayrı kararı belirliyor** (`PullStop`): kalan tablolar denensin
mi, ve bir süre sonra kendiliğinden tekrar denensin mi. Ağ yoksa kalan sekiz
tabloyu denemek sekiz gereksiz zaman aşımı; ama okunamayan bir üye satırı o
tabloya özgü ve randevuların inmesini engellememeli. Aynı şekilde ağ geri gelir,
bozuk satır gelmez — ikincisini arka planda on beş dakikada bir denemek yalnızca
pil harcar.

## Yenilemede kalan seanslar: kararı kullanıcı verir

**Karar:** Paket yenilenirken kalan seansların devredilip devredilmeyeceğini
antrenör seçiyor (`SessionCarryOver`: `CARRY` / `DISCARD`). Uygulamanın gömülü
bir politikası yok.

**Neden:** Önceki hâlde yenileme kalan seansları **koşulsuz siliyordu** ve bu,
aynı işlemin tarih yarısıyla çelişiyordu: üyeliği bitmemiş birinin kalan günleri
devrediyor (`baseDate = currentEndDate`), kalan seansları siliniyordu. Tek bir
işlemin iki yarısı birbirine zıt davranıyordu. Hangi yarının doğru olduğu ise
uygulamanın bilebileceği bir şey değil — salon politikasına, hatta aynı salonda
üyeden üyeye değişiyor.

**Varsayılan yok.** `renewPackage`'ın `carryOver` parametresinin varsayılan
değeri **bilinçli olarak yok**: varsayılan verilseydi yeni bir çağrı yeri onu
sessizce miras alır ve kullanıcıya hiç sorulmadan bir politika uygulanırdı.
Formda başlangıç seçimi `CARRY` — üye o seansların parasını ödemiş — ama seçim
ekranda açıkça duruyor.

**`totalSessions` de aynı değeri alıyor.** Ayrışmaları sessiz bir hata olurdu:
seans iadesi (`MemberDao.incrementSession`) tavan olarak `totalSessions`'a
bakıyor; tavan devredenleri saymazsa iptal edilen bir randevunun hakkı geri
verilemezdi.

**Sınırsız paketler:** Yeni paket sınırsızsa sonuç yine sınırsız — devredileni
"sınırsız"a eklemenin karşılığı yok ve bir sayı üretmek sınırsız paketi sessizce
sınırlı hâle getirirdi. Eski paket sınırsızsa devredecek **sayılabilir** hak yok;
sıfır sayılıyor, alternatifi uydurulmuş bir sayıydı.

**Soru her zaman sorulmuyor.** Seçim yalnızca yenilemede ve kalan seans sıfırdan
büyükken görünüyor: diğer hâllerde iki şık da aynı sonucu verir ve kullanıcıya
anlamsız bir karar dayatmak olurdu.

## Para: kuruş cinsinden tam sayı

**Karar:** Parasal tutarlar `Double` değil, kuruş cinsinden `Long` (`Money`).

**Neden:** `0.1 + 0.2 != 0.3`; toplamlar zamanla sapar. KMP'de `BigDecimal`
stdlib'de olmadığı için taşınabilir çözüm tam sayı minor unit.

## Fiyat kalemleri de hesabın parçası

**Karar:** `Pricing.breakdown` tutarın kalemlerini (paket fiyatı, uygulanan
iskonto, ara toplam, vade farkı oranı ve tutarı) tek bir yapıda veriyor.
`finalPrice` bu yapının yalnızca toplamını döndüren bir kabuk; `PriceBreakdown.total`
saklanmıyor, kalemlerden türetiliyor.

**Neden:** Önceden yalnızca toplam hesaplanıyordu, kalemleri ekran kendisi
üretiyordu. İkisi ayrıştı: iskonto satırı kullanıcının **yazdığı ham metni**
basıyor, toplam ise paket fiyatına kırpılmış değerle hesaplanıyordu. Paket 1.000
TL iken 5.000 TL iskonto yazan biri şunu görüyordu:

```
Paket Fiyatı        ₺1.000
İskonto             -₺5.000
Ödenecek Tutar      ₺0,00
```

Kart, kendi içinde tutarsız bir aritmetik gösteriyordu. Vade farkı ise hiçbir
satırda yazmıyordu: taksit seçilince toplam sebepsiz yükseliyor görünüyordu.

**Kırpma duruyor ama görünür.** Sessizce kırpmak yerine ekran "en fazla şu kadar
uygulandı" diyor — market sepeti bunu zaten doğru yapıyordu, üyelik formu
yapmıyordu.

**Ekran önizlemesi ayrı bir yol değil.** TL (`Double`) üzerinden hesaplayan
`previewPrice` kaldırıldı; gösterilen ile kaydedilen arasında artık dönüşüm
farkı bile yok.

**Türetilmiş değer, saklanan değil.** Önizleme fiyatı bir alan olarak
tutulduğunda, fiyatı etkileyen dört ayrı işleyicinin her biri onu yeniden
hesaplamakla yükümlüydü; beşincisini eklemek ya da birinde unutmak ekranda eski
tutarın kalması demekti. Kalemler artık form durumundan türetiliyor.

## Ekrana yazılan sayı da uygulamanın biçimini kullanır

**Karar:** Yüzde değerleri `Rate.percentLabel` ile yazılıyor: ayıraç virgül,
gereksiz ondalık yok. Para `Money.toString()` ile. Formlara hazır yazılan
değerler de bu biçimi kullanıyor, `Double.toString()` değil.

**Neden:** Görünen sebep tutarsızlıktı — "%40.0" ve "2500.0", hem klavyenin
ürettiği ayıraçla hem uygulamanın geri kalanıyla çelişiyordu. Asıl sebep ise
geri okunamamaları: `Double.toString()` 10.000.000'dan itibaren bilimsel
gösterime geçiyor ve `Decimals` ayrıştırıcısı "1.0E7" değerini **1.0E8** olarak
okuyor. O büyüklükte maaşı olan bir personel kartı açılıp maaş alanına hiç
dokunulmadan kaydedildiğinde maaş on katına çıkıyordu. Biçim bir görünüm
ayrıntısı değil; kutuya yazılan metin aynı zamanda geri okunacak veri.

## Ondalık girdi: tek ayrıştırıcı

**Karar:** Kullanıcının yazdığı her ondalık sayı `Decimals.parseOrNull` üzerinden
okunur; `toDoubleOrNull` doğrudan kullanılmaz.

**Neden:** `toDoubleOrNull` yalnızca noktayı kabul ediyor, uygulamanın klavyesi
ise Türkçe. `?: 0.0` deseniyle birleşince virgülle yazılan her değer sessizce
sıfır oluyordu — ölçüler, iskontolar, maaşlar.

## Kimlik doğrulama: tek kaynak Supabase Auth

**Karar:** Uygulamaya giriş yalnızca Supabase Auth ile yapılır. Personel e-posta
ve şifresiyle girer. Yerel `staff.password` karşılaştırması ve ayarlardaki salon
şifresiyle çalışan `admin` yolu kaldırıldı.

**Neden:** İkisi de sunucudan bağımsızdı. O yolla açılan oturumun bir salon
kimliği olmuyordu, dolayısıyla o oturumda girilen hiçbir veri sunucuya
gönderilemiyordu — üstelik sessizce: uygulama normal çalışmaya devam ediyor,
kuyruk doluyor, kimse fark etmiyor. İki kimlik kaynağı tutmak "hangisiyle
girdim" sorusunu her hata teşhisinin başına koyardı.

**Bedeli:** İlk giriş internet istiyor. Kabul edildi; ilk başarılı girişten sonra
oturum cihazda kalıyor ve günlük kullanım çevrimdışı sürüyor.

**Sonuç:** Salon sahibi de kendi Supabase hesabıyla giriyor (ADMIN rolüyle).

## Yetki sunucudan gelir

**Karar:** Kullanıcının rolü `gym_users.role` değerinden okunur, cihazdaki bir
tercihten değil. Tanınmayan bir rol geldiğinde **en dar** yetkiye düşülür.

**Neden:** Cihazda tutulan yetki, uygulama verisine erişebilen biri tarafından
değiştirilebilir. Sunucudaki değer ise erişim kurallarının dayanağıyla aynı
yerde duruyor. En dar yetkiye düşmek de bilinçli: bir yazım hatasının yönetici
yetkisi vermesi, yetki vermemesinden çok daha pahalı.

## Rol tek kaynaktan ve tepkili okunur

**Karar:** Rol yalnızca oturumdan (`Session.role`) okunur ve akış olarak
verilir (`CurrentUser`). Cihazda ikinci bir kopya tutulmaz.

**Neden:** Kopya girişte yazılıyordu, yalnızca orada. Uygulama açılışında oturum
`SessionManager.restore()` ile geri yükleniyor ve o yol kopyaya hiç dokunmuyordu:
sunucuda rolü düşürülen kullanıcı, cihazda giriş ekranından geçmediği sürece eski
yetkisiyle çalışmaya devam ediyordu. Kopya ayrıca tepkisizdi — ekranlar rolü ya
ViewModel kurulurken bir kez ya da bir `combine` bloğunun içinde okuyordu.

Personel bağlantısı (`staff.id`) da aynı sebeple akış: kart sonradan
doldurulduğunda ya da satır senkronizasyonla indiğinde kullanıcının çıkıp
yeniden girmesi gerekmiyor.

## "Bağlantı yok" ile "kayıt yok" ayrı şeyler

**Karar:** Personel bağlantısı üç durumlu (`StaffLink`): oturum yok, bağlantı
kurulmamış, bağlı. Boş metinle temsil edilmiyor.

**Neden:** Boş kimlikle yapılan karşılaştırma her zaman boş liste veriyordu ve
ekran bunu "sizin dersiniz/üyeniz yok" diye gösteriyordu. Oysa doğru cümle "kim
olduğunuzu bilmiyoruz" ve yapılacak iş salon sahibinde: personel kartına Supabase
kimliğini girmek. Eğitmen boş bir pano görüp uygulamanın verisini kaybettiğini
sanıyordu.

## Ekran görünürlüğü de tek kaynakta

**Karar:** Hangi rolün hangi ekranı göreceği `AppDestination` içinde; ekranlar bu
kararı kendileri vermiyor. Finans yalnızca salon sahibi ve yöneticide; Ayarlar
**her** rolde açık.

**Neden:** Aynı karar iki ekranda birbirinden habersiz kopyalanmıştı. Pano
eğitmene Finans, Market, Paketler ve Ayarlar kısayollarını gizliyordu; üye
listesindeki çekmece aynı dört hedefi herkese açıyordu — yani kısıt gerçek bir
kısıt değildi, yalnızca bir ekranda görünmeyen bir düğmeydi.

Ayarlar'ın herkese açık olması zorunlu: "Çıkış Yap" orada. Panonun eğitmene
Ayarlar'ı gizlemesi, çekmecedeki ikinci yol olmasa eğitmenin uygulamadan hiç
çıkamaması demekti.

**Sınır değil:** Okuma sunucuda salona bağlı herkese açık (migrasyon `0004`);
bu gizleme bir güvenlik sınırı değil, arayüz kararı. Gerçek sınır yazma
tarafında ve o sunucuda.

## Salon kimliği oturumdan gelir

**Karar:** `tenantId` sabit değil; `TenantProvider` üzerinden oturumdan okunuyor
ve sunucudaki `gyms.id` ile aynı değer. Oturum yokken veri işlemi hata veriyor.

**Neden:** Eski `"default"` sabiti yerelde çalışıyor gibi görünüyordu ama sunucu
tarafında `tenant_id` `uuid` tipinde ve `"default"` geçerli bir uuid değil —
o satırlar hiçbir zaman senkronize olamazdı. Sessiz bir varsayılana düşmek
yerine hata vermek, giriş yapılmadan veri ekranı açılmasının bir programlama
hatası olduğunu görünür kılıyor.

## Personel ↔ hesap bağlantısı ayrı bir alan

**Karar:** `staff.authUserId` kolonu, personel kaydını Supabase hesabına bağlar.
`gym_users` ile birleştirilmedi.

**Neden:** İkisi farklı sorulara yanıt veriyor. `gym_users` "bu kullanıcı hangi
salona bağlı ve hangi rolde" diyor ve **erişim kurallarının** dayanağı;
`staff.authUserId` ise "bu personel satırı hangi hesaba ait" diyor ve yalnızca
uygulama içi ilişkilendirme için. Birleştirmek, erişim kurallarını uygulamanın
veri tablolarından birine bağlamak olurdu.

Bağlantı olmadan kişi giriş yapabilir ama randevulardaki `staffId` ile eşleşme
kurulamaz, dolayısıyla "bugün benim derslerim" boş görünür. Giriş bu yüzden
engellenmiyor: salon sahibi gibi ders vermeyen bir kullanıcı için doğru sonuç
zaten boş liste.

## Oturum cihazda şifreli saklanır

**Karar:** Yenileme jetonu Android Keystore ile şifrelenip uygulamaya özel
tercih dosyasında tutulur. Şifrelenemiyorsa **hiç saklanmaz**.

**Neden:** Saklanan şey, şifreyi bilmeden kullanıcının yerine geçmeye yeten bir
jeton. Uygulamaya özel dosya başka uygulamalarca okunamıyor ama cihaz yedeği ya
da root erişimiyle çıkarılabiliyor; anahtar Keystore'da olduğu için kopyalanan
dosya başka bir cihazda işe yaramıyor.

`androidx.security:security-crypto` kullanımdan kaldırıldığı için aynı işi yapan
kırk satır elle yazıldı — bakımı bırakılmış bir bağımlılığa bağlanmaktansa.

Şifreleme başarısız olduğunda düz metin yazmak yerine saklamamak bilinçli:
bedeli uygulama kapandığında tekrar giriş istenmesi, alternatifi jetonu
korumasız bırakmak.

## Oturum geri yüklenmeden ekran gösterilmez

**Karar:** Açılışta saklanan oturum okunana kadar yükleniyor göstergesi
gösterilir; hangi ekranla başlanacağı ondan sonra belirlenir.

**Neden:** Beklemeden başlansaydı giriş ekranı bir an görünür, oturum geri
yüklenince altından değişirdi — kullanıcı o sırada e-postasını yazmaya başlamış
olabilirdi. Okuma yerel ve şifre çözme dışında iş yapmıyor, yani bekleme gözle
görülür değil.

## Çağrılmayan kod tutulmuyor

**Karar:** Hiçbir yerden çağrılmayan sorgu, depo metodu, bileşen ve yardımcı
siliniyor; yerine neden silindiğini söyleyen bir not bırakılıyor.

**Neden not bırakılıyor:** Silinen şeylerin çoğu bir kez düşünülmüş ve yazılmış
şeyler. Sessizce silmek, aynı sorunun altı ay sonra yeniden çözülmesi demek —
notlar "bu denenmişti, şu yüzden kalktı" diyor.

**Neden tutulmuyor:** Çağrılmayan kod derlendiği için doğru sanılıyor ama hiç
koşmadığı için doğruluğu sınanmıyor. Somut örnekler:

- `LedgerRepository`'nin dönem toplamları "ciro"nun **ikinci tanımıydı**. Bugün
  ekranın tanımıyla aynı sonucu veriyordu; bunun sürmesini kimse garanti
  etmiyordu. Biri değişince sessizce ayrışacaktı.
- `StaffDao.findByAuthUserId`, rol tek kaynağa taşınırken kalkan bir çağrı
  yerine hizmet ediyordu. Belgesi hâlâ artık var olmayan bir akışı anlatıyordu.
- İki benzer ölçüm bileşeninden biri ölüydü: sonraki değişikliğin yanlış olanda
  yapılması an meselesiydi.

**Ölü belge de ölü koddur.** `LedgerDao`'da üst üste iki KDoc bloğu duruyordu;
Kotlin yalnızca en yakınını bağladığı için üstteki hiçbir yere bağlı değildi ve
alttakinin **tersini** söylüyordu ("MARKET kayıtları hariç" — oysa geçerli kural
market borcunu sayıyor). Kod okuyup belgeye güvenen biri yanlış sonuca varırdı.

## Yarım kalmış giriş alanı da bir hata

**Karar:** Durumu, işleyicisi ve kaydı yazılmış ama ekranda kutusu olmayan
alanlar tamamlandı: üye e-postası, sipariş notu. Ölçüm silme de aynı biçimde
depoda vardı, ekranda yoktu.

**Neden:** Bunlar "eksik özellik" gibi görünmüyor çünkü kod tamam görünüyor.
Sonucu ise sessiz: her üye boş e-postayla, her sipariş notsuz kaydediliyordu ve
yanlış girilen bir ölçüm kayıtta sonsuza kadar kalıyordu. Ölçüm geçmişi zamanla
karşılaştırmak için tutulduğundan, silinemeyen hatalı bir satır geçmişi kalıcı
olarak yanlış gösteriyordu.

**İlgili:** Düzeltilen bir eksiğin uyarısı ekranda kalmamalı. "Paket seçiniz"
hatası yalnızca gönderimde siliniyordu; paket seçildiği anda siliniyor — ad ve
telefon alanları bunu zaten doğru yapıyordu.

## Hata ayıklama imzası sabit ve depoda

**Karar:** Hata ayıklama yapısı depodaki `app/debug.keystore` ile imzalanıyor;
AGP'nin her makinede kendi ürettiği anahtar kullanılmıyor.

**Neden:** CI koşucusu her tur sıfırdan başlıyor, yani her koşu **farklı** bir
imza üretiyordu. Sonucu telefonda görünüyor: yeni APK eskisinin üzerine
kurulamıyor (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), tek çıkış yolu kaldırıp
yeniden kurmak ve o da **uygulama verisini siliyor**. Yani iki CI koşusu arasında
veritabanı göçünü gerçek veriyle denemek mümkün değildi: denenecek eski veri her
kurulumda yok oluyordu. Göç hatası bu projede yapılabilecek en pahalı
hatalardan biri olduğu için, onu telefonda deneyememek kabul edilebilir değil.

**Anahtarın depoda olması bilinçli.** Gizli değil ve gizli olması beklenmiyor:
şifresi Android'in kendi varsayılanıyla aynı (`android`), yalnızca hata ayıklama
yapısını imzalıyor ve hiçbir mağazaya bir şey yüklemiyor. Yayın imzası buna
bağlanmayacak — o anahtar depoya değil depo gizli anahtarlarına girer.

## Panelin rol görünürlüğü uygulamayla aynı kaynaktan sınanıyor

**Karar:** Panelin sekme görünürlüğü `roller.js`te ve `roller.test.js` bunu
Kotlin dosyasını (`RoleAccess.kt`) **okuyup** karşılaştırıyor.

**Neden:** Panel Finans sekmesini her role gösteriyordu; uygulama ise onu
eğitmene göstermiyor (`AppDestination.FINANCE`). Aynı ürün, aynı soruya iki
farklı cevap veriyordu. Bu, "Ekran görünürlüğü de tek kaynakta" kararında
düzeltilen hatanın panele taşınmış hâliydi — üçüncü kopya.

Kopyayı silmek mümkün değil: biri Kotlin, biri JavaScript. Ama kopyanın
**sapması** sınanabilir. Test Kotlin'deki `when (this)` gövdesini ayrıştırıp
hedef → rol eşlemesini çıkarıyor ve panelin tablosuyla karşılaştırıyor.

**Ayrıştırma bozulursa test düşüyor, geçmiyor.** Kırılgan bir ayrıştırıcının en
kötü davranışı "okuyamadım, o hâlde geçtim" olurdu: kural sanki sınanıyor
görünür, gerçekte hiçbir şey sınanmaz. Bu yüzden hedef bulunamazsa, gövde
okunamazsa ya da hiç eşleşme çıkmazsa test açıkça düşüyor. Dört bozma senaryosu
(panel değişti, Kotlin değişti, yeni ekran eklendi, biçim tanınmaz oldu) elle
denenip yakalandığı doğrulandı.

**Ters yön de sınanıyor:** uygulamada olup panelde olmayan bir hedef varsa test
düşüyor — bilinçli atlananlar (`SETTINGS`) listede gerekçesiyle yazılı.

## Stok panelde hareketlerden türüyor, eksik veriden sayı üretilmiyor

**Karar:** Panel eldeki stoğu `stock_movements` toplamından hesaplıyor
(`StockMovementDao.onHand` ile birebir aynı kural). Toplama girecek veri eksik
olabiliyorsa sayı **gösterilmiyor**, `?` yazıyor.

**Neden toplama panelde yapılabiliyor:** Panelin kuralı "sayma ve düz toplama
dışında hesap yok". Stok, tek kolonun toplamı — hakediş ya da bakiye gibi bir iş
kuralı değil. Ürün üzerinde mutlak sayaç tutulmamasının sebebi ayrı ve
uygulamada yazılı: iki cihaz aynı anda satış yaptığında bir satış sessizce
kaybolurdu.

**Neden eksik veride sayı gösterilmiyor:** Bir stok sayısı ekranda tek başına
duruyor ve doğru görünüyor. 500 hareketin ilk 500'ünden hesaplanmış bir toplam,
doğru bir toplamdan **ayırt edilemez** — yanlış olduğunu gösteren hiçbir iz
taşımaz, salon sahibi ona bakıp sipariş verir. Bu yüzden okuma sınırına
dayanıldığında (`oku` artık `kesildi` döndürüyor) ve okunamayan bir hareket
olduğunda sayı yerine `?` çıkıyor.

Aynı sebeple `Number()` doğrudan kullanılmıyor: `Number(null)`, `Number("")` ve
`Number(false)` **sıfır** veriyor, yani eksik bir alan sessizce "0 adet hareket"
olarak toplanır. Bu tuzağa yazarken düşüldü, testi de o yüzden var.

**Negatif stok ayrı bir durum,** tükenmenin daha kötüsü değil: fazla satış ya da
eksik alım kaydı demek. Aynı rozeti vermek sebebini araştırılmaz kılardı.
Sayaçlar ve rozetler **aynı** sınıflandırmayı kullanıyor (`stokDurumu`); ayrı
yazıldıklarında ayrıştılar ve kutuda "3 tükendi" yazarken tabloda 2 rozet
görünüyordu.

## Panelin kolon adları SQL şemasıyla sınanıyor

**Karar:** Sekmelerin veri tanımı ayrı bir dosyada (`sekmeler.js`) ve
`sekmeler.test.js` her kolon adını `supabase/migrations` içindeki gerçek şemayla
karşılaştırıyor.

**Neden:** Tanımlar 20'den fazla kolon adı taşıyor ve üç yazım hatası türünün
hepsi sessiz — `order`da hata sunucudan 400 aldırıp sekmeyi boş açar, `ara`da
hata aramayı o alanda çalıştırmaz, `tarihAlani`nda hata süzgeci işlevsiz kılar.
Üçü de yalnızca o sekmeye basıp elle deneyerek fark edilir.

**Neden demo verisi yerine SQL:** Demo verisi de bir kopya; kopyayı kopyayla
karşılaştırmak ikisinin **birlikte** yanlış olmasını yakalamıyor. Panelin
sorguları sunucuya gidiyor, yani doğruyu söyleyen şey migrasyonlar.

Demo verisi ayrıca sınanıyor ama farklı bir soru için: şemada olmayan bir alan
taşımıyor mu (taşırsa panel ona güvenir, gerçek veride tanımsız gelir) ve panelin
aradığı alanları taşıyor mu (taşımazsa o arama önizlemede denenemez). İkinci
kontrol iki eksik buldu: `gym_members.email` ve `appointments.notes` şemada
vardı, demo verisinde yoktu.

## Yayın anahtarı depoda değil, hata ayıklama anahtarı depoda

**Karar:** `app/debug.keystore` depoda; yayın anahtarı depo gizli
anahtarlarında (CI) ya da `keystore.properties`te (yerel, `.gitignore`'da).

**Neden ayrım:** İkisi aynı türde dosya ama taşıdıkları risk aynı değil. Hata
ayıklama anahtarı gizli değil — şifresi Android'in varsayılanı, yalnızca test
yapısını imzalıyor. Yayın anahtarı **kaybedilirse** aynı uygulama bir daha
güncellenemiyor (Android güncellemeyi yalnızca aynı anahtarla imzalanmışsa kabul
ediyor; yeni anahtar, kullanıcının uygulamayı kaldırıp yeniden kurması ve
**verisini kaybetmesi** demek), **sızarsa** başkası aynı uygulama gibi görünen
bir sürüm yayınlayabiliyor.

**Yayın imzası yoksa hata ayıklama anahtarına DÜŞÜLMÜYOR.** Bu, kolay ve yanlış
olan seçenekti: debug anahtarıyla imzalı bir "yayın" APK'sı kurulur, çalışır ve
yayın gibi görünür — ama gerçek yayın anahtarıyla bir daha asla güncellenemez.
Sessiz ve geri dönüşü olmayan bir hata. Onun yerine imza atlanıyor ve AGP
çıktıyı `app-release-unsigned.apk` diye adlandırıyor: imzasız olduğu dosya
adından anlaşılıyor.

**Günlük CI eksik anahtarla düşmüyor, yayın akışı düşüyor.** İkisi farklı iş
yapıyor: günlük CI derlemenin geçtiğini gösteriyor, yayın akışı ise yalnızca
imzalı bir APK üretmek için var. İkincisinin imzasız bir çıktıyla "başarılı"
olması, yayınlanamayacak bir şeyi yayınlanmış göstermek olurdu.

## Sürüm numarası etiketten türer

**Karar:** `versionName` yayın etiketinden geliyor (`-PsurumAdi=1.2.0`),
`versionCode` ondan hesaplanıyor (`major*10000 + minor*100 + patch`). İkisi de
elle yazılmıyor.

**Neden:** Sabit yazılıydı (`1` / `"1.0"`). Dağıtımda bu iki sessiz hataya
açık: aynı `versionCode` ile ikinci bir sürüm yayınlanamıyor (cihaz
güncellemeyi görmüyor) ve elle artırmak unutulduğunda hiçbir şey şikâyet
etmiyor. Etiketten türetince APK'nın içindeki sürüm ile yayınlanan sürüm aynı
kaynaktan geliyor.

**Ara numaralar 100'ün altında kalmalı** ve aşılırsa derleme düşüyor: sessizce
geriye giden bir `versionCode`, güncellemenin cihazda hiç görünmemesi demek.
`0.0.0` da reddediliyor — biçime uyuyor ama `versionCode = 0` üretiyor ve
Android en az 1 istiyor.

**Etiketsiz derlemelerin sürümü `0.0.0-gelistirme`.** Yayın olmadığı APK'nın
kendisinden anlaşılıyor.

**Yayın akışı türetmenin uygulandığını da doğruluyor:** APK'nın içindeki
`versionName` `aapt2 dump badging` ile okunup etiketle karşılaştırılıyor.
`-PsurumAdi` bir yazım hatasıyla düşerse Gradle sessizce varsayılana döner ve
`v1.2.0` etiketiyle `0.0.0` sürümlü bir APK yayınlanırdı. Aynı sebeple imza da
`apksigner verify` ile kriptografik olarak doğrulanıyor: `signingConfig` bir
şekilde boş kalırsa AGP derlemeyi düşürmüyor, yalnızca dosya adına `-unsigned`
ekliyor.

## Room şemasının depoda olması sınanıyor

**Karar:** CI, `@Database(version = ...)` değerini okuyup o sürümün şema
JSON'unun depoda ve güncel olduğunu doğruluyor; değilse iş düşüyor.

**Neden:** Şema dosyası derleme sırasında `shared/schemas/` altına yazılıyor ama
depoya işlenmesi elle yapılıyor. İşlenmediğinde hiçbir şey şikâyet etmiyordu:
sürüm artırılıyor, CI yeşil kalıyor ve eksiklik ancak birisi geçiş testi yazmaya
çalıştığında ortaya çıkıyordu. Geçiş testleri eski ve yeni şemayı bu
dosyalardan okuyor, `MigrationTestHelper` da eski sürümün veritabanını onlardan
kuruyor — yani eksik bir şema dosyası, yazılamayan bir geçiş testi demek.

**Üretilmedi ile işlenmedi ayrı raporlanıyor.** Adım önce derlemenin dosyayı
gerçekten ürettiğini doğruluyor; üretmemişse sorun "işlenmemiş dosya" değil,
adımın KSP'den önce koşması olur. İkisini aynı mesaja sıkıştırmak, yanlış yere
bakılan bir teşhis turu demekti.

**Düşerken dosyanın içeriğini de yazdırıyor.** Yapıtı indirmek her ortamda
mümkün olmuyor (bulut depolama adresleri ağ ilkelerince engellenebiliyor —
bu depoda bizzat yaşandı), ama günlükten kopyalamak her zaman mümkün.

**Kolon sırası geçiş testlerinde sıralı karşılaştırılmıyor.** Room tabloyu
**ada** göre doğruluyor; `TableInfo` kolonları ada göre eşlenmiş bir küme ve
sıra eşitliğe girmiyor. Bu, `ALTER TABLE ... ADD COLUMN` kullanan geçişlerde
gözle görülür bir fark yaratıyor: kolon fiziksel olarak tablonun **sonuna**
ekleniyor, şema dosyası ise onu alan tanımındaki yerinde listeliyor. İkisini
sıralı karşılaştırmak, çalışan bir geçişi düşen bir test hâline getirirdi.
Tabloyu yeniden kuran geçişlerde (ör. kolon silme) sıra birebir tutuyor ve
orada sıralı karşılaştırma yapılıyor.

## Senkronizasyon yazma anında tetiklenmez

**Karar:** Tetikleme girişte, oturum geri yüklendiğinde, uygulama önplandayken
dakikada bir ve elle yapılır. Kuyruğa kayıt eklendiğinde tetiklenmez.

**Neden:** Kuyruğa alma, satırı değiştiren yazmayla aynı transaction içinde
yapılıyor. O anda başlayan bir tur henüz işlenmemiş kaydı göremez: boşuna koşar
ve değişiklik bir sonraki tetiklemeye kalırdı. Düzenli tetikleme bu yarışı
tamamen ortadan kaldırıyor.

**İlgili karar:** Senkronizasyonun durumu ve bekleyen değişiklik sayısı Ayarlar
ekranında görünür. Görünmez bir senkronizasyon, çalışmadığında da çalışıyormuş
gibi görünür; bekleyen sayının düşmemesi kullanıcının fark edebileceği tek
belirti.

## Hatalı kaydın düzeltilmesi: silme değil ters kayıt, seçim kullanıcıda

**Karar:** Finans defterinden satır **silinmiyor**. Hatalı kayıt, aynı tutarda
ve `reversesId` alanı dolu ikinci bir satırla iptal ediliyor; ikisi de listede
kalıyor, toplamlarda birbirini götürüyor ve iptal edilmiş kayıt "İPTAL EDİLDİ"
rozetiyle işaretleniyor.

**Üye silinirken defterine ne olacağı KULLANICIYA soruluyor.** Silme onayı
üyenin yaşayan defter kayıtlarını kutularıyla listeliyor; iptal edilecekler tek
tek ya da "tümünü seç" ile işaretleniyor ve yalnızca işaretlenenler iptal
ediliyor. Aynı seçim finans ekranında da var ("Düzelt" kipi), çünkü **önceden
silinmiş** üyelerin kayıtlarına başka bir yerden ulaşılamıyor.

**Neden soruluyor:** Silme daha önce deftere hiç dokunmuyordu ve bu görünmez bir
hataydı — yanlışlıkla kaydedilen üye siliniyor, ondan doğan tahsilatlar finansta
kalıyor, salon o parayı almış görünüyordu. Koşulsuz iptal etmek ise ters yönde
aynı ağırlıkta olurdu: gerçekten ödeme almış bir üyenin kaydı silindiğinde ciro
sessizce düşerdi. İki durum da meşru ve ayırt edebilecek tek şey kullanıcı.

**Neden hepsi baştan işaretli geliyor:** Diyaloğun geldiği yer neredeyse her
zaman hatalı kayıt. İki hatanın maliyeti simetrik değil: fazladan iptal edilen
kayıt finansta rozetiyle **görünüyor** ve elle yeniden girilebiliyor, oysa hiç
iptal edilmeyen kayıt hiçbir yerde uyarı üretmiyor — düzeltilen hatanın ta
kendisi.

**Ters kayıt cari döneme yazılıyor**, orijinalin dönemine değil. Muhasebe
düzeltmeyi cari döneme işler; orijinalin dönemine yazılsaydı kapanmış bir ayın
toplamı geriye dönük değişirdi.

**Ters kaydın kendisi iptal edilemiyor.** İptalin iptali tutarı toplamlara geri
getirirdi ve defter geriye doğru oyulmuş olurdu; düzeltmenin düzeltmesi yeni bir
kayıt girmektir.

**İşlem idempotent.** Zaten iptal edilmiş bir kayıt ikinci kez iptal edilmiyor:
iki cihazdan aynı düzeltme yapıldığında tutar bir kez daha düşmemeli.

**Toplu iptal ya hep ya hiç.** Yarısı uygulanmış bir düzeltmede kullanıcı
"N kayıt iptal edildi" görür ama hangilerinin atlandığını bilmez; bilinmeyen bir
kimlik işlemin tamamını düşürüyor.
