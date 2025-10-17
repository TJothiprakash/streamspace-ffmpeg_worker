package com.jp.ffmpegworker.service;

import com.jp.ffmpegworker.config.FolderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class FfmpegService {

    private static final Logger log = LoggerFactory.getLogger(FfmpegService.class);

    private static final int[] RESOLUTIONS = {144, 240, 360, 480, 720, 1080};

    private final FolderConfig folderConfig;

    public FfmpegService(FolderConfig folderConfig) {
        this.folderConfig = folderConfig;
    }

    public void transcodeToHLS(String inputPath) throws IOException, InterruptedException {
        File folder = new File(inputPath).getParentFile();

        log.info("🎬 Starting transcoding for {}", inputPath);

        int inputHeight = getVideoHeight(inputPath);
        log.info("Input video height: {}", inputHeight);

        List<Integer> targetResolutions = new ArrayList<>();
        for (int res : RESOLUTIONS) if (res <= inputHeight) targetResolutions.add(res);

        log.info("Target resolutions for ABR: {}", targetResolutions);

        List<String> variantPlaylists = new ArrayList<>();

        for (int i = 0; i < targetResolutions.size(); i++) {
            int res = targetResolutions.get(i);
            String resFolderPath = folder.getAbsolutePath() + "/" + res + "p";
            File resFolder = new File(resFolderPath);
            if (!resFolder.exists()) resFolder.mkdirs();

            String variantPlaylist = res + "p/" + res + "p.m3u8";
            variantPlaylists.add(variantPlaylist);

            List<String> command = new ArrayList<>();
            command.add(folderConfig.getFfmpegPath());
            command.add("-i");
            command.add(inputPath);
            command.add("-vf");
            command.add("scale=-2:" + res);
            command.add("-c:v");
            command.add("libx264");
            command.add("-c:a");
            command.add("aac");
            command.add("-ar");
            command.add("48000");

            int bitrate = 400 * (i + 1);
            command.add("-b:v");
            command.add(bitrate + "k");
            command.add("-b:a");
            command.add("128k");

            command.add("-hls_time");
            command.add("6");
            command.add("-hls_playlist_type");
            command.add("vod");
            command.add("-hls_segment_filename");
            command.add(resFolderPath + "/" + res + "p_%03d.ts");
            command.add(folder.getAbsolutePath() + "/" + variantPlaylist);

            log.info("Running FFmpeg for {}p...", res);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) log.info(line);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) log.error("❌ FFmpeg exited with code {} for {}p", exitCode, res);
        }

        createMasterPlaylist(folder.getAbsolutePath(), variantPlaylists, targetResolutions);
        log.info("✅ All variants created successfully.");
    }

    private void createMasterPlaylist(String outputFolder, List<String> playlists, List<Integer> resolutions) throws IOException {
        File master = new File(outputFolder + "/master.m3u8");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(master))) {
            writer.write("#EXTM3U\n");
            writer.write("#EXT-X-VERSION:3\n");
            for (int i = 0; i < playlists.size(); i++) {
                int res = resolutions.get(i);
                int bitrate = 400 * (i + 1) * 1000;
                writer.write("#EXT-X-STREAM-INF:BANDWIDTH=" + bitrate + ",RESOLUTION=1920x" + res + "\n");
                writer.write(playlists.get(i) + "\n");
            }
        }
        log.info("✅ Master playlist created: master.m3u8");
    }

    private int getVideoHeight(String inputPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                folderConfig.getFfprobePath(),
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=height",
                "-of", "csv=p=0",
                inputPath
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            if (line != null) return Integer.parseInt(line.trim());
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) throw new RuntimeException("ffprobe failed with exit code " + exitCode);
        return 240; // fallback
    }
}
