// Panelin rol tablosu, uygulamanın rol tablosuyla aynı mı?
//
// `roller.js` içindeki tablo Kotlin'deki `AppDestination.visibleTo` kuralının
// kopyası. Bu test o kopyayı **Kotlin dosyasını okuyup** karşılaştırıyor, yani
// iki taraf sapınca panel testi düşüyor.
//
// Bu testin varlık sebebi somut: aynı görünürlük kararı bu projede daha önce iki
// ekranda birbirinden habersiz duruyordu ve biri değişince kısıt gerçek bir
// kısıt olmaktan çıktı. Panel üçüncü kopya; sınanmasa aynı hata tekrar ederdi —
// üstelik bu kez fark etmek daha zor olurdu, çünkü iki ayrı dilde.

import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

import { SEKME_ROLLERI, SEKME_HEDEFI, sekmeGorunur, gorunurSekmeler } from "./roller.js";

const buradan = dirname(fileURLToPath(import.meta.url));
const KOTLIN_YOLU = join(
  buradan,
  "..", "shared", "src", "commonMain", "kotlin", "com", "gymapp", "data", "access", "RoleAccess.kt",
);

/**
 * Kotlin'deki `visibleTo` eşlemesini okur: hedef adı → rol kümesi.
 *
 * Ayrıştırma kırılgan ve bu **bilinçli olarak** kabul ediliyor: alternatif,
 * kuralı üçüncü bir yere elle yazmak. Kırılganlığın bedeli testin düşmesi —
 * yani gürültülü. Sessiz kalması ise kabul edilemez, o yüzden ayrıştırma
 * başarısız olursa (hiç eşleşme yok, beklenen hedefler eksik) test AÇIKÇA
 * düşüyor. "Ayrıştıramadım, o hâlde geçtim" bu testin yapabileceği en kötü şey.
 */
function kotlinRolleri() {
  const kaynak = readFileSync(KOTLIN_YOLU, "utf8");

  // `visibleTo` getter gövdesini al: `when (this) {` ile eşleşen kapanış
  // arasında. Dosyanın tamamında aramak, aşağıdaki `roleSummary` gibi başka
  // `when`lerin de eşleşmesine yol açardı.
  const bas = kaynak.indexOf("val visibleTo:");
  assert.ok(bas > 0, "RoleAccess.kt içinde `visibleTo` bulunamadı — test kuralı okuyamıyor.");
  const whenBas = kaynak.indexOf("when (this)", bas);
  assert.ok(whenBas > 0, "`visibleTo` içinde `when (this)` bulunamadı.");
  const govde = kaynak.slice(whenBas, kaynak.indexOf("\n    /** Bu rol", whenBas));
  assert.ok(govde.length > 50, "`visibleTo` gövdesi okunamadı.");

  // `HEDEF1, HEDEF2 -> setOf(StaffRole.X, StaffRole.Y)`
  // Satır sonu araya girebiliyor (Kotlin tarafında uzun satır bölünmüş).
  const desen = /([A-Z][A-Z_]*(?:\s*,\s*[A-Z][A-Z_]*)*)\s*->\s*\n?\s*setOf\(([^)]*)\)/g;
  const eslesme = {};
  for (const m of govde.matchAll(desen)) {
    const hedefler = m[1].split(",").map((h) => h.trim()).filter(Boolean);
    const roller = [...m[2].matchAll(/StaffRole\.([A-Z_]+)/g)].map((r) => r[1]);
    assert.ok(roller.length > 0, `Roller okunamadı: ${m[0]}`);
    for (const hedef of hedefler) eslesme[hedef] = roller.sort();
  }

  assert.ok(
    Object.keys(eslesme).length > 0,
    "RoleAccess.kt ayrıştırılamadı: hiçbir hedef okunamadı. Kotlin tarafındaki " +
      "biçim değiştiyse bu testin deseni de güncellenmeli — sessizce geçmemeli.",
  );
  return eslesme;
}

test("panelin rol tablosu Kotlin'deki AppDestination ile aynı", () => {
  const kotlin = kotlinRolleri();

  for (const [sekme, roller] of Object.entries(SEKME_ROLLERI)) {
    const hedef = SEKME_HEDEFI[sekme];
    assert.ok(hedef, `\`${sekme}\` sekmesi SEKME_HEDEFI içinde eşlenmemiş.`);

    const beklenen = kotlin[hedef];
    assert.ok(
      beklenen,
      `\`${hedef}\` Kotlin'deki visibleTo içinde yok. Sekme yanlış hedefe mi bağlı?`,
    );

    assert.deepEqual(
      [...roller].sort(),
      beklenen,
      `\`${sekme}\` sekmesinin rolleri uygulamadaki \`${hedef}\` ile aynı değil. ` +
        `Kotlin tarafı değiştiyse roller.js de güncellenmeli.`,
    );
  }
});

/**
 * Kotlin'de görünen her hedefin panelde bir karşılığı var mı?
 *
 * Ters yön de önemli: uygulamaya yeni bir ekran eklenip panele eklenmezse bunu
 * bilmek istiyoruz. Ama panelin uygulamanın her ekranını taşıması gerekmiyor —
 * bu yüzden test düşmüyor, bilinçli olarak **atlanan** hedefleri listeliyor ve
 * listede olmayan bir hedef çıkarsa düşüyor.
 */
test("uygulamadaki her hedef ya panelde var ya bilinçli atlanmış", () => {
  const kotlin = kotlinRolleri();
  const panelde = new Set(Object.values(SEKME_HEDEFI));

  // Ayarlar: panelde karşılığı yok ve olmamalı. Uygulamada oradaki tek zorunlu
  // şey "Çıkış Yap" ve panelde çıkış düğmesi başlıkta duruyor.
  const bilincliAtlanan = new Set(["SETTINGS"]);

  for (const hedef of Object.keys(kotlin)) {
    assert.ok(
      panelde.has(hedef) || bilincliAtlanan.has(hedef),
      `Uygulamada \`${hedef}\` ekranı var, panelde karşılığı yok. Ya sekme ` +
        `eklenmeli ya da bu test içindeki \`bilincliAtlanan\` listesine ` +
        `gerekçesiyle yazılmalı.`,
    );
  }
});

test("finans eğitmene kapalı, geri kalanı açık", () => {
  assert.equal(sekmeGorunur("finans", "TRAINER"), false);
  assert.equal(sekmeGorunur("finans", "MANAGER"), true);
  assert.equal(sekmeGorunur("finans", "ADMIN"), true);

  for (const sekme of ["ozet", "uyeler", "paketler", "randevular", "market", "satislar", "personel"]) {
    assert.equal(sekmeGorunur(sekme, "TRAINER"), true, `${sekme} eğitmene kapalı`);
  }
});

/**
 * Tanımsız sekme gizleniyor, gösterilmiyor.
 *
 * Varsayılanın "göster" olması, yeni bir sekme eklenip tabloya yazılmadığında onu
 * sessizce herkese açardı — ve bu tam olarak görünürlük kuralının anlamını
 * yitirdiği durum.
 */
test("tanımsız sekme gizli sayılır", () => {
  assert.equal(sekmeGorunur("boyle-bir-sekme-yok", "ADMIN"), false);
});

test("bilinmeyen rol hiçbir şey görmez", () => {
  // Sunucudan tanınmayan bir rol gelirse (ör. ileride eklenen bir rol) panel
  // her şeyi açmamalı. Uygulamadaki karşılığı da en dar yetkiye düşmek.
  assert.deepEqual(gorunurSekmeler("BOYLE_BIR_ROL_YOK"), []);
});

test("her sekme en az bir rolde görünür", () => {
  for (const [sekme, roller] of Object.entries(SEKME_ROLLERI)) {
    assert.ok(roller.length > 0, `${sekme} hiçbir rolde görünmüyor.`);
  }
});
