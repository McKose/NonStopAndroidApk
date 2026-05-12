# Şifre ve Rol Atamalarının Kaldırılması Planı

Kullanıcı girişindeki şifre gereksinimi ve personel yönetimindeki rol/şifre atamaları kaldırılacaktır. Sistem sadece kullanıcı adı ile girişe izin verecek şekilde basitleştirilecektir.

## Önerilen Değişiklikler

### Giriş Sistemi

#### [LoginScreen.kt](file:///C:/Project/nonstopandroidapk/app/src/main/java/com/gymapp/presentation/login/LoginScreen.kt)
- Şifre alanı ve ilgili `remember` değişkeni kaldırılacak.
- "Giriş Yap" butonu sadece kullanıcı adını `viewModel.login`'e gönderecek.

#### [LoginViewModel.kt](file:///C:/Project/nonstopandroidapk/app/src/main/java/com/gymapp/presentation/login/LoginViewModel.kt)
- `login` fonksiyonu sadece `nickname` alacak.
- `PasswordHasher` doğrulaması kaldırılacak.
- Admin girişi sadece "admin" kullanıcı adı ile yapılacak.
- `prefs.currentUserRole` ataması kaldırılacak veya sabit bir değer atanacak.

### Personel Yönetimi

#### [PersonnelScreen.kt](file:///C:/Project/nonstopandroidapk/app/src/main/java/com/gymapp/presentation/settings/PersonnelScreen.kt)
- `AddStaffDialog` ve `EditStaffDialog` içindeki şifre ve rol alanları kaldırılacak.
- Rol seçim menüleri (admin, yönetici, antrenör) UI'dan silinecek.

#### [PersonnelViewModel.kt](file:///C:/Project/nonstopandroidapk/app/src/main/java/com/gymapp/presentation/settings/PersonnelViewModel.kt)
- `addStaff` fonksiyonundaki `role` ve `initialPassword` parametreleri kaldırılacak (veya varsayılan değerlere çekilecek).
- Şifre üretme ve hashleme mantığı temizlenecek.
- `resetStaffPassword` fonksiyonu kaldırılacak.

### Veri Modeli ve Tercihler

#### [StaffEntity.kt](file:///C:/Project/nonstopandroidapk/app/src/main/java/com/gymapp/data/local/entity/StaffEntity.kt)
- `role` ve `password` alanları isteğe bağlı hale getirilecek veya varsayılan boş string atanacak (Veritabanı migration karmaşasından kaçınmak için alanları silmek yerine varsayılan değerler kullanılacak).

#### [AppPreferences.kt](file:///C:/Project/nonstopandroidapk/app/src/main/java/com/gymapp/data/local/preferences/AppPreferences.kt)
- `salonPasswordHash`, `verifySalonPassword` ve `updateSalonPassword` fonksiyonları işlevsiz hale getirilecek veya kaldırılacak.
- `currentUserRole` kullanımı minimuma indirilecek.

## Doğrulama Planı

### Manuel Doğrulama
- Sadece "admin" yazarak giriş yapılabildiği kontrol edilecek.
- Personel listesinden bir kullanıcı adı ile şifresiz giriş yapılabildiği doğrulanacak.
- Yeni personel eklerken rol ve şifre sorulmadığı teyit edilecek.
- Personel düzenleme ekranında şifre değiştirme alanının olmadığı görülecek.
