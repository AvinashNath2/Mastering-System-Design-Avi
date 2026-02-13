package main.java.com.example.screenagent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScreenshotService {

    private final ScreenConfig config;

    public String captureScreen() throws Exception {

        Files.createDirectories(Path.of(config.getDir()));

        String fileName = "screen_" + System.currentTimeMillis() + ".png";
        String fullPath = config.getDir() + "/" + fileName;

        ProcessBuilder pb = new ProcessBuilder(
                "screencapture",
                "-x",
                fullPath
        );

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Screenshot capture failed");
        }

        log.info("Screenshot saved at {}", fullPath);

        return fullPath;
    }
}
