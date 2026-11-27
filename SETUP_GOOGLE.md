# ตั้งค่า Google Cloud สำหรับปุ่ม "เชื่อมต่อบัญชี Google" (ทำครั้งเดียว)

แอปใช้ OAuth Device Code Flow จึงทำงานได้กับ microG (ไม่เรียก Google Play Services เลย)
ทำที่ console.cloud.google.com ในโปรเจกต์ที่สร้างไว้

1. APIs & Services → Library → เปิดใช้ **YouTube Data API v3**
2. APIs & Services → OAuth consent screen (หน้า Branding / Audience)
   - User type: External ใส่ชื่อแอป + อีเมลติดต่อ
   - Data access: เพิ่ม scope `youtube.readonly`
   - Audience: เพิ่มอีเมลที่จะใช้ล็อกอินใน **Test users**
3. Credentials / Clients → Create client → Application type: **TVs and Limited Input devices**
   - คัดลอก **Client ID** และ **Client Secret**
4. เปิดแอป → ตั้งค่า → บัญชี YouTube → วาง Client ID / Secret → บันทึก → "เชื่อมต่อบัญชี Google"
5. แอปแสดงรหัส → เปิด google.com/device (มือถือหรือคอมก็ได้) → ใส่รหัส → เลือกบัญชี → อนุญาต
   (หน้าจะเตือนว่า Google ยังไม่ได้ตรวจสอบแอป ให้กดดำเนินการต่อ เพราะเป็นแอปของคุณเอง)

## ข้อควรรู้
- สถานะ **Testing**: Google ให้ refresh token อายุประมาณ 7 วัน ต้องเชื่อมต่อใหม่ทุกสัปดาห์
  ถ้าใช้ส่วนตัวและไม่อยากเชื่อมซ้ำ ให้เปลี่ยน Publishing status เป็น In production
  (ยังขึ้นหน้าเตือน "ยังไม่ได้ตรวจสอบ" และจำกัดผู้ใช้ 100 คน แต่ใช้ได้โดยไม่ต้องส่งตรวจสอบ)
- client ประเภท Android ที่สร้างไว้ก่อนหน้านี้ ไม่ได้ใช้แล้ว ลบทิ้งได้
- Client Secret ของ client ประเภทนี้ Google ถือว่าไม่ใช่ความลับจริง แต่ก็อย่าแชร์สาธารณะ
- ติดตั้ง APK นี้ครั้งแรกต้องลบแอปเก่าก่อน เพราะเปลี่ยนคีย์ลงนาม

## ถ้าขึ้นข้อผิดพลาด
- "Client ID/Secret ไม่ถูกต้อง": สร้าง client ผิดประเภท (ต้องเป็น TVs and Limited Input devices) หรือคัดลอกไม่ครบ
- "คุณปฏิเสธ... หรือยังไม่ได้เพิ่มเป็น Test user": เพิ่มอีเมลใน Test users
- 403 ตอนนำเข้า: ยังไม่ได้เปิด YouTube Data API v3 ในโปรเจกต์เดียวกับ client
