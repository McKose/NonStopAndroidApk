-- ---------------------------------------------------------------------------
-- İletişim / deneme dersi istekleri
-- ---------------------------------------------------------------------------
-- Siteden gelen "deneme dersi istiyorum" formunun gittiği yer. Planın ana
-- dönüşüm noktasıydı (`docs/site-plani-kalanlar.md` md. 2) ve formun nereye
-- yazacağı salon sahibinin kararı: Supabase.
--
-- ### FORMU DOLDURAN KİŞİNİN HESABI YOK — ve tasarımı belirleyen şey bu
--
-- Depodaki öteki istek tablosu (`member_link_requests`, `0006`) benzer
-- görünüyor ama ÖNEMLİ BİR YERDE AYRILIYOR: onu yazan `authenticated`, çünkü
-- üye o noktada zaten hesap açmış oluyor. Buradaki ziyaretçinin hesabı yok ve
-- olmasını istemek formun bütün anlamını yok eder.
--
-- Akla gelen ilk çözüm `anon` rolüne `insert` kuralı yazmak. YAZILMADI:
--
--   - `anon` YAYINLANMIŞ anahtarla çalışıyor. Anahtar uygulamanın içinde ve
--     panelin kaynağında, yani herkesin elinde. Tabloya `insert` açmak,
--     "internetteki herkes bu tabloya istediği hızda satır yazabilir"
--     demek.
--   - Sızacak veri yok ama ZARAR VAR: bir betik milyonlarca satır yazıp
--     salonun Supabase kotasını tüketir ve gerçek talepleri gömer.
--   - Tablo kuralı hız sınırı KOYAMIYOR. `with check` yalnız satıra bakıyor;
--     "bu kaynaktan son bir saatte kaç satır geldi" sorusunu soramıyor.
--
-- Bu yüzden yazma işi `iletisim-istegi` Edge Function'ında ve `service_role`
-- ile yapılıyor. Gerekçe `personel-erisim-kaldir`ın kendi gerekçesiyle
-- birebir aynı: "anahtar sunucuda kalıyor, istemciye hiç gitmiyor."
-- Fonksiyon ayrıca doğrulama ve hız sınırı yapıyor — tablo kuralının
-- yapamadığı şeyler.
--
-- ### Bu tabloda `insert` KURALI YOK ve bu bir eksiklik değil
--
-- Satır bazlı güvenlik altında kuralı olmayan bir işlem tamamen reddedilir.
-- `service_role` ise satır bazlı güvenliği baypas ediyor, yani yazabilen tek
-- yol o — tam olarak istenen. `0001`de salon oluşturma için kullanılan desenin
-- aynısı ("kural yoksa yazma reddedilir").
--
-- ### `anon`a HİÇBİR yetki verilmiyor
--
-- `0005` bu kuralı zaten koymuş: "`anon` YALNIZCA duyuruları okuyor. Başka
-- hiçbir tabloya, hiçbir yetki." Bu tablo o kurala uyuyor. (Canlıda Supabase
-- varsayılanları yüzünden `anon`un bütün tablolarda yetkisi olduğu
-- biliniyor; gerekçesi ve neden tek seferde temizlenmediği
-- `supabase/README.md` → "Henüz yapılmadı" içinde. Bu dosya o açığı
-- büyütmüyor: aşağıda `anon`dan açıkça geri alınıyor.)
-- ---------------------------------------------------------------------------

create table if not exists public.contact_requests (
    -- Kimliği SUNUCU üretiyor. Öteki tablolarda `id text primary key` ve değeri
    -- istemci veriyor (uygulamanın kendi ürettiği uuid); burada istemci
    -- güvenilmez bir ziyaretçi, o yüzden kimliği ona bırakmak yanlış olurdu —
    -- aynı kimliği gönderip başkasının satırını ezmeye çalışabilirdi.
    id            uuid primary key default gen_random_uuid(),
    tenant_id     uuid not null references public.gyms (id) on delete cascade,

    -- UZUNLUK KISITLARI TABLODA, yalnız fonksiyonda değil. Fonksiyon da
    -- doğruluyor ama o İSTEMCİYE DÖNEN mesajı üretmek için; buradakiler son
    -- durak. Fonksiyonda bir doğrulama atlanırsa ya da ileride başka bir yol
    -- (panel, içe aktarma) yazmaya başlarsa, satır boyu yine bağlı kalıyor.
    -- Sınırsız `text` bırakmak, tek satırla gigabayt yazılabilmesi demekti.
    full_name     text not null check (length(full_name) between 2 and 80),
    phone         text not null check (length(phone) between 7 and 20),
    email         text          check (email is null or length(email) <= 120),
    message       text          check (message is null or length(message) <= 500),

    -- Hangi kanaldan geldi. Bugün tek değer var; kolon yine duruyor çünkü
    -- telefonla gelen bir talebi panelden girmek istenirse ayırt edilmesi
    -- gerekiyor ve sonradan kolon eklemek veri taşımayı gerektirir.
    source        text not null default 'WEB' check (source in ('WEB', 'PHONE', 'WALKIN')),

    -- İşin takibi: yeni → arandı → kapandı. Mezar taşı yok, çünkü bu tablo
    -- geçmiş satışlara bağlı değil; gerçekten silinmesi gereken bir satır
    -- (ör. spam) silinebilmeli. `delete` yetkisi yalnız `service_role`de
    -- (bkz. `0008`), yani silme panelden değil sunucudan geçiyor.
    state         text not null default 'NEW' check (state in ('NEW', 'CONTACTED', 'CLOSED')),
    -- Personelin not alması için. Ziyaretçinin yazdığı `message`dan AYRI
    -- tutuluyor: ikisi aynı kolona yazılsaydı ziyaretçinin ne dediği
    -- kaybolurdu.
    staff_note    text          check (staff_note is null or length(staff_note) <= 500),

    created_at_ms bigint not null,
    updated_at_ms bigint not null
);

-- Panelin listesi için: salon + durum + tarih.
create index if not exists contact_requests_tenant_idx
    on public.contact_requests (tenant_id, state, created_at_ms);

-- Edge Function'ın MÜKERRER BASTIRMA sorgusu için: "bu salonda bu telefondan
-- son N dakikada istek var mı". Yukarıdaki indeks telefonu kapsamadığı için
-- ayrı duruyor; onsuz sorgu tabloyu baştan sona tarardı ve tablo büyüdükçe
-- her form gönderimi yavaşlardı.
create index if not exists contact_requests_phone_idx
    on public.contact_requests (tenant_id, phone, created_at_ms);

alter table public.contact_requests enable row level security;

-- `create policy`nin `if not exists` biçimi yok; migrasyon tekrar
-- çalıştırılabilir olmalı (bkz. `0001`de yaşanan "already exists" hatası).
drop policy if exists contact_requests_staff_select on public.contact_requests;
drop policy if exists contact_requests_staff_update on public.contact_requests;

-- Personel kendi salonunun taleplerini görüyor. Seviye `link_requests_staff_select`
-- ile aynı: talebi gören kişi onu arayacak kişi, yani eğitmen de görebilmeli.
create policy contact_requests_staff_select on public.contact_requests
    for select
    to authenticated
    using (tenant_id in (select public.user_gym_ids()));

-- Durumu değiştirmek ve not almak: ADMIN + MANAGER.
-- `link_requests_staff_update` ile aynı seviye — "bu talep kapandı" demek bir
-- yönetim kararı.
create policy contact_requests_staff_update on public.contact_requests
    for update
    to authenticated
    using (
        tenant_id in (select public.user_gym_ids_with_role(array['ADMIN', 'MANAGER']))
    )
    with check (
        tenant_id in (select public.user_gym_ids_with_role(array['ADMIN', 'MANAGER']))
    );

-- `insert` YOK (yukarıdaki gerekçe) — yazma yalnız `service_role` üzerinden.
grant select, update on public.contact_requests to authenticated;

-- `authenticated`ten `insert` ve `delete` AÇIKÇA geri alınıyor.
--
-- Yukarıdaki `grant select, update` bunu KENDİLİĞİNDEN yapmıyor: `grant` yalnız
-- ekliyor. Boş bir PostgreSQL'de fark yok (verilmeyen yetki yoktur) ama canlıda
-- Supabase her yeni tabloyu üç role BÜTÜN ayrıcalıklarla açıyor, yani
-- `authenticated` bu satır olmadan `insert` yetkisini korur.
--
-- Satırın yokluğu bugün bir açık açmazdı — `insert` KURALI olmadığı için yazma
-- yine reddedilirdi. Ama o zaman tabloya yazılamamasının sebebi iki ortamda
-- AYRI olurdu (boş veritabanında yetki yokluğu, canlıda kural yokluğu) ve
-- geniş bir kural yazan biri canlıda sessizce yazmayı açardı. İki katmanın
-- aynı şeyi söylemesi bu yüzden önemli; `60_silme_yetkisi_test.sql`in başında
-- anlatılan ayrımın aynısı.
revoke insert, delete on public.contact_requests from authenticated;

-- `anon`dan açıkça geri alınıyor. Boş bir PostgreSQL'de `anon`un bu tabloda
-- hiç yetkisi yok, yani bu satır orada etkisiz; CANLIDA ise Supabase her yeni
-- tabloyu üç role bütün ayrıcalıklarla açtığı için GEREKLİ. Yazılmasaydı tablo
-- oluştuğu anda `anon` ona yazabilir hâle gelir ve bu dosyanın bütün gerekçesi
-- boşa çıkardı — `0004`/`0008`/`0009`da üç kez yaşanan hatanın aynısı.
revoke all on public.contact_requests from anon;

-- `service_role`: yazan taraf. Satır bazlı güvenliği baypas ediyor ama TABLO
-- yetkisi ayrı bir katman ve onsuz baypas bir şey ifade etmiyor.
grant select, insert, update, delete on public.contact_requests to service_role;
