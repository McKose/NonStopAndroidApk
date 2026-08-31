// Edge Function'ın çalışma zamanından beklediği YÜZEYİN TAMAMI.
//
// Deno'nun kendi tipleri burada yok (npm paketi değil, ayrı bir çalışma
// zamanı). Bu dosya olmasaydı `index.ts` hiçbir araçla denetlenemezdi:
// yazım hatası, eksik `await`, yanlış alan adı ancak canlıda — bir yönetici
// personel davet etmeye çalışırken — ortaya çıkardı.
//
// Bilerek DAR tutuluyor. Amaç Deno'yu tarif etmek değil, bu fonksiyonun
// gerçekten kullandığı iki API'yi sabitlemek. Üçüncü bir Deno API'si
// gerektiğinde buraya eklenmesi gerekecek — ve o an "bu bağımlılık gerçekten
// gerekli mi" sorusu kendiliğinden sorulmuş olacak.
declare namespace Deno {
  /** Ortam değişkenleri: `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, … */
  const env: {
    get(anahtar: string): string | undefined;
  };

  /** HTTP sunucusu. Supabase Edge çalışma zamanının giriş noktası. */
  function serve(
    isleyici: (istek: Request) => Response | Promise<Response>,
  ): void;
}
