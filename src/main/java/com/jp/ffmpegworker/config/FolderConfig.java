package com.jp.ffmpegworker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FolderConfig {

    @Value("${ffmpeg.bin-folder}")
    private String ffmpegFolder;

    @Value("${video.folder}")
    private String videoFolder;

    @Value("${wasabi.bucket}")
    private String bucketName;

    public String getFfmpegFolder() {
        return ffmpegFolder;
    }

    public String getVideoFolder() {
        return videoFolder;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getFfmpegPath() {
        return ffmpegFolder + "/ffmpeg.exe";
    }

    public String getFfprobePath() {
        return ffmpegFolder + "/ffprobe.exe";
    }
}
