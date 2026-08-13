-- ---------------------------------------------------------------------------
-- Salon yalıtımı testi
-- ---------------------------------------------------------------------------
-- Sınanan şey, bir salonun verisini diğerinden ayıran tek mekanizma: satır
-- bazlı güvenlik kuralları. Yanlış yazılmış bir kural sessizdir — uygulama
-- çalışmaya devam eder, testler geçer, veri sızar. Bu yüzden kuralın kendisi
-- test ediliyor.
--
-- Her iddia başarısızlıkta `raise exception` atıyor; psql `ON_ERROR_STOP` ile
-- koştuğu için işlem sıfırdan farklı kodla düşer.
--
-- ÖNEMLİ: testler `authenticated` rolüyle koşuyor. Tablo sahibi ve superuser
-- satır bazlı güvenliği baypas eder; sahip olarak koşan bir test her şeyi
-- görür ve hiçbir şey kanıtlamazdı.
-- ---------------------------------------------------------------------------

-- DİKKAT: burada GRANT YOK ve olmamalı.
--
-- İlk hâlinde vardı ve tam da bu yüzden test yanıltıcıydı: migrasyon hiçbir
-- yetki vermiyordu, testin kendisi veriyordu. Yani test, üretimde hiç oluşmayacak
-- bir durumu doğruluyordu — gerçek bir Supabase projesinde tablolar erişilemez
-- kalabilirdi ve bunu hiçbir şey söylemezdi. Yetkiler artık migrasyonun işi;
-- burada verilmeleri o kusuru tekrar gizlerdi.
--
-- Tek istisna `auth.uid()`: taklit fonksiyon testin kendi kurgusu, migrasyonun
-- değil.
grant execute on function auth.uid() to authenticated;

insert into auth.users (id, email) values
    ('11111111-1111-1111-1111-111111111111', 'a@salon.test'),
    ('22222222-2222-2222-2222-222222222222', 'b@salon.test');

insert into public.gyms (id, name) values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'Salon A'),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'Salon B');

insert into public.gym_users (user_id, gym_id, role) values
    ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0000-0000-0000-000000000001', 'ADMIN'),
    ('22222222-2222-2222-2222-222222222222', 'bbbbbbbb-0000-0000-0000-000000000002', 'ADMIN');

-- Satırlar sahip olarak yazılıyor: başlangıç durumunu kurmak testin konusu değil.
insert into public.products (id, tenant_id, name, category, price_minor, created_at_ms, updated_at_ms)
values ('p-a', 'aaaaaaaa-0000-0000-0000-000000000001', 'A ürünü', 'içecek', 5000, 1, 1),
       ('p-b', 'bbbbbbbb-0000-0000-0000-000000000002', 'B ürünü', 'içecek', 7000, 1, 1);

-- ─── A kullanıcısı gözüyle ─────────────────────────────────────────────────
set role authenticated;
set test.uid = '11111111-1111-1111-1111-111111111111';

do $$
declare
    görünen integer;
begin
    -- 1) Yalnızca kendi salonunun satırları görünmeli.
    select count(*) into görünen from public.products;
    if görünen <> 1 then
        raise exception 'Yalıtım kırık: A kullanıcısı % satır görüyor, 1 bekleniyordu', görünen;
    end if;

    if not exists (select 1 from public.products where id = 'p-a') then
        raise exception 'A kullanıcısı kendi satırını göremiyor';
    end if;

    -- 2) Başka salona yazmak reddedilmeli.
    --
    -- Bu, `with check` olmadan sessizce başarılı olurdu: kullanıcı okuyamadığı
    -- ama bozabildiği veriye yazardı. Kuralın en kolay unutulan yarısı bu.
    begin
        insert into public.products (id, tenant_id, name, category, price_minor,
                                     created_at_ms, updated_at_ms)
        values ('p-sizinti', 'bbbbbbbb-0000-0000-0000-000000000002', 'sızma', 'x', 1, 1, 1);
        raise exception 'GÜVENLİK AÇIĞI: başka salona yazma engellenmedi';
    exception
        when insufficient_privilege then null;  -- beklenen
    end;

    -- 3) Başka salonun satırını silmek hiçbir satırı etkilememeli.
    delete from public.products where id = 'p-b';
    if found then
        raise exception 'GÜVENLİK AÇIĞI: başka salonun satırı silinebildi';
    end if;

    -- 4) Kendi salonuna yazabilmeli — kural fazla kısıtlayıcı da olmamalı.
    insert into public.products (id, tenant_id, name, category, price_minor,
                                 created_at_ms, updated_at_ms)
    values ('p-a2', 'aaaaaaaa-0000-0000-0000-000000000001', 'A ürünü 2', 'içecek', 6000, 1, 1);

    -- 5) Salon listesi de süzülmeli.
    if (select count(*) from public.gyms) <> 1 then
        raise exception 'Yalıtım kırık: A kullanıcısı birden fazla salon görüyor';
    end if;
end
$$;

-- ─── Personel ↔ hesap bağlantısı ───────────────────────────────────────────
-- `staff.auth_user_id` giriş yapan kişiyi personel satırına bağlıyor. Bir hesabın
-- yalnızca tek bir personele bağlanabilmesi şart: iki satır aynı hesabı
-- gösterseydi giriş yapanın hangisi olduğu belirsizleşir, randevular ve
-- hakedişler sessizce yanlış kişiye yazılırdı.
do $$
begin
    insert into public.staff (id, tenant_id, full_name, title, role, branch,
                              commission_basis_points, monthly_salary_minor,
                              phone, nickname, auth_user_id,
                              created_at_ms, updated_at_ms)
    values ('st-a', 'aaaaaaaa-0000-0000-0000-000000000001', 'A Eğitmen', 'Eğitmen',
            'TRAINER', 'Fitness', 4000, 0, '+905001112233', 'aegitmen',
            '11111111-1111-1111-1111-111111111111', 1, 1);

    -- Aynı hesabı ikinci bir personele bağlamak reddedilmeli.
    begin
        insert into public.staff (id, tenant_id, full_name, title, role, branch,
                                  commission_basis_points, monthly_salary_minor,
                                  phone, nickname, auth_user_id,
                                  created_at_ms, updated_at_ms)
        values ('st-a2', 'aaaaaaaa-0000-0000-0000-000000000001', 'A İkiz', 'Eğitmen',
                'TRAINER', 'Fitness', 4000, 0, '+905001112244', 'aikiz',
                '11111111-1111-1111-1111-111111111111', 1, 1);
        raise exception 'Aynı hesap iki personele bağlanabildi';
    exception
        when unique_violation then null;  -- beklenen
    end;

    -- Hesabı bağlanmamış personel sayısı sınırlı olmamalı: tekillik kısmi.
    insert into public.staff (id, tenant_id, full_name, title, role, branch,
                              commission_basis_points, monthly_salary_minor,
                              phone, nickname, auth_user_id,
                              created_at_ms, updated_at_ms)
    values ('st-b1', 'aaaaaaaa-0000-0000-0000-000000000001', 'Bağsız 1', 'Eğitmen',
            'TRAINER', 'Fitness', 0, 0, '+905001112255', 'bagsiz1', null, 1, 1),
           ('st-b2', 'aaaaaaaa-0000-0000-0000-000000000001', 'Bağsız 2', 'Eğitmen',
            'TRAINER', 'Fitness', 0, 0, '+905001112266', 'bagsiz2', null, 1, 1);
end
$$;

reset role;

-- ─── Sahip gözüyle son durum ───────────────────────────────────────────────
do $$
begin
    if not exists (select 1 from public.products where id = 'p-b') then
        raise exception 'B salonunun satırı kaybolmuş';
    end if;
    if exists (select 1 from public.products where id = 'p-sizinti') then
        raise exception 'GÜVENLİK AÇIĞI: sızma satırı yazılmış';
    end if;
end
$$;

\echo 'Salon yalıtımı testi geçti.'
