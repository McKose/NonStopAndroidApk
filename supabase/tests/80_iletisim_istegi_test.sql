-- ---------------------------------------------------------------------------
-- İletişim / deneme dersi istekleri: yazma kapalı, okuma salonla sınırlı
-- ---------------------------------------------------------------------------
-- `0010`un sınandığı yer. Bu tablonun diğerlerinden farkı, onu dolduran
-- kişinin HESABI OLMAMASI: siteden gelen bir ziyaretçi. Tasarım kararı bu
-- yüzden "istemciye yazma açma, Edge Function `service_role` ile yazsın" oldu
-- ve testin asıl işi o kararın gerçekten tutmasını doğrulamak.
--
-- ÖNEMLİ: buradaki her iddia HEM boş bir PostgreSQL'de (bu testin koştuğu yer)
-- HEM de canlı Supabase projesinde doğru olmak zorunda — `60`ın başında
-- yazılı gerekçeyle. Bu dosyada o kısıt bir iddianın BİÇİMİNİ değiştirdi:
-- "`authenticated` yazamaz" iddiası, yazamamasının SEBEBİNE değil
-- SONUCUNA bakıyor. Sebep iki ortamda ayrı olabilirdi (boş veritabanında
-- yetki yokluğu, canlıda kural yokluğu); `0010` ikisini hizaladı ama test
-- yine sonuca bakıyor, çünkü korunması gereken şey o.
-- ---------------------------------------------------------------------------

-- İki salon ve iki personel: salon yalıtımını sınamak için ikisi de gerekli.
-- Tek salonla "yalnızca kendi salonunu görüyor" iddiası sınanamaz — görecek
-- başka salon olmadığı için test her hâlde geçer.
-- Kimlik öneki dosya numarasıyla eşleşiyor (`80...`): öteki test dosyaları da
-- aynı deseni kullanıyor ve bu, aynı veritabanında çakışmayı önlüyor.
insert into auth.users (id, email) values
    ('80000000-0000-0000-0000-0000000000a1', 'a80-admin@salon.test'),
    ('80000000-0000-0000-0000-0000000000a3', 'a80-egitmen@salon.test'),
    ('80000000-0000-0000-0000-0000000000b1', 'b80-admin@salon.test');

insert into public.gyms (id, name) values
    ('80000000-0000-0000-0000-000000000001', 'İletişim Salonu A'),
    ('80000000-0000-0000-0000-000000000002', 'İletişim Salonu B');

insert into public.gym_users (user_id, gym_id, role) values
    ('80000000-0000-0000-0000-0000000000a1', '80000000-0000-0000-0000-000000000001', 'ADMIN'),
    ('80000000-0000-0000-0000-0000000000a3', '80000000-0000-0000-0000-000000000001', 'TRAINER'),
    ('80000000-0000-0000-0000-0000000000b1', '80000000-0000-0000-0000-000000000002', 'ADMIN');

-- Satırı `service_role` değil, testin kendisi (tablo sahibi) yazıyor; gerçek
-- akışta yazan taraf Edge Function ve `service_role`. Burada önemli olan
-- satırların VAR OLMASI — okuma yalıtımı onların üzerinde sınanıyor.
insert into public.contact_requests
    (id, tenant_id, full_name, phone, message, created_at_ms, updated_at_ms)
values ('80000000-0000-0000-0000-0000000000c1', '80000000-0000-0000-0000-000000000001',
        'Ayşe Yılmaz', '05550000001', 'Deneme dersi istiyorum', 10, 10),
       ('80000000-0000-0000-0000-0000000000c2', '80000000-0000-0000-0000-000000000002',
        'Veli Demir', '05550000002', null, 11, 11);

-- ─── 1. Yetki katmanı ──────────────────────────────────────────────────────
do $$
begin
    -- `anon`un HİÇBİR yetkisi olmamalı. Bu, tablonun var olma gerekçesinin
    -- kendisi: `anon` yayınlanmış anahtarla çalışıyor ve ona `insert` açmak
    -- "internetteki herkes istediği hızda satır yazabilir" demekti.
    if has_table_privilege('anon', 'public.contact_requests', 'select')
       or has_table_privilege('anon', 'public.contact_requests', 'insert')
       or has_table_privilege('anon', 'public.contact_requests', 'update')
       or has_table_privilege('anon', 'public.contact_requests', 'delete') then
        raise exception '`anon` contact_requests üzerinde yetkili; hiçbir yetkisi olmamalı';
    end if;

    -- `authenticated` okuyup güncelleyebilir, YAZAMAZ ve SİLEMEZ.
    if not has_table_privilege('authenticated', 'public.contact_requests', 'select') then
        raise exception '`authenticated` okuyamıyor; panel listeyi gösteremez';
    end if;
    if not has_table_privilege('authenticated', 'public.contact_requests', 'update') then
        raise exception '`authenticated` güncelleyemiyor; durum değiştirilemez';
    end if;
    if has_table_privilege('authenticated', 'public.contact_requests', 'insert') then
        raise exception '`authenticated` yazabiliyor; yazma yalnız service_role üzerinden olmalı';
    end if;
    if has_table_privilege('authenticated', 'public.contact_requests', 'delete') then
        raise exception '`authenticated` silebiliyor; silme yalnız service_role üzerinden olmalı';
    end if;

    -- `service_role` yazan taraf.
    if not has_table_privilege('service_role', 'public.contact_requests', 'insert') then
        raise exception '`service_role` yazamıyor; Edge Function çalışmaz';
    end if;

    raise notice 'contact_requests yetkileri doğru: anon kapalı, authenticated okur/günceller, service_role yazar.';
end
$$;

-- ─── 2. `insert` KURALI olmamalı ───────────────────────────────────────────
-- Yetki katmanının yedeği. Geniş bir kural yazan biri (ör. `for all`) yazmayı
-- sessizce açabilir; `for all` `insert`i de kapsıyor. Bu yüzden kuralların
-- kapsamı ayrıca sayılıyor.
do $$
declare
    bulunan text;
begin
    select string_agg(policyname || ' (' || cmd || ')', ', ')
      into bulunan
      from pg_policies
     where schemaname = 'public'
       and tablename = 'contact_requests'
       and cmd in ('INSERT', 'ALL');
    if bulunan is not null then
        raise exception
            'contact_requests üzerinde yazmayı kapsayan kural var (%); yazma yalnız service_role üzerinden olmalı',
            bulunan;
    end if;
end
$$;

-- ─── 3. Davranış: personel YALNIZCA kendi salonunun taleplerini görüyor ────
set role authenticated;

set test.uid = '80000000-0000-0000-0000-0000000000a1';   -- Salon A yöneticisi
do $$
declare s int;
begin
    select count(*) into s from public.contact_requests;
    if s <> 1 then
        raise exception 'Salon A yöneticisi % talep görüyor; yalnız kendi salonunun 1 talebini görmeli', s;
    end if;
    raise notice 'Yönetici yalnızca kendi salonunun taleplerini görüyor — doğru.';
end
$$;

set test.uid = '80000000-0000-0000-0000-0000000000a3';   -- Salon A eğitmeni
do $$
declare s int;
begin
    -- Eğitmen de görüyor ve bu bilinçli: talebi arayacak kişi o olabilir.
    select count(*) into s from public.contact_requests;
    if s <> 1 then
        raise exception 'Salon A eğitmeni % talep görüyor; 1 görmeli', s;
    end if;
    raise notice 'Eğitmen de kendi salonunun taleplerini görüyor — doğru.';
end
$$;

set test.uid = '';                                        -- giriş yapılmamış
do $$
declare s int;
begin
    select count(*) into s from public.contact_requests;
    if s <> 0 then
        raise exception 'Giriş yapmamış çağıran % talep görüyor; 0 görmeli', s;
    end if;
    raise notice 'Giriş yapmamış çağıran hiç talep görmüyor — doğru.';
end
$$;

-- ─── 4. Davranış: giriş yapmış personel bile YAZAMIYOR ─────────────────────
set test.uid = '80000000-0000-0000-0000-0000000000a1';
do $$
begin
    begin
        insert into public.contact_requests
            (tenant_id, full_name, phone, created_at_ms, updated_at_ms)
        values ('80000000-0000-0000-0000-000000000001', 'Sahte Kayıt', '05550000009', 20, 20);
        raise exception 'Yönetici talep yazabildi; yazma yalnız service_role üzerinden olmalı';
    exception
        when insufficient_privilege then
            raise notice 'Yönetici talep yazamıyor (yetki) — doğru.';
        -- Canlıda sebep farklı olabilir: yetki Supabase varsayılanıyla
        -- gelirse engelleyen şey `insert` kuralının yokluğu olur ve hata
        -- sınıfı `check_violation` yerine RLS ihlali döner. `0010` ikisini
        -- hizaladı, yine de iki sebebin ikisi de kabul ediliyor — korunan
        -- şey SONUÇ, sebep değil.
        when others then
            if sqlstate in ('42501', '44000') then
                raise notice 'Yönetici talep yazamıyor (kural) — doğru.';
            else
                raise;
            end if;
    end;
end
$$;

reset role;

-- ─── 5. Uzunluk kısıtları gerçekten uyguluyor ──────────────────────────────
-- Fonksiyon da doğruluyor ama bu son durak: fonksiyonda bir doğrulama
-- atlanırsa ya da ileride başka bir yol yazmaya başlarsa satır boyu yine
-- bağlı kalmalı. Sınırsız `text`, tek satırla gigabayt yazılabilmesi demekti.
do $$
begin
    begin
        insert into public.contact_requests
            (tenant_id, full_name, phone, created_at_ms, updated_at_ms)
        values ('80000000-0000-0000-0000-000000000001', repeat('a', 81), '05550000003', 30, 30);
        raise exception '81 karakterli ad kabul edildi; kısıt uygulamıyor';
    exception when check_violation then
        raise notice 'Aşırı uzun ad reddediliyor — doğru.';
    end;

    begin
        insert into public.contact_requests
            (tenant_id, full_name, phone, message, created_at_ms, updated_at_ms)
        values ('80000000-0000-0000-0000-000000000001', 'Normal Ad', '05550000004',
                repeat('m', 501), 31, 31);
        raise exception '501 karakterli mesaj kabul edildi; kısıt uygulamıyor';
    exception when check_violation then
        raise notice 'Aşırı uzun mesaj reddediliyor — doğru.';
    end;

    begin
        insert into public.contact_requests
            (tenant_id, full_name, phone, created_at_ms, updated_at_ms)
        values ('80000000-0000-0000-0000-000000000001', 'A', '05550000005', 32, 32);
        raise exception 'Tek karakterli ad kabul edildi; kısıt uygulamıyor';
    exception when check_violation then
        raise notice 'Tek karakterli ad reddediliyor — doğru.';
    end;
end
$$;

select 'İletişim isteği testi geçti.' as sonuc;
