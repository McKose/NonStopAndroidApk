# Yayın (release) sürümü çıkarmak

Hata ayıklama yapısı her CI koşusunda otomatik üretiliyor ve depodaki sabit
anahtarla imzalanıyor — onun için hiçbir şey yapmanız gerekmiyor.

Bu belge **yayın** sürümü için: kendi anahtarınızla imzalanmış, dağıtılabilir
APK. Anahtarı **siz** üretiyorsunuz ve depoya girmiyor.

---

## Neden anahtar depoda değil

Hata ayıklama anahtarı depoda (`app/debug.keystore`) ve bu bilinçli: gizli
değil, şifresi Android'in varsayılanı, yalnızca test yapısını imzalıyor.

Yayın anahtarı bambaşka bir şey:

- **Kaybederseniz** aynı uygulamayı bir daha güncelleyemezsiniz. Android, bir
  uygulamanın güncellemesini yalnızca aynı anahtarla imzalanmışsa kabul ediyor.
  Yeni anahtarla çıkacak sürüm, kullanıcının uygulamayı kaldırıp yeniden
  kurmasını gerektirir — ve bu **verisini siler**.
- **Sızarsa** başkası sizin uygulamanız gibi görünen bir sürüm yayınlayabilir.

Bu yüzden: anahtar dosyası `.gitignore`'da, CI'da depo gizli anahtarlarından
okunuyor ve **yedeğini sizin tutmanız gerekiyor.**

---

## 1. Anahtarı üret (tek seferlik)

Kendi makinenizde:

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -storetype PKCS12 \
  -alias nonstop \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -dname "CN=NonStop Studio, O=NonStop Studio, C=TR"
```

Şifre soracak — **güçlü bir şifre seçin ve bir şifre yöneticisine kaydedin.**
`-validity 10950` = 30 yıl; daha kısası, anahtarın süresi dolduğunda uygulamayı
güncelleyemez hâle gelmeniz demek.

> **Şimdi yedekleyin.** `release.keystore` dosyasını ve şifresini şifre
> yöneticinize / bir yedeğe koyun. Bu dosya yalnızca sizde var ve kopyası yok.

## 2. Depo gizli anahtarlarına ekle

Anahtar dosyasını base64'e çevirin (tek satır olması önemli):

```bash
base64 -w 0 release.keystore > release.keystore.b64
```

Sonra depoda **Settings → Secrets and variables → Actions → New repository
secret** ile dört değer ekleyin:

| Gizli anahtar adı | Değer |
|---|---|
| `YAYIN_KEYSTORE_BASE64` | `release.keystore.b64` dosyasının içeriği |
| `YAYIN_KEYSTORE_SIFRESI` | anahtar deposunun şifresi |
| `YAYIN_ANAHTAR_ADI` | `nonstop` (yukarıdaki `-alias` değeri) |
| `YAYIN_ANAHTAR_SIFRESI` | anahtarın şifresi (PKCS12'de depo şifresiyle aynı) |

Ayrıca sunucu ayarları gerekiyor — bunlar zaten ekli olabilir:

| Gizli anahtar adı | Değer |
|---|---|
| `SUPABASE_URL` | proje adresi |
| `SUPABASE_ANON_KEY` | `anon` anahtarı |

Yayın akışı bunlar eksikse **düşüyor**: sunucu ayarı olmayan bir yayın APK'sı
kurulur ama giriş ekranında "sunucu ayarları eksik" der. Günlük CI'da bu kabul
edilebilir (amaç derlemenin geçtiğini görmek), yayında değil.

## 3. Denemeden yayınla (önerilir)

Gerçek bir sürüm etiketi atmadan önce akışın çalıştığını görün:

```
Actions → "Yayın (imzalı APK)" → Run workflow
  surum:   0.0.1
  yayınla: ✗ (işaretsiz)
```

Bu, APK'yı üretip imzasını doğruluyor ama **GitHub Release oluşturmuyor**.
Yapıtlardan indirip telefonunuzda deneyebilirsiniz.

## 4. Gerçek sürüm

```bash
git tag v1.0.0
git push origin v1.0.0
```

Akış kendiliğinden çalışıyor ve bitince **Releases** sekmesinde imzalı APK'yı
bulacaksınız.

---

## Sürüm numarası nasıl belirleniyor

`versionName` ve `versionCode` elle yazılmıyor; **etiketten türüyor.**

```
v1.2.0  →  versionName = "1.2.0"   versionCode = 10200
```

`versionCode` = `major*10000 + minor*100 + patch`. Sıralamayı korumak için ara
numaralar 100'ün altında kalmalı — aşarsanız derleme düşer ve sebebini yazar.

Bunun elle yazılmamasının sebebi: aynı `versionCode` ile ikinci bir sürüm
yayınlamak mümkün değil (cihaz güncellemeyi görmez), ve elle bumping
unutulduğunda hiçbir şey şikâyet etmiyor. Etiketten türetince APK'nın içindeki
sürüm ile yayınlanan sürüm aynı kaynaktan geliyor.

Etiket olmadan derlenen APK'ların sürümü bilinçli olarak `0.0.0-gelistirme`:
yayın olmadığı APK'nın kendisinden anlaşılıyor.

---

## Akış neyi doğruluyor

Yayın akışı "derledi, bitti" demiyor. Sırayla:

1. **Sürüm biçimi** — `X.Y.Z` değilse durur.
2. **Anahtar okunabilir mi** — base64 çözülüyor, `keytool` ile deponun
   açıldığı ve **takma adın gerçekten o depoda olduğu** doğrulanıyor. Yanlış
   alias, aksi hâlde derlemenin sonunda anlaşılmaz bir hatayla düşerdi.
3. **Testler** — etiket atmak testleri atlamanın yolu değil.
4. **İmza gerçekten atıldı mı** — `apksigner verify` ile kriptografik doğrulama,
   artı dosya adında `unsigned` geçmediğinin kontrolü. Bu adım olmasa imzasız
   bir APK "yayın" diye yayınlanabilirdi.
5. **APK içindeki sürüm etiketle aynı mı** — `aapt2 dump badging` ile okunuyor.
   `-PsurumAdi` bir yazım hatasıyla düşerse Gradle sessizce
   `0.0.0-gelistirme` kullanır ve `v1.2.0` etiketiyle `0.0.0` sürümlü bir APK
   yayınlanırdı.

İmzanın SHA-256 parmak izi sürüm notlarına yazılıyor: yayınlanan APK'nın hangi
anahtarla imzalandığı sürüm kaydında görünüyor.

---

## Yerel makinede imzalı derleme

Gizli anahtarlar CI'a özgü; yerelde `keystore.properties` kullanılıyor
(`.gitignore`'da, depo köküne):

```properties
storeFile=/home/kullanici/anahtarlar/release.keystore
storePassword=...
keyAlias=nonstop
keyPassword=...
```

Sonra:

```bash
./gradlew :app:assembleRelease -PsurumAdi=1.0.0
```

Dosya yoksa ya da eksikse derleme **düşmüyor**; imzasız APK üretiyor ve hangi
değerin eksik olduğunu yazıyor. Çıktının adı da söylüyor:
`app-release-unsigned.apk`.

**Hata ayıklama anahtarına bilinçli olarak düşülmüyor.** Debug anahtarıyla
imzalı bir "yayın" APK'sı kurulur, çalışır ve yayın gibi görünür — ama gerçek
yayın anahtarıyla bir daha asla güncellenemez. Sessiz ve geri dönüşü olmayan bir
hata olurdu.

---

## Kod küçültme (R8) neden kapalı

`isMinifyEnabled = false`. Açmak Room, Koin ve `kotlinx.serialization` için
saklama kuralları yazmayı gerektiriyor; eksik bir kural **çalışma zamanında ve
yalnızca yayın yapısında** patlıyor — yani en pahalı yerde, kullanıcının
telefonunda. Açılacaksa kendi doğrulama turuyla açılmalı, yayın kurulumuna
sıkıştırılmamalı.
