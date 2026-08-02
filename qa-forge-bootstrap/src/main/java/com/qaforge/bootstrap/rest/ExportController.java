package com.qaforge.bootstrap.rest;

import com.qaforge.domain.port.out.TestFileStorePort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/export} — downloads a repository's active tests as a ZIP (PRD §12.8). */
@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private final TestFileStorePort testFileStorePort;

    public ExportController(TestFileStorePort testFileStorePort) {
        this.testFileStorePort = testFileStorePort;
    }

    @GetMapping
    public ResponseEntity<byte[]> export(@RequestParam String repository,
                                          @RequestParam(required = false) String layer) {
        byte[] zip = testFileStorePort.exportZip(repository);
        String fileName = repository.replace('/', '-') + "-tests.zip";

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(fileName).build().toString())
            .body(zip);
    }
}
