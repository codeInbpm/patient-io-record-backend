package com.patient.io.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.patient.io.common.BusinessException;
import com.patient.io.config.MinioConfig;
import io.minio.*;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @PostConstruct
    public void initBucket() {
        try {
            String bucketName = minioConfig.getBucketName();
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                log.info("MinIO bucket '{}' 不存在，开始自动创建小程序专属 Bucket...", bucketName);
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

                // 设置公共只读 Policy，使前端/小程序可直接通过 URL 访问图片
                String policy = "{\n" +
                        "  \"Version\": \"2012-10-17\",\n" +
                        "  \"Statement\": [\n" +
                        "    {\n" +
                        "      \"Effect\": \"Allow\",\n" +
                        "      \"Principal\": \"*\",\n" +
                        "      \"Action\": [\"s3:GetObject\"],\n" +
                        "      \"Resource\": [\"arn:aws:s3:::" + bucketName + "/*\"]\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}";
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build());
                log.info("MinIO 专属 Bucket '{}' 创建成功并已配置公共读访问策略", bucketName);
            } else {
                log.info("MinIO 专属 Bucket '{}' 已就绪", bucketName);
            }
        } catch (Exception e) {
            log.error("初始化 MinIO Bucket 异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 上传补液凭据图片
     * @param file 上传的文件
     * @param folder 子目录
     * @return 访问 URL 和 objectKey
     */
    public UploadResult uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        try {
            String originalFilename = file.getOriginalFilename();
            String ext = StrUtil.subAfter(originalFilename, ".", true);
            if (StrUtil.isBlank(ext)) {
                ext = "jpg";
            }
            String datePath = DateUtil.format(new Date(), "yyyyMMdd");
            String subPath = StrUtil.isNotBlank(folder) ? folder : "infusion";
            String objectKey = String.format("%s/%s/%s.%s", subPath, datePath, UUID.fastUUID().toString(true), ext);

            String bucketName = minioConfig.getBucketName();
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectKey)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(file.getContentType() != null ? file.getContentType() : "image/jpeg")
                                .build()
                );
            }

            // 拼接文件访问地址：http://endpoint/bucketName/objectKey
            String endpoint = StrUtil.removeSuffix(minioConfig.getEndpoint(), "/");
            String fileUrl = String.format("%s/%s/%s", endpoint, bucketName, objectKey);

            return new UploadResult(originalFilename, objectKey, fileUrl, file.getSize());
        } catch (Exception e) {
            log.error("MinIO 文件上传失败", e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class UploadResult {
        private String originalFileName;
        private String objectKey;
        private String fileUrl;
        private Long fileSize;
    }
}
