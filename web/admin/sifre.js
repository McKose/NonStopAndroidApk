// Şifre değiştirmenin KARAR mantığı — çizim yok, ağ yok.
//
// Uygulamadaki `SifreKurali` (Kotlin) ile **aynı kurallar** ve bu bir kopya:
// tarayıcı Kotlin çalıştırmıyor, dolayısıyla kaçınılmaz. Sapması sessiz
// olurdu — bir tarafta kabul edilen şifre diğerinde reddedilir ve kullanıcı
// hangisinin doğru olduğunu bilemezdi — o yüzden iki taraftaki testler aynı
// örnekleri kullanıyor.
//
// Adlar `SIFRE_`/`sifre` önekli: önizleme üreticisi modülleri tek kapsamda
// birleştiriyor ve çakışan iki tanım panelin tamamını düşürüyor. Takma adla
// içe almak çözüm DEĞİL — birleştirmede `import ... as` diye bir şey kalmıyor.

/**
 * En az uzunluk. Supabase'in varsayılanı 6; burası bilinçli olarak daha katı
 * ve uygulamadaki `SifreKurali.EN_AZ_UZUNLUK` ile aynı olmak zorunda.
 */
export const SIFRE_EN_AZ_UZUNLUK = 8;

/**
 * Yeni şifreyi doğrular.
 *
 * @returns `{ gecerli: true, sifre }` ya da `{ gecerli: false, mesaj }`
 */
export function sifreyiDogrula({ mevcut = "", yeni = "", tekrar = "" } = {}) {
  if (mevcut === "") {
    return { gecerli: false, mesaj: "Mevcut şifrenizi girin." };
  }
  if (yeni.length < SIFRE_EN_AZ_UZUNLUK) {
    return {
      gecerli: false,
      mesaj: `Yeni şifre en az ${SIFRE_EN_AZ_UZUNLUK} karakter olmalı.`,
    };
  }

  // Baştaki/sondaki boşluk KIRPILMIYOR, reddediliyor.
  //
  // Kırpmak sessiz bir tuzak olurdu: kullanıcı "abc12345 " yazar, sunucuya
  // "abc12345" gider, sonra giriş ekranında yazdığının aynısını yazıp "şifre
  // yanlış" cevabını alır — kaybettiği karakteri hiçbir yerde göremeden.
  if (yeni !== yeni.trim()) {
    return { gecerli: false, mesaj: "Şifrenin başında veya sonunda boşluk olamaz." };
  }
  if (yeni !== tekrar) {
    return { gecerli: false, mesaj: "İki şifre birbirini tutmuyor." };
  }
  if (yeni === mevcut) {
    return { gecerli: false, mesaj: "Yeni şifre eskisiyle aynı olamaz." };
  }

  return { gecerli: true, sifre: yeni };
}

/**
 * Sunucu yanıtını okunur bir mesaja çevirir.
 *
 * Ham durum kodu ("Hata 422") kullanıcıya hiçbir şey anlatmıyor; her kodun
 * karşılığı ne yapılması gerektiğini söylüyor. Bilinmeyen kodlarda sunucunun
 * kendi mesajı kullanılıyor — uydurulmuş bir açıklama, anlaşılmaz bir koddan
 * kötüdür.
 */
export function sifreHataMesaji(durumKodu, govde) {
  const sunucudan = typeof govde?.mesaj === "string" ? govde.mesaj : "";

  switch (durumKodu) {
    case 401:
    case 403:
      // Buraya gelindiyse mevcut şifre ZATEN doğrulanmıştı (bkz.
      // `sifreDegistir`); jeton düşmüş demektir ve söylenecek şey farklı.
      return "Oturumunuz sona ermiş. Çıkıp yeniden giriş yapın.";
    case 422:
      return sunucudan || "Sunucu bu şifreyi kabul etmedi.";
    case 0:
      // Ağ hatası: yanıt hiç gelmedi. Ayrı tutuluyor çünkü yapılacak şey
      // farklı — tekrar denemek güvenli, işlem tekrar edilebilir.
      return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip tekrar deneyin.";
    default:
      return sunucudan || `Şifre değiştirilemedi (${durumKodu}).`;
  }
}
