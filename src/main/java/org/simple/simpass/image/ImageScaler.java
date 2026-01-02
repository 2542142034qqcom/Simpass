package org.simple.simpass.image;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageScaler {
    
    /**
     * 将图像缩放到适合Minecraft地图的尺寸（最大128x128）
     * 保持原始宽高比
     */
    public static BufferedImage scaleImageToFitMap(BufferedImage originalImage) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        // Minecraft地图的最大尺寸是128x128
        int maxWidth = 128;
        int maxHeight = 128;
        
        // 计算缩放比例
        double scaleX = (double) maxWidth / originalWidth;
        double scaleY = (double) maxHeight / originalHeight;
        double scale = Math.min(scaleX, scaleY); // 使用较小的比例以保持宽高比
        
        // 如果图像已经适合地图尺寸，则不进行缩放
        if (scale >= 1.0) {
            return originalImage;
        }
        
        // 计算新的尺寸
        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);
        
        // 创建缩放后的图像
        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();
        
        // 设置渲染提示以获得更好的图像质量
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 绘制缩放后的图像
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        
        return scaledImage;
    }
    
    /**
     * 将图像缩放到指定的最大尺寸
     */
    public static BufferedImage scaleImageToMaxSize(BufferedImage originalImage, int maxSize) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        // 如果图像已经适合指定尺寸，则不进行缩放
        if (originalWidth <= maxSize && originalHeight <= maxSize) {
            return originalImage;
        }
        
        // 计算缩放比例
        double scaleX = (double) maxSize / originalWidth;
        double scaleY = (double) maxSize / originalHeight;
        double scale = Math.min(scaleX, scaleY); // 使用较小的比例以保持宽高比
        
        // 计算新的尺寸
        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);
        
        // 创建缩放后的图像
        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();
        
        // 设置渲染提示以获得更好的图像质量
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 绘制缩放后的图像
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        
        return scaledImage;
    }
}