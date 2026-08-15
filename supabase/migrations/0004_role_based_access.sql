-- ---------------------------------------------------------------------------
-- Role dayalı yazma yetkisi
-- ---------------------------------------------------------------------------
-- 0002'deki kural tek cümleydi: "salona bağlı olan her şeyi yapabilir". Salon
-- yalıtımı için doğruydu ama salon **içinde** hiçbir ayrım yoktu — yeni işe
-- başlamış bir eğitmenin telefonu, salonun fiyat listesini değiştirebilir,
-- personel kaydı ekleyebilir, satırları kalıcı olarak silebilirdi.
--
-- Burada iki ayrı katman geliyor:
--
--   1. DELETE tamamen kapatılıyor (aşağıda, "Silme" bölümü).
--   2. Yazma (insert/update) tabloya göre role bağlanıyor.
--
-- Okuma bilinçli olarak değişmiyor: salona bağlı olan salonun verisini görür.
-- Okumayı role göre bölmek, uygulamanın ekranlarını (üye listesi, randevu
-- takvimi, satış geçmişi) role göre bölmek demekti; oysa bir eğitmenin randevu
-- yazabilmesi için üye listesini görmesi zaten gerekiyor.
--
-- ### Uygulama sırası
-- Bu dosya 0002'nin kurduğu kuralların ÜZERİNE yazıyor: `_tenant_access`
-- politikasını siliyor ve DELETE yetkisini geri alıyor. Migrasyonlar sırayla
-- (ve gerektiğinde tekrar tekrar) uygulandığı için 0002 her koşuda kuralı
-- yeniden kuruyor, bu dosya da her koşuda üzerine yazıyor. Sonuç sırayla
-- deterministik. Alternatif — 0002'yi düzenlemek — kullanıcının sunucusunda
-- çoktan uygulanmış bir dosyayı değiştirmek olurdu.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- Rolün sorgulanması
-- ---------------------------------------------------------------------------
-- `user_gym_ids()` ile aynı gerekçelerle `security definer` ve sabit arama yolu:
-- kural bu fonksiyonu çağırıyor, fonksiyon `gym_users`'ı okuyor ve o tabloda da
-- satır bazlı güvenlik açık — normal bir fonksiyon kendi kuralını tetikleyip
-- sonsuz özyinelemeye girerdi.
--
-- Ayrı bir fonksiyon; `user_gym_ids()` aşırı yüklenmedi. İki imzalı bir isim,
-- bir kuralda parametreyi yazmayı unutunca sessizce "kısıtsız" hâle düşerdi —
-- yani en tehlikeli yazım hatası derlenirdi.
create or replace function public.user_gym_ids_with_role(allowed text[])
returns setof uuid
language sql
stable
security definer
set search_path = public
as $$
    select gym_id
      from public.gym_users
     where user_id = auth.uid()
       and role = any(allowed)
$$;

comment on function public.user_gym_ids_with_role(text[]) is
    'Kullanıcının, verilen rollerden birine sahip olduğu salonlar. Yazma kuralları buna dayanır.';

grant execute on function public.user_gym_ids_with_role(text[]) to authenticated;

-- ---------------------------------------------------------------------------
-- Tablo başına yazma rolleri
-- ---------------------------------------------------------------------------
-- `staff` yalnızca ADMIN'e açık. Personel kaydı kimin hangi salonda ne iş
-- yaptığını, hakediş oranını ve hangi hesaba bağlı olduğunu taşıyor; bunları
-- herkesin değiştirebilmesi, maaş ve prim verisini salondaki herkese açmak
-- olurdu.
--
-- `gym_packages` ve `products` ADMIN + MANAGER'a açık: fiyat listesi. Bir
-- eğitmenin paket fiyatını değiştirebilmesi için sebep yok, satış yapabilmesi
-- için gerek de yok — satış `orders` tarafında.
--
-- Kalan tablolar günlük işin kendisi: üye kaydı, randevu, satış, ölçüm, kasa
-- hareketi, stok. Eğitmen bunları yapamazsa uygulama işe yaramaz.
do $$
declare
    t text;
    yazabilen text[];
    hepsi constant text[] := array['ADMIN', 'MANAGER', 'TRAINER'];
begin
    foreach t in array array[
        'gym_packages', 'staff', 'gym_members', 'products',
        'appointments', 'orders', 'measurements',
        'ledger_entries', 'stock_movements'
    ]
    loop
        yazabilen := case t
            when 'staff'        then array['ADMIN']
            when 'gym_packages' then array['ADMIN', 'MANAGER']
            when 'products'     then array['ADMIN', 'MANAGER']
            else hepsi
        end;

        -- 0002'nin tek parçalı kuralı kaldırılıyor. `for all` olduğu için
        -- bırakılsaydı aşağıdaki kurallarla YAN YANA çalışırdı: politikalar
        -- VEYA'lanır, yani eski kural tek başına her yazmaya izin vermeye
        -- devam eder ve bu dosya hiçbir şey değiştirmemiş olurdu.
        execute format('drop policy if exists %I on public.%I', t || '_tenant_access', t);

        execute format('drop policy if exists %I on public.%I', t || '_select', t);
        execute format('drop policy if exists %I on public.%I', t || '_insert', t);
        execute format('drop policy if exists %I on public.%I', t || '_update', t);

        -- Okuma: salona bağlı olan görür (0002 ile aynı).
        execute format(
            'create policy %I on public.%I for select
                 to authenticated
                 using (tenant_id in (select public.user_gym_ids()))',
            t || '_select', t
        );

        -- Ekleme: yalnızca yetkili rol, yalnızca kendi salonuna.
        execute format(
            'create policy %I on public.%I for insert
                 to authenticated
                 with check (tenant_id in (select public.user_gym_ids_with_role(%L)))',
            t || '_insert', t, yazabilen
        );

        -- Güncelleme. `using` ve `with check` ikisi de gerekli ve farklı şeyler
        -- söylüyor: `using` HANGİ satırın hedeflenebileceğini, `with check`
        -- satırın yazıldıktan SONRA neye benzeyebileceğini. `with check`
        -- olmasaydı yetkili bir kullanıcı kendi salonundaki satırın
        -- `tenant_id`'sini başka salona çevirebilir, yani satırı karşı salona
        -- taşıyabilirdi.
        execute format(
            'create policy %I on public.%I for update
                 to authenticated
                 using (tenant_id in (select public.user_gym_ids_with_role(%L)))
                 with check (tenant_id in (select public.user_gym_ids_with_role(%L)))',
            t || '_update', t, yazabilen, yazabilen
        );

        -- ─── Silme ───────────────────────────────────────────────────────────
        -- Hiçbir role DELETE verilmiyor ve DELETE politikası yok.
        --
        -- Uygulama zaten hiç DELETE göndermiyor: silme mezar taşıyla yapılıyor
        -- (`deleted_at_ms` dolduruluyor), yani sunucuya giden şey bir UPDATE.
        -- Senkronizasyon istemcisi de yalnızca POST (upsert) ve GET kullanıyor.
        -- Dolayısıyla yetkiyi geri almak işlevsel olarak hiçbir şeye mal olmuyor
        -- ama kalıcı veri kaybı sınıfını tamamen kapatıyor: ele geçirilmiş bir
        -- jetonla bile satır silinemez, en fazla mezar taşı konur — ve mezar
        -- taşı geri alınabilir, silinen satır alınamaz.
        --
        -- 0002 `delete` yetkisini vermişti; burada geri alınıyor.
        execute format('revoke delete on public.%I from authenticated', t);
        execute format('grant select, insert, update on public.%I to authenticated', t);
    end loop;
end
$$;
