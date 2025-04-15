package com.ezetap.server.web.portal;

import com.ezetap.server.web.portal.service.OfferExtractionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class FileProcessingController {
    private static final Logger logger = LoggerFactory.getLogger(FileProcessingController.class);

    @Autowired
    private OfferExtractionService offerExtractionService;

    @PostMapping(value = "/csv/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> processCsvFile(@RequestParam("file") MultipartFile file) {
        logger.info("Received CSV file processing request");
        try {
            if (file == null || file.isEmpty()) {
                logger.error("No file provided in request");
                return ResponseEntity.badRequest().build();
            }

            byte[] fileBytes = file.getBytes();
            byte[] processedFile = offerExtractionService.processCsvFile(fileBytes);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "processed_offers.xlsx");
            
            logger.info("Successfully processed CSV file");
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(processedFile);
        } catch (Exception e) {
            logger.error("Error processing CSV file: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
} 