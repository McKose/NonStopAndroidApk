-- ---------------------------------------------------------------------------
-- `delete` yetkisi: istemci rollerinde kapalı, `service_role`da açık
-- ---------------------------------------------------------------------------
-- Diğer testler silmeyi DAVRANIŞ üzerinden sınıyor: `delete` denenir, hata
-- beklenir (bkz. `20_role_test.sql`). O testler değerli ama neyin engellediğini
-- ayırt etmiyorlar — yetki mi, RLS mi. İkisi arasındaki fark önemli, çünkü
-- RLS bir kural yazımıyla kaybolabiliyor: geniş bir `for all` kuralı (`for all`
-- `delete`i de kapsıyor) yazan biri silmeyi sessizce açar ve davranış testi
-- yine geçer — yeni kural silmeye izin verdiği için değil, o testteki
-- kullanıcının kuralın kapsamına girmemesi ihtimali yüzünden.
--
-- Bu dosya bir alt katmana bakıyor: TABLO YETKİSİNİN kendisi. Yetki yoksa RLS
-- hiç devreye girmiyor ve hiçbir kural yazımı bunu geri açamıyor.
--
-- Sınanan karar `0004` + `0008`:
--   - `authenticated` ve `anon`: `delete` YOK (uygulama zaten göndermiyor;
--     silme mezar taşıyla yapılıyor, yani sunucuya giden şey bir `update`)
--   - `service_role`: `delete` VAR (erişimi geri alma akışı yazıldığında
--     `gym_users` satırını silecek olan o)
--
-- `anon` ayrıca sınanıyor çünkü o rol YAYINLANMIŞ anahtarla çalışıyor: anahtar
-- uygulamanın içinde ve panelin kaynağında, yani herkesin elinde. `0004`
-- yalnızca `authenticated`ten almıştı; `anon`daki açık `0008`e kadar durdu.
-- ---------------------------------------------------------------------------

-- ÖNEMLİ: buradaki her iddia HEM boş bir PostgreSQL'de (bu testin koştuğu yer)
-- HEM de canlı Supabase projesinde doğru olmak zorunda. Supabase her yeni
-- tabloyu üç role bütün ayrıcalıklarla açıyor; bu depodaki migrasyonlar ise
-- boş bir veritabanına uygulanıyor. İkisinde ayrışan bir iddia, testi burada
-- yeşil tutup üretim hakkında yanlış şey söylerdi — nitekim bu dosyanın ilk
-- yazımında oldu: "`service_role` her tabloda silebilir" iddiası canlıda
-- doğruydu (varsayılan yüzünden), boş veritabanında değildi. Sonuç `0008`e bir
-- `grant` eklemek oldu; iddia da yalnızca o `grant`in kapsadığı tabloya indi.
do $$
declare
    -- Kiracıya bağlı veri tabloları — YALNIZCA mezar taşı kontrolü için elle
    -- sayılıyor. "Hangi tabloya `update` gerekli" bir ürün kararı ve katalogdan
    -- okunamıyor: `gyms` ya da `member_link_requests` için aynı şey doğru değil.
    veri_tablolari text[] := array[
        'gym_members', 'appointments', 'orders', 'measurements',
        'ledger_entries', 'stock_movements', 'gym_packages', 'products',
        'staff'
    ];
    t text;
    r text;
    tablo record;
begin
    -- ── İstemci rolleri HİÇBİR tabloda silemez ──────────────────────────────
    -- Tablolar katalogdan okunuyor, elle sayılmıyor. `0008` ilk yazımında altı
    -- tabloyu atlamıştı çünkü listesi `0004`ten kopyalanmıştı; elle sayılan bir
    -- liste burada aynı hatayı sessizce tekrarlardı — atlanan tablo test
    -- edilmediği için takım yeşil kalırdı. Böylece şemaya eklenen her yeni
    -- tablo da kendiliğinden kapsama giriyor.
    for tablo in select tablename from pg_tables where schemaname = 'public' loop
        foreach r in array array['anon', 'authenticated'] loop
            if has_table_privilege(r, 'public.' || tablo.tablename, 'delete') then
                raise exception
                    'GÜVENLİK AÇIĞI: %, public.% tablosunda delete yetkisi tutuyor',
                    r, tablo.tablename;
            end if;
        end loop;
    end loop;

    -- ── Mezar taşı yolu açık kalmalı ────────────────────────────────────────
    -- Silme yetkisini almak, silmenin GERÇEK karşılığını da kapatsaydı uygulama
    -- hiçbir kaydı kaldıramazdı. Bu döngü olmadan test, her şeyi kapatan bir
    -- migrasyonu da "geçti" sayardı.
    foreach t in array veri_tablolari loop
        if not has_table_privilege('authenticated', 'public.' || t, 'update') then
            raise exception
                'public.% üzerinde authenticated update edemiyor; mezar taşı yazılamaz', t;
        end if;
    end loop;

    -- ── Erişimi geri alma akışının ihtiyacı ─────────────────────────────────
    -- Yalnızca `gym_users`: `0008` yetkiyi açıkça yalnızca orada veriyor.
    -- Diğer tablolarda `service_role`ün silebilmesi canlıda Supabase'in
    -- varsayılanından geliyor, bu depodan değil — dolayısıyla burada iddia
    -- edilemez.
    if not has_table_privilege('service_role', 'public.gym_users', 'delete') then
        raise exception
            'service_role gym_users satırı silemiyor; erişim geri alma akışı yazılamaz';
    end if;

    raise notice 'delete yetkisi istemci rollerinde kapalı, service_role gym_users''ta açık — doğru.';
end
$$;

-- ─── RLS tarafı: hiçbir tabloda delete kuralı olmamalı ─────────────────────
-- Yetki katmanı yukarıda kapatıldı, bu ikinci katman. `for all` ayrıca
-- aranıyor: `create policy ... for all` yazan biri `delete`i de açtığını
-- fark etmeyebiliyor ve `cmd` sütununda bu `ALL` görünüyor, `DELETE` değil —
-- yalnızca `DELETE` arayan bir kontrol onu kaçırırdı.
do $$
declare
    kural record;
begin
    for kural in
        select tablename, policyname, cmd
          from pg_policies
         where schemaname = 'public'
           and cmd in ('DELETE', 'ALL')
    loop
        raise exception
            'GÜVENLİK AÇIĞI: public.% üzerinde % kuralı silmeyi kapsıyor (cmd=%)',
            kural.tablename, kural.policyname, kural.cmd;
    end loop;

    raise notice 'Hiçbir tabloda silmeyi kapsayan kural yok — doğru.';
end
$$;

select 'Silme yetkisi testi geçti.' as sonuc;
