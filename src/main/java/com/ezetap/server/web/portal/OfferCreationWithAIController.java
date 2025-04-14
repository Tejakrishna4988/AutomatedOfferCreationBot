package com.ezetap.server.web.portal;

import com.ezetap.server.web.portal.service.OfferExtractionService;
import com.ezetap.shared.api.input.brand.emi.RawTextRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.io.OutputStream;
import java.util.Base64;

@RestController
@RequestMapping("/api/offer")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Api(value = "Offer Creation API", tags = {"Offer Creation"})
public class OfferCreationWithAIController {
    private static final Logger logger = LoggerFactory.getLogger(OfferCreationWithAIController.class);
    
    @Autowired
    private OfferExtractionService offerService;
    private final ObjectMapper objectMapper;

    @Autowired
    public OfferCreationWithAIController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ApiOperation(value = "Extract JSON from input file")
    @PostMapping("/extract-json")
    public ResponseEntity<?> extractJson(@RequestParam("file") MultipartFile file) {
        try {
            String jsonResponse = offerService.extractAndGenerateOfferJson(file);
            return ResponseEntity.ok(jsonResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing file: " + e.getMessage());
        }
    }

    @ApiOperation(value = "Generate Excel from input file")
    @RequestMapping(
        value = "/generateExcel",
        method = RequestMethod.POST,
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public void generateExcel(
            @RequestParam("file") MultipartFile file,
            HttpServletResponse response) {
        logger.info("Received request to generate Excel from file: {}", file.getOriginalFilename());
        try {
            if (file == null || file.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No file provided");
                return;
            }

            String contentType = file.getContentType();
            logger.info("File content type: {}", contentType);
            
            if (contentType == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid file type");
                return;
            }

            byte[] excelBytes;
            if (contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") || 
                contentType.equals("application/vnd.ms-excel")) {
                excelBytes = offerService.processExcelFile(file.getBytes());
            } else if (contentType.equals("text/csv")) {
                excelBytes = offerService.processCsvFile(file.getBytes());
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported file type. Only Excel and CSV files are supported.");
                return;
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=offer_details.xlsx");
            response.setHeader("Content-Length", String.valueOf(excelBytes.length));
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");

            try (ServletOutputStream outputStream = response.getOutputStream()) {
                outputStream.write(excelBytes);
                outputStream.flush();
            }
        } catch (Exception e) {
            logger.error("Error generating Excel: ", e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "Error generating Excel: " + e.getMessage());
            } catch (Exception ex) {
                logger.error("Error sending error response: ", ex);
            }
        }
    }

    @ApiOperation(
        httpMethod = "POST",
        value = "API to extract information from raw text.",
        notes = "This API accepts raw text and extracts structured offer data in JSON format with specific field names and formatting."
    )
    @PostMapping(
        value = "/extractText",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> extractOffer(@RequestBody RawTextRequest request) {
        try {
            String jsonOutput = offerService.extractFromRawText(request.getRawText());
            
            // Validate JSON structure
            JsonNode jsonNode = objectMapper.readTree(jsonOutput);
            validateRequiredFields(jsonNode);
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonOutput);
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Error processing text: " + e.getMessage()));
        }
    }

    @ApiOperation(
        value = "Process input data and return structured JSON",
        notes = "Accepts either a file (Excel/CSV) or raw text input and returns structured offer data in JSON format"
    )
    @PostMapping(
        value = "/processData",
        consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE},
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> processData(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "rawText", required = false) String rawText) {
        try {
            String jsonOutput;
            
            if (file != null && !file.isEmpty()) {
                String contentType = file.getContentType();
                if (contentType == null) {
                    throw new IllegalArgumentException("Invalid file type");
                }
                
                if (contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") || 
                    contentType.equals("application/vnd.ms-excel") ||
                    contentType.equals("text/csv")) {
                    jsonOutput = offerService.extractAndGenerateOfferJson(file);
                } else {
                    throw new IllegalArgumentException("Unsupported file type");
                }
            } else if (rawText != null && !rawText.trim().isEmpty()) {
                jsonOutput = offerService.extractFromRawText(rawText);
            } else {
                throw new IllegalArgumentException("No input provided");
            }

            // Validate JSON structure
            JsonNode jsonNode = objectMapper.readTree(jsonOutput);
            validateRequiredFields(jsonNode);
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonOutput);
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Error processing input: " + e.getMessage()));
        }
    }

    @PostMapping("/extract-from-text")
    public ResponseEntity<?> extractFromText(@RequestBody String rawText, HttpServletResponse response) {
        try {
            // Extract JSON from raw text
            String jsonResponse = offerService.extractFromRawText(rawText);
            
            // Create Excel from JSON
            byte[] excelBytes = offerService.generateExcelFromJson(jsonResponse);
            
            // Set response headers for Excel download
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=extracted_offers.xlsx");
            response.setContentLength(excelBytes.length);
            
            // Write Excel data to response
            try (OutputStream outputStream = response.getOutputStream()) {
                outputStream.write(excelBytes);
                outputStream.flush();
            }
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing text: " + e.getMessage());
        }
    }

    private void validateRequiredFields(JsonNode json) throws IllegalArgumentException {
        String[] requiredFields = {
            "sku_code", "min_amount", "bank_name", "card_type",
            "full_swipe_offer_amount_type", "emi_offer_amount_type",
            "full_swipe_offer_value", "emi_offer_value",
            "start_date", "end_date"
        };

        for (String field : requiredFields) {
            if (!json.has(field) || json.get(field).asText().trim().isEmpty()) {
                throw new IllegalArgumentException("Missing required field: " + field);
            }
        }

        // Validate date formats
        validateDateFormat(json.get("start_date").asText());
        validateDateFormat(json.get("end_date").asText());
    }

    private void validateDateFormat(String date) throws IllegalArgumentException {
        if (!date.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            throw new IllegalArgumentException("Invalid date format. Expected format: yyyy-MM-dd HH:mm:ss");
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ErrorResponse handleIllegalArgumentException(IllegalArgumentException e) {
        return new ErrorResponse(e.getMessage());
    }

    private static class ErrorResponse {
        private final String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }
    }
} 