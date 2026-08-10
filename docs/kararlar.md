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
