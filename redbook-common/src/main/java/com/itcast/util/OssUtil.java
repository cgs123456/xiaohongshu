package com.itcast.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import lombok.Data;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.UUID;

/**
 * 阿里云oss工具类
 */
@Data
public class OssUtil {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

    /**
     * OSS 客户端是线程安全的，按配置懒加载单例复用，避免每次上传都新建连接池
     */
    private volatile OSS ossClient;

    private OSS getClient() {
        if (ossClient == null) {
            synchronized (this) {
                if (ossClient == null) {
                    ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
                }
            }
        }
        return ossClient;
    }

    /**
     * 上传图片
     * @param bytes
     * @return
     */
    public String uploadImg(byte[] bytes){
        return uploadImg(bytes, null);
    }

    /**
     * 上传图片
     * @param bytes 图片内容
     * @param originalFilename 原始文件名（用于保留扩展名，可为空）
     * @return 访问 URL
     */
    public String uploadImg(byte[] bytes, String originalFilename){
        String fileName = UUID.randomUUID().toString().concat(resolveExtension(originalFilename));
        OSS client = getClient();
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                bucketName, fileName, new ByteArrayInputStream(bytes));
        client.putObject(putObjectRequest);
        return buildUrl(fileName);
    }

    /**
     * 删除图片
     * @param imageUrl 图片URL
     */
    public void deleteImg(String imageUrl) {
        try {
            String fileName = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
            getClient().deleteObject(bucketName, fileName);
        } catch (Exception e) {
            throw new RuntimeException("删除OSS图片失败: " + imageUrl, e);
        }
    }

    private String buildUrl(String fileName) {
        String host = endpoint;
        if (host.startsWith("http://") || host.startsWith("https://")) {
            host = host.substring(host.indexOf("://") + 3);
        }
        return "https://" + bucketName + "." + host + "/" + fileName;
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null) {
            return ".png";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return ".png";
        }
        String ext = originalFilename.substring(dot).toLowerCase(Locale.ROOT);
        // 只放行常见图片扩展名，避免把非法字符拼进对象名
        if (ext.matches("\\.(png|jpg|jpeg|gif|webp|bmp)")) {
            return ext;
        }
        return ".png";
    }
}
