package main.java.com.example.screenagent;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class ScreenshotController {

    private final ScreenshotService screenshotService;

    @PostMapping("/capture")
    public ResponseEntity<Resource> capture() throws Exception {

        // Capture screenshot and get saved file path
        String filePath = screenshotService.captureScreen();

        Path path = Path.of(filePath);
        Resource resource = new UrlResource(path.toUri());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + path.getFileName())
                .header(HttpHeaders.CONTENT_TYPE, "image/png")
                .body(resource);
    }
}
