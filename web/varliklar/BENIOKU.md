# Site varlıkları

## Karşılama videosu — henüz eklenmedi

Banner şu an `salon-ic.jpg` ile açılıyor. Video eklendiğinde fotoğraf
**yerinde kalıyor** ve video hazır olduğunda üstüne geçiyor.

### Açmak için

1. Videoyu bu klasöre `kahraman.mp4` adıyla koyun.
2. `web/index.html` içinde `data-kaynak` alanını doldurun:

   ```html
   <video class="kahraman-video" id="kahraman-video"
          muted playsinline loop preload="none"
          data-kaynak="varliklar/kahraman.mp4"
          poster="varliklar/salon-ic.jpg"></video>
   ```

Yol boş bırakıldığı sürece tarayıcı video için **hiç istek atmıyor**. Dolu
yazılıp dosya konmazsa yayın testi (`yayin-dosyalari.test.js`) bunu yakalar ve
CI kırmızı döner — sessiz 404 olmuyor.

### Videoda dikkat edilecekler

| Konu | Öneri | Neden |
|---|---|---|
| Süre | 8–15 sn, kusursuz döngü | Döngüye alınıyor; dikişin görünmemesi için başı ve sonu benzer kare olmalı |
| Çözünürlük | 1920×1080 yeter | Banner `object-fit: cover`; daha yükseği yalnızca dosya boyutunu şişirir |
| Boyut | **3 MB altı** | Mobil veriyle açılıyor; büyük dosya yüklenene kadar fotoğraf görünür ama video hiç başlamayabilir |
| Ses | Sessiz kodlanmış olmalı | Otomatik oynatma sessiz olmayan videoda tarayıcılarca reddediliyor |
| Kodek | H.264 (yaygın) | Safari/iOS uyumu için en güvenlisi |

Ses parçası olan bir dosya da çalışır (`muted` verili), ama boşuna yer kaplar.

### Sessizce fotoğrafta kalınan durumlar

Bunların hiçbiri hata değil, tasarlanmış davranış:

- Video yolu boş
- Dosya indirilemedi (ağ hatası)
- Tarayıcı otomatik oynatmayı reddetti (iOS düşük güç modu gibi)
- Kullanıcı **hareket azaltma** tercihini açmış — bu durumda video hiç
  indirilmiyor
