-- ---------------------------------------------------------------------------
-- Üye kayıt isteği (member_link_requests) erişim testi
-- ---------------------------------------------------------------------------
-- Bu tablo kendine özgü bir risk taşıyor: yazan taraf HENÜZ ÜYE OLMAYAN,
-- salonun hiç tanımadığı bir kullanıcı. `member_accounts` ile karşılaştırın —
-- orada satırı personel yazıyor, burada satırı yabancı yazıyor. Kural bir
-- adım gevşek olsaydı, internetten kayıt olan herkes salonun bekleyen kayıt
-- listesini (ad, telefon, e-posta) okuyabilirdi.
--
-- Dört ayrı iddia sınanıyor:
--
--   1. Üye yalnızca KENDİ isteğini yazıp okuyabiliyor — başkasınınkini değil.
--   2. Üye kendi isteğini "LINKED" yapamıyor. Yapabilseydi panelde "bağlandı"
--      görünen ama hiçbir bağ kurulmamış satırlar oluşurdu ve personel o
--      satırları listede hiç görmezdi.
--   3. Personel yalnızca KENDİ salonunun isteklerini görüyor.
--   4. Eğitmen durumu değiştiremiyor — bu bir erişim kararı, ADMIN/MANAGER işi.
--
-- ÖNEMLİ: `set role authenticated` şart. Tablo sahibi satır bazlı güvenliği
-- baypas eder; sahip olarak koşan bir test her şeyi görür ve hiçbir şey
-- kanıtlamaz.
--
-- Kimlik önekleri `m40-`: aynı veritabanında 10/20/30 testleriyle birlikte
-- koşuyor ve `gym_members.phone` kiracı bazında tekil — çakışan bir telefon
-- kurgu aşamasında düşerdi.
-- ---------------------------------------------------------------------------

grant execute on function auth.uid() to authenticated;

-- ─── Kurgu ─────────────────────────────────────────────────────────────────
-- İki salon, her birinde bir ADMIN. Salon A'da ayrıca bir eğitmen.
-- Üç kayıt olmuş yabancı: ikisi A'ya, biri B'ye başvuruyor.
insert into auth.users (id, email) values
    ('40000000-0000-0000-0000-0000000000a1', 'a-yonetici@salon.test'),
    ('40000000-0000-0000-0000-0000000000a2', 'a-egitmen@salon.test'),
    ('40000000-0000-0000-0000-0000000000b1', 'b-yonetici@salon.test'),
    ('40000000-0000-0000-0000-0000000000c1', 'basvuran1@ornek.test'),
    ('40000000-0000-0000-0000-0000000000c2', 'basvuran2@ornek.test'),
    ('40000000-0000-0000-0000-0000000000c3', 'basvuran3@ornek.test');

insert into public.gyms (id, name) values
    ('40000000-0000-0000-0000-000000000001', 'İstek Salonu A'),
    ('40000000-0000-0000-0000-000000000002', 'İstek Salonu B');

insert into public.gym_users (user_id, gym_id, role) values
    ('40000000-0000-0000-0000-0000000000a1', '40000000-0000-0000-0000-000000000001', 'ADMIN'),
    ('40000000-0000-0000-0000-0000000000a2', '40000000-0000-0000-0000-000000000001', 'TRAINER'),
    ('40000000-0000-0000-0000-0000000000b1', '40000000-0000-0000-0000-000000000002', 'ADMIN');

-- Personelin isteği eşleştireceği gerçek üyelik kaydı.
insert into public.gym_members
    (id, tenant_id, full_name, phone, email, status, payment_type,
     remaining_sessions, end_date_ms, created_at_ms, updated_at_ms)
values ('m40-uye-1', '40000000-0000-0000-0000-000000000001', 'Başvuran Bir', '+905324000001',
        'basvuran1@ornek.test', 'ACTIVE', 'CASH', 5, 1900000000000, 1, 1);

-- ═══════════════════════════════════════════════════════════════════════════
-- BAŞVURAN gözüyle
-- ═══════════════════════════════════════════════════════════════════════════
set role authenticated;
set test.uid = '40000000-0000-0000-0000-0000000000c1';

do $$
declare
    görünen integer;
    etkilenen integer;
    durum text;
begin
    -- 1) Kendi isteğini yazabiliyor.
    insert into public.member_link_requests
        (auth_user_id, tenant_id, full_name, phone, email, note, created_at_ms, updated_at_ms)
    values ('40000000-0000-0000-0000-0000000000c1', '40000000-0000-0000-0000-000000000001',
            'Başvuran Bir', '+905324000001', 'basvuran1@ornek.test', null, 1, 1);

    -- 2) Yalnızca kendi isteğini görüyor.
    select count(*) into görünen from public.member_link_requests;
    if görünen <> 1 then
        raise exception 'Başvuran % istek görüyor, 1 bekleniyordu', görünen;
    end if;

    -- 3) Bekleyen isteğini düzeltebiliyor — yanlış telefon yazmış olabilir.
    update public.member_link_requests
       set phone = '+905324000099', updated_at_ms = 2
     where auth_user_id = '40000000-0000-0000-0000-0000000000c1';
    get diagnostics etkilenen = row_count;
    if etkilenen <> 1 then
        raise exception 'Başvuran kendi bekleyen isteğini düzeltemiyor';
    end if;

    -- 4) Durumu KENDİSİ değiştiremiyor.
    --
    -- Buradaki ölçüm biçimi bilinçli: satır bazlı güvenlik `using` süzgecine
    -- takılan satırları SESSİZCE eleyip 0 satır günceller, `with check`
    -- ihlalinde ise hata fırlatır. Hangisinin çalıştığı kuralın yazımına bağlı
    -- ve yeniden yazıldığında sessizce değişebilir — "hata bekle" demek testi
    -- kuralın biçimine bağlardı. Sonuca bakıyoruz: durum gerçekten değişti mi?
    begin
        update public.member_link_requests
           set state = 'LINKED'
         where auth_user_id = '40000000-0000-0000-0000-0000000000c1';
    exception
        when insufficient_privilege or check_violation then null;  -- ikisi de kabul
    end;

    select state into durum from public.member_link_requests
     where auth_user_id = '40000000-0000-0000-0000-0000000000c1';
    if durum <> 'PENDING' then
        raise exception 'GÜVENLİK AÇIĞI: başvuran isteğini kendisi "%" yapabildi', durum;
    end if;

    raise notice 'Başvuran kendi isteğini yazıp düzeltebiliyor, durumunu değiştiremiyor.';
end
$$;

-- Başkası adına istek yazamıyor: bu engel olmasaydı bir kullanıcı, tanımadığı
-- birinin adını ve telefonunu salonun listesine düşürebilirdi.
do $$
begin
    insert into public.member_link_requests
        (auth_user_id, tenant_id, full_name, phone, created_at_ms, updated_at_ms)
    values ('40000000-0000-0000-0000-0000000000c2', '40000000-0000-0000-0000-000000000001',
            'Sahte Kayıt', '+905324000002', 1, 1);
    raise exception 'GÜVENLİK AÇIĞI: başkası adına kayıt isteği yazılabiliyor';
exception
    when insufficient_privilege then
        raise notice 'Başkası adına istek yazılamıyor — doğru.';
end
$$;

-- İkinci ve üçüncü başvuran kendi isteklerini yazıyor.
set test.uid = '40000000-0000-0000-0000-0000000000c2';
insert into public.member_link_requests
    (auth_user_id, tenant_id, full_name, phone, email, created_at_ms, updated_at_ms)
values ('40000000-0000-0000-0000-0000000000c2', '40000000-0000-0000-0000-000000000001',
        'Başvuran İki', '+905324000002', 'basvuran2@ornek.test', 1, 1);

set test.uid = '40000000-0000-0000-0000-0000000000c3';
insert into public.member_link_requests
    (auth_user_id, tenant_id, full_name, phone, created_at_ms, updated_at_ms)
values ('40000000-0000-0000-0000-0000000000c3', '40000000-0000-0000-0000-000000000002',
        'B Salonu Başvuranı', '+905324000003', 1, 1);

-- Aynı kişi ikinci kez istek gönderemiyor: birincil anahtar bunu engelliyor.
-- Engel olmasaydı bir kişi listeyi şişirip personelin işini kullanılamaz hâle
-- getirebilirdi.
do $$
begin
    insert into public.member_link_requests
        (auth_user_id, tenant_id, full_name, phone, created_at_ms, updated_at_ms)
    values ('40000000-0000-0000-0000-0000000000c3', '40000000-0000-0000-0000-000000000002',
            'Tekrar', '+905324000003', 1, 1);
    raise exception 'BEKLENMEDİK: aynı hesap ikinci istek yazabildi';
exception
    when unique_violation then
        raise notice 'Aynı hesap ikinci istek yazamıyor — doğru.';
end
$$;

-- ═══════════════════════════════════════════════════════════════════════════
-- PERSONEL gözüyle
-- ═══════════════════════════════════════════════════════════════════════════
set test.uid = '40000000-0000-0000-0000-0000000000a1';   -- A salonu ADMIN

do $$
declare
    görünen integer;
    etkilenen integer;
begin
    -- Yalnızca kendi salonunun istekleri. 3 görünseydi A salonunun personeli
    -- B salonuna başvuran kişinin adını ve telefonunu okuyor olurdu.
    select count(*) into görünen from public.member_link_requests;
    if görünen <> 2 then
        raise exception 'SIZINTI: A yöneticisi % istek görüyor, 2 bekleniyordu', görünen;
    end if;

    if exists (select 1 from public.member_link_requests
                where auth_user_id = '40000000-0000-0000-0000-0000000000c3') then
        raise exception 'SIZINTI: A yöneticisi B salonunun isteğini görüyor';
    end if;

    -- Bağlama akışı: önce hesap bağlanıyor, sonra istek "LINKED" işaretleniyor.
    insert into public.member_accounts (member_id, tenant_id, auth_user_id, linked_at_ms)
    values ('m40-uye-1', '40000000-0000-0000-0000-000000000001',
            '40000000-0000-0000-0000-0000000000c1', 2);

    update public.member_link_requests
       set state = 'LINKED', updated_at_ms = 2
     where auth_user_id = '40000000-0000-0000-0000-0000000000c1';
    get diagnostics etkilenen = row_count;
    if etkilenen <> 1 then
        raise exception 'Yönetici isteği LINKED yapamıyor (% satır)', etkilenen;
    end if;

    raise notice 'Yönetici kendi salonunun isteklerini görüyor ve bağlayabiliyor.';
end
$$;

-- B salonunun yöneticisi A'nın isteğini değiştiremiyor. Okuma süzgecinden
-- geçemediği için güncelleme sessizce 0 satıra dokunuyor — hata beklenmiyor.
set test.uid = '40000000-0000-0000-0000-0000000000b1';

do $$
declare
    etkilenen integer;
begin
    update public.member_link_requests
       set state = 'REJECTED'
     where auth_user_id = '40000000-0000-0000-0000-0000000000c2';
    get diagnostics etkilenen = row_count;
    if etkilenen <> 0 then
        raise exception 'SIZINTI: B yöneticisi A salonunun isteğini değiştirdi';
    end if;
    raise notice 'Başka salonun yöneticisi isteğe dokunamıyor — doğru.';
end
$$;

-- Satırın gerçekten değişmediği SAHİP gözüyle ayrıca doğrulanıyor.
--
-- `row_count = 0` tek başına yeterli bir kanıt değil: güncelleme okuma
-- süzgecinden geçemediği için 0 dönebilir, ama aynı sorgu satırı gördüğü hâlde
-- 0 dönemez. İki ölçüm birbirini tutuyor. Sahip olarak koşmak gerekiyor çünkü
-- `authenticated` rolü zaten o satırı okuyamıyor.
reset role;

do $$
declare
    durum text;
begin
    select state into durum from public.member_link_requests
     where auth_user_id = '40000000-0000-0000-0000-0000000000c2';
    if durum <> 'PENDING' then
        raise exception 'SIZINTI: A salonunun isteği "%" oldu', durum;
    end if;
end
$$;

set role authenticated;

-- Eğitmen: isteği GÖRÜYOR (kiracı okuma kuralı) ama durumunu değiştiremiyor.
-- Görmesi kasıtlı — eğitmen üyeyi tanıyıp yöneticiye "bu kişi bizim" diyebilir;
-- erişim kararını vermek başka bir şey.
set test.uid = '40000000-0000-0000-0000-0000000000a2';

do $$
declare
    etkilenen integer;
begin
    if not exists (select 1 from public.member_link_requests
                    where auth_user_id = '40000000-0000-0000-0000-0000000000c2') then
        raise exception 'Eğitmen kendi salonunun isteğini göremiyor';
    end if;

    update public.member_link_requests
       set state = 'LINKED'
     where auth_user_id = '40000000-0000-0000-0000-0000000000c2';
    get diagnostics etkilenen = row_count;
    if etkilenen <> 0 then
        raise exception 'GÜVENLİK AÇIĞI: eğitmen isteği bağlı işaretleyebiliyor';
    end if;

    raise notice 'Eğitmen isteği görüyor ama durumunu değiştiremiyor — doğru.';
end
$$;

-- Eğitmen ölçümü de sahip gözüyle doğrulanıyor. Burada `row_count = 0`
-- gerçekten zayıf bir kanıt: eğitmen satırı OKUYABİLİYOR, yani sıfırın tek
-- açıklaması güncelleme kuralının eleme yapması. Değeri ayrıca görmek şart.
reset role;

do $$
declare
    durum text;
begin
    select state into durum from public.member_link_requests
     where auth_user_id = '40000000-0000-0000-0000-0000000000c2';
    if durum <> 'PENDING' then
        raise exception 'GÜVENLİK AÇIĞI: eğitmen isteği "%" yaptı', durum;
    end if;
end
$$;

set role authenticated;

-- Bağlanmış isteği başvuran artık düzeltemiyor: `using` içindeki
-- `state = 'PENDING'` bunu sağlıyor. Sağlamasaydı üye, bağlandıktan sonra
-- adını değiştirip personelin kayıt izini bozabilirdi.
set test.uid = '40000000-0000-0000-0000-0000000000c1';

do $$
declare
    etkilenen integer;
begin
    update public.member_link_requests
       set full_name = 'Değiştirildi'
     where auth_user_id = '40000000-0000-0000-0000-0000000000c1';
    get diagnostics etkilenen = row_count;
    if etkilenen <> 0 then
        raise exception 'Bağlanmış istek başvuran tarafından değiştirilebiliyor';
    end if;
    raise notice 'Bağlanmış istek başvuran tarafından değiştirilemiyor — doğru.';
end
$$;

-- Anonim ziyaretçi bu tabloya hiç dokunamıyor: `authenticated` dışında yetki
-- verilmedi. Kayıt olmadan istek yazılabilseydi liste çöple dolardı.
set role anon;

do $$
begin
    perform 1 from public.member_link_requests;
    raise exception 'GÜVENLİK AÇIĞI: anonim kayıt isteklerini okuyabiliyor';
exception
    when insufficient_privilege then
        raise notice 'Anonim kayıt isteklerine erişemiyor — doğru.';
end
$$;

reset role;
select 'Üye kayıt isteği testi geçti.' as sonuc;
