/**
 * کلاس مدل کاربر
 * این کلاس اطلاعات هر کاربر را نگهداری می‌کند
 */
public class User {
    public String username;   // نام کاربری تلگرام (بدون @)
    public String gender;     // جنسیت (Mr. یا Ms.)
    public String lastname;   // نام خانوادگی
    public Long chatId;       // شناسه چت تلگرام

    // Constructor پیش‌فرض
    public User() {}

    // Constructor با پارامتر
    public User(String username, String gender, String lastname) {
        this.username = username;
        this.gender = gender;
        this.lastname = lastname;
    }

    // Getter و Setter ها
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    @Override
    public String toString() {
        return "User{username='" + username + "', gender='" + gender + "', lastname='" + lastname + "'}";
    }
}
