-- ---------------------------------------------------------------------------
-- Personel daveti: yardımcı fonksiyon ve yönetici görüşü testi
-- ---------------------------------------------------------------------------
-- 0007 iki şey getiriyor ve ikisi de yanlış kurulduğunda SESSİZ:
--
--   1. `auth_kullanici_id(e-posta)` — `auth.users`a bakan `security definer`
--      bir fonksiyon. Yetki kontrolü gövdenin İÇİNDE olduğu için, kontrolün
--      düşmesi hiçbir hata üretmez: fonksiyon çalışmaya devam eder, yalnızca
--      artık herkese cevap verir. Yani bozulduğunda belirtisi yok.
--
--   2. `gym_users_admin_select` — yöneticinin kendi salonundaki erişimleri
--      görmesi. Fazla açılırsa bir salonun yöneticisi BAŞKA salonun
--      personelini görür; az açılırsa davet ekranı boş kalır.
--
-- ÖNEMLİ: `set role authenticated` şart. Tablo sahibi satır bazlı güvenliği
-- baypas eder; sahip olarak koşan bir test her şeyi görür ve hiçbir şey
-- kanıtlamaz.
--
-- Kimlik ÖNEKLERİ ve E-POSTALAR `50` ad alanında: bütün testler aynı
-- veritabanında koşuyor. Diğer testler e-postayı yalnızca dolgu olarak
-- kullandığı için aralarında çakışanlar var ve zararsızdı; e-postadan ARAMA
-- yapan ilk test bu, dolayısıyla çakışma burada gerçek bir yanlış sonuca
-- dönüşüyor. (Nitekim ilk yazımda dönüştü: 40'ın eğitmeni bulundu.)
-- ---------------------------------------------------------------------------

grant execute on function auth.uid() to authenticated;

-- ─── Kurgu ─────────────────────────────────────────────────────────────────
-- İki salon. A'da bir ADMIN, bir MANAGER, bir TRAINER. B'de bir ADMIN.
-- Ayrıca hiçbir salona bağlı OLMAYAN bir hesap: davet edilecek kişiyi temsil
-- ediyor (hesabı var, yetkisi yok).
insert into auth.users (id, email) values
    ('50000000-0000-0000-0000-0000000000a1', 'a50-admin@salon.test'),
    ('50000000-0000-0000-0000-0000000000a2', 'a50-mudur@salon.test'),
    ('50000000-0000-0000-0000-0000000000a3', 'a50-egitmen@salon.test'),
    ('50000000-0000-0000-0000-0000000000b1', 'b50-admin@salon.test'),
    ('50000000-0000-0000-0000-0000000000d1', 'Yeni.Personel50@Ornek.TEST');

insert into public.gyms (id, name) values
    ('50000000-0000-0000-0000-000000000001', 'Davet Salonu A'),
    ('50000000-0000-0000-0000-000000000002', 'Davet Salonu B');

insert into public.gym_users (user_id, gym_id, role) values
    ('50000000-0000-0000-0000-0000000000a1', '50000000-0000-0000-0000-000000000001', 'ADMIN'),
    ('50000000-0000-0000-0000-0000000000a2', '50000000-0000-0000-0000-000000000001', 'MANAGER'),
    ('50000000-0000-0000-0000-0000000000a3', '50000000-0000-0000-0000-000000000001', 'TRAINER'),
    ('50000000-0000-0000-0000-0000000000b1', '50000000-0000-0000-0000-000000000002', 'ADMIN');


-- ═══════════════════════════════════════════════════════════════════════════
-- auth_kullanici_id: yetkisiz çağıranlar
-- ═══════════════════════════════════════════════════════════════════════════
set role authenticated;

-- Giriş yapmamış çağıran. `auth.uid()` null → gövdedeki `exists` düşüyor.
--
-- Bu, fonksiyonun en önemli iddiası: `public` şemasındaki bir `security
-- definer` fonksiyonun `execute` yetkisi varsayılan olarak herkese verilir.
-- Kontrol gövdede olmasaydı, adresi bilen herkes e-posta sorgulayabilirdi.
set test.uid = '';

do $$
declare
    sonuc uuid;
begin
    select public.auth_kullanici_id('a50-admin@salon.test') into sonuc;
    if sonuc is not null then
        raise exception 'Giriş yapmamış çağıran kimlik alabildi: %', sonuc;
    end if;
    raise notice 'Giriş yapmamış çağıran kimlik alamıyor — doğru.';
end
$$;

-- Eğitmen: giriş yapmış ama ADMIN değil.
set test.uid = '50000000-0000-0000-0000-0000000000a3';

do $$
declare
    sonuc uuid;
begin
    select public.auth_kullanici_id('a50-admin@salon.test') into sonuc;
    if sonuc is not null then
        raise exception 'Eğitmen kimlik sorgulayabildi: %', sonuc;
    end if;
    raise notice 'Eğitmen kimlik sorgulayamıyor — doğru.';
end
$$;

-- Müdür de sorgulayamıyor. Davet bir ADMIN işi; MANAGER fiyat listesi ve
-- günlük işi yönetiyor, kimin uygulamaya girebileceğini değil.
set test.uid = '50000000-0000-0000-0000-0000000000a2';

do $$
declare
    sonuc uuid;
begin
    select public.auth_kullanici_id('a50-admin@salon.test') into sonuc;
    if sonuc is not null then
        raise exception 'Müdür kimlik sorgulayabildi: %', sonuc;
    end if;
    raise notice 'Müdür kimlik sorgulayamıyor — doğru.';
end
$$;


-- ═══════════════════════════════════════════════════════════════════════════
-- auth_kullanici_id: ADMIN
-- ═══════════════════════════════════════════════════════════════════════════
set test.uid = '50000000-0000-0000-0000-0000000000a1';

do $$
declare
    sonuc uuid;
begin
    -- 1) Var olan hesabı buluyor.
    select public.auth_kullanici_id('a50-egitmen@salon.test') into sonuc;
    if sonuc is distinct from '50000000-0000-0000-0000-0000000000a3'::uuid then
        raise exception 'Yönetici var olan hesabı bulamadı: %', sonuc;
    end if;

    -- 2) Olmayan e-postada null — hata değil.
    --
    -- Davet akışı bu iki durumu ayırt etmek zorunda: null ise yeni hesap
    -- açılacak, doluysa var olan hesap bağlanacak. Olmayan e-postada hata
    -- fırlatsaydı normal davet yolu istisna üzerinden akardı.
    select public.auth_kullanici_id('hic-yok50@ornek.test') into sonuc;
    if sonuc is not null then
        raise exception 'Olmayan e-posta için kimlik döndü: %', sonuc;
    end if;

    -- 3) Büyük/küçük harf ve boşluk farkı önemli değil.
    --
    -- Yönetici e-postayı elle yazıyor. `Yeni.Personel@Ornek.TEST` ile
    -- ` yeni.personel@ornek.test ` aynı hesap; ayrı sayılsaydı davet
    -- "bu e-posta kayıtlı" hatasına düşer ve sebebi görünmezdi.
    select public.auth_kullanici_id('  YENI.personel50@ornek.test  ') into sonuc;
    if sonuc is distinct from '50000000-0000-0000-0000-0000000000d1'::uuid then
        raise exception 'Harf/boşluk normalleştirmesi çalışmıyor: %', sonuc;
    end if;

    raise notice 'Yönetici kimlik sorgulayabiliyor, normalleştirme çalışıyor — doğru.';
end
$$;

-- ─── Belirsizlik: aynı e-postayı taşıyan iki hesap ──────────────────────────
--
-- Fonksiyonun ilk yazımı `limit 1` idi ve bu durumda RASTGELE birini
-- döndürüyordu. Dönen kimlik `gym_users`a yazılıyor, yani salona yanlış kişi
-- alınabiliyordu — üstelik kimsenin fark edemeyeceği biçimde: ekranda "davet
-- edildi" yazardı.
--
-- Supabase normalde bir e-postaya tek hesap veriyor, ama bu fonksiyonun
-- doğruluğu buna BAĞLI OLMAMALI: varsayım bir gün değişirse (sağlayıcı başına
-- hesap, taşıma sırasında oluşan kopya) belirti bir yetki hatası olurdu.
-- Belirsizlikte `null` dönüyor ve kararı insana bırakıyoruz.
reset role;

insert into auth.users (id, email) values
    ('50000000-0000-0000-0000-0000000000e1', 'kopya50@ornek.test'),
    ('50000000-0000-0000-0000-0000000000e2', 'KOPYA50@ornek.test');

set role authenticated;
set test.uid = '50000000-0000-0000-0000-0000000000a1';

do $$
declare
    sonuc uuid;
begin
    select public.auth_kullanici_id('kopya50@ornek.test') into sonuc;
    if sonuc is not null then
        raise exception 'Belirsiz e-posta için kimlik döndü: % — yanlış hesap bağlanabilirdi', sonuc;
    end if;
    raise notice 'Aynı e-postalı iki hesapta kimlik dönmüyor — doğru.';
end
$$;


-- ═══════════════════════════════════════════════════════════════════════════
-- gym_users görüşü
-- ═══════════════════════════════════════════════════════════════════════════
do $$
declare
    gorunen integer;
    yabanci integer;
begin
    -- Yönetici KENDİ salonundaki üç bağlılığı da görüyor.
    select count(*) into gorunen
      from public.gym_users
     where gym_id = '50000000-0000-0000-0000-000000000001';
    if gorunen <> 3 then
        raise exception 'Yönetici kendi salonunda % satır görüyor, 3 bekleniyordu', gorunen;
    end if;

    -- Ama başka salonun satırlarını görmüyor.
    select count(*) into yabanci
      from public.gym_users
     where gym_id = '50000000-0000-0000-0000-000000000002';
    if yabanci <> 0 then
        raise exception 'Yönetici başka salonun % satırını görüyor', yabanci;
    end if;

    raise notice 'Yönetici yalnızca kendi salonunun erişimlerini görüyor — doğru.';
end
$$;

-- Eğitmen hâlâ YALNIZCA kendi satırını görüyor.
--
-- 0001'deki eski kural duruyor mu diye bakılıyor. Kaldırılmış olsaydı eğitmen
-- kendi satırını da okuyamaz, oturum açılışında rolünü çözemez ve uygulama
-- onu yetkisiz sanardı — davet özelliği eklerken günlük kullanımı bozmak.
set test.uid = '50000000-0000-0000-0000-0000000000a3';

do $$
declare
    gorunen integer;
    kendi integer;
begin
    select count(*) into gorunen from public.gym_users;
    select count(*) into kendi
      from public.gym_users
     where user_id = '50000000-0000-0000-0000-0000000000a3';

    if kendi <> 1 then
        raise exception 'Eğitmen kendi bağlılığını göremiyor — rolü çözülemez';
    end if;
    if gorunen <> 1 then
        raise exception 'Eğitmen % satır görüyor, yalnızca kendisininki bekleniyordu', gorunen;
    end if;

    raise notice 'Eğitmen yalnızca kendi bağlılığını görüyor — doğru.';
end
$$;

-- Anonim ziyaretçi tabloya HİÇ erişemiyor.
--
-- "0 satır dönüyor" iddiası burada yetersiz olurdu: satır bazlı güvenlik bir
-- gün gevşetilirse tablo yetkisi kapıyı sessizce açık bırakır. Aranan garanti,
-- yetkinin hiç olmaması — `anon` rolüne `gym_users` üzerinde `select` hiç
-- verilmedi (0002 yalnızca `authenticated`a verdi) ve 0007 de vermiyor.
-- 30 numaralı testteki desenin aynısı.
reset role;
set role anon;
set test.uid = '';

do $$
declare
    sayi integer;
begin
    select count(*) into sayi from public.gym_users;
    raise exception 'GÜVENLİK AÇIĞI: anonim gym_users tablosunu sorgulayabiliyor (% satır)', sayi;
exception
    when insufficient_privilege then
        raise notice 'Anonim gym_users tablosuna erişemiyor — doğru.';
end
$$;

reset role;
select 'Personel davet testi geçti.' as sonuc;
