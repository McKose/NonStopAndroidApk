import { test } from "node:test";
import assert from "node:assert/strict";
import { demoIstemcisi } from "./demo.js";
import { uyelikDurumu, tutarYaz, tarihYaz, silinmemisler } from "./domain.js";
import { uyeDagilimi, yaklasanBitisler, defterToplami, ayBasi } from "./ozet.js";

/**
 * Demo verisi gerçek biçimde mi.
 *
 * Demo, ekranı kurulum yapmadan değerlendirmek için var. Verisi gerçek sunucu
 * biçiminden saparsa demo ekranı hiç görülmeyecek bir şeyi gösterir — ve ekranı
 * ona göre ayarlamak gerçek veride bozuk görünmesine yol açar. Bu yüzden demo
 * verisi, ekranın gerçekte kullandığı fonksiyonlardan geçiriliyor.
 */

/** Her test kendi istemcisiyle çalışıyor: oturum durumu testler arasında sızmasın. */
function girisYapmis() {
  const c = demoIstemcisi();
  c.girisYap("demo@nonstopstudio.tr", "demo");
  return c;
}

const istemci = girisYapmis();

test("demo çıkış yapmış başlar", async () => {
  // Giriş ekranı da değerlendirilecek şeyin parçası; doğrudan panele girseydi
  // oradaki alanlar ve hata mesajları hiç görülmezdi.
  const yeni = demoIstemcisi();
  assert.equal(yeni.oturumOku(), null);
  assert.equal((await yeni.oku("gym_members")).tur, "oturumsuz");
});

test("demo verisi gerçek okuma yolundan geçiyor", async () => {
  const uyeler = await istemci.oku("gym_members");
  assert.equal(uyeler.tur, "tamam");
  assert.ok(uyeler.satirlar.length > 0, "demo üyesi yok");
});

test("demo üyeleri her durumu kapsıyor", async () => {
  // Ekranı değerlendirmenin anlamı, her rozetin nasıl göründüğünü görmek.
  // Hepsi aktif olsaydı demo, süresi dolmuş bir üyenin nasıl göründüğünü
  // göstermezdi.
  const { satirlar } = await istemci.oku("gym_members");
  const dagilim = uyeDagilimi(silinmemisler(satirlar), Date.now());

  assert.ok(dagilim.AKTIF > 0, "aktif üye yok");
  assert.ok(dagilim.SURESI_DOLDU > 0, "süresi dolmuş üye yok");
  assert.ok(dagilim.DONDURULDU > 0, "dondurulmuş üye yok");
  assert.ok(dagilim.ARSIVDE > 0, "arşivde üye yok");
});

test("demo verisinde yaklaşan bitiş var", async () => {
  const { satirlar } = await istemci.oku("gym_members");
  assert.ok(yaklasanBitisler(satirlar, Date.now()).length > 0, "yaklaşan bitiş yok");
});

test("demo defterinde her tür kayıt var", async () => {
  const { satirlar } = await istemci.oku("ledger_entries");
  const simdi = Date.now();
  const toplam = defterToplami(satirlar, ayBasi(simdi) - 40 * 24 * 3600 * 1000, simdi);

  assert.ok(toplam.PAYMENT > 0, "tahsilat yok");
  assert.ok(toplam.EXPENSE > 0, "gider yok");
  assert.ok(toplam.CHARGE > 0, "borç kaydı yok");
});

test("demo satırları ekranın okuduğu alanları taşıyor", async () => {
  // Alan adı sapması demo ekranında "—" olarak görünürdü; testte açıkça
  // yakalanıyor.
  const { satirlar } = await istemci.oku("gym_members");
  for (const uye of satirlar) {
    assert.ok(uye.full_name, `ad eksik: ${uye.id}`);
    assert.ok(uye.phone, `telefon eksik: ${uye.id}`);
    assert.notEqual(tutarYaz(uye.price_paid_minor), "—", `tutar okunamıyor: ${uye.id}`);
    assert.notEqual(tarihYaz(uye.end_date_ms), "—", `tarih okunamıyor: ${uye.id}`);
    assert.ok(uyelikDurumu(uye, Date.now()).length > 0);
  }
});

test("demo girişi her zaman başarılı", async () => {
  // Demo modunda giriş ekranı geçilebilmeli; aksi hâlde panele hiç bakılamazdı.
  const sonuc = await istemci.girisYap("her", "sey");
  assert.equal(sonuc.tur, "tamam");
  assert.ok(sonuc.oturum.gym_name.includes("demo"));
});

test("tanınmayan tablo boş döner, çökmez", async () => {
  const sonuc = await istemci.oku("olmayan_tablo");
  assert.equal(sonuc.tur, "tamam");
  assert.deepEqual(sonuc.satirlar, []);
});
