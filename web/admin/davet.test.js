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
