-- ---------------------------------------------------------------------------
-- Üye erişimi ve herkese açık duyuru testi
-- ---------------------------------------------------------------------------
-- Sınanan şey: üyenin kendi verisini görürken salonun geri kalanını GÖRMEMESİ.
--
-- Bu testin varlık sebebi, 0005'te bilinçli olarak seçilmeyen tasarım: üyeye bir
-- `gym_users` satırı vermek. O yol seçilseydi bütün okuma kuralları eşleşir ve
-- üye salonun tamamını — her üyenin sağlık verisi, tüm kasa hareketleri,
-- personel maaşları — okuyabilirdi. Yanlış yazılmış bir kural sessizdir:
-- uygulama çalışır, panel çalışır, veri sızar. O yüzden sızmadığı tek tek
-- kanıtlanıyor.
--
-- Ayrıca `anon` rolü ilk kez bir tabloyu okuyor (duyurular). Anonim erişimin
-- YALNIZCA yayınlanmış duyurularla sınırlı kaldığı da burada sınanıyor.
--
-- ÖNEMLİ: testler `authenticated` / `anon` rolleriyle koşuyor. Tablo sahibi ve
-- superuser satır bazlı güvenliği baypas eder; sahip olarak koşan bir test her
-- şeyi görür ve hiçbir şey kanıtlamazdı.
-- ---------------------------------------------------------------------------

-- Taklit `auth.uid()` testin kendi kurgusu (bkz. 10_rls_test.sql). `anon` da
-- çağırıyor çünkü duyuru kuralı o rolde de değerlendiriliyor.
grant execute on function auth.uid() to authenticated;
grant execute on function auth.uid() to anon;

-- ─── Kurgu ─────────────────────────────────────────────────────────────────
-- Salon A: bir yönetici, bir eğitmen, iki üye (biri hesaplı, biri hesapsız).
-- Salon B: başka bir salon — üyenin oraya hiç erişememesi gerekiyor.
insert into auth.users (id, email) values
    ('a0000000-0000-0000-0000-00000000000a', 'yonetici@salon.test'),
    ('a0000000-0000-0000-0000-00000000000b', 'egitmen@salon.test'),
    ('c0000000-0000-0000-0000-00000000000c', 'uye1@salon.test'),
    ('c0000000-0000-0000-0000-00000000000d', 'uye2@salon.test'),
    ('c0000000-0000-0000-0000-00000000000e', 'baglantisiz@salon.test');

insert into public.gyms (id, name) values
    ('dddddddd-0000-0000-0000-000000000001', 'Üye Salonu A'),
    ('dddddddd-0000-0000-0000-000000000002', 'Üye Salonu B');

insert into public.gym_users (user_id, gym_id, role) values
    ('a0000000-0000-0000-0000-00000000000a', 'dddddddd-0000-0000-0000-000000000001', 'ADMIN'),
    ('a0000000-0000-0000-0000-00000000000b', 'dddddddd-0000-0000-0000-000000000001', 'TRAINER');

insert into public.gym_members
    (id, tenant_id, full_name, phone, email, status, payment_type,
     remaining_sessions, end_date_ms, health_notes, created_at_ms, updated_at_ms)
values
    ('m30-uye-1', 'dddddddd-0000-0000-0000-000000000001', 'Üye Bir', '+905320000001',
     'uye1@salon.test', 'ACTIVE', 'CASH', 8, 1900000000000, 'eğitmenin klinik notu', 1, 1),
    ('m30-uye-2', 'dddddddd-0000-0000-0000-000000000001', 'Üye İki', '+905320000002',
     'uye2@salon.test', 'ACTIVE', 'CASH', 4, 1900000000000, 'ikinci üyenin notu', 1, 1),
    ('m30-uye-b', 'dddddddd-0000-0000-0000-000000000002', 'B Salonu Üyesi', '+905320000003',
     null, 'ACTIVE', 'CASH', 2, 1900000000000, null, 1, 1);

-- Üye 1 ve 2 hesaplı; üçüncü kullanıcı bilinçli olarak BAĞLANTISIZ.
insert into public.member_accounts (member_id, tenant_id, auth_user_id, linked_at_ms) values
    ('m30-uye-1', 'dddddddd-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-00000000000c', 1),
    ('m30-uye-2', 'dddddddd-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-00000000000d', 1);

insert into public.measurements
    (id, tenant_id, member_id, date_ms, height, weight, shoulder, chest, waist, hips, leg, arm,
     created_at_ms, updated_at_ms)
values
    ('m30-olcum-1', 'dddddddd-0000-0000-0000-000000000001', 'm30-uye-1', 100, 170, 70, 40, 90, 75, 95, 55, 30, 1, 1),
    ('m30-olcum-2', 'dddddddd-0000-0000-0000-000000000001', 'm30-uye-2', 100, 160, 60, 38, 85, 70, 90, 52, 28, 1, 1);

-- Para ve maaş: üyenin ASLA görmemesi gereken iki tablo.
insert into public.ledger_entries
    (id, tenant_id, type, category, amount_minor, payment_method, description,
     occurred_at_ms, created_at_ms)
values ('m30-defter-1', 'dddddddd-0000-0000-0000-000000000001', 'PAYMENT', 'MEMBERSHIP',
        240000, 'CASH', 'üyelik tahsilatı', 1, 1);

insert into public.staff
    (id, tenant_id, full_name, title, role, branch, commission_basis_points,
     monthly_salary_minor, phone, nickname, created_at_ms, updated_at_ms)
values ('m30-personel-1', 'dddddddd-0000-0000-0000-000000000001', 'Eğitmen Bir', 'Eğitmen',
        'TRAINER', 'Merkez', 4000, 3500000, '+905320000009', 'egitmen1', 1, 1);

-- Duyurular: yayınlanmış, taslak, süresi geçmiş ve ileri tarihli.
insert into public.announcements
    (id, tenant_id, title, body, kind, starts_at_ms, ends_at_ms, is_published,
     created_at_ms, updated_at_ms)
values
    ('m30-duyuru-yayinda', 'dddddddd-0000-0000-0000-000000000001', 'Açık ders', 'Bu hafta',
     'EVENT', null, null, true, 1, 1),
    ('m30-duyuru-taslak', 'dddddddd-0000-0000-0000-000000000001', 'Taslak', 'Henüz hazır değil',
     'NOTICE', null, null, false, 1, 1),
    ('m30-duyuru-gecmis', 'dddddddd-0000-0000-0000-000000000001', 'Geçmiş etkinlik', 'Bitti',
     'EVENT', null, (extract(epoch from now()) * 1000)::bigint - 86400000, true, 1, 1),
    ('m30-duyuru-ileri', 'dddddddd-0000-0000-0000-000000000001', 'İleri tarihli', 'Henüz başlamadı',
     'EVENT', (extract(epoch from now()) * 1000)::bigint + 86400000, null, true, 1, 1),
    ('m30-duyuru-silinmis', 'dddddddd-0000-0000-0000-000000000001', 'Silinmiş', 'Mezar taşı',
     'AD', null, null, true, 1, 1);
update public.announcements set deleted_at_ms = 1 where id = 'm30-duyuru-silinmis';

-- ═══════════════════════════════════════════════════════════════════════════
-- ÜYE gözüyle
-- ═══════════════════════════════════════════════════════════════════════════
set role authenticated;
set test.uid = 'c0000000-0000-0000-0000-00000000000c';   -- uye-1

do $$
declare
    görünen integer;
    etkilenen integer;
    sayi integer;
    metin text;
begin
    -- 1) Yalnızca KENDİ üye kaydını görüyor.
    --
    -- En önemli iddia. Bu sayı 2 ya da 3 olsaydı üye salonun üye listesini
    -- (adlar, telefonlar, sağlık notları) okuyor olurdu.
    select count(*) into görünen from public.gym_members;
    if görünen <> 1 then
        raise exception 'SIZINTI: üye % üye kaydı görüyor, 1 bekleniyordu', görünen;
    end if;

    if not exists (select 1 from public.gym_members where id = 'm30-uye-1') then
        raise exception 'Üye kendi kaydını göremiyor';
    end if;

    if exists (select 1 from public.gym_members where id = 'm30-uye-2') then
        raise exception 'SIZINTI: üye başka üyenin kaydını görüyor';
    end if;

    -- 2) Başka salonun üyesi hiç görünmemeli.
    if exists (select 1 from public.gym_members where id = 'm30-uye-b') then
        raise exception 'SIZINTI: üye başka salonun üyesini görüyor';
    end if;

    -- 3) Para ve maaş kapalı.
    --
    -- Kural yerine yalnızca arayüzde gizlemek yeterli olmazdı: anon anahtar ve
    -- jeton elde olduğu için API'ye doğrudan istek atmak mümkün.
    select count(*) into görünen from public.ledger_entries;
    if görünen <> 0 then
        raise exception 'SIZINTI: üye % kasa hareketi görüyor', görünen;
    end if;

    select count(*) into görünen from public.staff;
    if görünen <> 0 then
        raise exception 'SIZINTI: üye % personel kaydı (maaş dahil) görüyor', görünen;
    end if;

    select count(*) into görünen from public.orders;
    if görünen <> 0 then
        raise exception 'SIZINTI: üye % sipariş görüyor', görünen;
    end if;

    -- 4) Yalnızca kendi ölçümleri.
    select count(*) into görünen from public.measurements;
    if görünen <> 1 then
        raise exception 'SIZINTI: üye % ölçüm görüyor, 1 bekleniyordu', görünen;
    end if;
    if not exists (select 1 from public.measurements where id = 'm30-olcum-1') then
        raise exception 'Üye kendi ölçümünü göremiyor';
    end if;

    -- 5) Üye kaydına YAZAMIYOR.
    --
    -- Üye kendi adını, kalan seansını ya da ödeme durumunu değiştirememeli.
    -- Okuma kuralı eklendi diye yazma da açılmış olabilir mi? Kanıtlanıyor.
    --
    -- DİKKAT — burada iddia "hata atıyor" DEĞİL, "veri değişmiyor":
    -- PostgreSQL'de satır bazlı güvenlik UPDATE'te satırları **sessizce
    -- süzüyor**. `using` eşleşmezse ifade 0 satır etkiler ve hiçbir hata
    -- atmaz; `insufficient_privilege` yalnızca `with check` ihlalinde ya da
    -- yetkinin (grant) hiç olmadığı durumda geliyor.
    --
    -- Bu ayrım testin kendisini yanıltabilirdi: ilk hâli hata bekliyordu ve
    -- düştü — oysa veri güvendeydi. Tersi daha kötü olurdu; hata beklemek
    -- bazı durumlarda geçer ve testi "yazmayı engelliyor" sanırken aslında
    -- yalnızca "hata atıyor" sınanmış olurdu. Sonucu ölçmek her iki tuzağı da
    -- kapatıyor.
    update public.gym_members set remaining_sessions = 999 where id = 'm30-uye-1';
    get diagnostics etkilenen = row_count;
    if etkilenen <> 0 then
        raise exception 'GÜVENLİK AÇIĞI: üye kendi kaydını değiştirdi (% satır)', etkilenen;
    end if;

    select remaining_sessions into sayi from public.gym_members where id = 'm30-uye-1';
    if sayi <> 8 then
        raise exception 'GÜVENLİK AÇIĞI: kalan seans % oldu, 8 olmalıydı', sayi;
    end if;

    -- 6) Eğitmenin klinik notunu ezemiyor — 0005'in ayrı tablo kararının sebebi.
    update public.gym_members set health_notes = 'üye ezdi' where id = 'm30-uye-1';
    get diagnostics etkilenen = row_count;
    if etkilenen <> 0 then
        raise exception 'GÜVENLİK AÇIĞI: üye klinik notu değiştirdi';
    end if;

    select health_notes into metin from public.gym_members where id = 'm30-uye-1';
    if metin <> 'eğitmenin klinik notu' then
        raise exception 'GÜVENLİK AÇIĞI: klinik not "%" oldu', metin;
    end if;

    -- 7) Kendi adına sağlık beyanı EKLEYEBİLİYOR — kural fazla kısıtlayıcı da
    --    olmamalı, yoksa özelliğin kendisi çalışmaz.
    insert into public.member_health_updates
        (id, tenant_id, member_id, reported_at_ms, conditions, consent_at_ms, created_at_ms)
    values ('m30-beyan-1', 'dddddddd-0000-0000-0000-000000000001', 'm30-uye-1', 100,
            'sırt ağrısı', 100, 100);

    -- 8) BAŞKA üye adına beyan ekleyemiyor.
    begin
        insert into public.member_health_updates
            (id, tenant_id, member_id, reported_at_ms, conditions, consent_at_ms, created_at_ms)
        values ('m30-beyan-sahte', 'dddddddd-0000-0000-0000-000000000001', 'm30-uye-2', 100,
                'sahte beyan', 100, 100);
        raise exception 'GÜVENLİK AÇIĞI: üye başka üye adına beyan ekleyebiliyor';
    exception
        when insufficient_privilege then null;  -- beklenen
    end;

    -- 9) Beyan yalnızca eklenen: kendi beyanını bile değiştiremiyor.
    --
    -- Beyan bir geçmiş. Üye dün söylediğini silememeli; düzeltme yeni satırla.
    begin
        update public.member_health_updates set conditions = 'değişti' where id = 'm30-beyan-1';
        raise exception 'GÜVENLİK AÇIĞI: sağlık beyanı değiştirilebiliyor';
    exception
        when insufficient_privilege then null;  -- beklenen
    end;

    begin
        delete from public.member_health_updates where id = 'm30-beyan-1';
        raise exception 'GÜVENLİK AÇIĞI: sağlık beyanı silinebiliyor';
    exception
        when insufficient_privilege then null;  -- beklenen
    end;

    -- 10) Kendi beyanını okuyabiliyor, başkasının beyanını okuyamıyor.
    select count(*) into görünen from public.member_health_updates;
    if görünen <> 1 then
        raise exception 'Üye kendi beyanını okuyamıyor (% satır)', görünen;
    end if;

    -- 11) Duyuru: yayınlanmış ve penceresi açık olan tek satır.
    select count(*) into görünen from public.announcements;
    if görünen <> 1 then
        raise exception 'Üye % duyuru görüyor, 1 bekleniyordu (taslak/geçmiş/ileri/silinmiş sızıyor)', görünen;
    end if;

    -- 12) Üye hesap bağını görebiliyor (üye alanı bunu okumak zorunda), ama
    --     yalnızca kendisinin.
    select count(*) into görünen from public.member_accounts;
    if görünen <> 1 then
        raise exception 'SIZINTI: üye % hesap bağı görüyor, 1 bekleniyordu', görünen;
    end if;

    -- 13) Bağı kendisi kuramıyor — kurabilse başka üyeye bağlanıp onun sağlık
    --     verisini okurdu. Erişim kararının tamamı personelde.
    begin
        insert into public.member_accounts (member_id, tenant_id, auth_user_id, linked_at_ms)
        values ('m30-uye-2', 'dddddddd-0000-0000-0000-000000000001',
                'c0000000-0000-0000-0000-00000000000c', 1);
        raise exception 'GÜVENLİK AÇIĞI: üye kendini başka üyeye bağlayabiliyor';
    exception
        when insufficient_privilege then null;  -- beklenen
        when unique_violation then null;        -- tekillik de kabul: yine engellendi
    end;

    raise notice 'Üye erişimi: kendi verisi görünüyor, salonun geri kalanı kapalı.';
end
$$;

-- ═══════════════════════════════════════════════════════════════════════════
-- BAĞLANTISIZ kullanıcı gözüyle
-- ═══════════════════════════════════════════════════════════════════════════
-- Hesabı olan ama hiçbir üyeye bağlanmamış kişi. Kayıt olup onay bekleyen
-- durumun kendisi ve hiçbir şey görmemeli.
set test.uid = 'c0000000-0000-0000-0000-00000000000e';

do $$
declare
    görünen integer;
begin
    select count(*) into görünen from public.gym_members;
    if görünen <> 0 then
        raise exception 'SIZINTI: bağlantısız kullanıcı % üye kaydı görüyor', görünen;
    end if;

    select count(*) into görünen from public.measurements;
    if görünen <> 0 then
        raise exception 'SIZINTI: bağlantısız kullanıcı % ölçüm görüyor', görünen;
    end if;

    select count(*) into görünen from public.member_health_updates;
    if görünen <> 0 then
        raise exception 'SIZINTI: bağlantısız kullanıcı % beyan görüyor', görünen;
    end if;

    -- Kendi adına beyan da ekleyemiyor: bağlı olmadığı için `user_member_ids()`
    -- boş ve `with check` eşleşmiyor.
    begin
        insert into public.member_health_updates
            (id, tenant_id, member_id, reported_at_ms, consent_at_ms, created_at_ms)
        values ('m30-beyan-bagsiz', 'dddddddd-0000-0000-0000-000000000001', 'm30-uye-1', 1, 1, 1);
        raise exception 'GÜVENLİK AÇIĞI: bağlantısız kullanıcı beyan ekleyebiliyor';
    exception
        when insufficient_privilege then null;  -- beklenen
    end;

    raise notice 'Bağlantısız kullanıcı hiçbir üye verisi görmüyor.';
end
$$;

-- ═══════════════════════════════════════════════════════════════════════════
-- ANONİM (giriş yapılmamış) gözüyle
-- ═══════════════════════════════════════════════════════════════════════════
set role anon;
set test.uid = '';

do $$
declare
    görünen integer;
begin
    -- 1) Yayınlanmış ve penceresi açık duyuru görünüyor — açılış sayfasının
    --    çalışması buna bağlı.
    select count(*) into görünen from public.announcements;
    if görünen <> 1 then
        raise exception 'Anonim % duyuru görüyor, 1 bekleniyordu', görünen;
    end if;

    if not exists (select 1 from public.announcements where id = 'm30-duyuru-yayinda') then
        raise exception 'Anonim yayınlanmış duyuruyu göremiyor — açılış sayfası boş kalır';
    end if;

    -- 2) Taslak, süresi geçmiş, ileri tarihli ve silinmiş duyuru GÖRÜNMEMELİ.
    --
    -- Süzme yalnızca istemcide yapılsaydı adresi bilen biri bunları API'den
    -- okuyabilirdi: yayınlanmamış bir kampanya ya da bitmiş bir etkinlik.
    if exists (select 1 from public.announcements where id = 'm30-duyuru-taslak') then
        raise exception 'SIZINTI: yayınlanmamış taslak anonim olarak okunuyor';
    end if;
    if exists (select 1 from public.announcements where id = 'm30-duyuru-gecmis') then
        raise exception 'SIZINTI: süresi geçmiş duyuru anonim olarak okunuyor';
    end if;
    if exists (select 1 from public.announcements where id = 'm30-duyuru-ileri') then
        raise exception 'SIZINTI: ileri tarihli duyuru anonim olarak okunuyor';
    end if;
    if exists (select 1 from public.announcements where id = 'm30-duyuru-silinmis') then
        raise exception 'SIZINTI: silinmiş duyuru anonim olarak okunuyor';
    end if;

    -- 3) Duyuru yazamıyor.
    begin
        insert into public.announcements
            (id, tenant_id, title, body, kind, is_published, created_at_ms, updated_at_ms)
        values ('m30-duyuru-anon', 'dddddddd-0000-0000-0000-000000000001', 'sızma', 'x',
                'AD', true, 1, 1);
        raise exception 'GÜVENLİK AÇIĞI: anonim duyuru ekleyebiliyor';
    exception
        when insufficient_privilege then null;  -- beklenen
    end;

    raise notice 'Anonim erişim: yalnızca yayınlanmış duyurular.';
end
$$;

-- Anonimin BAŞKA hiçbir tabloya erişemediği ayrı ayrı sınanıyor.
--
-- Döngü tablo listesini elle saymıyor: yeni bir tablo eklenip anonime yanlışlıkla
-- yetki verilirse bu test onu da yakalasın. `announcements` bilinçli olarak
-- dışarıda — anonimin okuduğu tek tablo o.
do $$
declare
    t text;
    sayı integer;
begin
    for t in
        select table_name from information_schema.tables
        where table_schema = 'public' and table_type = 'BASE TABLE'
          and table_name <> 'announcements'
        order by table_name
    loop
        begin
            execute format('select count(*) from public.%I', t) into sayı;
            -- Yetki varsa sorgu çalışır. Satır dönmemesi yeterli değil:
            -- yetkinin hiç olmaması gerekiyor, çünkü kural bir gün gevşetilirse
            -- yetki sessizce kapıyı açık bırakır.
            raise exception 'GÜVENLİK AÇIĞI: anonim "%" tablosunu sorgulayabiliyor', t;
        exception
            when insufficient_privilege then null;  -- beklenen
        end;
    end loop;
    raise notice 'Anonim, duyurular dışında hiçbir tabloya erişemiyor.';
end
$$;

-- ═══════════════════════════════════════════════════════════════════════════
-- PERSONEL gözüyle
-- ═══════════════════════════════════════════════════════════════════════════
set role authenticated;
set test.uid = 'a0000000-0000-0000-0000-00000000000a';   -- ADMIN

do $$
declare
    görünen integer;
begin
    -- Personel taslakları da görüyor: panelden düzenlenebilmeleri için.
    select count(*) into görünen from public.announcements;
    if görünen < 4 then
        raise exception 'Yönetici taslak duyuruları göremiyor (% satır)', görünen;
    end if;

    -- Üyenin beyanı personele görünüyor — özelliğin amacı bu.
    if not exists (select 1 from public.member_health_updates where id = 'm30-beyan-1') then
        raise exception 'Üyenin sağlık beyanı personele görünmüyor';
    end if;

    -- Yönetici bağ kurabiliyor.
    insert into public.member_accounts (member_id, tenant_id, auth_user_id, linked_at_ms)
    values ('m30-uye-b', 'dddddddd-0000-0000-0000-000000000002',
            'c0000000-0000-0000-0000-00000000000e', 1);
    raise exception 'BEKLENMEDİK: yönetici başka salona bağ kurabildi';
exception
    when insufficient_privilege then
        raise notice 'Yönetici başka salona bağ kuramıyor — doğru.';
end
$$;

do $$
begin
    -- Kendi salonunda bağ kurabiliyor.
    insert into public.member_accounts (member_id, tenant_id, auth_user_id, linked_at_ms)
    values ('m30-uye-1', 'dddddddd-0000-0000-0000-000000000001',
            'c0000000-0000-0000-0000-00000000000e', 1);
    raise exception 'BEKLENMEDİK: aynı üyeye ikinci hesap bağlandı';
exception
    when unique_violation then
        raise notice 'Bir üyeye ikinci hesap bağlanamıyor — doğru.';
end
$$;

-- Bir hesabın iki üyeye bağlanamadığı da sınanıyor: bu kısıt olmasa bir hesap
-- birden fazla üyenin sağlık verisini okuyabilirdi.
do $$
begin
    insert into public.member_accounts (member_id, tenant_id, auth_user_id, linked_at_ms)
    values ('m30-uye-2', 'dddddddd-0000-0000-0000-000000000001',
            'c0000000-0000-0000-0000-00000000000c', 1);
    raise exception 'GÜVENLİK AÇIĞI: bir hesap iki üyeye bağlanabiliyor';
exception
    when unique_violation then
        raise notice 'Bir hesap iki üyeye bağlanamıyor — doğru.';
end
$$;

-- ─── Eğitmen duyuru yayınlayamıyor ─────────────────────────────────────────
set test.uid = 'a0000000-0000-0000-0000-00000000000b';   -- TRAINER

do $$
begin
    insert into public.announcements
        (id, tenant_id, title, body, kind, is_published, created_at_ms, updated_at_ms)
    values ('m30-duyuru-egitmen', 'dddddddd-0000-0000-0000-000000000001', 'eğitmen yazdı', 'x',
            'AD', true, 1, 1);
    raise exception 'GÜVENLİK AÇIĞI: eğitmen duyuru yayınlayabiliyor';
exception
    when insufficient_privilege then
        raise notice 'Eğitmen duyuru yayınlayamıyor — doğru.';
end
$$;

reset role;
select 'Üye erişimi ve duyuru testi geçti.' as sonuc;
