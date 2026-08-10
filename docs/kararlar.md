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
