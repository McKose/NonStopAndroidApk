-- ---------------------------------------------------------------------------
-- Role dayalı yazma yetkisi testi
-- ---------------------------------------------------------------------------
-- 10_rls_test.sql salonlar ARASI yalıtımı sınıyor. Burada sınanan şey salon
-- İÇİ ayrım: aynı salona bağlı üç kişi, rollerine göre farklı şeyler
-- yapabilmeli.
--
-- Bu ayrımın yanlış yazılması sessiz: uygulama çalışmaya devam eder, hiçbir
-- ekran değişmez, yalnızca yetkisi olmayan biri bir şeyi değiştirebiliyordur.
-- Bu yüzden her rol için hem İZİN VERİLEN hem REDDEDİLEN yol ayrı ayrı
-- kanıtlanıyor. Yalnızca reddi sınamak, "her şeyi reddet" diye yazılmış bozuk
-- bir kuralı da geçirirdi.
-- ---------------------------------------------------------------------------

insert into auth.users (id, email) values
    ('33333333-3333-3333-3333-333333333333', 'yonetici@salon.test'),
    ('44444444-4444-4444-4444-444444444444', 'egitmen@salon.test');

-- Üçü de AYNI salonda (Salon A). Fark yalnızca rol — sınanmak istenen tam
-- olarak bu. Farklı salonlarda olsalardı reddin sebebi rol mü yalıtım mı
-- ayırt edilemezdi.
insert into public.gym_users (user_id, gym_id, role) values
    ('33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000001', 'MANAGER'),
    ('44444444-4444-4444-4444-444444444444', 'aaaaaaaa-0000-0000-0000-000000000001', 'TRAINER');

-- Başlangıç satırları sahip olarak yazılıyor: kurulum testin konusu değil.
insert into public.gym_members (id, tenant_id, full_name, phone, status,
                                payment_type, created_at_ms, updated_at_ms)
values ('uye-1', 'aaaaaaaa-0000-0000-0000-000000000001', 'Deneme Üye',
        '+905009998877', 'ACTIVE', 'CASH', 1, 1);

-- ═══ EĞİTMEN (TRAINER) ═════════════════════════════════════════════════════
set role authenticated;
set test.uid = '44444444-4444-4444-4444-444444444444';

do $$
begin
    -- ─── Yapabilmesi gerekenler: günlük iş ─────────────────────────────────
    insert into public.gym_members (id, tenant_id, full_name, phone, status,
                                    payment_type, created_at_ms, updated_at_ms)
    values ('uye-egitmen', 'aaaaaaaa-0000-0000-0000-000000000001', 'Eğitmenin Üyesi',
            '+905009998866', 'ACTIVE', 'CASH', 1, 1);

    insert into public.appointments (id, tenant_id, member_id, staff_id, training_type,
                                     start_time_ms, end_time_ms, state,
                                     created_at_ms, updated_at_ms)
    values ('rnd-1', 'aaaaaaaa-0000-0000-0000-000000000001', 'uye-1', 'st-a',
            'FITNESS', 1000, 2000, 'SCHEDULED', 1, 1);

    insert into public.ledger_entries (id, tenant_id, type, category, amount_minor,
                                       payment_method, description,
                                       occurred_at_ms, created_at_ms)
    values ('kasa-1', 'aaaaaaaa-0000-0000-0000-000000000001', 'PAYMENT', 'MEMBERSHIP',
            5000, 'CASH', 'satış', 1, 1);

    -- Kendi yazdığı üyeyi güncelleyebilmeli.
    update public.gym_members set full_name = 'Eğitmenin Üyesi (düzeltildi)'
     where id = 'uye-egitmen';
    if not found then
        raise exception 'Eğitmen kendi salonundaki üyeyi güncelleyemedi';
    end if;

    -- ─── Yapamaması gerekenler ─────────────────────────────────────────────
    --
    -- DİKKAT: reddin İKİ farklı biçimi var ve karıştırılmaları kolay.
    --
    --   * `using` bir satırı elemekte SESSİZ: düz bir UPDATE hata vermez,
    --     yalnızca 0 satır etkiler. Reddi "hata fırlatır" diye sınamak bu
    --     yüzden yanlış olurdu — testi geçmek için kuralı gevşetmeye
    --     çalışırdınız.
    --   * `with check` ihlali ise HATA fırlatır (42501 → HTTP 403).
    --
    -- Uygulamanın gönderdiği şey her zaman upsert (`on conflict do update`)
    -- olduğu için gerçek gönderim yolu her zaman GÜRÜLTÜLÜ olanı: 403 dönüyor,
    -- `pushResultForStatus` bunu kalıcı hata sayıyor ve kayıt kuyrukta
    -- işaretleniyor. Yani sessiz eleme uygulamada hiç görülmüyor. Aşağıda
    -- ikisi de ayrı ayrı kanıtlanıyor.

    -- 1) Personel eklemek. Personel satırı hakediş oranı ve maaş taşıyor.
    begin
        insert into public.staff (id, tenant_id, full_name, title, role, branch,
                                  commission_basis_points, monthly_salary_minor,
                                  phone, nickname, created_at_ms, updated_at_ms)
        values ('st-sizinti', 'aaaaaaaa-0000-0000-0000-000000000001', 'Sızma', 'Eğitmen',
                'TRAINER', 'Fitness', 10000, 999999, '+905000000001', 'sizma', 1, 1);
        raise exception 'GÜVENLİK AÇIĞI: eğitmen personel kaydı ekleyebildi';
    exception
        when insufficient_privilege then null;  -- beklenen
    end;

    -- 2) Var olan personel kaydını değiştirmek — kendi hakedişini yükseltmek
    --    dahil. Sessiz eleme: 0 satır.
    update public.staff set commission_basis_points = 10000 where id = 'st-a';
    if found then
        raise exception 'GÜVENLİK AÇIĞI: eğitmen hakediş oranını değiştirebildi';
    end if;

    -- 3) Fiyat değiştirmek. Yine sessiz eleme.
    update public.products set price_minor = 1 where id = 'p-a';
    if found then
        raise exception 'GÜVENLİK AÇIĞI: eğitmen ürün fiyatını değiştirebildi';
    end if;

    -- 4) Paket eklemek.
    begin
        insert into public.gym_packages (id, tenant_id, name, type, category,
                                         validity_days, base_price_minor,
                                         created_at_ms, updated_at_ms)
        values ('pkt-sizinti', 'aaaaaaaa-0000-0000-0000-000000000001', 'Bedava paket',
                'FITNESS', 'INDIVIDUAL', 365, 0, 1, 1);
        raise exception 'GÜVENLİK AÇIĞI: eğitmen paket ekleyebildi';
    exception
        when insufficient_privilege then null;  -- beklenen
    end;

    -- 5) UPSERT yolu. Uygulamanın sunucuya gönderdiği şey tam olarak bu:
    --    `on conflict do update`. Yalnızca düz UPDATE sınansaydı, ekleme
    --    kuralı doğru ama güncelleme kuralı eksik bırakılmış bir sürüm
    --    testlerden geçerdi — üstelik gerçek gönderim yolu açık kalırdı.
    begin
        insert into public.products (id, tenant_id, name, category, price_minor,
                                     created_at_ms, updated_at_ms)
        values ('p-a', 'aaaaaaaa-0000-0000-0000-000000000001', 'ele geçirildi', 'x', 1, 1, 2)
            on conflict (id) do update set price_minor = excluded.price_minor;
        raise exception 'GÜVENLİK AÇIĞI: eğitmen upsert ile fiyat değiştirebildi';
    exception
        when insufficient_privilege then null;  -- beklenen
    end;

    -- ─── Okuma kısıtlanmamalı ──────────────────────────────────────────────
    -- Eğitmen fiyat yazamaz ama görmek zorunda: satış ekranı fiyatı gösteriyor.
    -- Yazma kuralını yazarken okuma kuralını da daraltmak kolay bir hata ve
    -- belirtisi "ürün listesi boş görünüyor" olurdu.
    if not exists (select 1 from public.products where id = 'p-a') then
        raise exception 'Eğitmen ürünleri göremiyor; okuma fazla daraltılmış';
    end if;
    if not exists (select 1 from public.staff where id = 'st-a') then
        raise exception 'Eğitmen personeli göremiyor; randevu ekranı çalışmaz';
    end if;
end
$$;

-- ═══ YÖNETİCİ (MANAGER) ════════════════════════════════════════════════════
set test.uid = '33333333-3333-3333-3333-333333333333';

do $$
begin
    -- Fiyat listesi yönetebilmeli.
    update public.products set price_minor = 5500 where id = 'p-a';
    if not found then
        raise exception 'Yönetici ürün fiyatını değiştiremedi';
    end if;

    insert into public.gym_packages (id, tenant_id, name, type, category,
                                     validity_days, base_price_minor,
                                     created_at_ms, updated_at_ms)
    values ('pkt-yonetici', 'aaaaaaaa-0000-0000-0000-000000000001', '10 ders',
            'FITNESS', 'INDIVIDUAL', 90, 250000, 1, 1);

    -- Günlük iş de yapabilmeli.
    insert into public.gym_members (id, tenant_id, full_name, phone, status,
                                    payment_type, created_at_ms, updated_at_ms)
    values ('uye-yonetici', 'aaaaaaaa-0000-0000-0000-000000000001', 'Yöneticinin Üyesi',
            '+905009998855', 'ACTIVE', 'CASH', 1, 1);

    -- Ama personel kaydına dokunamamalı: maaş ve hakediş ADMIN'in işi.
    update public.staff set monthly_salary_minor = 999999 where id = 'st-a';
    if found then
        raise exception 'GÜVENLİK AÇIĞI: yönetici maaş değiştirebildi';
    end if;

    -- Uygulamanın gerçek yolu (upsert) ise açık hata vermeli.
    begin
        insert into public.staff (id, tenant_id, full_name, title, role, branch,
                                  commission_basis_points, monthly_salary_minor,
                                  phone, nickname, created_at_ms, updated_at_ms)
        values ('st-a', 'aaaaaaaa-0000-0000-0000-000000000001', 'A Eğitmen', 'Eğitmen',
                'TRAINER', 'Fitness', 4000, 999999, '+905001112233', 'aegitmen', 1, 2)
            on conflict (id) do update set monthly_salary_minor = excluded.monthly_salary_minor;
        raise exception 'GÜVENLİK AÇIĞI: yönetici upsert ile maaş değiştirebildi';
    exception
        when insufficient_privilege then null;  -- beklenen
    end;
end
$$;

-- ═══ SALON SAHİBİ (ADMIN) ══════════════════════════════════════════════════
set test.uid = '11111111-1111-1111-1111-111111111111';

do $$
begin
    -- Personel kaydını yönetebilmeli.
    update public.staff set monthly_salary_minor = 3000000 where id = 'st-a';
    if not found then
        raise exception 'Salon sahibi personel kaydını güncelleyemedi';
    end if;

    insert into public.staff (id, tenant_id, full_name, title, role, branch,
                              commission_basis_points, monthly_salary_minor,
                              phone, nickname, created_at_ms, updated_at_ms)
    values ('st-yeni', 'aaaaaaaa-0000-0000-0000-000000000001', 'Yeni Eğitmen', 'Eğitmen',
            'TRAINER', 'Fitness', 3000, 0, '+905000000002', 'yenieg', 1, 1);

    -- ADMIN bile silemez: silme mezar taşıyla yapılıyor.
    begin
        delete from public.gym_members where id = 'uye-1';
        raise exception 'GÜVENLİK AÇIĞI: salon sahibi satır silebildi';
    exception
        when insufficient_privilege then null;  -- beklenen
    end;

    -- Mezar taşı yolu ise açık olmalı — silmenin gerçek karşılığı bu.
    update public.gym_members set deleted_at_ms = 123 where id = 'uye-1';
    if not found then
        raise exception 'Mezar taşı yazılamadı; silmenin karşılığı kalmadı';
    end if;
end
$$;

reset role;

-- ═══ İKİ SALONA BAĞLI KULLANICI ════════════════════════════════════════════
-- Güncelleme kuralının `with check` yarısı burada sınanıyor.
--
-- İlk hâlinde bu, "ADMIN kendi satırını karşı salona taşıyamaz" diye tek salonlu
-- bir kullanıcıyla yazılmıştı ve HİÇBİR ŞEY KANITLAMIYORDU: `with check`
-- tamamen kaldırıldığında bile test geçiyordu, çünkü taşınan satır o
-- kullanıcının okuma kuralına takılıp zaten reddediliyordu. Yani testin
-- düşmesinin sebebi sınamak istediği kural değildi.
--
-- Ayırt edici kurgu, kullanıcının iki salonda FARKLI role sahip olması: okuma
-- kuralı iki salonu da görüyor, dolayısıyla reddin tek kaynağı `with check`
-- kalıyor. Kurgu hayalî de değil — çok salonlu şemanın bütün amacı bu ve
-- "A salonunda sahip, B salonunda eğitmen" en olası ikinci durum.
--
-- Korunan şey: B salonunda yalnızca eğitmen olan biri, A salonundaki bir
-- personel satırını B'ye taşıyarak B'ye personel yazamamalı — orada bu yetkisi
-- yok.
insert into auth.users (id, email) values
    ('55555555-5555-5555-5555-555555555555', 'ikisalon@salon.test');

insert into public.gym_users (user_id, gym_id, role) values
    ('55555555-5555-5555-5555-555555555555', 'aaaaaaaa-0000-0000-0000-000000000001', 'ADMIN'),
    ('55555555-5555-5555-5555-555555555555', 'bbbbbbbb-0000-0000-0000-000000000002', 'TRAINER');

insert into public.staff (id, tenant_id, full_name, title, role, branch,
                          commission_basis_points, monthly_salary_minor,
                          phone, nickname, created_at_ms, updated_at_ms)
values ('st-tasima', 'aaaaaaaa-0000-0000-0000-000000000001', 'Taşıma Denemesi', 'Eğitmen',
        'TRAINER', 'Fitness', 0, 0, '+905000000003', 'tasima', 1, 1);

set role authenticated;
set test.uid = '55555555-5555-5555-5555-555555555555';

do $$
begin
    -- Önce kurgunun geçerli olduğu doğrulanıyor: bu kullanıcı B salonunu
    -- GERÇEKTEN görüyor. Görmeseydi aşağıdaki red, okuma kuralından gelirdi ve
    -- test yine hiçbir şey kanıtlamazdı.
    if not exists (select 1 from public.gyms where id = 'bbbbbbbb-0000-0000-0000-000000000002') then
        raise exception 'Kurgu bozuk: kullanıcı B salonunu görmüyor, red okumadan gelir';
    end if;

    -- A salonunda ADMIN olduğu için satırı hedefleyebiliyor (`using` geçiyor).
    -- Reddin tek kaynağı `with check`.
    begin
        update public.staff
           set tenant_id = 'bbbbbbbb-0000-0000-0000-000000000002'
         where id = 'st-tasima';
        raise exception 'GÜVENLİK AÇIĞI: satır, yetkisi olmayan salona taşınabildi';
    exception
        when insufficient_privilege then null;  -- beklenen
    end;

    -- Aynı kullanıcı B salonuna doğrudan personel de yazamamalı (orada eğitmen).
    begin
        insert into public.staff (id, tenant_id, full_name, title, role, branch,
                                  commission_basis_points, monthly_salary_minor,
                                  phone, nickname, created_at_ms, updated_at_ms)
        values ('st-b-sizinti', 'bbbbbbbb-0000-0000-0000-000000000002', 'B Sızma', 'Eğitmen',
                'TRAINER', 'Fitness', 0, 0, '+905000000004', 'bsizma', 1, 1);
        raise exception 'GÜVENLİK AÇIĞI: eğitmen olduğu salona personel yazabildi';
    exception
        when insufficient_privilege then null;  -- beklenen
    end;

    -- Ama B salonunda günlük işi yapabilmeli: rol orada eğitmen, yasak değil.
    insert into public.gym_members (id, tenant_id, full_name, phone, status,
                                    payment_type, created_at_ms, updated_at_ms)
    values ('uye-b', 'bbbbbbbb-0000-0000-0000-000000000002', 'B Üyesi',
            '+905009998844', 'ACTIVE', 'CASH', 1, 1);
end
$$;

reset role;

-- ─── Sahip gözüyle son durum ───────────────────────────────────────────────
do $$
begin
    if exists (select 1 from public.staff where id = 'st-sizinti') then
        raise exception 'GÜVENLİK AÇIĞI: eğitmenin personel satırı yazılmış';
    end if;
    if exists (select 1 from public.gym_packages where id = 'pkt-sizinti') then
        raise exception 'GÜVENLİK AÇIĞI: eğitmenin paketi yazılmış';
    end if;

    -- Eğitmenin fiyat denemeleri (düz update ve upsert) geçmemiş olmalı;
    -- yöneticinin değişikliği durmalı.
    if (select price_minor from public.products where id = 'p-a') <> 5500 then
        raise exception 'Ürün fiyatı beklenen değerde değil: %',
            (select price_minor from public.products where id = 'p-a');
    end if;

    -- Taşıma denemesi tutmamış olmalı.
    if (select tenant_id from public.staff where id = 'st-tasima')
       <> 'aaaaaaaa-0000-0000-0000-000000000001' then
        raise exception 'GÜVENLİK AÇIĞI: personel satırı salon değiştirmiş';
    end if;
    if exists (select 1 from public.staff where id = 'st-b-sizinti') then
        raise exception 'GÜVENLİK AÇIĞI: B salonuna personel yazılmış';
    end if;

    -- Eğitmenin hakediş denemesi de tutmamış olmalı; ADMIN'in maaş
    -- güncellemesi durmalı.
    if (select commission_basis_points from public.staff where id = 'st-a') <> 4000 then
        raise exception 'GÜVENLİK AÇIĞI: hakediş oranı değişmiş';
    end if;
    if (select monthly_salary_minor from public.staff where id = 'st-a') <> 3000000 then
        raise exception 'Salon sahibinin maaş güncellemesi kaybolmuş';
    end if;

    -- Silinmemiş, yalnızca mezar taşı konmuş olmalı.
    if not exists (select 1 from public.gym_members where id = 'uye-1') then
        raise exception 'Üye satırı silinmiş; mezar taşı bekleniyordu';
    end if;
end
$$;

\echo 'Role dayalı yazma yetkisi testi geçti.'
