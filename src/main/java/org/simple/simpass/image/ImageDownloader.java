package org.simple.simpass.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class ImageDownloader {
    
    /**
     * Download image from URL
     */
    public static BufferedImage downloadImage(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            
            HttpURLConnection httpConnection = (HttpURLConnection) connection;
            httpConnection.setRequestMethod("GET");
            
            if (httpConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream inputStream = httpConnection.getInputStream()) {
                    return ImageIO.read(inputStream);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // 如果下载失败，创建一个简单的占位图像
        BufferedImage fallbackImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                fallbackImage.setRGB(x, y, 0xFFFFFF); // 白色背景
            }
        }
        
        // 绘制简单的二维码框架
        for (int i = 0; i < 20; i++) {
            int x = (int) (Math.random() * 128);
            int y = (int) (Math.random() * 128);
            if (x < 120 && y < 120) {
                for (int dx = 0; dx < 4; dx++) {
                    for (int dy = 0; dy < 4; dy++) {
                        if (x + dx < 128 && y + dy < 128) {
                            fallbackImage.setRGB(x + dx, y + dy, (i % 3 == 0) ? 0x000000 : 0xFFFFFF);
                        }
                    }
                }
            }
        }
        
        return fallbackImage;
    }
}