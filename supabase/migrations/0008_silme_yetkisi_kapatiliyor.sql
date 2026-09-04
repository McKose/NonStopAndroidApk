-- ---------------------------------------------------------------------------
-- `delete` yetkisinin kalan açıkları kapatılıyor
-- ---------------------------------------------------------------------------
-- `0004` şunu yazmıştı ve gerekçesi hâlâ aynen geçerli:
--
--   > Uygulama zaten hiç DELETE göndermiyor: silme mezar taşıyla yapılıyor
--   > (`deleted_at_ms` dolduruluyor), yani sunucuya giden şey bir UPDATE.
--   > Dolayısıyla yetkiyi geri almak işlevsel olarak hiçbir şeye mal olmuyor
--   > ama kalıcı veri kaybı sınıfını tamamen kapatıyor.
--
-- Ama o dosyanın döngüsü yetkiyi yalnızca `authenticated` rolünden geri aldı.
-- Canlı veritabanına bakıldığında (`information_schema.role_table_grants`) iki
-- boşluk kaldığı görülüyor:
--
--   1. `anon` bütün veri tablolarında hâlâ `delete` tutuyor. Bu rol
--      **yayınlanmış anahtarla** çalışıyor: anahtar uygulamanın içinde, panelin
--      kaynağında, herkesin elinde. Bugün tek engeli RLS — hiçbir tabloda
--      `for delete` kuralı olmaması. Yani koruma tek katmanda duruyor ve ileride
--      biri geniş bir `for all` kuralı yazarsa (kolay bir hata: `for all`
--      `delete`i de kapsıyor) silme sessizce açılır.
--
--   2. `0004`ün döngüsü yalnızca KİRACI VERİ tablolarını geziyordu. Şemadaki
--      diğer altı tablo hiç uğramadı ve hepsi `anon` ve `authenticated`
--      için `delete` tutuyordu: yetki tablosu `gym_users` (`0007`), salon
--      kaydı `gyms` (`0001`), üye tarafının tabloları `member_accounts`,
--      `member_link_requests`, `member_health_updates` ve `announcements`
--      (`0005`, `0006`). Yine RLS kapatıyor ama en kritik tablolarda tek
--      katman kalması savunulacak bir durum değil.
--
-- Bu dosya yalnızca yetki geri alıyor: yeni tablo, yeni kural, yeni kolon yok.
-- Uygulama hiçbir yerde `DELETE` göndermediği için davranış değişmiyor.
--
-- ### `service_role` neden dışarıda — ve neden AÇIKÇA yazılıyor
-- Ondan geri alınmıyor: bir personelin erişimini kaldırma akışı henüz yazılmadı
-- (bkz. `supabase/README.md` → "Henüz yapılmadı") ve yazıldığında `gym_users`
-- satırını silmesi gerekecek; o iş Edge Function'da, `service_role` ile
-- yapılacak. Bugün yetkiyi geri almak, yarın onu geri vermekten başka bir işe
-- yaramazdı.
--
-- Ama "geri alınmadı" ile "verildi" aynı şey değil ve fark burada ortaya çıktı:
-- canlı projede `service_role` o yetkiyi zaten tutuyor — Supabase'in varsayılanı
-- verdiği için, bu depodaki hiçbir dosya istediği için değil. Boş bir
-- PostgreSQL'e (`tests/run.sh`) yalnızca bu migrasyonlar uygulandığında yetki
-- ortaya çıkmıyor. Yani niyet, görünmez bir platform varsayılanına yaslanıyordu.
--
-- Aşağıdaki `grant` o boşluğu kapatıyor. Canlıda hiçbir şeyi değiştirmiyor
-- (yetki zaten var); değiştirdiği şey, yetkinin nereden geldiğinin YAZILI
-- olması — ve testin onu doğrulayabilmesi.
--
-- ### `truncate` neden listede yok
-- `anon` ve `authenticated` bütün tablolarda `truncate` de tutuyor ve `truncate`
-- RLS'e HİÇ takılmıyor. Yine de bir açık değil: PostgREST yalnızca
-- `select/insert/update/delete` ve fonksiyon çağrısı üretiyor, `truncate`
-- cümlesi kuran bir yol yok. Buradan geri alınmamasının sebebi Supabase'in bunu
-- her yeni tabloya varsayılan olarak vermesi — tek seferlik bir `revoke`,
-- bir sonraki tabloda yeniden açılacağı için yanlış bir güven duygusu verirdi.
-- Doğru yeri varsayılanların kendisi (`alter default privileges`) ve bu, ayrı
-- ve daha geniş bir karar.
-- ---------------------------------------------------------------------------

-- Tablolar ELLE SAYILMIYOR, katalogdan okunuyor.
--
-- İlk yazımda `0004`ün listesi kopyalanmıştı ve eksikti: `announcements`,
-- `gyms`, `member_accounts`, `member_health_updates`, `member_link_requests`
-- listede yoktu — çünkü onlar `0001`, `0005` ve `0006`da eklenmişti, `0004`ün
-- gezdiği kiracı veri tabloları arasında değillerdi. Elle sayılan bir liste
-- tam olarak böyle eksik kalıyor: yeni tablo başka bir dosyada doğuyor ve bu
-- dosyaya kimse dönmüyor.
--
-- Katalogdan okumak bunu kökten çözmüyor — bu migrasyondan SONRA açılan bir
-- tablo yine varsayılanla `delete` alacak — ama bugünkü şemanın tamamını
-- kapsıyor ve kapsamı bir insanın hatırlamasına bırakmıyor. Kalıcı çözüm
-- `alter default privileges`; bu, ayrı ve daha geniş bir karar (yukarıdaki
-- `truncate` notuna bakın: aynı sebep).
do $$
declare
    t record;
begin
    for t in
        select tablename
          from pg_tables
         where schemaname = 'public'
    loop
        execute format('revoke delete on public.%I from anon, authenticated', t.tablename);
    end loop;
end
$$;

-- Erişimi geri alma akışının ihtiyacı; yukarıdaki gerekçeye bakın.
grant delete on public.gym_users to service_role;
