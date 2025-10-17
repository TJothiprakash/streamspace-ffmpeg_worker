package com.jp.ffmpegworker.controller;

import com.jp.ffmpegworker.dto.TranscodeRequest;
import com.jp.ffmpegworker.service.FfmpegService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/ffmpeg")
public class FfmpegController {

    private final FfmpegService ffmpegService;

    public FfmpegController(FfmpegService ffmpegService) {
        this.ffmpegService = ffmpegService;
    }
    @PostMapping("/transcode")
    public ResponseEntity<String> transcodeVideo(@RequestBody TranscodeRequest request) throws IOException, InterruptedException {
        ffmpegService.transcodeToHLS(request.getInputPath());
        return ResponseEntity.ok("Transcoding started");
    }

}
