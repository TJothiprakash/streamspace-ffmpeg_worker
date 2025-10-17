package com.jp.ffmpegworker.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jp.ffmpegworker.service.FfmpegService;
import com.jp.ffmpegworker.service.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TranscodeListener {

    private static final Logger log = LoggerFactory.getLogger(TranscodeListener.class);

    private final FfmpegService ffmpegService;
    private final S3Service s3Service;

    public TranscodeListener(FfmpegService ffmpegService, S3Service s3Service) {
        this.ffmpegService = ffmpegService;
        this.s3Service = s3Service;
    }

    @RabbitListener(queues = "#{T(com.jp.ffmpegworker.config.RabbitConfig).TRANSCODE_QUEUE}")
    public void processVideo(String message) {
        log.info("🔹 Received task payload: {}", message);

        try {
            // 1️⃣ Parse JSON payload
            ObjectMapper mapper = new ObjectMapper();
            JsonNode payload = mapper.readTree(message);

            int videoId = payload.get("videoId").asInt();
            String s3Key = payload.get("s3Key").asText();
            log.info("Parsed videoId={} and s3Key={}", videoId, s3Key);

            // 2️⃣ Generate presigned URL
            String presignedUrl = s3Service.generatePresignedUrl(s3Key);

            // 3️⃣ Download original file
            String localFilePath = s3Service.downloadFile(presignedUrl);
            log.info("✅ Downloaded file locally: {}", localFilePath);

            // 4️⃣ Transcode to HLS (creates master.m3u8, 240p, 360p, etc.)
            ffmpegService.transcodeToHLS(localFilePath);

            // 5️⃣ Upload the entire folder under videoId/
            s3Service.uploadVideoOutputFolder(videoId, localFilePath);

            log.info("✅ Finished transcoding & uploading for videoId={}", videoId);

        } catch (Exception e) {
            log.error("❌ Error processing message: {}", message, e);
        }
    }
}
