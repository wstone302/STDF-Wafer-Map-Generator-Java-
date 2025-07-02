import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO; // For saving images
import javax.swing.*; // For saving images

public class WaferMapGenerator {

    // 用於儲存晶圓圖中每個座標的詳細數據
    private Map<String, WaferChipData> waferChipDataMap = new HashMap<>();

    private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;

    private int totalChips = 0;
    private int passCount = 0;

    // 內部類別來儲存每個晶粒的詳細數據
    private static class WaferChipData {
        int x;
        int y;
        String partId;
        int binValue; // 從 PART_TXT 轉換而來
        char status;  // 'P' for Pass, 'F' for Fail

        public WaferChipData(int x, int y, String partId, int binValue, char status) {
            this.x = x;
            this.y = y;
            this.partId = partId;
            this.binValue = binValue;
            this.status = status;
        }
    }

    // constructor to load data
    public WaferMapGenerator(String inputTxtPath) throws IOException {
        loadData(inputTxtPath);
    }

    private void loadData(String inputTxtPath) throws IOException {
        File inputFile = new File(inputTxtPath);
        if (!inputFile.exists()) {
            System.err.println("錯誤：輸入檔案未找到於 " + inputTxtPath);
            System.err.println("請確認 Python 腳本已運行並生成了 '" + inputTxtPath + "'。");
            throw new FileNotFoundException("Input file not found: " + inputTxtPath);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;
            int lineNumber = 0; // 用於錯誤訊息

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim(); // 移除行首尾空白

                // 只處理以 "PRR|" 開頭的記錄行
                if (!line.startsWith("PRR|")) {
                    continue; // 跳過非 PRR 記錄 (例如標頭、其他記錄類型)
                }

                String[] parts = line.split("\\|"); // 使用管道符號 '|' 分割

                // 確保有足夠的欄位來提取 X, Y, PART_ID, 和 PART_TXT
                if (parts.length < 12) { // 需要到索引 11 (PART_TXT)，所以總長度 > 11
                    System.err.println("警告：第 " + lineNumber + " 行 PRR 格式不正確（欄位不足），跳過: " + line);
                    continue;
                }

                try {
                    // 提取 X_COORD 和 Y_COORD
                    int x = Integer.parseInt(parts[7].trim()); // X_COORD 在索引 7
                    int y = Integer.parseInt(parts[8].trim()); // Y_COORD 在索引 8
                    String partId = parts[10].trim(); // PART_ID 在索引 10

                    // 根據 Python 邏輯解析 BIN 值 (PART_TXT 轉換為整數)
                    int binValue = 0; // Default bin value if parsing fails
                    try {
                        binValue = (int) Float.parseFloat(parts[11].trim()); // Convert float string to int
                    } catch (NumberFormatException e) {
                        System.err.println("警告：第 " + lineNumber + " 行的 PART_TXT 數字格式錯誤，設為 0: " + parts[11].trim());
                    }

                    // 更新座標範圍
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);

                    totalChips++;
                    char status = 'F'; // 預設為 Fail
                    if (binValue == 1) { // 假設 BIN=1 為 Pass
                        passCount++;
                        status = 'P'; // Pass
                    }

                    String key = x + "," + y;
                    waferChipDataMap.put(key, new WaferChipData(x, y, partId, binValue, status));

                } catch (NumberFormatException e) {
                    System.err.println("警告：第 " + lineNumber + " 行的 X 或 Y 座標數字格式錯誤，跳過: " + line + " (" + e.getMessage() + ")");
                }
            }
        }
    }

    public WaferChipData getChipData(int x, int y) {
        return waferChipDataMap.get(x + "," + y);
    }

    public int getMinX() { return minX; }
    public int getMaxX() { return maxX; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }
    public int getTotalChips() { return totalChips; }
    public int getPassCount() { return passCount; }

    public double getYieldRate() {
        return (totalChips > 0) ? (double) passCount / totalChips * 100 : 0.0;
    }

    // === 繪圖面板的基類 ===
    private abstract class BaseWaferMapPanel extends JPanel {
        protected final int CELL_SIZE = 20; // 每個晶粒的顯示大小 (像素)
        protected final int PADDING = 30;   // 圖形邊緣的留白

        public BaseWaferMapPanel() {
            if (totalChips > 0) {
                int width = (maxX - minX + 1) * CELL_SIZE + 2 * PADDING;
                int height = (maxY - minY + 1) * CELL_SIZE + 2 * PADDING;
                setPreferredSize(new Dimension(width, height));
            } else {
                setPreferredSize(new Dimension(300, 200)); // 預設大小如果沒有數據
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (totalChips == 0) {
                g.setColor(Color.RED);
                g.drawString("沒有晶圓數據或數據無效。", PADDING, PADDING);
                return;
            }

            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 計算繪圖的偏移量，使 minX, minY 位於左下角
            int offsetX = PADDING - minX * CELL_SIZE;
            int offsetY = PADDING + (maxY * CELL_SIZE); // Swing 的 Y 軸向下增長，需要反轉

            // 繪製晶粒
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    WaferChipData chipData = getChipData(x, y);
                    
                    int drawX = offsetX + x * CELL_SIZE;
                    int drawY = offsetY - y * CELL_SIZE;

                    if (chipData != null) {
                        drawChip(g2d, chipData, drawX, drawY);
                    } else {
                        // 繪製空白區域
                        g2d.setColor(Color.LIGHT_GRAY);
                        g2d.fillRect(drawX, drawY, CELL_SIZE - 1, CELL_SIZE - 1);
                        g2d.setColor(Color.BLACK);
                        g2d.drawRect(drawX, drawY, CELL_SIZE - 1, CELL_SIZE - 1);
                    }
                }
            }

            // 繪製 X 軸標籤
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.PLAIN, 10));
            for (int x = minX; x <= maxX; x++) {
                String label = String.valueOf(x);
                int drawX = offsetX + x * CELL_SIZE + CELL_SIZE / 2;
                g2d.drawString(label, drawX - g2d.getFontMetrics().stringWidth(label) / 2, PADDING / 2 + 5);
            }

            // 繪製 Y 軸標籤
            for (int y = minY; y <= maxY; y++) {
                String label = String.valueOf(y);
                int drawY = offsetY - y * CELL_SIZE + CELL_SIZE / 2;
                g2d.drawString(label, PADDING / 2 - g2d.getFontMetrics().stringWidth(label) / 2, drawY + g2d.getFontMetrics().getAscent() / 2 - 2);
            }
        }

        protected abstract void drawChip(Graphics2D g2d, WaferChipData chipData, int drawX, int drawY);
        protected abstract String getTitle();
    }

    // === PART_ID 繪圖面板 ===
    private class PartIdWaferMapPanel extends BaseWaferMapPanel {
        @Override
        protected void drawChip(Graphics2D g2d, WaferChipData chipData, int drawX, int drawY) {
            // 根據 PART_ID 映射顏色 (簡單的灰度或隨機色)
            // 為了模擬 Python 的 viridis 漸變，這會比較複雜，這裡用簡單的灰度
            // 或者您可以根據 PART_ID 的大小來調整顏色
            int id = Integer.parseInt(chipData.partId);
            float hue = (float) id / (totalChips + 1); // 簡單的色相映射
            Color color = Color.getHSBColor(hue, 0.8f, 0.9f); // 飽和度和亮度固定

            g2d.setColor(color);
            g2d.fillRect(drawX, drawY, CELL_SIZE - 1, CELL_SIZE - 1);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(drawX, drawY, CELL_SIZE - 1, CELL_SIZE - 1);

            // 繪製 PART_ID 文本
            g2d.setColor(Color.BLACK); // 文本顏色
            g2d.setFont(new Font("Arial", Font.PLAIN, 8)); // 小字體
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(chipData.partId);
            int textHeight = fm.getAscent();
            g2d.drawString(chipData.partId, drawX + (CELL_SIZE - textWidth) / 2, drawY + (CELL_SIZE + textHeight) / 2 - 2);
        }

        @Override
        protected String getTitle() {
            return "Wafer Map (PART_ID)";
        }
    }

    // === BIN 繪圖面板 ===
    private class BinWaferMapPanel extends BaseWaferMapPanel {
        @Override
        protected void drawChip(Graphics2D g2d, WaferChipData chipData, int drawX, int drawY) {
            Color chipColor;
            switch (chipData.status) {
                case 'P':
                    chipColor = Color.GREEN; // Pass
                    break;
                case 'F':
                    chipColor = Color.RED;   // Fail
                    break;
                default:
                    chipColor = Color.LIGHT_GRAY; // Should not happen for actual chips
            }
            g2d.setColor(chipColor);
            g2d.fillRect(drawX, drawY, CELL_SIZE - 1, CELL_SIZE - 1);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(drawX, drawY, CELL_SIZE - 1, CELL_SIZE - 1);
        }

        @Override
        protected String getTitle() {
            return "Wafer Map (BIN - For Yield)";
        }
    }


    public static void main(String[] args) {
        String inputTxtPath = "unpacked/output.txt";
        String yieldSummaryFile = "output/wafer_yield_summary.txt"; // 輸出到 output 目錄
        String partIdImagePath = "output/wafer_map_part_id.png";
        String binImagePath = "output/wafer_map_bin.png";

        WaferMapGenerator generator = null;
        try {
            generator = new WaferMapGenerator(inputTxtPath);

            // --- 控制台晶圓圖顯示 ---
            System.out.println("\n--- 晶圓圖 (P: Pass, F: Fail) ---");
            System.out.println("原始晶圓範圍: X [" + generator.minX + "," + generator.maxX + "], Y [" + generator.minY + "," + generator.maxY + "]");

            // 打印 X 軸標籤
            System.out.print("       "); // 為了對齊 Y 軸標籤
            for (int x = generator.minX; x <= generator.maxX; x++) {
                System.out.printf("%4d", x); // 打印實際的 X 座標
            }
            System.out.println();

            // 打印晶圓圖內容 (Y 軸從最大值到最小值遞減)
            for (int y = generator.maxY; y >= generator.minY; y--) {
                System.out.printf("%4d   ", y); // 打印 Y 軸標籤
                for (int x = generator.minX; x <= generator.maxX; x++) {
                    WaferChipData chipData = generator.getChipData(x, y);
                    if (chipData != null) {
                        System.out.printf("%4c", chipData.status); // 打印良率狀態
                    } else {
                        System.out.printf("%4c", '.'); // 空白
                    }
                }
                System.out.println();
            }

            // --- 輸出良率總結 ---
            File summaryFile = new File(yieldSummaryFile);
            File summaryOutputDir = summaryFile.getParentFile();
            if (summaryOutputDir != null && !summaryOutputDir.exists()) {
                summaryOutputDir.mkdirs(); // 確保輸出目錄存在
            }

            try (BufferedWriter summaryWriter = new BufferedWriter(new FileWriter(summaryFile))) {
                summaryWriter.write("Total Chips: " + generator.totalChips + "\n");
                summaryWriter.write("PASS (BIN=1): " + generator.passCount + "\n");
                double yield = generator.getYieldRate();
                summaryWriter.write(String.format("Yield Rate: %.2f%%\n", yield));
                System.out.println("良率總結輸出完成： " + yieldSummaryFile);
            } catch (IOException e) {
                System.err.println("寫入良率總結檔案時發生錯誤: " + e.getMessage());
                e.printStackTrace();
            }

            // --- 彈出 Swing 視窗繪製晶圓圖 ---
            // 必須在 Swing 的事件調度執行緒 (EDT) 上運行 GUI 操作
            WaferMapGenerator finalGenerator = generator; // 匿名內部類需要 final 或 effectively final 變量
            SwingUtilities.invokeLater(() -> {
                // 創建主視窗
                JFrame mainFrame = new JFrame("Wafer Map Viewer (Java)");
                mainFrame.setLayout(new GridLayout(1, 2)); // 兩張圖並排顯示

                // 創建 PART_ID 圖面板
                PartIdWaferMapPanel partIdPanel = finalGenerator.new PartIdWaferMapPanel();
                mainFrame.add(createPanelWithTitle(partIdPanel, partIdPanel.getTitle()));

                // 創建 BIN 圖面板
                BinWaferMapPanel binPanel = finalGenerator.new BinWaferMapPanel();
                mainFrame.add(createPanelWithTitle(binPanel, binPanel.getTitle()));

                mainFrame.pack(); // 調整視窗大小以適應內容
                mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                mainFrame.setLocationRelativeTo(null); // 視窗置中
                mainFrame.setVisible(true);

                // --- 可選：保存圖片到檔案 ---
                savePanelAsImage(partIdPanel, partIdImagePath);
                savePanelAsImage(binPanel, binImagePath);
            });

        } catch (FileNotFoundException e) {
            // 檔案未找到的錯誤訊息已在 loadData 中打印
        } catch (IOException e) {
            System.err.println("讀取檔案時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 輔助方法：創建帶有標題的面板
    private static JPanel createPanelWithTitle(JPanel panel, String title) {
        JPanel container = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        container.add(titleLabel, BorderLayout.NORTH);
        container.add(panel, BorderLayout.CENTER);
        return container;
    }

    // 輔助方法：將 JPanel 內容保存為圖片
    private static void savePanelAsImage(JPanel panel, String filePath) {
        try {
            File outputDir = new File(filePath).getParentFile();
            if (outputDir != null && !outputDir.exists()) {
                outputDir.mkdirs();
            }

            BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            panel.printAll(g2d); // 使用 printAll 確保所有組件都被繪製
            g2d.dispose();

            ImageIO.write(image, "PNG", new File(filePath));
            System.out.println("圖片已保存到: " + filePath);
        } catch (IOException ex) {
            System.err.println("保存圖片時發生錯誤: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}