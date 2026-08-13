-- ---------------------------------------------------------------------------
-- Personel kaydı ile Supabase Auth hesabının bağlanması
-- ---------------------------------------------------------------------------
-- Kimlik doğrulama Supabase Auth'a taşındı: giriş yapan kişiden elde edilen tek
-- kimlik `auth.users.id`. Uygulamanın buna ihtiyacı var, çünkü randevu ve
-- hakediş kayıtları yerel `staff.id` değerine bakıyor. İki kimlik arasında bir
-- köprü olmadan "bugün benim derslerim" ya da "bu ayki hakedişim" soruları
-- yanıtlanamaz.
--
-- Kolon **nullable**: bir personel kaydı, hesabı açılmadan önce de var olabilir
-- (önce personel eklenir, sonra hesap açılır). Zorunlu olsaydı bu sıra
-- imkânsızlaşırdı.
--
-- `gym_users` ile karıştırılmamalı: o tablo "bu kullanıcı hangi salona bağlı ve
-- hangi rolde" diyor ve **erişim kurallarının** dayanağı. Buradaki kolon ise
-- "bu personel satırı hangi hesaba ait" diyor ve yalnızca uygulama içi
-- ilişkilendirme için. İkisini tek tabloda birleştirmek, erişim kurallarını
-- uygulamanın veri tablolarından birine bağlamak olurdu.
-- ---------------------------------------------------------------------------

alter table public.staff
    add column if not exists auth_user_id uuid references auth.users (id) on delete set null;

comment on column public.staff.auth_user_id is
    'Bu personelin Supabase Auth hesabı. null = hesap henüz bağlanmamış.';

-- Bir hesap yalnızca tek bir personel kaydına bağlanabilir.
--
-- Olmasaydı iki personel satırı aynı hesabı gösterebilir ve giriş yapan kişinin
-- hangisi olduğu belirsizleşirdi; randevular ve hakedişler sessizce yanlış
-- kişiye yazılırdı. Kısmi indeks: `null` değerler tekillik dışında kalıyor,
-- yoksa hesabı bağlanmamış ikinci personel eklenemezdi.
create unique index if not exists staff_auth_user_id_key
    on public.staff (auth_user_id)
    where auth_user_id is not null;
