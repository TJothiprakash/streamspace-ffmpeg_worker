package com.jp.ffmpegworker.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class TranscodeRequest {
    private String inputPath;
    private String outputFolder;
}
