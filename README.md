# CyberTube

แอป YouTube สำหรับ Android — UI ใหม่ + ระบบเล่นเขียนเอง (Compose + Media3)
ใช้ **NewPipeExtractor เฉพาะ YouTube** เป็นตัวดึงข้อมูล ไม่มีบริการอื่น

## ฟีเจอร์ v0.1
- ค้นหา (พร้อม suggestion) + เลื่อนโหลดต่อ
- เล่นวิดีโอ: เลือกคุณภาพ (รวมวิดีโอ+เสียงแยกสตรีมถึง 1080p+), ความเร็ว, แตะสองครั้งข้าม ±10 วิ, เต็มจอ
- เล่นเบื้องหลัง / เฉพาะเสียง (ปุ่มหูฟัง) พร้อมแจ้งเตือนควบคุมเพลง, มินิเพลเยอร์
- สมัครติดตามช่อง (เก็บในเครื่อง ไม่ต้องล็อกอิน) + ฟีดวิดีโอล่าสุด
- ประวัติการดู

## v0.2.0 (เฟส 1)
- แก้ player ชนแถบสถานะ Android, เมนูล่างอยู่ทุกหน้า (รวมหน้าเล่นวิดีโอ) และเล็กลง
- seek bar บางลง + แสดงส่วนที่โหลดล่วงหน้า, buffer 30–120 วิ
- หน้า Settings: ความคมชัดเริ่มต้น, About (ผู้สร้าง Tum), open source licenses
- แก้ description ที่แสดงแท็ก HTML และวันที่ที่แสดงเป็น ISO

## โครงสร้าง
- `core/` YtDownloader (OkHttp), YtRepo (search/stream/channel), Store (subs/history)
- `player/` PlayerHolder (ExoPlayer ตัวเดียวทั้งแอป), PlaybackService (MediaSession), datasource/YoutubeHttpDataSource (คัดจาก NewPipe)
- `potoken/` PoToken provider (คัดจาก NewPipe — จำเป็นให้ YouTube เล่นได้เสถียร)
- `ui/` Theme (Cyber), Components, Screens, PlayerScreen

## Build
push ขึ้น GitHub → Actions → artifact `CyberTube-APK`

## License
โค้ดส่วนที่คัดจาก NewPipe และ NewPipeExtractor เป็น GPL-3.0 → แอปนี้ต้องเผยแพร่ภายใต้ GPL-3.0 ด้วย
(ใช้ส่วนตัวไม่มีปัญหา)
