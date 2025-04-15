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
import org.springframework.http.HttpHeaders;
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

    @ApiOperation(value = "Extract JSON from input file or raw text")
    @PostMapping("/extract-json")
    public ResponseEntity<?> extractJson(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "text", required = false) String text) {
        try {
            String jsonResponse;
            if (file != null && !file.isEmpty()) {
                jsonResponse = offerService.extractAndGenerateOfferJson(file);
            } else if (text != null && !text.trim().isEmpty()) {
                jsonResponse = offerService.extractFromRawText(text, false);
            } else {
                return ResponseEntity.badRequest().body("Either file or text must be provided");
            }
            return ResponseEntity.ok(jsonResponse);
        } catch (Exception e) {
            logger.error("Error processing input: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing input: " + e.getMessage());
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
    @PostMapping("/extractText")
    public ResponseEntity<String> extractOffer(@RequestBody String rawText) {
        try {
            String jsonResponse = offerService.extractFromRawText(rawText, false);
            return ResponseEntity.ok(jsonResponse);
        } catch (Exception e) {
            logger.error("Error processing text: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing text: " + e.getMessage());
        }
    }

    @PostMapping("/extract-from-text")
    public ResponseEntity<byte[]> extractFromText(@RequestBody String rawText) {
        try {
            // First get the JSON response for Excel format
            String jsonResponse = offerService.extractFromRawText(rawText, true);
            
            // Generate Excel from the JSON response
            byte[] excelBytes = offerService.generateExcelFromJson(jsonResponse);
            
            // Set response headers for Excel download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "extracted_offers.xlsx");
            headers.setContentLength(excelBytes.length);
            
            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error processing text: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error processing text: " + e.getMessage()).getBytes());
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
                jsonOutput = offerService.extractFromRawText(rawText, false);
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

    @ApiOperation(value = "Extract data from CSV and return Excel file")
    @PostMapping("/extractCsv")
    public ResponseEntity<byte[]> extractCsv(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("No file provided".getBytes());
            }

            // Process the CSV file
            byte[] excelBytes = offerService.processCsvFile(file.getBytes());
            
            // Set response headers for Excel download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "extracted_offers.xlsx");
            headers.setContentLength(excelBytes.length);
            
            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error processing CSV file: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error processing CSV file: " + e.getMessage()).getBytes());
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