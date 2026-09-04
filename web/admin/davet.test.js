// Personel erişimi: karar mantığı testleri.
//
// Bu dosyanın sınadığı şeylerin ortak özelliği: hepsi YANLIŞ OLDUĞUNDA
// ekranda düzgün görünüyor. Yanlış bir durum etiketi, yanlış normalleştirilmiş
// bir e-posta ya da yutulmuş bir hata kodu — hiçbiri çökme üretmiyor, yalnızca
// yöneticiyi yanlış yönlendiriyor.

import test from "node:test";
import assert from "node:assert/strict";

import {
  DAVET_YETKILERI,
  YETKI_ACIKLAMA,
  erisimDurumu,
  erisimEtiketi,
  davetiDogrula,
  davetHataMesaji,
  davetBasariMesaji,
  erisimKaldirilabilirMi,
  kaldirmaOnayMetni,
  kaldirmaHataMesaji,
  kaldirmaBasariMesaji,
} from "./davet.js";

// ─── Erişim durumu ──────────────────────────────────────────────────────────

test("hesabı olmayan personel: hesap_yok", () => {
  assert.deepEqual(
    erisimDurumu({ auth_user_id: null }, []),
    { durum: "hesap_yok", yetki: null },
  );
  // Alan hiç yoksa da aynı sonuç: `staff` satırı eski bir sürümden gelmiş
  // olabilir ve `undefined` "hesabı var" sayılmamalı.
  assert.equal(erisimDurumu({}, []).durum, "hesap_yok");
  assert.equal(erisimDurumu(null, []).durum, "hesap_yok");
});

/**
 * Asıl sinsi hâl: hesap var, salon yetkisi yok.
 *
 * Bu kişi giriş yapabiliyor ve hiçbir veri göremiyor — uygulamada hata da
 * çıkmıyor. İki hâlli bir model (`erişimi var / yok`) bunu "erişimi var"
 * tarafına yazardı ve yönetici sorunu hiç göremezdi.
 */
test("hesabı olan ama yetkisi olmayan personel: yetki_yok", () => {
  const sonuc = erisimDurumu({ auth_user_id: "auth-1" }, [
    { user_id: "baska-hesap", role: "ADMIN" },
  ]);
  assert.deepEqual(sonuc, { durum: "yetki_yok", yetki: null });
});

test("yetkisi olan personel: erisim_var ve yetki okunuyor", () => {
  const sonuc = erisimDurumu({ auth_user_id: "auth-1" }, [
    { user_id: "auth-1", role: "MANAGER" },
  ]);
  assert.deepEqual(sonuc, { durum: "erisim_var", yetki: "MANAGER" });
});

/**
 * Yetki `gym_users`tan okunuyor, `staff.role`dan DEĞİL.
 *
 * İki tablo aynı adı taşıyan ayrı alanlara sahip ve ayrışabiliyorlar:
 * `staff.role` personelin unvanı, gerçek yetkiyi belirleyen `gym_users.role`.
 * Ekranda `staff.role` gösterilseydi yönetici birine ADMIN yetkisi verdiğini
 * sanırken kişi eğitmen yetkisiyle geziyor olabilirdi.
 */
test("gösterilen yetki gym_users'tan geliyor, staff.role'dan değil", () => {
  const sonuc = erisimDurumu(
    { auth_user_id: "auth-1", role: "ADMIN" },   // staff.role
    [{ user_id: "auth-1", role: "TRAINER" }],    // gym_users.role
  );
  assert.equal(sonuc.yetki, "TRAINER", "staff.role gösterilmiş — yanlış tablo");
});

test("her durumun bir etiketi var", () => {
  for (const d of ["erisim_var", "yetki_yok", "hesap_yok"]) {
    assert.ok(erisimEtiketi(d).length > 0, `${d} etiketsiz`);
  }
  assert.equal(erisimEtiketi("bilinmeyen"), "Bilinmiyor");
});

// ─── Doğrulama ──────────────────────────────────────────────────────────────

test("eksik alanlar reddediliyor", () => {
  assert.equal(davetiDogrula({}).gecerli, false);
  assert.equal(davetiDogrula({ personelId: "p1" }).gecerli, false);
  assert.equal(
    davetiDogrula({ personelId: "p1", eposta: "a@b.co" }).gecerli,
    false,
    "yetki seçilmeden geçti",
  );
});

test("bozuk e-posta reddediliyor", () => {
  for (const e of ["ali", "ali@", "@ornek.com", "ali@ornek", "a li@ornek.com"]) {
    const s = davetiDogrula({ personelId: "p1", eposta: e, yetki: "TRAINER" });
    assert.equal(s.gecerli, false, `kabul edildi: ${e}`);
  }
});

/**
 * E-posta küçük harfe çevriliyor.
 *
 * Sunucu da aynısını yapıyor ve `auth.users` araması harf duyarsız. İkisi
 * ayrışsaydı yönetici `Ali@...` yazıp "hesap yok", sonra `ali@...` yazıp
 * "hesap var" cevabı alırdı — aynı kişi için iki farklı gerçek.
 */
test("e-posta ve yetki normalleştiriliyor", () => {
  const s = davetiDogrula({
    personelId: "  p1  ",
    eposta: "  Ali.Veli@Ornek.COM ",
    yetki: "trainer",
  });
  assert.equal(s.gecerli, true);
  assert.deepEqual(s.deger, {
    personelId: "p1",
    eposta: "ali.veli@ornek.com",
    yetki: "TRAINER",
  });
});

test("tanınmayan yetki reddediliyor", () => {
  const s = davetiDogrula({ personelId: "p1", eposta: "a@b.co", yetki: "OWNER" });
  assert.equal(s.gecerli, false);
});

test("her yetkinin açıklaması var", () => {
  for (const y of DAVET_YETKILERI) {
    assert.ok(YETKI_ACIKLAMA[y]?.length > 0, `${y} açıklamasız`);
  }
  assert.deepEqual(
    Object.keys(YETKI_ACIKLAMA).sort(),
    [...DAVET_YETKILERI].sort(),
    "açıklama listesi yetki listesiyle ayrışmış",
  );
});

// ─── Hata ve başarı mesajları ───────────────────────────────────────────────

test("bilinen hata kodları ne yapılacağını söylüyor", () => {
  assert.match(davetHataMesaji(401, {}), /giriş/i);
  assert.match(davetHataMesaji(403, {}), /yönetici/i);
  assert.match(davetHataMesaji(404, {}), /bulunamadı/i);
  assert.match(davetHataMesaji(0, {}), /bağlantı/i);
});

/**
 * Sunucunun kendi mesajı korunuyor.
 *
 * 409 birden çok sebepten dönebiliyor (personel başka hesaba bağlı, hesap
 * başka personele bağlı, e-posta tek hesaba eşlenemedi) ve hangisi olduğunu
 * yalnızca sunucu biliyor. Sabit bir metinle değiştirmek, yöneticiye yanlış
 * düzeltmeyi denetirdi.
 */
test("sunucu mesajı varsa o kullanılıyor", () => {
  const mesaj = "Bu hesap başka bir personel kaydına bağlı.";
  assert.equal(davetHataMesaji(409, { hata: mesaj }), mesaj);
  assert.equal(davetHataMesaji(502, { hata: mesaj }), mesaj);
});

test("bilinmeyen kod, mesaj yoksa kodu gösteriyor", () => {
  assert.match(davetHataMesaji(418, {}), /418/);
});

test("başarı mesajı iki durumu ayırıyor", () => {
  const acildi = davetBasariMesaji({ durum: "hesap_acildi", personel: "Ayşe" });
  const baglandi = davetBasariMesaji({
    durum: "mevcut_hesap_baglandi",
    personel: "Ayşe",
  });

  assert.notEqual(acildi, baglandi);
  // Mevcut hesap bağlandığında şifre DEĞİŞMİYOR ve bunun yazması gerekiyor:
  // yönetici geçici şifre bekleyip bulamayınca işlemin yarım kaldığını sanar.
  assert.match(baglandi, /şifre/i);
});

// ─── Erişimi kaldırma ───────────────────────────────────────────────────────

const BEN = "11111111-1111-1111-1111-111111111111";
const BASKASI = "22222222-2222-2222-2222-222222222222";

test("erişimi olan personelin erişimi kaldırılabiliyor", () => {
  const sonuc = erisimKaldirilabilirMi({
    durum: "erisim_var",
    personelAuthId: BASKASI,
    oturumKullaniciId: BEN,
  });
  assert.equal(sonuc.olur, true);
});

/**
 * Salonun kilitlenmesini engelleyen tek kontrol bu.
 *
 * Bu ekranı yalnızca ADMIN görüyor; çağıran kendini kaldıramadığına göre
 * salonda her zaman en az bir ADMIN kalıyor. Kontrol düşerse belirti geç ve
 * geri dönülmez: son yönetici kendini kaldırır, panele kimse giremez ve
 * düzeltmek Supabase paneli gerektirir.
 */
test("kendi erişimini kaldıramıyor", () => {
  const sonuc = erisimKaldirilabilirMi({
    durum: "erisim_var",
    personelAuthId: BEN,
    oturumKullaniciId: BEN,
  });
  assert.equal(sonuc.olur, false);
  assert.match(sonuc.sebep, /[Kk]endi/);
});

test("hesabı olmayanda kaldırılacak bir şey yok", () => {
  const sonuc = erisimKaldirilabilirMi({
    durum: "hesap_yok",
    personelAuthId: null,
    oturumKullaniciId: BEN,
  });
  assert.equal(sonuc.olur, false);
});

/**
 * `yetki_yok` KALDIRILABİLİR olmalı.
 *
 * Orada `gym_users` satırı zaten yok ama `staff.auth_user_id` duruyor ve o
 * artık bağ panelde bir arıza uyarısı üretiyor ("giriş yapıyor, boş ekran
 * görüyor"). Kaldırma kapatılsaydı o satırı temizlemenin panelden yolu
 * kalmazdı — yani bu akışın var oluş sebebi olan duruma geri dönülürdü.
 */
test("yetkisi eksik personelin artık bağı temizlenebiliyor", () => {
  const sonuc = erisimKaldirilabilirMi({
    durum: "yetki_yok",
    personelAuthId: BASKASI,
    oturumKullaniciId: BEN,
  });
  assert.equal(sonuc.olur, true);
});

/** Eksik/boş girdi kaldırmayı açmamalı: varsayılan "hayır" olmalı. */
test("kimlik bilinmiyorsa kaldırma kapalı", () => {
  assert.equal(erisimKaldirilabilirMi().olur, false);
  assert.equal(
    erisimKaldirilabilirMi({ durum: "erisim_var", personelAuthId: null }).olur,
    false,
  );
});

/**
 * Onay metni ne OLMAYACAĞINI da söylüyor.
 *
 * "Erişimi kaldır" ifadesi kişinin geçmişinin de silineceği izlenimini
 * verebiliyor. Metin bunu açıkça yalanlamazsa yönetici düğmeye basmaktan
 * çekinir ve akış kullanılmaz — yazılmamış olmasından farkı kalmaz.
 */
test("onay metni geçmişin durduğunu söylüyor", () => {
  const metin = kaldirmaOnayMetni("Ayşe");
  assert.match(metin, /Ayşe/);
  assert.match(metin, /geçmiş/i);
  assert.match(metin, /silinmiyor|DURUYOR/);
});

test("onay metni ad yoksa da anlamlı", () => {
  assert.match(kaldirmaOnayMetni(""), /personel/i);
});

test("kaldırma hataları okunur mesaja çevriliyor", () => {
  assert.match(kaldirmaHataMesaji(401, {}), /[Oo]turum/);
  assert.match(kaldirmaHataMesaji(403, {}), /yönetici/i);
  assert.match(kaldirmaHataMesaji(0, {}), /[Bb]ağlantı/);
  assert.match(kaldirmaHataMesaji(418, {}), /418/);
});

/**
 * 409 ve 502'de sunucunun kendi mesajı korunuyor.
 *
 * İkisi de "ne yapmalı" bilgisi taşıyor: 409 kendi erişimini kaldırma
 * denemesini, 502 yarıda kalmış bir işlemi anlatıyor ve ikincisinde yapılacak
 * şey somut — tekrar çalıştırmak. Sabit bir metinle değiştirmek o bilgiyi
 * atardı.
 */
test("kaldırmada sunucu mesajı korunuyor", () => {
  const mesaj = "Erişim kaldırıldı ama hesap bağı temizlenemedi.";
  assert.equal(kaldirmaHataMesaji(502, { hata: mesaj }), mesaj);
  assert.equal(kaldirmaHataMesaji(409, { hata: mesaj }), mesaj);
});

test("kaldırma başarı mesajı iki durumu ayırıyor", () => {
  const kaldirildi = kaldirmaBasariMesaji({ durum: "kaldirildi", personel: "Ayşe" });
  const zaten = kaldirmaBasariMesaji({ durum: "zaten_yok", personel: "Ayşe" });
  assert.notEqual(kaldirildi, zaten);
  assert.match(zaten, /zaten/i);
});
