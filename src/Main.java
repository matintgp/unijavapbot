import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * کلاس اصلی ربات تلگرام
 * این کلاس شامل حلقه اصلی polling و مدیریت دستورات است
 */
public class Main {
    // توکن ربات تلگرام - از BotFather دریافت می‌شود
    static String botToken = "ur_bot_token";
    
    // ذخیره chat_id های شناخته شده برای ارسال اعلان آنلاین شدن
    static Set<Long> knownChatIds = new HashSet<>();

    public static void main(String[] args) {
        System.out.println("🤖 ربات در حال راه‌اندازی...");
        
        // ابتدا همه chat_id های موجود را از آپدیت‌ها جمع‌آوری می‌کنیم
        collectAllChatIds();
        
        // اعلان آنلاین شدن ربات به همه کاربران شناخته شده
        notifyBotOnline();
        
        // شروع polling برای دریافت دستورات
        System.out.println("🔄 در حال گوش دادن به دستورات...");
        startPolling();
    }
    
    /**
     * جمع‌آوری همه chat_id های موجود از آپدیت‌ها
     * این متد یکبار در شروع برنامه اجرا می‌شود
     */
    public static void collectAllChatIds() {
        try {
            String urlString = "https://api.telegram.org/bot" + botToken + "/getUpdates";
            URL url = URI.create(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            JSONObject json = new JSONObject(response.toString());
            JSONArray results = json.getJSONArray("result");

            for (int i = 0; i < results.length(); i++) {
                JSONObject update = results.getJSONObject(i);
                if (update.has("message")) {
                    JSONObject msg = update.getJSONObject("message");
                    JSONObject chat = msg.getJSONObject("chat");
                    knownChatIds.add(chat.getLong("id"));
                }
            }
            
            System.out.println("✅ " + knownChatIds.size() + " کاربر شناسایی شد.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * اعلان آنلاین شدن ربات به همه کاربران
     */
    public static void notifyBotOnline() {
        String onlineMessage = "🟢 ربات آنلاین شد!\n\nبرای راهنما دستور /start را ارسال کنید.";
        
        for (Long chatId : knownChatIds) {
            MessagePhotoSender.sendMessage(botToken, chatId, onlineMessage);
            System.out.println("📤 اعلان آنلاین شدن به " + chatId + " ارسال شد.");
        }
    }
    
    /**
     * Polling برای دریافت و پردازش دستورات جدید
     * این متد یک حلقه بی‌نهایت است که مدام پیام‌های جدید را چک می‌کند
     */
    public static void startPolling() {
        long lastUpdateId = 0;
        
        while (true) {
            try {
                // Long Polling: 30 ثانیه صبر می‌کند تا پیام جدید برسد
                String urlString = "https://api.telegram.org/bot" + botToken + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=30";
                URL url = URI.create(urlString).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(35000);
                conn.setReadTimeout(35000);

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                JSONObject json = new JSONObject(response.toString());
                JSONArray results = json.getJSONArray("result");

                for (int i = 0; i < results.length(); i++) {
                    JSONObject update = results.getJSONObject(i);
                    lastUpdateId = update.getLong("update_id");
                    
                    if (update.has("message")) {
                        JSONObject msg = update.getJSONObject("message");
                        JSONObject chat = msg.getJSONObject("chat");
                        Long chatId = chat.getLong("id");
                        
                        // اضافه کردن به لیست شناخته‌شده‌ها
                        knownChatIds.add(chatId);
                        
                        if (msg.has("text")) {
                            String text = msg.getString("text");
                            handleCommand(chatId, text, chat);
                        }
                    }
                }

            } catch (Exception e) {
                System.out.println("⚠️ خطا در polling: " + e.getMessage());
                try {
                    Thread.sleep(5000); // صبر 5 ثانیه قبل از تلاش مجدد
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }
    
    /**
     * پردازش دستورات دریافتی
     * @param chatId شناسه چت کاربر
     * @param text متن پیام دریافتی
     */
    public static void handleCommand(Long chatId, String text, JSONObject chat) {
        if (text.equals("/start")) {
            String helpMessage = "👋 سلام! به ربات خوش آمدید.\n\n" +
                    "این ربات برای ارسال پیام و عکس به کاربران از روی لیست اکسل طراحی شده است.\n\n" +
                    "دستورات:\n" +
                    "/send - ارسال پیام به کاربران لیست\n" +
                    "/status - نمایش وضعیت ربات";
            MessagePhotoSender.sendMessage(botToken, chatId, helpMessage);
            System.out.println("📩 /start از " + chatId);

            // ذخیره اطلاعات کاربر در اکسل در صورت جدید بودن
            User user = new User();
            user.setChatId(chatId);
            if (chat.has("username")) {
                user.setUsername(chat.getString("username"));
            }
            if (chat.has("last_name")) {
                user.setLastname(chat.optString("last_name", ""));
            } else if (chat.has("first_name")) {
                user.setLastname(chat.optString("first_name", ""));
            }
            ExcelReader.upsertUserRow(user, "user2.xlsx");
            
        } else if (text.equals("/send")) {
            // ارسال به کاربران از اکسل
            sendToExcelUsers();
            MessagePhotoSender.sendMessage(botToken, chatId, "✅ ارسال به کاربران لیست انجام شد.");
            
        } else if (text.equals("/status")) {
            String status = "📊 وضعیت ربات:\n" +
                    "• کاربران شناخته شده: " + knownChatIds.size();
            MessagePhotoSender.sendMessage(botToken, chatId, status);
        }
    }
    
    /**
     * ارسال پیام و عکس به کاربران از فایل اکسل
     */
    public static void sendToExcelUsers() {
        List<User> users = ExcelReader.readUsersFromExcel("user2.xlsx");
        
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            System.out.println(user.username + "  " + i);
            
            String message = "Hello " + user.getGender() + " " + user.getLastname() + " " +
                    "for the first assignment, please translate these pages into persian. " +
                    "The translation must be extremely accurate. You may use AI tools.";
            
            Long chatId = getChatIdByUsername(user.username);

            if (chatId != null) {
                MessagePhotoSender.sendMessage(botToken, chatId, message);
                MessagePhotoSender.sendKPhotos(botToken, chatId, "fol", 6, 6 * i);
                System.out.println("✅ ارسال به " + user.username + " انجام شد.");
            } else {
                System.out.println("❌ " + user.username + " - کاربر پیدا نشد یا پیام نداده است.");
            }
        }
    }

    /**
     * پیدا کردن chat_id کاربر بر اساس username
     * توجه: فقط کاربرانی که قبلاً به ربات پیام داده‌اند پیدا می‌شوند
     * @param targetUsername نام کاربری تلگرام (بدون @)
     * @return chat_id یا null اگر پیدا نشد
     */
    public static Long getChatIdByUsername(String targetUsername) {
        try {
            String urlString = "https://api.telegram.org/bot" + botToken + "/getUpdates";
            URL url = URI.create(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            JSONObject json = new JSONObject(response.toString());
            JSONArray results = json.getJSONArray("result");

            for (int i = 0; i < results.length(); i++) {
                if (!results.getJSONObject(i).has("message"))
                    continue;

                JSONObject msg = results.getJSONObject(i).getJSONObject("message");
                JSONObject chat = msg.getJSONObject("chat");

                if (chat.has("username")) {
                    String username = chat.getString("username");

                    if (username.equalsIgnoreCase(targetUsername)) {
                        return chat.getLong("id");
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
