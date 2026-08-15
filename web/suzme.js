// Listelerin aranması ve tarihe göre süzülmesi.
//
// Süzme **tarayıcıda**, sunucuda değil. Sebebi salonun ölçeği: birkaç yüz üye,
// birkaç bin defter kaydı. Bu boyutta sunucuya sorgu göndermek her tuş
// vuruşunda ağ turu demek olurdu; tarayıcıda süzmek anında sonuç veriyor.
// Kayıt sayısı on binlere çıkarsa doğru cevap değişir ve süzme sunucuya taşınır
// — o zaman değiştirilecek yer burası, çağıran ekranlar değil.

/**
 * Karşılaştırma için metni sadeleştirir.
 *
 * Türkçe'de büyük/küçük harf dönüşümü tek başına yetmiyor ve JavaScript'in
 * varsayılanı burada **yanlış**: `"IŞIL".toLowerCase()` → `"ışıl"` değil
 * `"ışıl"`in beklendiği yerde `"i̇şıl"` üretir, `"İ".toLowerCase()` ise `i` artı
 * ayrı bir birleşen nokta verir. Yani salon sahibi "isil" yazıp "Işıl"ı
 * bulamaz.
 *
 * Bu yüzden Türkçe'ye özgü harfler önce ASCII karşılıklarına eşleniyor, sonra
 * kalan aksanlar (é, ñ gibi) ayrıştırılıp atılıyor. Sonuç: "AYŞE", "ayşe" ve
 * "ayse" aynı metne indirgeniyor — kullanıcı nasıl yazarsa yazsın bulur.
 *
 * `ı` ve `i` bilinçli olarak **aynı** harfe indirgeniyor. Dilbilimsel olarak
 * ayrı harfler, ama arama kutusunda ayırmak "Işıl" ile "İşil"i farklı sonuçlara
 * düşürürdü ve kullanıcı hangisini yazdığını çoğu zaman bilmiyor.
 */
const HARF_ESLEME = {
  "İ": "i", "I": "i", "ı": "i",
  "Ş": "s", "ş": "s",
  "Ğ": "g", "ğ": "g",
  "Ü": "u", "ü": "u",
  "Ö": "o", "ö": "o",
  "Ç": "c", "ç": "c",
};

export function metinSadelestir(metin) {
  if (metin === null || metin === undefined) return "";
  return String(metin)
    .replace(/[İIıŞşĞğÜüÖöÇç]/g, (harf) => HARF_ESLEME[harf])
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .trim();
}

/**
 * Satırın verilen alanlarından herhangi biri sorguyu içeriyor mu?
 *
 * Boş sorgu **her** satırı geçiriyor: "arama yapılmadı" ile "hiçbir şey
 * eşleşmedi" farklı durumlar ve ilkinde liste dolu kalmalı.
 *
 * Sorgu boşluğa göre parçalanıp her parça ayrı aranıyor: "ayşe 0532" yazan biri
 * hem adı hem telefonu kastediyor, oysa tek parça olarak arandığında hiçbir
 * alanda böyle bir metin yok ve sonuç boş çıkardı.
 */
export function metinEslesir(satir, alanlar, sorgu) {
  const parcalar = metinSadelestir(sorgu).split(/\s+/).filter(Boolean);
  if (parcalar.length === 0) return true;

  const havuz = alanlar
    .map((alan) => metinSadelestir(satir[alan]))
    .filter(Boolean)
    .join(" ");

  return parcalar.every((parca) => havuz.includes(parca));
}

/**
 * `<input type="date">` değerini o günün **yerel** başlangıcına çevirir.
 *
 * `new Date("2026-08-15")` kullanılmıyor: o biçim UTC gece yarısı olarak
 * ayrıştırılıyor ve Türkiye'de (UTC+3) sonuç 15 Ağustos 03:00 oluyor. Yani
 * 15 Ağustos gece yarısı ile 03:00 arasındaki kayıtlar aralığın dışında kalır
 * ve kimse sebebini anlamaz. Parçalayıp yerel kurucuya vermek bunu çözüyor.
 */
export function gunBasi(tarihMetni) {
  const parcalar = ayristir(tarihMetni);
  if (!parcalar) return null;
  const [yil, ay, gun] = parcalar;
  return new Date(yil, ay - 1, gun, 0, 0, 0, 0).getTime();
}

/**
 * Aynı değerin gün **sonu** karşılığı (23:59:59.999).
 *
 * Gün başı kullanılıp `<=` ile karşılaştırılsaydı, bitiş gününe ait kayıtların
 * neredeyse tamamı aralığın dışında kalırdı — yalnızca tam gece yarısı yazılmış
 * olanlar girerdi. Kullanıcı "1–15 Ağustos" derken 15'ini de kastediyor.
 */
export function gunSonu(tarihMetni) {
  const parcalar = ayristir(tarihMetni);
  if (!parcalar) return null;
  const [yil, ay, gun] = parcalar;
  return new Date(yil, ay - 1, gun, 23, 59, 59, 999).getTime();
}

/** `"2026-08-15"` → `[2026, 8, 15]`; tanınmayan biçimde `null`. */
function ayristir(tarihMetni) {
  if (!tarihMetni) return null;
  const eslesme = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(tarihMetni).trim());
  if (!eslesme) return null;
  const yil = Number(eslesme[1]);
  const ay = Number(eslesme[2]);
  const gun = Number(eslesme[3]);
  if (ay < 1 || ay > 12 || gun < 1 || gun > 31) return null;
  // Taşan gün (ör. 31 Nisan) JavaScript'te sessizce sonraki aya kayar; bunu
  // geçerli saymak, kullanıcının hiç yazmadığı bir tarihle süzmek olurdu.
  const deneme = new Date(yil, ay - 1, gun);
  if (deneme.getMonth() !== ay - 1 || deneme.getDate() !== gun) return null;
  return [yil, ay, gun];
}

/**
 * Satırı tarih aralığına göre sınar.
 *
 * Sınır **her iki uçta da dahil**. Tarihi olmayan satır (ör. bitiş tarihi
 * girilmemiş süresiz üyelik) aralık verildiğinde eleniyor: "1–15 Ağustos
 * arasında bitenler" sorusunun cevabı, hiç bitmeyen bir üyelik değil.
 */
export function tarihAraliginda(satir, alan, baslangicMs, bitisMs) {
  if (baslangicMs === null && bitisMs === null) return true;

  const ham = satir[alan];
  if (ham === null || ham === undefined || ham === "") return false;
  const deger = Number(ham);
  if (!Number.isFinite(deger)) return false;

  if (baslangicMs !== null && deger < baslangicMs) return false;
  if (bitisMs !== null && deger > bitisMs) return false;
  return true;
}

/**
 * Metin ve tarih süzgeçlerini birlikte uygular.
 *
 * İkisi **VE** ile birleşiyor: iki süzgeç de doluysa satır ikisini birden
 * sağlamalı. VEYA olsaydı tarih aralığı seçmek listeyi daraltmak yerine
 * genişletirdi — kullanıcının beklediğinin tam tersi.
 */
export function suz(satirlar, secenekler = {}) {
  const {
    sorgu = "",
    alanlar = [],
    tarihAlani = null,
    baslangic = null,
    bitis = null,
  } = secenekler;

  const baslangicMs = gunBasi(baslangic);
  const bitisMs = gunSonu(bitis);

  return satirlar.filter((satir) => {
    if (alanlar.length > 0 && !metinEslesir(satir, alanlar, sorgu)) return false;
    if (tarihAlani && !tarihAraliginda(satir, tarihAlani, baslangicMs, bitisMs)) return false;
    return true;
  });
}
