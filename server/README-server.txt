سيرفر tajmi3 (تسجيل دخول جوجل + مزامنة البيانات)
================================================

التركيب على اللينكس:
  git clone https://github.com/hosni12gg-create/tajmi3-android
  cd tajmi3-android/server
  ./install.sh

بعدين الوصول من برّا (Tailscale Funnel، مجاني، بدون دومين):
  curl -fsSL https://tailscale.com/install.sh | sh
  sudo tailscale up
  sudo tailscale funnel --bg 8787
بيطلعلك عنوان https://....ts.net  ← هاد اللي بنحطه بالتطبيق.

الإيميلات المسموحة:  nano .env  ثم  sudo systemctl restart tajmi3
حالة السيرفر:       sudo systemctl status tajmi3
السجل:              journalctl -u tajmi3 -f
تحديث السيرفر:      git pull && sudo systemctl restart tajmi3
النسخ الاحتياطي:     يومياً الساعة 3:30 بمجلد backups (آخر 14 نسخة)

الأمان:
- السيرفر بيتحقق من توقيع جوجل بنفسه، ومن إن التوكن لتطبيقك، ومن إيميلك بالقائمة.
- مفتاح الجلسات بيتولّد لحاله بـ data/session.key (لا ترفعه ولا تشاركه).
