package org.simple.simpass.image;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.map.MapPalette;

import java.awt.image.BufferedImage;

public class SimpleMapHelper {
    
    /**
     * Convert BufferedImage to bytes that represent pixel colors
     * Uses different algorithms for QR codes (high-contrast) vs regular images (color)
     */
    public static byte[] getPixels(java.awt.image.BufferedImage image) {
        return getPixels(image, false); // 默认为非二维码模式（彩色模式）
    }
    
    /**
     * Convert BufferedImage to bytes that represent pixel colors
     * @param image The input image
     * @param isQRCode If true, uses high-contrast black and white algorithm ideal for QR codes
     */
    public static byte[] getPixels(java.awt.image.BufferedImage image, boolean isQRCode) {
        int width = Math.min(image.getWidth(), 128);
        int height = Math.min(image.getHeight(), 128);
        byte[] pixels = new byte[128 * 128]; // 地图大小为128x128
        
        if (isQRCode) {
            // 二维码模式：使用高对比度黑白算法
            // 初始化所有像素为白色
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = 127; // 白色背景
            }
            
            // 填充像素数据
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = image.getRGB(x, y);
                    int alpha = (rgb >> 24) & 0xFF;
                    
                    // 如果像素是透明的，保持白色
                    if (alpha < 128) { // 使用半透明阈值
                        pixels[y * 128 + x] = 127; // 白色
                    } else {
                        // 使用颜色转换
                        java.awt.Color color = new java.awt.Color(rgb);
                        // 转换为黑白，使用亮度阈值
                        if (isDarkPixel(color)) {
                            pixels[y * 128 + x] = 0; // 黑色
                        } else {
                            pixels[y * 128 + x] = 127; // 白色
                        }
                    }
                }
            }
        } else {
            // 彩色图片模式：使用颜色映射算法
            // 初始化所有像素为白色
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = 127; // 白色背景 (MapPalette index 127 is white)
            }
            
            // 填充像素数据
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = image.getRGB(x, y);
                    int alpha = (rgb >> 24) & 0xFF;
                    
                    // 如果像素是透明的，保持白色
                    if (alpha < 128) { // 使用半透明阈值
                        pixels[y * 128 + x] = 127; // 白色
                    } else {
                        // 获取RGB颜色分量
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;
                        
                        // 将RGB颜色转换为最接近的Minecraft地图颜色
                        byte mapColorIndex = findClosestMapColor(r, g, b);
                        pixels[y * 128 + x] = mapColorIndex;
                    }
                }
            }
        }
        
        return pixels;
    }
    
    private static boolean isDarkPixel(java.awt.Color color) {
        // 如果颜色的亮度低于某个阈值，则认为是深色（将显示为黑色）
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        // 计算亮度 (使用标准亮度公式)
        double brightness = (0.299 * r + 0.587 * g + 0.114 * b);
        return brightness < 128; // 阈值为128，平衡黑白转换
    }
    
    /**
     * Find the closest Minecraft map color for the given RGB values
     */
    private static byte findClosestMapColor(int r, int g, int b) {
        // Minecraft 1.16+ has 133 colors in the MapPalette
        // We'll create a color matching algorithm that finds the closest color
        // Based on Minecraft's internal color palette
        
        // Predefined Minecraft map colors (RGB values)
        int[][] mapColors = {
            {0, 0, 0},       // 0 - Black
            {127, 127, 127}, // 1 - Dark Grey
            {255, 255, 255}, // 2 - White
            {191, 191, 191}, // 3 - Light Grey
            {255, 0, 0},     // 4 - Red
            {167, 80, 19},   // 5 - Orange
            {255, 216, 59},  // 6 - Yellow
            {127, 203, 84},  // 7 - Lime
            {45, 106, 79},   // 8 - Dark Green
            {53, 70, 27},    // 9 - Brown
            {107, 107, 107}, // 10 - Dark Grey
            {72, 106, 114},  // 11 - Dark Cyan
            {106, 133, 171}, // 12 - Light Blue
            {59, 76, 192},   // 13 - Blue
            {79, 45, 167},   // 14 - Purple
            {222, 111, 161}, // 15 - Magenta
            {131, 58, 103},  // 16 - Dark Pink
            {216, 127, 51},  // 17 - Gold
            {178, 162, 147}, // 18 - Tan
            {127, 167, 150}, // 19 - Teal
            {64, 153, 157},  // 20 - Turquoise
            {52, 94, 69},    // 21 - Dark Teal
            {52, 143, 83},   // 22 - Dark Turquoise
            {135, 107, 98},  // 23 - Taupe
            {89, 125, 39},   // 24 - Avocado
            {125, 115, 82},  // 25 - Khaki
            {189, 198, 106}, // 26 - Light Yellow
            {105, 61, 76},   // 27 - Mauve
            {180, 74, 104},  // 28 - Rose
            {34, 40, 49},    // 29 - Dark Blue Grey
            {27, 34, 53},    // 30 - Dark Blue
            {42, 57, 123},   // 31 - Medium Blue
            {53, 49, 147},   // 32 - Indigo
            {49, 82, 143},   // 33 - Light Blue
            {52, 102, 91},   // 34 - Sea Green
            {52, 102, 77},   // 35 - Sage
            {80, 105, 57},   // 36 - Moss
            {105, 84, 57},   // 37 - Beige
            {105, 57, 67},   // 38 - Dark Rose
            {84, 57, 105},   // 39 - Lavender
            {57, 67, 105},   // 40 - Periwinkle
            {57, 105, 105},  // 41 - Turquoise
            {57, 105, 84},   // 42 - Sea Foam
            {105, 94, 57},   // 43 - Light Tan
            {105, 57, 84},   // 44 - Pink
            {127, 0, 0},     // 45 - Dark Red
            {102, 0, 0},     // 46 - Darker Red
            {76, 0, 0},      // 47 - Deep Red
            {51, 0, 0},      // 48 - Deeper Red
            {127, 38, 0},    // 49 - Dark Orange
            {102, 30, 0},    // 50 - Darker Orange
            {76, 23, 0},     // 51 - Deep Orange
            {51, 15, 0},     // 52 - Deeper Orange
            {127, 76, 0},    // 53 - Dark Gold
            {102, 61, 0},    // 54 - Darker Gold
            {76, 46, 0},     // 55 - Deep Gold
            {51, 31, 0},     // 56 - Deeper Gold
            {127, 114, 0},   // 57 - Dark Yellow
            {102, 91, 0},    // 58 - Darker Yellow
            {76, 68, 0},     // 59 - Deep Yellow
            {51, 46, 0},     // 60 - Deeper Yellow
            {127, 127, 0},   // 61 - Dark Yellow-Orange
            {102, 102, 0},   // 62 - Darker Yellow-Orange
            {76, 76, 0},     // 63 - Deep Yellow-Orange
            {51, 51, 0},     // 64 - Deeper Yellow-Orange
            {89, 127, 0},    // 65 - Dark Yellow-Green
            {71, 102, 0},    // 66 - Darker Yellow-Green
            {53, 76, 0},     // 67 - Deep Yellow-Green
            {36, 51, 0},     // 68 - Deeper Yellow-Green
            {51, 127, 0},    // 69 - Dark Green-Yellow
            {41, 102, 0},    // 70 - Darker Green-Yellow
            {31, 76, 0},     // 71 - Deep Green-Yellow
            {20, 51, 0},     // 72 - Deeper Green-Yellow
            {13, 127, 0},    // 73 - Dark Lime
            {10, 102, 0},    // 74 - Darker Lime
            {8, 76, 0},      // 75 - Deep Lime
            {5, 51, 0},      // 76 - Deeper Lime
            {0, 127, 0},     // 77 - Dark Lime Green
            {0, 102, 0},     // 78 - Darker Lime Green
            {0, 76, 0},      // 79 - Deep Lime Green
            {0, 51, 0},      // 80 - Deeper Lime Green
            {0, 127, 38},    // 81 - Dark Green
            {0, 102, 30},    // 82 - Darker Green
            {0, 76, 23},     // 83 - Deep Green
            {0, 51, 15},     // 84 - Deeper Green
            {0, 127, 76},    // 85 - Dark Green-Cyan
            {0, 102, 61},    // 86 - Darker Green-Cyan
            {0, 76, 46},     // 87 - Deep Green-Cyan
            {0, 51, 31},     // 88 - Deeper Green-Cyan
            {0, 127, 114},   // 89 - Dark Cyan
            {0, 102, 91},    // 90 - Darker Cyan
            {0, 76, 68},     // 91 - Deep Cyan
            {0, 51, 46},     // 92 - Deeper Cyan
            {0, 127, 127},   // 93 - Dark Cyan-Blue
            {0, 102, 102},   // 94 - Darker Cyan-Blue
            {0, 76, 76},     // 95 - Deep Cyan-Blue
            {0, 51, 51},     // 96 - Deeper Cyan-Blue
            {0, 89, 127},    // 97 - Dark Light Blue
            {0, 71, 102},    // 98 - Darker Light Blue
            {0, 53, 76},     // 99 - Deep Light Blue
            {0, 36, 51},     // 100 - Deeper Light Blue
            {0, 51, 127},    // 101 - Dark Blue
            {0, 41, 102},    // 102 - Darker Blue
            {0, 31, 76},     // 103 - Deep Blue
            {0, 20, 51},     // 104 - Deeper Blue
            {13, 0, 127},    // 105 - Dark Blue-Purple
            {10, 0, 102},    // 106 - Darker Blue-Purple
            {8, 0, 76},      // 107 - Deep Blue-Purple
            {5, 0, 51},      // 108 - Deeper Blue-Purple
            {51, 0, 127},    // 109 - Dark Purple
            {41, 0, 102},    // 110 - Darker Purple
            {31, 0, 76},     // 111 - Deep Purple
            {20, 0, 51},     // 112 - Deeper Purple
            {89, 0, 127},    // 113 - Dark Purple-Pink
            {71, 0, 102},    // 114 - Darker Purple-Pink
            {53, 0, 76},     // 115 - Deep Purple-Pink
            {36, 0, 51},     // 116 - Deeper Purple-Pink
            {127, 0, 114},   // 117 - Dark Magenta
            {102, 0, 91},    // 118 - Darker Magenta
            {76, 0, 68},     // 119 - Deep Magenta
            {51, 0, 46},     // 120 - Deeper Magenta
            {127, 0, 76},    // 121 - Dark Pink
            {102, 0, 61},    // 122 - Darker Pink
            {76, 0, 46},     // 123 - Deep Pink
            {51, 0, 31},     // 124 - Deeper Pink
            {127, 0, 38},    // 125 - Dark Red-Pink
            {102, 0, 30},    // 126 - Darker Red-Pink
            {76, 0, 23},     // 127 - Deep Red-Pink
            {51, 0, 15},     // 128 - Deeper Red-Pink
            {242, 242, 242}, // 129 - Very Light Grey
            {127, 127, 127}, // 130 - Light Grey
            {0, 0, 0},       // 131 - Black
            {255, 255, 255}  // 132 - White
        };
        
        // Find the closest color using Euclidean distance
        int bestMatch = 2; // Default to white
        int minDistance = Integer.MAX_VALUE;
        
        for (int i = 0; i < mapColors.length; i++) {
            int[] color = mapColors[i];
            int dr = r - color[0];
            int dg = g - color[1];
            int db = b - color[2];
            int distance = dr * dr + dg * dg + db * db;
            
            if (distance < minDistance) {
                minDistance = distance;
                bestMatch = i;
            }
        }
        
        // Ensure the result is within valid byte range
        return (byte) Math.max(0, Math.min(132, bestMatch));
    }
    
    /**
     * Get next available map ID
     */
    public static int getNextMapId(World world) {
        // 使用一个简单的计数器，起始值为1000000以避免冲突
        return 1000000 + (int) (System.currentTimeMillis() % 100000);
    }
}