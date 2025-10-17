package com.jp.ffmpegworker.service;

import com.jp.ffmpegworker.config.FolderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@Service
public class S3Service {

    private static final Logger log = LoggerFactory.getLogger(S3Service.class);

    private final FolderConfig folderConfig;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3Service(FolderConfig folderConfig, S3Client s3Client, S3Presigner s3Presigner) {
        this.folderConfig = folderConfig;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    // ✅ Generate presigned URL
    public String generatePresignedUrl(String objectKey) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(folderConfig.getBucketName())
                    .key(objectKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .getObjectRequest(getObjectRequest)
                    .signatureDuration(Duration.ofMinutes(15))
                    .build();

            String url = s3Presigner.presignGetObject(presignRequest).url().toString();
            log.info("Generated presigned URL for {}: {}", objectKey, url);
            return url;

        } catch (Exception e) {
            log.error("❌ Failed to generate presigned URL for {}: {}", objectKey, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    // ✅ Download original file from presigned URL
    public String downloadFile(String presignedUrl) {
        try {
            String fileName = presignedUrl.substring(presignedUrl.lastIndexOf('/') + 1);
            if (fileName.contains("?")) {
                fileName = fileName.substring(0, fileName.indexOf('?'));
            }

            Path localFolder = Paths.get(folderConfig.getVideoFolder());
            if (!localFolder.toFile().exists()) localFolder.toFile().mkdirs();

            Path localFile = localFolder.resolve(fileName);
            log.info("⬇ Downloading {} to {}", presignedUrl, localFile.toAbsolutePath());

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(presignedUrl))
                    .build();

            java.net.http.HttpResponse<Path> response = client.send(
                    request,
                    java.net.http.HttpResponse.BodyHandlers.ofFile(localFile)
            );

            if (response.statusCode() != 200) {
                throw new RuntimeException("Download failed with HTTP " + response.statusCode());
            }

            log.info("✅ Download complete: {}", localFile);
            return localFile.toAbsolutePath().toString();
        } catch (Exception e) {
            log.error("❌ Failed to download {}: {}", presignedUrl, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    // ✅ Uploads transcoded outputs to Wasabi under {videoId}/ folder
    public void uploadVideoOutputFolder(int videoId, String localFilePath) {
        File inputFile = new File(localFilePath);
        File parentFolder = inputFile.getParentFile(); // same folder as transcoded output
        log.info("Uploading HLS output folder for videoId={} from {}", videoId, parentFolder.getAbsolutePath());

        uploadRecursively(parentFolder, parentFolder.getAbsolutePath(), String.valueOf(videoId));

        // 🧹 Cleanup local files after successful upload
        try {
            deleteRecursively(parentFolder);
            log.info("🧹 Cleaned up local transcoded files from {}", parentFolder.getAbsolutePath());
        } catch (Exception e) {
            log.error("⚠️ Failed to clean up local files: {}", e.getMessage(), e);
        }
    }

    // ✅ Upload recursively while prefixing all paths with videoId/
    private void uploadRecursively(File folder, String basePath, String videoIdPrefix) {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                uploadRecursively(file, basePath, videoIdPrefix);
            } else {
                String relativePath = folderRelativePath(file, basePath);
                String key = videoIdPrefix + "/" + relativePath; // Prefix with videoId/
                log.info("⬆ Uploading {} to Wasabi as {}", file.getAbsolutePath(), key);

                try {
                    s3Client.putObject(PutObjectRequest.builder()
                                    .bucket(folderConfig.getBucketName())
                                    .key(key)
                                    .build(),
                            file.toPath());
                } catch (Exception e) {
                    log.error("❌ Failed to upload {}: {}", file.getAbsolutePath(), e.getMessage(), e);
                }
            }
        }
    }

    private String folderRelativePath(File file, String basePath) {
        log.info("recursively deletion starting base folder path : " + file.getAbsolutePath()
                .substring(basePath.length() + 1)
                .replace("\\", "/"));

        return file.getAbsolutePath()
                .substring(basePath.length() + 1)
                .replace("\\", "/");
    }

    // 🧹 Recursive deletion of folder contents
    private void deleteRecursively(File folder) {
        log.info("deleting the folder : " + folder);
        if (folder == null || !folder.exists()) return;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteRecursively(f);
                } else {
                    if (!f.delete()) {
                        log.warn("⚠️ Could not delete file: {}", f.getAbsolutePath());
                    }
                }
            }
        }
        if (!folder.delete()) {
            log.warn("⚠️ Could not delete folder: {}", folder.getAbsolutePath());
        }
    }
}
