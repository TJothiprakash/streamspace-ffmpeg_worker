package com.jp.ffmpegworker.dto;

import lombok.Data;

@Data
public class TranscodeRequest {
    private String inputPath;
    private String outputFolder;
}
