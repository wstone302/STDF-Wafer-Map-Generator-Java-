import java.io.*;
import java.util.*;
import java.util.regex.*;

public class StdfOutToCsv {
    // 輸入的 STDF 文字檔案路徑
    private static final String INPUT_FILE_PATH = "./unpacked/main_Lot_1_Wafer_1_Oct_13_09h33m41s_STDF.out";
    // 輸出的 CSV 檔案路徑
    private static final String OUTPUT_FILE_PATH = "output/converted.csv"; // 輸出到 output 目錄

    // 正則表達式用於匹配欄位行: KEY = VALUE (TYPE) 或 KEY = "VALUE" (TYPE)
    private static final Pattern FIELD_PATTERN = Pattern.compile("^\\s*(\\w+)\\s*=\\s*(.+?)(?:\\s*\\(.+?\\))?$");

    public static void main(String[] args) {
        // 確保 output 目錄存在
        File outputFile = new File(OUTPUT_FILE_PATH);
        File outputDir = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            outputDir.mkdirs();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(INPUT_FILE_PATH));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

            // 寫入 CSV 標頭 - 包含所有重要欄位，與 WaferMapGenerator 預期一致
            // 您的 WaferMapGenerator 期望 X,Y,PART_ID,BIN
            // 但您的 STDF .out 檔案有 HARD_BIN 和 SOFT_BIN，我們將使用 HARD_BIN 作為 BIN
            writer.write("X_COORD,Y_COORD,PART_ID,HARD_BIN,SOFT_BIN,PART_TXT\n"); // 調整標頭

            String line;
            boolean insidePrr = false;
            Map<String, String> currentPrrFields = new HashMap<>(); // 使用 Map 儲存當前 Prr 記錄的欄位

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // 檢測 Prr 記錄的開始
                // 這裡使用更精確的正則表達式來匹配記錄頭
                if (line.matches("^Record \\d+, type=Prr, \\d+ entries:?$")) {
                    insidePrr = true;
                    currentPrrFields.clear(); // 清空之前記錄的數據
                    continue; // 跳過當前行 (記錄頭)
                }

                if (insidePrr) {
                    Matcher matcher = FIELD_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        String fieldName = matcher.group(1).trim();
                        String fieldValue = matcher.group(2).trim();

                        // 移除字符串值外部的雙引號
                        if (fieldValue.startsWith("\"") && fieldValue.endsWith("\"")) {
                            fieldValue = fieldValue.substring(1, fieldValue.length() - 1);
                        }
                        currentPrrFields.put(fieldName, fieldValue);

                        // 當我們收集到 PART_ID (通常是 Prr 記錄的最後一個關鍵欄位) 時，認為一個 Prr 記錄塊結束
                        // 或者當遇到下一個記錄頭時，也結束當前 Prr 塊
                        if (fieldName.equals("PART_ID") || line.matches("^Record \\d+, type=\\w+, \\d+ entries:?$")) {
                            // 提取所需數據
                            String xCoord = currentPrrFields.getOrDefault("X_COORD", "");
                            String yCoord = currentPrrFields.getOrDefault("Y_COORD", "");
                            String partId = currentPrrFields.getOrDefault("PART_ID", "");
                            String hardBin = currentPrrFields.getOrDefault("HARD_BIN", "");
                            String softBin = currentPrrFields.getOrDefault("SOFT_BIN", "");
                            String partTxt = currentPrrFields.getOrDefault("PART_TXT", "");

                            // 寫入 CSV
                            writer.write(String.format("%s,%s,%s,%s,%s,\"%s\"\n",
                                    xCoord, yCoord, partId, hardBin, softBin, partTxt)); // 調整順序為 X,Y,ID,BIN...

                            insidePrr = false; // 結束當前 Prr 記錄的處理
                            // 如果當前行是新的記錄頭，則需要重新處理它
                            if (line.matches("^Record \\d+, type=\\w+, \\d+ entries:?$")) {
                                // 重新處理當前行作為新記錄的開始
                                // 這裡可以遞歸調用或重新設定 insidePrr 和 currentPrrFields
                                // 為了簡單，我們只重置狀態，下一次循環會正確處理新記錄頭
                            }
                        }
                    }
                }
            }

            System.out.println("轉換完成，輸出: " + OUTPUT_FILE_PATH);

        } catch (FileNotFoundException e) {
            System.err.println("錯誤：找不到輸入檔案 " + INPUT_FILE_PATH + "。請確認路徑正確。");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("讀取或寫入檔案時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
    }
}