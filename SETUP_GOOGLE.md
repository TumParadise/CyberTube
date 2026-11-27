# ตั้งค่า Google Cloud สำหรับปุ่ม "เชื่อมต่อบัญชี Google" (ทำครั้งเดียว)

ทำที่ console.cloud.google.com (ใช้บัญชี Google ของคุณเอง)

1. สร้างโปรเจกต์ใหม่ เช่น `CyberTube`
2. APIs & Services → Library → เปิดใช้ **YouTube Data API v3**
3. APIs & Services → OAuth consent screen
   - User type: External, ใส่ชื่อแอป + อีเมลติดต่อ
   - เพิ่ม scope: `.../auth/youtube.readonly`
   - สถานะ **Testing** แล้วเพิ่มอีเมลของคุณ (และคนในครอบครัวที่จะใช้) ใน **Test users**
     (แอปส่วนตัวไม่ต้องส่งตรวจสอบ แต่จำกัดผู้ทดสอบสูงสุด 100 คน และสิทธิ์ของโหมด Testing อาจหมดอายุใน 7 วัน ต้องกดเชื่อมต่อใหม่)
4. APIs & Services → Credentials → Create credentials → **OAuth client ID**
   - Application type: **Android**
   - Package name: `com.cybertube.app`
   - SHA-1: `13:15:0F:22:9B:2C:B6:4A:A6:5B:C6:08:B0:3A:B0:BB:47:1F:86:C6`
     (SHA-1 ของ `app/cybertube-debug.keystore` ตรวจซ้ำได้ด้วย
     `keytool -list -v -keystore app/cybertube-debug.keystore -storepass android`)
5. รอสักครู่ (บางครั้งนานถึงไม่กี่ชั่วโมง) แล้วเปิดแอป → ตั้งค่า → เชื่อมต่อบัญชี Google

ไม่ต้องใส่ Client ID ในโค้ด Google จับคู่จาก package name + SHA-1 ของ APK เอง

## ถ้าขึ้นรหัส 10
package name หรือ SHA-1 ใน Console ไม่ตรงกับ APK ที่ติดตั้ง (ตรวจว่า APK มาจาก build ที่ใช้คีย์ `cybertube-debug.keystore`)

## หมายเหตุ
- ติดตั้ง APK นี้ครั้งแรกต้องลบแอปเก่าก่อน เพราะเปลี่ยนคีย์ลงนาม
- บน microG: การรองรับ Authorization API ขึ้นกับเวอร์ชัน microG ถ้าเชื่อมไม่ได้ให้ลองบนเครื่องที่มี Google Play Services ก่อนเพื่อแยกสาเหตุ
