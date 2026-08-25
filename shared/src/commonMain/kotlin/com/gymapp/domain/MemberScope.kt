package com.gymapp.domain

/**
 * Üye listesinin kapsamı.
 *
 * Eğitmen için varsayılan [MINE]: panonun sayaçları baştan beri "kendi üyelerim"
 * diyordu, liste ise salonun tamamını gösteriyordu. Aynı ekranda iki farklı
 * cevap veren bir uygulamada hangisinin doğru olduğu anlaşılmıyor.
 *
 * Seçim **görünür**, çünkü sessiz süzme bu ekranda gerçek bir işi bozardı:
 * eğitmenin yeni kaydettiği üyenin henüz randevusu yok, yani "benim üyem"
 * sayılmaz ve listeden kaybolurdu. Kullanıcı da onu bir daha bulamazdı.
 * Yönetici rollerinde seçim hiç gösterilmiyor; onlar için kapsam zaten salon.
 */
enum class MemberScope { MINE, ALL }
