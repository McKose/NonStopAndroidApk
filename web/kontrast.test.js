// Metin renkleri WCAG AA kontrastını geçmeli.
//
// Bu test neden var: sitenin en soluk yazısının rengi `site.css` içinde
// YAZILI DEĞİL, HESAPLANIYOR —
//
//     .yapimci { color: color-mix(in srgb, var(--soluk) 78%, transparent); }
//
// `color-mix` saydamlık üretiyor, tarayıcı onu zeminle harmanlıyor ve
// görünen renk `--soluk` ile `--zemin`in karışımı oluyor. Yani dosyaya
// bakan kimse o satırın kontrastını göremiyor. İlk yazımda oran %70'ti ve
// 4.45:1 veriyordu — gereken 4.5:1'in hemen altı. Lighthouse yakaladı
// (erişilebilirlik 95), gözle fark edilmesi mümkün değildi.
//
// Asıl kırılganlık tek bir satır değil, BAĞ: oran palete bağlı, yani
// `--soluk` ya da `--zemin` değiştiğinde kontrast hiçbir belirti vermeden
// düşebilir. Test tam o bağı tutuyor.
//
// Ölçüt WCAG 2.1 AA, normal boy metin için 4.5:1. Hesap WCAG'in kendi
// bağıntı ışıklılığı formülü; Lighthouse'un bildirdiği 4.44 değeri bu
// hesapla 4.45 çıkıyor (yuvarlama), yani model doğrulanmış sayılıyor.

import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const BURADA = dirname(fileURLToPath(import.meta.url));
const SITE_CSS = readFileSync(join(BURADA, "site.css"), "utf8");

const AA_NORMAL = 4.5;

/** `--ad: #rrggbb;` değerini paletten okuyor. */
function paletten(ad) {
  const m = SITE_CSS.match(new RegExp(`--${ad}\\s*:\\s*(#[0-9a-fA-F]{6})\\s*;`));
  assert.ok(m, `site.css içinde --${ad} tanımı bulunamadı`);
  return m[1].toLowerCase();
}

const bilesenler = (renk) =>
  [1, 3, 5].map((i) => parseInt(renk.slice(i, i + 2), 16));

/** WCAG bağıntı ışıklılığı. */
function isiklilik(renk) {
  const [r, g, b] = bilesenler(renk).map((k) => {
    const s = k / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function kontrast(on, arka) {
  const [acik, koyu] = [isiklilik(on), isiklilik(arka)].sort((a, b) => b - a);
  return (acik + 0.05) / (koyu + 0.05);
}

const ikilik = (n) => Math.round(n).toString(16).padStart(2, "0");

/** `color-mix(in srgb, X p%, transparent)`in zemin üzerinde görünen rengi. */
function saydamKarisim(on, arka, oran) {
  const [a, b] = [bilesenler(on), bilesenler(arka)];
  return "#" + a.map((v, i) => ikilik(v * oran + b[i] * (1 - oran))).join("");
}

const ZEMIN = paletten("zemin");

test("palet değerleri okunabildi (desen bozulduysa test boşa geçmesin)", () => {
  assert.match(ZEMIN, /^#[0-9a-f]{6}$/);
  assert.match(paletten("metin"), /^#[0-9a-f]{6}$/);
  assert.match(paletten("soluk"), /^#[0-9a-f]{6}$/);
});

test("paletin metin renkleri zemin üzerinde AA geçiyor", () => {
  for (const ad of ["metin", "soluk"]) {
    const renk = paletten(ad);
    const o = kontrast(renk, ZEMIN);
    assert.ok(
      o >= AA_NORMAL,
      `--${ad} (${renk}) zemin (${ZEMIN}) üzerinde ${o.toFixed(2)}:1 — ` +
        `AA için ${AA_NORMAL}:1 gerekiyor`,
    );
  }
});

test("`.yapimci` hesaplanan rengi AA geçiyor", () => {
  // Kural `color-mix` ile yazıldığı için oran dosyadan okunuyor: sabit bir
  // sayı yazılırsa test asıl kaynakla bağını kaybeder ve oran değişince
  // sessizce geçmeye devam eder.
  const m = SITE_CSS.match(
    /\.yapimci\s*\{[^}]*color\s*:\s*color-mix\(\s*in\s+srgb\s*,\s*var\(--soluk\)\s*(\d+)%\s*,\s*transparent\s*\)/,
  );
  assert.ok(
    m,
    "`.yapimci` içindeki `color-mix(... var(--soluk) N%, transparent)` " +
      "kuralı bulunamadı — kural değiştiyse bu test de gözden geçirilmeli",
  );

  const oran = Number(m[1]) / 100;
  const gorunen = saydamKarisim(paletten("soluk"), ZEMIN, oran);
  const o = kontrast(gorunen, ZEMIN);

  assert.ok(
    o >= AA_NORMAL,
    `.yapimci %${m[1]} oranında ${gorunen} olarak görünüyor ve zemin ` +
      `(${ZEMIN}) üzerinde ${o.toFixed(2)}:1 — AA için ${AA_NORMAL}:1 ` +
      "gerekiyor. Oranı yükseltin ya da paleti gözden geçirin.",
  );
});
