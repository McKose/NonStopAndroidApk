-- ---------------------------------------------------------------------------
-- `security definer` fonksiyonlarında rol yetkileri
-- ---------------------------------------------------------------------------
-- `60_silme_yetkisi_test.sql` tablo yetkilerine bakıyor; bu dosya aynı şeyi
-- FONKSİYONLAR için yapıyor. Ayrı bir katman, çünkü `security definer` bir
-- fonksiyon tanımlayanın yetkileriyle koşuyor: çağırana açık bırakılan böyle
-- bir fonksiyon, RLS'in arkasından dolanan bir uç nokta demek.
--
-- Sınanan karar `0009`: dört fonksiyonun da `execute` yetkisi `anon`da KAPALI,
-- `authenticated` ve `service_role`da AÇIK.
--
-- ÖNEMLİ: buradaki her iddia HEM boş bir PostgreSQL'de (bu testin koştuğu yer)
-- HEM de canlı Supabase projesinde doğru olmak zorunda — `60`ın başında
-- yazılı gerekçeyle. Bu dosyada o kısıt bir iddiayı DIŞARIDA BIRAKTI:
-- "`anon` `gyms`i sorgularsa hata değil boş sonuç alır" davranışı canlıda
-- doğru (Supabase `anon`a varsayılan olarak `select` vermiş) ama boş
-- veritabanında değil (`0005` `anon`a yalnızca `announcements` veriyor, yani
-- orada `permission denied for table gyms` gelir). O yüzden burada YETKİ
-- KATMANI sınanıyor, davranış değil; davranış canlı projede ölçüldü ve
-- `0009`un başında yazılı.
-- ---------------------------------------------------------------------------

do $$
declare
    fonksiyonlar text[] := array[
        'public.user_gym_ids()',
        'public.user_gym_ids_with_role(text[])',
        'public.user_member_ids()',
        'public.auth_kullanici_id(text)'
    ];
    f text;
    sayi int;
begin
    -- ── 1. Katalog temelli: `anon` HİÇBİR `security definer` fonksiyonu
    --       çağıramaz ───────────────────────────────────────────────────────
    -- Elle sayılan listeye değil KATALOĞA bakılıyor. Gerekçesi `0008`in
    -- gerekçesiyle aynı: bugün dört fonksiyon var, beşincisi eklendiğinde
    -- Supabase ona da varsayılan `execute` yetkisini `anon`a verir ve elle
    -- liste tutan bir test bunu GÖRMEZ. Katalog sorgusu görür ve yazarı
    -- kararını açıkça vermeye zorlar.
    select count(*) into sayi
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public'
       and p.prosecdef
       and has_function_privilege('anon', p.oid, 'execute');
    if sayi <> 0 then
        raise exception
            '`anon` % adet `security definer` fonksiyonu çağırabiliyor; hiçbirini çağıramamalı',
            sayi;
    end if;

    -- ── 2. Tripwire: ortada gerçekten `security definer` fonksiyon var ─────
    -- Yukarıdaki iddia, hiç fonksiyon olmadığında da geçer. O hâlde test
    -- sessizce hiçbir şey sınamamış olur — bu depoda tam olarak o sınıf hata
    -- birkaç kez yaşandı, o yüzden sayı da sınanıyor.
    select count(*) into sayi
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.prosecdef;
    if sayi < 4 then
        raise exception
            'yalnızca % `security definer` fonksiyon bulundu; beklenen en az 4 — sorgu ya da şema yanlış olabilir',
            sayi;
    end if;

    -- ── 3. `authenticated` dördünü de çağırabiliyor ───────────────────────
    -- Bu iddia 1. maddenin dengesi. Yetkiyi `public`ten almak, boş bir
    -- PostgreSQL'de `authenticated`i de keser (orada yetkisi yalnızca
    -- `public` üzerinden gelir) ve bu üç fonksiyonu çağıran ~40 erişim kuralı
    -- birden çöker. `0009` o yüzden açıkça geri veriyor; burada da sınanıyor,
    -- çünkü "güvenliği sıkılaştırdım" diye her şeyi kapatan bir değişiklik
    -- tam bu şekilde sessizce üretimi kırar.
    foreach f in array fonksiyonlar loop
        if not has_function_privilege('authenticated', f, 'execute') then
            raise exception '`authenticated` %''yi çağıramıyor; erişim kuralları buna bağlı', f;
        end if;
    end loop;

    -- ── 4. `service_role` dördünü de çağırabiliyor ────────────────────────
    -- Edge Function'ların rolü. Satır bazlı güvenliği baypas ettiği için
    -- kurallar onda değerlendirilmiyor, ama fonksiyonu doğrudan çağırması
    -- gerekebiliyor ve yetkisi bugün var; kapatılmadığı sınanıyor.
    foreach f in array fonksiyonlar loop
        if not has_function_privilege('service_role', f, 'execute') then
            raise exception '`service_role` %''yi çağıramıyor', f;
        end if;
    end loop;

    raise notice 'Fonksiyon yetkileri doğrulandı: anon kapalı, authenticated ve service_role açık.';
end
$$;

-- ---------------------------------------------------------------------------
-- 5. Kuralların kapsamı: hiçbir `{public}` kural bu fonksiyonları çağırmıyor
-- ---------------------------------------------------------------------------
-- Bu, 1. maddenin ÖNKOŞULUNU koruyor ve asıl kırılgan yer burası.
--
-- Postgres'te bir erişim kuralında geçen fonksiyon için SORGULAYAN ROLÜN
-- `execute` yetkisi gerekiyor. Yani `{public}` (dolayısıyla `anon`) kapsamlı
-- bir kural bu fonksiyonlardan birini çağırırsa, `anon`un o tabloya sorgusu
-- BOŞ SONUÇ YERİNE HATA döndürür. `0009` öncesinde `gyms_select` tam olarak
-- böyleydi ve ölçüldü: `permission denied for function user_gym_ids`.
--
-- Yani bu iddia düşerse mesaj "güvenlik zayıfladı" değil, "site kırıldı"
-- olur — tersi yönde bir hata. Kuralı yeniden genişleten biri bunu
-- göremeyeceği için burada sabitleniyor.
do $$
declare
    bulunan text;
begin
    select string_agg(tablename || '.' || policyname || ' ' || roles::text, ', ')
      into bulunan
      from pg_policies
     where schemaname = 'public'
       and roles::text like '%public%'
       and (coalesce(qual, '') || ' ' || coalesce(with_check, ''))
           ~ '\y(user_gym_ids|user_gym_ids_with_role|user_member_ids|auth_kullanici_id)\s*\(';
    if bulunan is not null then
        raise exception
            '`{public}` kapsamlı kural `security definer` fonksiyon çağırıyor (%); `anon` o tabloda HATA alır, boş sonuç almaz',
            bulunan;
    end if;
end
$$;

-- `gyms_select` özellikle sınanıyor: `0009`da `{public}`ten `{authenticated}`e
-- daraltılan kural o ve şemadaki tek istisnaydı.
do $$
declare
    roller text;
begin
    select p.roles::text into roller
      from pg_policies p
     where p.schemaname = 'public' and p.tablename = 'gyms' and p.policyname = 'gyms_select';
    if roller is null then
        raise exception '`gyms_select` kuralı bulunamadı';
    end if;
    if roller <> '{authenticated}' then
        raise exception '`gyms_select` kapsamı % — `{authenticated}` olmalı', roller;
    end if;
end
$$;
