# USB Deploy Rehberi — Xiaomi Redmi Note 10 Pro (Android 13)

## 1. Cihaz Hazırlığı

### Adım 1.1 — Geliştirici Modunu Aç
```
Ayarlar → Telefon Hakkında → MIUI Sürümü
→ 7 kez art arda dokun
→ "Artık bir geliştiricisiniz" mesajını gör
```

### Adım 1.2 — USB Hata Ayıklamayı Aç
```
Ayarlar → Ek Ayarlar → Geliştirici Seçenekleri →
  ✅ USB Hata Ayıklama
  ✅ USB Üzerinden Yüklemeye İzin Ver   ← MIUI'da kritik, aksi halde "INSTALL_FAILED_USER_RESTRICTED"
```

### Adım 1.3 — MIUI'ya Özel: Yükleme Kısıtlamasını Kaldır
```
Ayarlar → Ek Ayarlar → Gizlilik →
  ✅ Bilinmeyen Kaynaklara İzin Ver  (genel switch)

veya MIUI 14 için:
  Güvenlik uygulaması → İzinler → Yükle Bilinmeyen Uygulamalar
```

---

## 2. ADB ile Bağlantı Kontrolü

```bash
# Bağlı cihazları listele
adb devices

# Beklenen çıktı:
# List of devices attached
# XXXXXXXX    device     ← "device" olmalı; "unauthorized" ise cihazda onay ver

# Cihaz bilgisi kontrol
adb shell getprop ro.product.model     # → M2101K6G
adb shell getprop ro.build.version.sdk # → 33
```

### Sık Karşılaşılan MIUI Sorunları

| Hata | Çözüm |
|------|-------|
| `unauthorized` | Cihazda "Her zaman izin ver" seç, `adb kill-server && adb start-server` |
| `INSTALL_FAILED_USER_RESTRICTED` | "USB üzerinden yüklemeye izin ver" toggle'ını aç/kapat |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | `adb uninstall com.gymapp` ile eski sürümü kaldır |
| `insufficient permissions` | Linux'ta: `sudo adb devices` veya udev kuralı ekle |

---

## 3. Android Studio ile Deploy

```
Run → Edit Configurations → App
  Module: app
  Deploy: Default APK
  
Toolbar'dan hedef cihazı seç:
  "Xiaomi Redmi Note 10 Pro (API 33)"
  
▶ Run  (veya Shift+F10)
```

**Build variant:** `debug` (geliştirme için yeterli, imzasız APK)

---

## 4. Saf ADB ile Manuel Deploy

```bash
# 1. Debug APK derle
./gradlew assembleDebug

# 2. APK yolu
# app/build/outputs/apk/debug/app-debug.apk

# 3. Cihaza yükle
adb install -r app/build/outputs/apk/debug/app-debug.apk
# -r = replace (aynı paket varsa güncelle)

# 4. Uygulamayı başlat
adb shell am start -n com.gymapp/.MainActivity

# 5. Logcat (sadece uygulama logları)
adb logcat -s "GymApp" --pid=$(adb shell pidof -s com.gymapp)
```

---

## 5. Android 13 Runtime İzin Akışı (POST_NOTIFICATIONS)

Android 13'te bildirim izni otomatik verilmiyor. Uygulama ilk açılışta:

```kotlin
// MainActivity.kt içinde
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    if (ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATION_PERMISSION
        )
    }
}
```

---

## 6. Hızlı Test Komutu Seti

```bash
# Tek satırda: derle → yükle → başlat → logcat
./gradlew assembleDebug && \
  adb install -r app/build/outputs/apk/debug/app-debug.apk && \
  adb shell am start -n com.gymapp/.MainActivity && \
  adb logcat -v time com.gymapp:V *:S
```
