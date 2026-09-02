// Şifre kuralının panel tarafı.
//
// Örnekler uygulamadaki `SifreKuraliTest` (Kotlin) ile **birebir aynı**. Kural
// iki yerde yazılı olmak zorunda (tarayıcı Kotlin çalıştırmıyor) ve ayrışması
// sessiz olurdu: bir tarafta kabul edilen şifre diğerinde reddedilir,
// kullanıcı da hangisinin doğru olduğunu bilemezdi.

import test from "node:test";
import assert from "node:assert/strict";

import { SIFRE_EN_AZ_UZUNLUK, sifreyiDogrula, sifreHataMesaji } from "./sifre.js";

const MEVCUT = "eski1234";

function reddi(yeni, { tekrar = yeni, mevcut = MEVCUT } = {}) {
  const sonuc = sifreyiDogrula({ mevcut, yeni, tekrar });
  assert.equal(sonuc.gecerli, false, `kabul edildi: '${yeni}'`);
  return sonuc.mesaj;
}

test("geçerli şifre kabul ediliyor", () => {
  const sonuc = sifreyiDogrula({ mevcut: MEVCUT, yeni: "yeniSifre1", tekrar: "yeniSifre1" });
  assert.equal(sonuc.gecerli, true);
  assert.equal(sonuc.sifre, "yeniSifre1");
});

test("kısa şifre reddediliyor", () => {
  assert.match(reddi("kisa12"), new RegExp(String(SIFRE_EN_AZ_UZUNLUK)));
  // Tam sınır kabul ediliyor: "en az 8" sekizi dışarıda bırakmamalı.
  assert.equal(
    sifreyiDogrula({ mevcut: MEVCUT, yeni: "12345678", tekrar: "12345678" }).gecerli,
    true,
  );
});

test("tekrar tutmuyorsa reddediliyor", () => {
  assert.match(reddi("yeniSifre1", { tekrar: "yeniSifre2" }), /tutmuyor/);
});

/**
 * Yeni şifre eskisiyle aynı olamaz.
 *
 * Bu akışın var oluş sebebi geçici şifreyi kalıcı olmaktan çıkarmak; aynı
 * şifreyi tekrar yazmak işlemin yapıldığı izlenimi verir ama hiçbir şey
 * değiştirmez.
 */
test("yeni şifre eskisiyle aynı olamaz", () => {
  assert.match(reddi(MEVCUT), /aynı/);
});

test("mevcut şifre boş bırakılamaz", () => {
  assert.match(reddi("yeniSifre1", { mevcut: "" }), /Mevcut/);
});

/**
 * Baştaki/sondaki boşluk KIRPILMIYOR, reddediliyor.
 *
 * Kırpılsaydı kullanıcı "yeniSifre1 " yazar, sunucuya "yeniSifre1" giderdi;
 * sonra giriş ekranında yazdığının aynısını yazıp "şifre yanlış" cevabını
 * alırdı ve kaybettiği karakteri hiçbir yerde göremezdi.
 */
test("boşluklu şifre kırpılmıyor, reddediliyor", () => {
  assert.match(reddi("yeniSifre1 "), /boşluk/);
  assert.match(reddi(" yeniSifre1"), /boşluk/);
});

/** Şifrenin içindeki boşluk serbest: yalnızca uçlardaki sorun. */
test("ortadaki boşluk kabul ediliyor", () => {
  const sonuc = sifreyiDogrula({ mevcut: MEVCUT, yeni: "iki kelime", tekrar: "iki kelime" });
  assert.equal(sonuc.gecerli, true);
  assert.equal(sonuc.sifre, "iki kelime", "şifre olduğu gibi geçmeli");
});

// ─── Hata mesajları ─────────────────────────────────────────────────────────

/**
 * 401/403 burada "mevcut şifre yanlış" DEĞİL.
 *
 * Mevcut şifre bu noktadan önce zaten doğrulandı; buraya gelindiyse jeton
 * düşmüş demektir. "Şifreniz yanlış" demek kullanıcıyı doğru olan şifresini
 * aramaya gönderirdi.
 */
test("oturum hatası şifre hatasıyla karıştırılmıyor", () => {
  assert.match(sifreHataMesaji(401, {}), /Oturum/);
  assert.match(sifreHataMesaji(403, {}), /Oturum/);
});

test("ağ hatası ayrı bir mesaj veriyor", () => {
  assert.match(sifreHataMesaji(0, {}), /bağlantı/i);
});

test("sunucu mesajı varsa o kullanılıyor", () => {
  const mesaj = "Password should be at least 6 characters.";
  assert.equal(sifreHataMesaji(422, { mesaj }), mesaj);
  assert.equal(sifreHataMesaji(500, { mesaj }), mesaj);
});

test("bilinmeyen kod, mesaj yoksa kodu gösteriyor", () => {
  assert.match(sifreHataMesaji(418, {}), /418/);
});
