import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * کلاس خواندن اطلاعات کاربران از فایل اکسل
 * از کتابخانه Apache POI برای خواندن فایل xlsx استفاده می‌کند
 */
public class ExcelReader {

    private static final String[] DEFAULT_HEADERS = {"username", "gender", "lastname", "chat_id"};

    /**
     * خواندن لیست کاربران از فایل اکسل
     * ستون‌های مورد انتظار: username, gender, lastname
     * @param filePath مسیر فایل اکسل
     * @return لیست کاربران
     */
    public static List<User> readUsersFromExcel(String filePath) {
        List<User> users = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            
            // پیدا کردن ایندکس ستون‌ها از هدر (سطر اول)
            Row headerRow = sheet.getRow(0);
            HeaderIndexes indexes = resolveHeaders(headerRow);

            if (indexes.usernameCol == -1) {
                System.out.println("خطا: ستون username در فایل اکسل پیدا نشد!");
                return users;
            }

            // خواندن داده‌ها از سطر دوم به بعد
            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                User user = new User();
                
                // خواندن username
                Cell usernameCell = row.getCell(indexes.usernameCol);
                if (usernameCell != null) {
                    user.setUsername(getCellValue(usernameCell));
                }

                // خواندن gender
                if (indexes.genderCol != -1) {
                    Cell genderCell = row.getCell(indexes.genderCol);
                    if (genderCell != null) {
                        user.setGender(getCellValue(genderCell));
                    }
                }

                // خواندن lastname
                if (indexes.lastnameCol != -1) {
                    Cell lastnameCell = row.getCell(indexes.lastnameCol);
                    if (lastnameCell != null) {
                        user.setLastname(getCellValue(lastnameCell));
                    }
                }

                // خواندن chat_id اگر موجود باشد
                if (indexes.chatIdCol != -1) {
                    Cell chatIdCell = row.getCell(indexes.chatIdCol);
                    if (chatIdCell != null) {
                        switch (chatIdCell.getCellType()) {
                            case NUMERIC -> user.setChatId((long) chatIdCell.getNumericCellValue());
                            case STRING -> {
                                try {
                                    user.setChatId(Long.parseLong(chatIdCell.getStringCellValue().trim()));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }

                // فقط کاربرانی که username دارند اضافه شوند
                if (user.getUsername() != null && !user.getUsername().isEmpty()) {
                    users.add(user);
                }
            }

            System.out.println("تعداد " + users.size() + " کاربر از فایل اکسل خوانده شد.");

        } catch (IOException e) {
            System.out.println("خطا در خواندن فایل اکسل: " + e.getMessage());
            e.printStackTrace();
        }

        return users;
    }

    /**
     * ثبت یا به‌روزرسانی اطلاعات کاربر در فایل اکسل.
     * اگر کاربر با username یا chat_id موجود باشد، فقط مقادیر خالی تکمیل می‌شوند؛ وگرنه سطر جدید اضافه می‌شود.
     */
    public static void upsertUserRow(User user, String filePath) {
        if ((user.getUsername() == null || user.getUsername().isBlank()) && user.getChatId() == null) {
            System.out.println("⚠️ امکان ثبت کاربر بدون username یا chat_id وجود ندارد.");
            return;
        }

        Workbook workbook = null;
        try {
            File file = new File(filePath);
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    workbook = new XSSFWorkbook(fis);
                }
            } else {
                workbook = new XSSFWorkbook();
            }

            if (workbook.getNumberOfSheets() == 0) {
                workbook.createSheet("Users");
            }

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                headerRow = sheet.createRow(0);
                for (int i = 0; i < DEFAULT_HEADERS.length; i++) {
                    headerRow.createCell(i).setCellValue(DEFAULT_HEADERS[i]);
                }
            }

            HeaderIndexes indexes = resolveHeaders(headerRow);
            if (indexes.chatIdCol == -1) {
                indexes = ensureChatIdColumn(sheet, headerRow, indexes);
            }

            int targetRowIndex = findExistingRow(sheet, indexes, user);
            Row targetRow = targetRowIndex == -1 ? sheet.createRow(sheet.getLastRowNum() + 1) : sheet.getRow(targetRowIndex);

            setCellIfEmpty(targetRow, indexes.usernameCol, user.getUsername());
            setCellIfEmpty(targetRow, indexes.genderCol, user.getGender());
            setCellIfEmpty(targetRow, indexes.lastnameCol, user.getLastname());
            setChatIdIfEmpty(targetRow, indexes.chatIdCol, user.getChatId());

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            System.out.println("📝 اطلاعات کاربر در فایل اکسل ثبت/به‌روزرسانی شد.");

        } catch (IOException e) {
            System.out.println("خطا در به‌روزرسانی فایل اکسل: " + e.getMessage());
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static HeaderIndexes ensureChatIdColumn(Sheet sheet, Row headerRow, HeaderIndexes current) {
        int newColIndex = headerRow.getLastCellNum() == -1 ? 0 : headerRow.getLastCellNum();
        headerRow.createCell(newColIndex).setCellValue("chat_id");
        for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row != null) {
                row.createCell(newColIndex);
            }
        }
        return new HeaderIndexes(current.usernameCol, current.genderCol, current.lastnameCol, newColIndex);
    }

    private static void setChatIdIfEmpty(Row row, int columnIndex, Long chatId) {
        if (columnIndex == -1 || chatId == null) return;
        Cell cell = row.getCell(columnIndex);
        boolean shouldWrite = false;
        if (cell == null) {
            cell = row.createCell(columnIndex);
            shouldWrite = true;
        } else if (cell.getCellType() == CellType.BLANK || (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank())) {
            shouldWrite = true;
        }
        if (shouldWrite) {
            cell.setCellValue(chatId);
        }
    }

    private static void setCellIfEmpty(Row row, int columnIndex, String value) {
        if (columnIndex == -1 || value == null || value.isBlank()) return;
        Cell cell = row.getCell(columnIndex);
        boolean shouldWrite = false;
        if (cell == null) {
            cell = row.createCell(columnIndex);
            shouldWrite = true;
        } else if (cell.getCellType() == CellType.BLANK || (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank())) {
            shouldWrite = true;
        }
        if (shouldWrite) {
            cell.setCellValue(value);
        }
    }

    private static int findExistingRow(Sheet sheet, HeaderIndexes indexes, User user) {
        String normalizedUsername = user.getUsername() == null ? null : user.getUsername().trim().toLowerCase();
        Long chatId = user.getChatId();

        for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;

            if (indexes.usernameCol != -1 && normalizedUsername != null) {
                Cell usernameCell = row.getCell(indexes.usernameCol);
                if (usernameCell != null && usernameCell.getCellType() == CellType.STRING) {
                    String cellValue = usernameCell.getStringCellValue().trim().toLowerCase();
                    if (cellValue.equals(normalizedUsername)) {
                        return rowNum;
                    }
                }
            }

            if (indexes.chatIdCol != -1 && chatId != null) {
                Cell chatCell = row.getCell(indexes.chatIdCol);
                if (chatCell != null) {
                    if (chatCell.getCellType() == CellType.NUMERIC && (long) chatCell.getNumericCellValue() == chatId) {
                        return rowNum;
                    } else if (chatCell.getCellType() == CellType.STRING) {
                        try {
                            if (Long.parseLong(chatCell.getStringCellValue().trim()) == chatId) {
                                return rowNum;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        return -1;
    }

    private static HeaderIndexes resolveHeaders(Row headerRow) {
        HeaderIndexes indexes = new HeaderIndexes();
        if (headerRow == null) {
            return indexes;
        }
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String header = cell.getStringCellValue().trim().toLowerCase();
                switch (header) {
                    case "username" -> indexes.usernameCol = i;
                    case "gender" -> indexes.genderCol = i;
                    case "lastname" -> indexes.lastnameCol = i;
                    case "chat_id" -> indexes.chatIdCol = i;
                }
            }
        }
        return indexes;
    }

    private static class HeaderIndexes {
        int usernameCol = -1;
        int genderCol = -1;
        int lastnameCol = -1;
        int chatIdCol = -1;

        HeaderIndexes() {}

        HeaderIndexes(int usernameCol, int genderCol, int lastnameCol, int chatIdCol) {
            this.usernameCol = usernameCol;
            this.genderCol = genderCol;
            this.lastnameCol = lastnameCol;
            this.chatIdCol = chatIdCol;
        }
    }

    /**
     * تبدیل مقدار سلول به رشته
     * این متد انواع مختلف سلول (متنی، عددی، بولین) را به String تبدیل می‌کند
     */
    private static String getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}
