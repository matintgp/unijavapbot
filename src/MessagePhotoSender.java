import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/**
 * کلاس ارسال پیام و عکس به تلگرام
 * این کلاس شامل متدهای ارتباط با API تلگرام است
 */
public class MessagePhotoSender {

    /**
     * ارسال k عکس از یک پوشه به کاربر
     * @param botToken توکن ربات
     * @param chatId شناسه چت کاربر
     * @param folderPath مسیر پوشه عکس‌ها
     * @param k تعداد عکس‌هایی که باید ارسال شود
     * @param start ایندکس شروع (از کدام عکس شروع کند)
     */
    public static void sendKPhotos(String botToken, Long chatId, String folderPath, int k, int start) {
        try {
            File folder = new File(folderPath);
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png"));

            if (files == null || files.length == 0) {
                System.out.println("هیچ عکسی در فولدر وجود ندارد!");
                return;
            }

            // مرتب‌سازی فایل‌ها بر اساس عدد داخل نام فایل
            // مثال: 1.jpg, 2.jpg, 10.jpg به ترتیب عددی مرتب می‌شوند
            Arrays.sort(files, Comparator.comparingInt(f -> {
                String name = f.getName()
                        .replace(".jpg", "")
                        .replace(".png", "")
                        .replaceAll("[^0-9]", ""); // فقط اعداد نگه داشته می‌شوند
                if (name.isEmpty()) return 0;
                return Integer.parseInt(name.trim());
            }));

            // محاسبه تعداد عکس‌هایی که باید ارسال شوند
            int end = Math.min(start + k, files.length);

            for (int i = start; i < end; i++) {
                sendPhoto(botToken, chatId, files[i]);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ارسال یک عکس به کاربر
     * از multipart/form-data برای آپلود فایل استفاده می‌کند
     * @param botToken توکن ربات
     * @param chatId شناسه چت کاربر
     * @param photoFile فایل عکس
     */
    public static void sendPhoto(String botToken, Long chatId, File photoFile) throws Exception {
        String urlString = "https://api.telegram.org/bot" + botToken + "/sendPhoto";
        URL url = URI.create(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        // ساخت boundary یکتا برای multipart
        String boundary = "===" + System.currentTimeMillis() + "===";
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        OutputStream outputStream = conn.getOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

        // اضافه کردن chat_id به فرم
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"chat_id\"").append("\r\n\r\n");
        writer.append(chatId.toString()).append("\r\n");

        // اضافه کردن فایل عکس به فرم
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"photo\"; filename=\"").append(photoFile.getName()).append("\"").append("\r\n");
        writer.append("Content-Type: image/jpeg").append("\r\n\r\n");
        writer.flush();

        // خواندن و ارسال محتوای فایل
        FileInputStream inputStream = new FileInputStream(photoFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
        inputStream.close();

        // پایان multipart
        writer.append("\r\n").flush();
        writer.append("--").append(boundary).append("--").append("\r\n");
        writer.close();

        int responseCode = conn.getResponseCode();
        System.out.println("ارسال " + photoFile.getName() + " به " + chatId + " با کد پاسخ: " + responseCode);
    }
    
    /**
     * ارسال پیام متنی به کاربر
     * @param botToken توکن ربات
     * @param chatId شناسه چت کاربر
     * @param message متن پیام
     */
    public static void sendMessage(String botToken, Long chatId, String message) {
        try {
            String urlString = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            URL url = URI.create(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

            // URL encode کردن پیام برای جلوگیری از مشکل کاراکترهای خاص
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
            String data = "chat_id=" + chatId + "&text=" + encodedMessage;
            
            OutputStream os = conn.getOutputStream();
            os.write(data.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                System.out.println("✅ پیام با موفقیت ارسال شد به " + chatId);
            } else {
                System.out.println("❌ خطا در ارسال پیام. کد: " + responseCode);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
