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
