package com.ezetap.server.web.portal.service;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.AzureKeyCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Base64;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;

@Service
public class OfferExtractionService {
    private static final Logger logger = LoggerFactory.getLogger(OfferExtractionService.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Value("${azure.openai.endpoint}")
    private String endpoint;

    @Value("${azure.openai.key}")
    private String key;

    @Value("${azure.openai.deployment-id}")
    private String deploymentId;

    @Value("${azure.openai.api-version}")
    private String apiVersion;

    private OpenAIClient getClient() {
        return new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(key))
                .buildClient();
    }

    public String extractAndGenerateOfferJson(MultipartFile file) throws Exception {
        // Convert MultipartFile to ByteArrayInputStream
        ByteArrayInputStream inputStream = new ByteArrayInputStream(file.getBytes());
        String fileContent = readExcelFile(inputStream);
        logger.info("Excel Content:\n{}", fileContent);
        
        String prompt = "You are an expert at analyzing Excel data and converting it into structured JSON format. " +
            "Your task is to analyze the following Excel data and extract offer details into a specific JSON format.\n\n" +
            "CRITICAL INSTRUCTIONS:\n" +
            "1. The response MUST be a single JSON object\n" +
            "2. All fields must be present in the response\n" +
            "3. Dates must be in YYYY-MM-DD format\n" +
            "4. Extract the brand name from the data\n" +
            "5. Determine if it's an Instant Discount or Additional Cashback\n" +
            "6. Create a descriptive offer description\n" +
            "7. Generate an appropriate offer code\n\n" +
            "Required JSON Format:\n" +
            "{\n" +
            "  \"brand\": \"Brand name (e.g., Xiaomi)\",\n" +
            "  \"offerType\": \"Instant Discount or Additional Cashback\",\n" +
            "  \"offerStartDate\": \"YYYY-MM-DD\",\n" +
            "  \"offerEndDate\": \"YYYY-MM-DD\",\n" +
            "  \"offerDescription\": \"Detailed description of the offer\",\n" +
            "  \"orgAcquisitionType\": \"Brand led/Direct/Bank Led\",\n" +
            "  \"velocityCheckType\": \"PERDAY/PERMONTH/None\",\n" +
            "  \"commonVelocityEnabled\": boolean,\n" +
            "  \"velocityCheckApplied\": boolean,\n" +
            "  \"velocityCheckCount\": number,\n" +
            "  \"priority\": \"High/Medium/Low\",\n" +
            "  \"offerCode\": \"Generated offer code\"\n" +
            "}\n\n" +
            "Excel Data:\n" + fileContent;

        try {
            String response = callAIService(prompt);
            logger.info("AI Response:\n{}", response);
            
            // Validate the response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(response);
            
            // Validate required fields
            String[] requiredFields = {
                "brand", "offerType", "offerStartDate", "offerEndDate",
                "offerDescription", "orgAcquisitionType", "velocityCheckType",
                "commonVelocityEnabled", "velocityCheckApplied", "velocityCheckCount",
                "priority", "offerCode"
            };
            
            for (String field : requiredFields) {
                if (!node.has(field)) {
                    throw new IOException("Missing required field: " + field);
                }
            }
            
            return response;
        } catch (Exception e) {
            logger.error("Error processing response: {}", e.getMessage());
            throw new Exception("Error processing response: " + e.getMessage());
        }
    }

    private byte[] createExcelFromJson(String jsonResponse) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Extracted Offers");
            
            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setWrapText(true);

            // Define headers with exact format
            String[] headers = {
                "Sku Code (All/Specific SKU/NA)*",
                "Min Amount*",
                "Max Amount",
                "Include States",
                "Exclude States",
                "Bank Name (All/Specific Bank/Few Banks)*",
                "Card Type (Credit/Debit/Both)",
                "Full Swipe Offer Amount Type (Fixed/Percentage)*",
                "Full Swipe Offer Value",
                "Full Swipe Offer Max Amount (Percentage Type Case)",
                "EMI Offer Amount Type (Fixed/Percentage)*",
                "EMI Offer Value",
                "EMI Offer Max Amount (Percentage Type Case)",
                "Full Swipe Subvention Type (Fixed/Percentage)",
                "Full Swipe Bank Subvention Value",
                "Full Swipe Brand Subvention Value",
                "EMI Subvention Type (Fixed/Percentage)",
                "EMI Bank Subvention Value",
                "EMI Brand Subvention Value",
                "Start Date(yyyy-MM-dd HH:mm:sss)",
                "End Date(yyyy-MM-dd HH:mm:sss)"
            };

            // Create header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 256 * 30); // 30 characters width
            }
            headerRow.setHeight((short) 900); // 45 points height

            // Parse JSON and populate data rows
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            
            int rowNum = 1;
            for (JsonNode offer : rootNode) {
                Row row = sheet.createRow(rowNum++);
                populateRowFromJson(row, offer);
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void populateRowFromJson(Row row, JsonNode offerNode) {
        int columnIndex = 0;
        
        // Create cells for each field
        createCell(row, columnIndex++, offerNode.get("sku_code"));
        createCell(row, columnIndex++, offerNode.get("min_amount"));
        createCell(row, columnIndex++, offerNode.get("max_amount"));
        createCell(row, columnIndex++, offerNode.get("include_states"));
        createCell(row, columnIndex++, offerNode.get("exclude_states"));
        createCell(row, columnIndex++, offerNode.get("bank_name"));
        createCell(row, columnIndex++, offerNode.get("card_type"));
        createCell(row, columnIndex++, offerNode.get("full_swipe_offer_amount_type"));
        createCell(row, columnIndex++, offerNode.get("full_swipe_offer_value"));
        createCell(row, columnIndex++, offerNode.get("full_swipe_offer_max_amount"));
        createCell(row, columnIndex++, offerNode.get("emi_offer_amount_type"));
        createCell(row, columnIndex++, offerNode.get("emi_offer_value"));
        createCell(row, columnIndex++, offerNode.get("emi_offer_max_amount"));
        createCell(row, columnIndex++, offerNode.get("full_swipe_subvention_type"));
        createCell(row, columnIndex++, offerNode.get("full_swipe_bank_subvention_value"));
        createCell(row, columnIndex++, offerNode.get("full_swipe_brand_subvention_value"));
        createCell(row, columnIndex++, offerNode.get("emi_subvention_type"));
        createCell(row, columnIndex++, offerNode.get("emi_bank_subvention_value"));
        createCell(row, columnIndex++, offerNode.get("emi_brand_subvention_value"));
        createCell(row, columnIndex++, offerNode.get("start_date"));
        createCell(row, columnIndex++, offerNode.get("end_date"));
    }

    private void createCell(Row row, int columnIndex, JsonNode node) {
        Cell cell = row.createCell(columnIndex);
        if (node != null && !node.isNull()) {
            String value = node.asText().trim();
            // Try to parse as number if it looks like one
            if (value.matches("-?\\d+(\\.\\d+)?")) {
                try {
                    double numValue = Double.parseDouble(value);
                    cell.setCellValue(numValue);
                    return;
                } catch (NumberFormatException e) {
                    // Not a number, continue with string handling
                }
            }
            // Handle date format
            if (value.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                try {
                    cell.setCellValue(value);
                    CellStyle dateStyle = row.getSheet().getWorkbook().createCellStyle();
                    dateStyle.setDataFormat((short)14); // mm/dd/yyyy
                    cell.setCellStyle(dateStyle);
                    return;
                } catch (Exception e) {
                    // Not a valid date, continue with string handling
                }
            }
            // Default to string value
            cell.setCellValue(value);
        } else {
            cell.setCellValue("");
        }
    }

    private void createCell(Row row, int columnIndex, String value) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value != null ? value : "");
    }

    private Map<String, Integer> getHeaderMap(Row headerRow) {
        Map<String, Integer> headerMap = new HashMap<>();
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                String headerName = cell.getStringCellValue().trim();
                headerMap.put(headerName, cell.getColumnIndex());
            }
        }
        return headerMap;
    }

    private String getCellValue(Row row, Map<String, Integer> headerMap, String headerName) {
        Integer colIndex = headerMap.get(headerName);
        if (colIndex != null) {
            Cell cell = row.getCell(colIndex);
            return getCellValueAsString(cell);
        }
        return "";
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return DATE_FORMAT.format(cell.getDateCellValue());
                }
                // Remove decimal if it's a whole number
                double value = cell.getNumericCellValue();
                if (value == (long) value) {
                    return String.format("%d", (long) value);
                }
                return String.valueOf(value);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        return cell.getStringCellValue();
                    } catch (Exception ex) {
                        return "";
                    }
                }
            default:
                return "";
        }
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return "";
        }
        // If the date already matches our format, return it
        if (dateStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            return dateStr;
        }
        try {
            // Parse the date and format it as required
            Date date = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
            return DATE_FORMAT.format(date);
        } catch (Exception e) {
            logger.error("Error parsing date: " + dateStr, e);
            return dateStr;
        }
    }

    private String readCsvFile(MultipartFile file) throws IOException {
        StringBuilder csvText = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                csvText.append(line).append("\n");
            }
        }
        return csvText.toString();
    }

    private String readExcelFile(ByteArrayInputStream inputStream) throws IOException {
        StringBuilder excelText = new StringBuilder();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                int rowCount = sheet.getLastRowNum() + 1; // +1 because getLastRowNum is 0-based
                int dataRowCount = 0;
                
                // Count non-empty rows
                for (Row row : sheet) {
                    boolean hasData = false;
                    for (Cell cell : row) {
                        if (cell != null && cell.getCellType() != CellType.BLANK) {
                            hasData = true;
                            break;
                        }
                    }
                    if (hasData) dataRowCount++;
                }
                
                logger.info("Sheet {}: {} - Total Rows: {}, Data Rows: {}", 
                    sheetIndex + 1, sheet.getSheetName(), rowCount, dataRowCount);
                
                excelText.append("\n\n=== Sheet ").append(sheetIndex + 1)
                    .append(": ").append(sheet.getSheetName())
                    .append(" (Total Rows: ").append(rowCount)
                    .append(", Data Rows: ").append(dataRowCount).append(") ===\n\n");
                
                // Add column headers
                Row headerRow = sheet.getRow(0);
                if (headerRow != null) {
                    excelText.append("Headers: ");
                    for (Cell cell : headerRow) {
                        excelText.append(getCellValueAsString(cell)).append("\t");
                    }
                    excelText.append("\n\n");
                }
                
                // Add data rows
                for (Row row : sheet) {
                    boolean rowHasData = false;
                    StringBuilder rowText = new StringBuilder();
                    for (Cell cell : row) {
                        String cellValue = getCellValueAsString(cell);
                        if (!cellValue.isEmpty()) rowHasData = true;
                        rowText.append(cellValue).append("\t");
                    }
                    if (rowHasData) {
                        excelText.append("Row ").append(row.getRowNum() + 1).append(": ").append(rowText).append("\n");
                    }
                }
            }
        }
        return excelText.toString();
    }

    public String extractFromRawText(String rawText, boolean isExcelFormat) throws IOException {
        logger.info("Extracting JSON from raw text: {}", rawText);
        
        String prompt;
        if (isExcelFormat) {
            prompt = "You are a business assistant AI. Your task is to extract structured offer data from raw text. " +
                "The text may contain information about multiple offers that need to be processed individually.\n\n" +
                "### INSTRUCTIONS:\n" +
                "1. Process **every distinct offer** mentioned in the text.\n" +
                "2. Each offer should be mapped to **one JSON object**.\n" +
                "3. Your final output must be a **JSON array** of multiple offer objects.\n" +
                "4. Do **not** merge or combine information across different offers.\n" +
                "5. If the text describes 4 offers, your output must have 4 JSON objects.\n" +
                "6. **DO NOT OMIT ANY OFFER** – include all, even if some fields are missing.\n" +
                "7. If a value is missing, return it as an **empty string** in the JSON.\n\n" +
                "### SPECIAL CLARIFICATION FOR `sku_code`:\n" +
                "- If the text mentions specific products, combine product name, variant, and Product ID like:\n" +
                "  `\"Xiaomi Pad 6|6GB+128GB|47867\"` or `\"Redmi Pad|4GB+128GB|43553\"`\n" +
                "- Use this combined value as the **`sku_code`** field.\n" +
                "  - If an offer applies to multiple SKUs, list them as comma-separated.\n" +
                "  - If the offer applies to all products, use `\"All\"`.\n" +
                "  - If no SKU info is present, use `\"NA\"`.\n\n" +
                "### YOUR OUTPUT MUST FOLLOW THIS EXACT JSON STRUCTURE:\n" +
                "[\n" +
                "  {\n" +
                "    \"sku_code\": \"\",\n" +
                "    \"min_amount\": \"\",\n" +
                "    \"max_amount\": \"\",\n" +
                "    \"include_states\": \"\",\n" +
                "    \"exclude_states\": \"\",\n" +
                "    \"bank_name\": \"\",\n" +
                "    \"card_type\": \"\",\n" +
                "    \"full_swipe_offer_amount_type\": \"\",\n" +
                "    \"full_swipe_offer_value\": \"\",\n" +
                "    \"full_swipe_offer_max_amount\": \"\",\n" +
                "    \"emi_offer_amount_type\": \"\",\n" +
                "    \"emi_offer_value\": \"\",\n" +
                "    \"emi_offer_max_amount\": \"\",\n" +
                "    \"full_swipe_subvention_type\": \"\",\n" +
                "    \"full_swipe_bank_subvention_value\": \"\",\n" +
                "    \"full_swipe_brand_subvention_value\": \"\",\n" +
                "    \"emi_subvention_type\": \"\",\n" +
                "    \"emi_bank_subvention_value\": \"\",\n" +
                "    \"emi_brand_subvention_value\": \"\",\n" +
                "    \"start_date\": \"\",\n" +
                "    \"end_date\": \"\"\n" +
                "  }\n" +
                "]\n\n" +
                "### ADDITIONAL INSTRUCTIONS:\n" +
                "1. For dates, use format: YYYY-MM-DD HH:mm:ss\n" +
                "2. For amount fields, use numbers without currency symbols\n" +
                "3. For percentage fields, use the word \"Percentage\"\n" +
                "4. For fixed amount fields, use the word \"Fixed\"\n" +
                "5. For card type, use \"Credit\", \"Debit\", or \"Both\"\n" +
                "6. For bank name, use the actual bank name or \"All\"\n\n" +
                "Text Data:\n" + rawText + "\n\n" +
                "Please analyze the text and return a JSON array of offer objects with the exact structure shown above.";
        } else {
            prompt = "You are an expert at analyzing text and converting it into structured JSON format. " +
                "Your task is to analyze the following text and convert it into a JSON object with the following structure:\n\n" +
                "{\n" +
                "  \"brand\": \"Brand name (e.g., OPPO)\",\n" +
                "  \"offerType\": \"Additional Cashback\",\n" +
                "  \"offerStartDate\": \"YYYY-MM-DD\",\n" +
                "  \"offerEndDate\": \"YYYY-MM-DD\",\n" +
                "  \"offerDescription\": \"Detailed description of the offer\",\n" +
                "  \"orgAcquisitionType\": \"Direct\",\n" +
                "  \"velocityCheckType\": \"PERDAY\",\n" +
                "  \"commonVelocityEnabled\": true,\n" +
                "  \"velocityCheckApplied\": \"Per Transaction\",\n" +
                "  \"velocityCheckCount\": 1,\n" +
                "  \"priority\": 1,\n" +
                "  \"offerCode\": \"Generated offer code (e.g., BRAND_YYYY-MM-DD_YYYY-MM-DD)\"\n" +
                "}\n\n" +
                "CRITICAL INSTRUCTIONS:\n" +
                "1. The response MUST be a single JSON object\n" +
                "2. All fields must be present in the response\n" +
                "3. Dates must be in YYYY-MM-DD format\n" +
                "4. offerType should always be \"Additional Cashback\"\n" +
                "5. orgAcquisitionType should always be \"Direct\"\n" +
                "6. velocityCheckType should always be \"PERDAY\"\n" +
                "7. commonVelocityEnabled should always be true\n" +
                "8. velocityCheckApplied should always be \"Per Transaction\"\n" +
                "9. velocityCheckCount should always be 1\n" +
                "10. priority should always be 1\n" +
                "11. offerCode should be generated based on brand and dates\n\n" +
                "Text Data:\n" + rawText + "\n\n" +
                "Please analyze the text and return a JSON object with the exact structure shown above.";
        }

        try {
            String response = callAIService(prompt);
            logger.info("AI Response:\n{}", response);
            
            // Validate that the response is valid JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(response);
            
            if (isExcelFormat) {
                if (!node.isArray()) {
                    // If the response is not an array, wrap it in an array
                    ArrayNode arrayNode = mapper.createArrayNode();
                    arrayNode.add(node);
                    response = mapper.writeValueAsString(arrayNode);
                    node = mapper.readTree(response);
                }
            } else {
                if (!node.isObject()) {
                    throw new IOException("Invalid JSON response format: Expected an object");
                }
            }
            
            // Validate required fields based on format
            String[] requiredFields;
            if (isExcelFormat) {
                requiredFields = new String[]{
                    "sku_code", "min_amount", "max_amount", "include_states", "exclude_states",
                    "bank_name", "card_type", "full_swipe_offer_amount_type", "full_swipe_offer_value",
                    "full_swipe_offer_max_amount", "emi_offer_amount_type", "emi_offer_value",
                    "emi_offer_max_amount", "full_swipe_subvention_type", "full_swipe_bank_subvention_value",
                    "full_swipe_brand_subvention_value", "emi_subvention_type", "emi_bank_subvention_value",
                    "emi_brand_subvention_value", "start_date", "end_date"
                };
                
                // Validate each object in the array
                for (JsonNode offerNode : node) {
                    for (String field : requiredFields) {
                        if (!offerNode.has(field)) {
                            throw new IOException("Missing required field: " + field);
                        }
                    }
                }
            } else {
                requiredFields = new String[]{
                    "brand", "offerType", "offerStartDate", "offerEndDate",
                    "offerDescription", "orgAcquisitionType", "velocityCheckType",
                    "commonVelocityEnabled", "velocityCheckApplied", "velocityCheckCount",
                    "priority", "offerCode"
                };
                
                for (String field : requiredFields) {
                    if (!node.has(field)) {
                        throw new IOException("Missing required field: " + field);
                    }
                }
            }
            
            return response;
        } catch (Exception e) {
            logger.error("Error processing text: {}", e.getMessage());
            throw new IOException("Error processing text: " + e.getMessage());
        }
    }

    public byte[] generateExcelFromJson(String jsonResponse) throws IOException {
        logger.info("Generating Excel from JSON response");
        logger.info("Raw JSON response: {}", jsonResponse);
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Offers");
            
            // Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            logger.info("Parsed JSON structure: {}", rootNode.toString());
            
            if (!rootNode.isArray()) {
                logger.info("Converting single object to array");
                ArrayNode arrayNode = mapper.createArrayNode();
                arrayNode.add(rootNode);
                rootNode = arrayNode;
            }
            
            logger.info("Number of offers to process: {}", rootNode.size());
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "Sku Code (All/Specific SKU/NA)*",
                "Min Amount*",
                "Max Amount",
                "Include States",
                "Exclude States",
                "Bank Name (All/Specific Bank/Few Banks)*",
                "Card Type (Credit/Debit/Both)",
                "Full Swipe Offer Amount Type (Fixed/Percentage)*",
                "Full Swipe Offer Value",
                "Full Swipe Offer Max Amount (Percentage Type Case)",
                "EMI Offer Amount Type (Fixed/Percentage)*",
                "EMI Offer Value",
                "EMI Offer Max Amount (Percentage Type Case)",
                "Full Swipe Subvention Type (Fixed/Percentage)",
                "Full Swipe Bank Subvention Value",
                "Full Swipe Brand Subvention Value",
                "EMI Subvention Type (Fixed/Percentage)",
                "EMI Bank Subvention Value",
                "EMI Brand Subvention Value",
                "Start Date(yyyy-MM-dd HH:mm:sss)",
                "End Date(yyyy-MM-dd HH:mm:sss)"
            };
            
            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setWrapText(true);
            
            // Populate header row
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 256 * 30); // 30 characters width
            }
            headerRow.setHeight((short) 900); // 45 points height
            
            // Handle both single object and array cases
            if (rootNode.isArray()) {
                // Process each object in the array
                int rowNum = 1;
                for (JsonNode offerNode : rootNode) {
                    Row dataRow = sheet.createRow(rowNum++);
                    int colNum = 0;
                    
                    // Map the JSON fields to Excel columns
                    createCell(dataRow, colNum++, offerNode.get("sku_code"));
                    createCell(dataRow, colNum++, offerNode.get("min_amount"));
                    createCell(dataRow, colNum++, offerNode.get("max_amount"));
                    createCell(dataRow, colNum++, offerNode.get("include_states"));
                    createCell(dataRow, colNum++, offerNode.get("exclude_states"));
                    createCell(dataRow, colNum++, offerNode.get("bank_name"));
                    createCell(dataRow, colNum++, offerNode.get("card_type"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_offer_amount_type"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_offer_value"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_offer_max_amount"));
                    createCell(dataRow, colNum++, offerNode.get("emi_offer_amount_type"));
                    createCell(dataRow, colNum++, offerNode.get("emi_offer_value"));
                    createCell(dataRow, colNum++, offerNode.get("emi_offer_max_amount"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_subvention_type"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_bank_subvention_value"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_brand_subvention_value"));
                    createCell(dataRow, colNum++, offerNode.get("emi_subvention_type"));
                    createCell(dataRow, colNum++, offerNode.get("emi_bank_subvention_value"));
                    createCell(dataRow, colNum++, offerNode.get("emi_brand_subvention_value"));
                    createCell(dataRow, colNum++, offerNode.get("start_date"));
                    createCell(dataRow, colNum++, offerNode.get("end_date"));
                }
            } else {
                // Handle single object case
                Row dataRow = sheet.createRow(1);
                int colNum = 0;
                
                // Map the JSON fields to Excel columns
                createCell(dataRow, colNum++, rootNode.get("sku_code"));
                createCell(dataRow, colNum++, rootNode.get("min_amount"));
                createCell(dataRow, colNum++, rootNode.get("max_amount"));
                createCell(dataRow, colNum++, rootNode.get("include_states"));
                createCell(dataRow, colNum++, rootNode.get("exclude_states"));
                createCell(dataRow, colNum++, rootNode.get("bank_name"));
                createCell(dataRow, colNum++, rootNode.get("card_type"));
                createCell(dataRow, colNum++, rootNode.get("full_swipe_offer_amount_type"));
                createCell(dataRow, colNum++, rootNode.get("full_swipe_offer_value"));
                createCell(dataRow, colNum++, rootNode.get("full_swipe_offer_max_amount"));
                createCell(dataRow, colNum++, rootNode.get("emi_offer_amount_type"));
                createCell(dataRow, colNum++, rootNode.get("emi_offer_value"));
                createCell(dataRow, colNum++, rootNode.get("emi_offer_max_amount"));
                createCell(dataRow, colNum++, rootNode.get("full_swipe_subvention_type"));
                createCell(dataRow, colNum++, rootNode.get("full_swipe_bank_subvention_value"));
                createCell(dataRow, colNum++, rootNode.get("full_swipe_brand_subvention_value"));
                createCell(dataRow, colNum++, rootNode.get("emi_subvention_type"));
                createCell(dataRow, colNum++, rootNode.get("emi_bank_subvention_value"));
                createCell(dataRow, colNum++, rootNode.get("emi_brand_subvention_value"));
                createCell(dataRow, colNum++, rootNode.get("start_date"));
                createCell(dataRow, colNum++, rootNode.get("end_date"));
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            logger.error("Error generating Excel from JSON: ", e);
            throw new IOException("Error generating Excel: " + e.getMessage());
        }
    }

    private String extractBankNames(String offerDescription) {
        // Extract bank names from the offer description
        String[] banks = {"SBI Cards", "HDFC Bank", "Axis Bank", "BOB Cards", 
                         "IDFC First Bank", "Federal Bank", "DBS Cards"};
        List<String> foundBanks = new ArrayList<>();
        
        for (String bank : banks) {
            if (offerDescription.contains(bank)) {
                foundBanks.add(bank);
            }
        }
        
        if (foundBanks.isEmpty()) {
            return "All";
        } else if (foundBanks.size() == 1) {
            return foundBanks.get(0);
        } else {
            return String.join(", ", foundBanks);
        }
    }

    private String callAIService(String prompt) throws Exception {
        OpenAIClient client = getClient();
        
        List<ChatRequestMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new ChatRequestUserMessage(prompt));

        ChatCompletionsOptions options = new ChatCompletionsOptions(chatMessages)
                .setTemperature(0.3);

        ChatCompletions chatCompletions = client.getChatCompletions(deploymentId, options);
        
        if (chatCompletions.getChoices() != null && !chatCompletions.getChoices().isEmpty()) {
            String response = chatCompletions.getChoices().get(0).getMessage().getContent();
            // Clean the response to ensure it's valid JSON
            return cleanJsonResponse(response);
        }
        
        throw new Exception("No response from Azure OpenAI");
    }

    private String cleanJsonResponse(String response) throws IOException {
        try {
            // First try to parse the response directly
            ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(response);
            return response;
        } catch (Exception e) {
            // If direct parsing fails, try to extract JSON from the response
            logger.warn("Initial JSON parsing failed, attempting to extract JSON from response");
            logger.info("Raw response before cleaning: {}", response);
            
            // Look for JSON-like content in the response
            int startIndex = response.indexOf("[");
            int endIndex = response.lastIndexOf("]");
            
            if (startIndex == -1 || endIndex == -1) {
                startIndex = response.indexOf("{");
                endIndex = response.lastIndexOf("}");
            }
            
            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                String potentialJson = response.substring(startIndex, endIndex + 1);
                try {
                    // Validate the extracted content
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.readTree(potentialJson);
                    logger.info("Successfully extracted and validated JSON: {}", potentialJson);
                    return potentialJson;
                } catch (Exception ex) {
                    logger.error("Failed to extract valid JSON from response: {}", ex.getMessage());
                    throw new IOException("Could not extract valid JSON from AI response");
                }
            }
            
            logger.error("No valid JSON found in response");
            throw new IOException("No valid JSON found in AI response");
        }
    }

    public byte[] processExcelFile(byte[] fileBytes) throws IOException {
        logger.info("Starting Excel file processing");
        try {
            // Validate file content
            if (fileBytes == null || fileBytes.length == 0) {
                throw new IllegalArgumentException("File is empty");
            }

            // Read Excel content
            String excelContent = readExcelFile(new ByteArrayInputStream(fileBytes));
            logger.info("Excel Content (first 1000 chars):\n{}", excelContent.substring(0, Math.min(1000, excelContent.length())));

            // Create AI prompt
            String prompt = "You are a business assistant AI. Your task is to extract structured offer data from a product offer sheet provided in Excel format. Each row in the Excel sheet represents a distinct offer entry and must be processed individually.\n\n" +
                "### INSTRUCTIONS:\n" +
                "1. Process **every row** in every sheet.\n" +
                "2. Each row should be mapped to **one JSON object**.\n" +
                "3. Your final output must be a **JSON array** of multiple offer objects.\n" +
                "4. Do **not** merge or combine information across rows.\n" +
                "5. If a sheet has 4 rows, your output must have 4 JSON objects.\n" +
                "6. **DO NOT OMIT ANY ROW** – include all, even if some fields are missing.\n" +
                "7. If a value is missing, return it as an **empty string** in the JSON.\n\n" +
                "### SPECIAL CLARIFICATION FOR `sku_code`:\n" +
                "- Each row contains the product name, variant, and Product ID. Combine these to form the SKU like:\n" +
                "  `\"Xiaomi Pad 6|6GB+128GB|47867\"` or `\"Redmi Pad|4GB+128GB|43553\"`\n" +
                "- Use this combined value as the **`sku_code`** field.\n" +
                "- If a row applies to multiple SKUs, list them as comma-separated.\n" +
                "- If the offer applies to all SKUs, use `\"All\"`.\n" +
                "- If no SKU info is present, use `\"NA\"`.\n\n" +
                "### YOUR OUTPUT MUST FOLLOW THIS EXACT JSON STRUCTURE:\n" +
                "[\n" +
                "  {\n" +
                "    \"sku_code\": \"\",\n" +
                "    \"min_amount\": \"\",\n" +
                "    \"max_amount\": \"\",\n" +
                "    \"include_states\": \"\",\n" +
                "    \"exclude_states\": \"\",\n" +
                "    \"bank_name\": \"\",\n" +
                "    \"card_type\": \"\",\n" +
                "    \"full_swipe_offer_amount_type\": \"\",\n" +
                "    \"full_swipe_offer_value\": \"\",\n" +
                "    \"full_swipe_offer_max_amount\": \"\",\n" +
                "    \"emi_offer_amount_type\": \"\",\n" +
                "    \"emi_offer_value\": \"\",\n" +
                "    \"emi_offer_max_amount\": \"\",\n" +
                "    \"full_swipe_subvention_type\": \"\",\n" +
                "    \"full_swipe_bank_subvention_value\": \"\",\n" +
                "    \"full_swipe_brand_subvention_value\": \"\",\n" +
                "    \"emi_subvention_type\": \"\",\n" +
                "    \"emi_bank_subvention_value\": \"\",\n" +
                "    \"emi_brand_subvention_value\": \"\",\n" +
                "    \"start_date\": \"\",\n" +
                "    \"end_date\": \"\"\n" +
                "  }\n" +
                "]\n\n" +
                "### ADDITIONAL INSTRUCTIONS:\n" +
                "1. For dates, use format: YYYY-MM-DD HH:mm:ss\n" +
                "2. For amount fields, use numbers without currency symbols\n" +
                "3. For percentage fields, use the word \"Percentage\"\n" +
                "4. For fixed amount fields, use the word \"Fixed\"\n" +
                "5. For card type, use \"Credit\", \"Debit\", or \"Both\"\n" +
                "6. For bank name, use the actual bank name or \"All\"\n\n" +
                "Excel Data:\n" + excelContent;

            // Call AI service to process the Excel content
            String jsonResponse = callAIService(prompt);
            logger.info("AI Response:\n{}", jsonResponse);

            // Parse the AI response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            
            if (!rootNode.isArray()) {
                logger.info("Converting single object to array");
                ArrayNode arrayNode = mapper.createArrayNode();
                arrayNode.add(rootNode);
                rootNode = arrayNode;
            }
            
            // Create Excel workbook with the processed data
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Offer Details");
                
                // Create header style
                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                headerStyle.setAlignment(HorizontalAlignment.CENTER);
                headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                headerStyle.setBorderBottom(BorderStyle.THIN);
                headerStyle.setBorderTop(BorderStyle.THIN);
                headerStyle.setBorderLeft(BorderStyle.THIN);
                headerStyle.setBorderRight(BorderStyle.THIN);
                headerStyle.setWrapText(true);
                
                String[] headers = {
                    "Sku Code (All/Specific SKU/NA)*",
                    "Min Amount*",
                    "Max Amount",
                    "Include States",
                    "Exclude States",
                    "Bank Name (All/Specific Bank/Few Banks)*",
                    "Card Type (Credit/Debit/Both)",
                    "Full Swipe Offer Amount Type (Fixed/Percentage)*",
                    "Full Swipe Offer Value",
                    "Full Swipe Offer Max Amount (Percentage Type Case)",
                    "EMI Offer Amount Type (Fixed/Percentage)*",
                    "EMI Offer Value",
                    "EMI Offer Max Amount (Percentage Type Case)",
                    "Full Swipe Subvention Type (Fixed/Percentage)",
                    "Full Swipe Bank Subvention Value",
                    "Full Swipe Brand Subvention Value",
                    "EMI Subvention Type (Fixed/Percentage)",
                    "EMI Bank Subvention Value",
                    "EMI Brand Subvention Value",
                    "Start Date(yyyy-MM-dd HH:mm:sss)",
                    "End Date(yyyy-MM-dd HH:mm:sss)"
                };

                // Create header row
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                    sheet.setColumnWidth(i, 256 * 30);
                }
                headerRow.setHeight((short) 900);

                // Process each object from AI response
                int rowNum = 1;
                for (JsonNode offerNode : rootNode) {
                    Row dataRow = sheet.createRow(rowNum++);
                    int colNum = 0;
                    
                    // Map JSON fields to Excel columns
                    createCell(dataRow, colNum++, offerNode.get("sku_code"));
                    createCell(dataRow, colNum++, offerNode.get("min_amount"));
                    createCell(dataRow, colNum++, offerNode.get("max_amount"));
                    createCell(dataRow, colNum++, offerNode.get("include_states"));
                    createCell(dataRow, colNum++, offerNode.get("exclude_states"));
                    createCell(dataRow, colNum++, offerNode.get("bank_name"));
                    createCell(dataRow, colNum++, offerNode.get("card_type"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_offer_amount_type"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_offer_value"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_offer_max_amount"));
                    createCell(dataRow, colNum++, offerNode.get("emi_offer_amount_type"));
                    createCell(dataRow, colNum++, offerNode.get("emi_offer_value"));
                    createCell(dataRow, colNum++, offerNode.get("emi_offer_max_amount"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_subvention_type"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_bank_subvention_value"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_brand_subvention_value"));
                    createCell(dataRow, colNum++, offerNode.get("emi_subvention_type"));
                    createCell(dataRow, colNum++, offerNode.get("emi_bank_subvention_value"));
                    createCell(dataRow, colNum++, offerNode.get("emi_brand_subvention_value"));
                    createCell(dataRow, colNum++, offerNode.get("start_date"));
                    createCell(dataRow, colNum++, offerNode.get("end_date"));
                }

                // Auto-size columns
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }

                // Write to byte array
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                workbook.write(outputStream);
                byte[] result = outputStream.toByteArray();
                logger.info("Generated Excel file size: {} bytes", result.length);
                return result;
            }
        } catch (Exception e) {
            logger.error("Error processing Excel file: ", e);
            throw new IOException("Error processing Excel file: " + e.getMessage());
        }
    }

    public byte[] processCsvFile(byte[] fileBytes) throws IOException {
        logger.info("Starting file processing");
        try {
            // Validate file content
            if (fileBytes == null || fileBytes.length == 0) {
                throw new IllegalArgumentException("File is empty");
            }

            // Detect file type and process accordingly
            String fileContent;
            if (isExcelFile(fileBytes)) {
                logger.info("Detected Excel file, processing as Excel");
                fileContent = readExcelFile(new ByteArrayInputStream(fileBytes));
            } else {
                logger.info("Detected CSV file, processing as CSV");
                fileContent = new String(fileBytes);
            }
            
            logger.info("File Content (first 1000 chars):\n{}", fileContent.substring(0, Math.min(1000, fileContent.length())));

            // Create AI prompt for processing
            String prompt = "You are a business assistant AI. Your task is to extract structured offer data from a product offer sheet provided in tabular format. Each row represents a distinct offer entry and must be processed individually.\n\n" +
                "### INSTRUCTIONS:\n" +
                "1. Process **every row** in the data.\n" +
                "2. Each row should be mapped to **one JSON object**.\n" +
                "3. Your final output must be a **JSON array** of multiple offer objects.\n" +
                "4. Do **not** merge or combine information across rows.\n" +
                "5. If there are 4 rows, your output must have 4 JSON objects.\n" +
                "6. **DO NOT OMIT ANY ROW** – include all, even if some fields are missing.\n" +
                "7. If a value is missing, return it as an **empty string** in the JSON.\n\n" +
                "### SPECIAL CLARIFICATION FOR `sku_code`:\n" +
                "- Each row contains the product name, variant, and Product ID. Combine these to form the SKU like:\n" +
                "  `\"Xiaomi Pad 6|6GB+128GB|47867\"` or `\"Redmi Pad|4GB+128GB|43553\"`\n" +
                "- Use this combined value as the **`sku_code`** field.\n" +
                "- If a row applies to multiple SKUs, list them as comma-separated.\n" +
                "- If the offer applies to all SKUs, use `\"All\"`.\n" +
                "- If no SKU info is present, use `\"NA\"`.\n\n" +
                "### YOUR OUTPUT MUST FOLLOW THIS EXACT JSON STRUCTURE:\n" +
                "[\n" +
                "  {\n" +
                "    \"sku_code\": \"\",\n" +
                "    \"min_amount\": \"\",\n" +
                "    \"max_amount\": \"\",\n" +
                "    \"include_states\": \"\",\n" +
                "    \"exclude_states\": \"\",\n" +
                "    \"bank_name\": \"\",\n" +
                "    \"card_type\": \"\",\n" +
                "    \"full_swipe_offer_amount_type\": \"\",\n" +
                "    \"full_swipe_offer_value\": \"\",\n" +
                "    \"full_swipe_offer_max_amount\": \"\",\n" +
                "    \"emi_offer_amount_type\": \"\",\n" +
                "    \"emi_offer_value\": \"\",\n" +
                "    \"emi_offer_max_amount\": \"\",\n" +
                "    \"full_swipe_subvention_type\": \"\",\n" +
                "    \"full_swipe_bank_subvention_value\": \"\",\n" +
                "    \"full_swipe_brand_subvention_value\": \"\",\n" +
                "    \"emi_subvention_type\": \"\",\n" +
                "    \"emi_bank_subvention_value\": \"\",\n" +
                "    \"emi_brand_subvention_value\": \"\",\n" +
                "    \"start_date\": \"\",\n" +
                "    \"end_date\": \"\"\n" +
                "  }\n" +
                "]\n\n" +
                "### ADDITIONAL INSTRUCTIONS:\n" +
                "1. For dates, use format: YYYY-MM-DD HH:mm:ss\n" +
                "2. For amount fields, use numbers without currency symbols\n" +
                "3. For percentage fields, use the word \"Percentage\"\n" +
                "4. For fixed amount fields, use the word \"Fixed\"\n" +
                "5. For card type, use \"Credit\", \"Debit\", or \"Both\"\n" +
                "6. For bank name, use the actual bank name or \"All\"\n\n" +
                "Data:\n" + fileContent;

            // Call AI service
            String response = callAIService(prompt);
            logger.info("Raw AI Response:\n{}", response);

            // Clean and validate JSON response
            String cleanedResponse = cleanJsonResponse(response);
            logger.info("Cleaned JSON Response:\n{}", cleanedResponse);

            // Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(cleanedResponse);
            
            if (!rootNode.isArray()) {
                logger.info("Converting single object to array");
                ArrayNode arrayNode = mapper.createArrayNode();
                arrayNode.add(rootNode);
                rootNode = arrayNode;
            }
            
            // Create Excel workbook with the processed data
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Offer Details");
                
                // Create header style
                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                headerStyle.setAlignment(HorizontalAlignment.CENTER);
                headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                headerStyle.setBorderBottom(BorderStyle.THIN);
                headerStyle.setBorderTop(BorderStyle.THIN);
                headerStyle.setBorderLeft(BorderStyle.THIN);
                headerStyle.setBorderRight(BorderStyle.THIN);
                headerStyle.setWrapText(true);
                
                String[] headers = {
                    "Sku Code (All/Specific SKU/NA)*",
                    "Min Amount*",
                    "Max Amount",
                    "Include States",
                    "Exclude States",
                    "Bank Name (All/Specific Bank/Few Banks)*",
                    "Card Type (Credit/Debit/Both)",
                    "Full Swipe Offer Amount Type (Fixed/Percentage)*",
                    "Full Swipe Offer Value",
                    "Full Swipe Offer Max Amount (Percentage Type Case)",
                    "EMI Offer Amount Type (Fixed/Percentage)*",
                    "EMI Offer Value",
                    "EMI Offer Max Amount (Percentage Type Case)",
                    "Full Swipe Subvention Type (Fixed/Percentage)",
                    "Full Swipe Bank Subvention Value",
                    "Full Swipe Brand Subvention Value",
                    "EMI Subvention Type (Fixed/Percentage)",
                    "EMI Bank Subvention Value",
                    "EMI Brand Subvention Value",
                    "Start Date(yyyy-MM-dd HH:mm:sss)",
                    "End Date(yyyy-MM-dd HH:mm:sss)"
                };

                // Create header row
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                    sheet.setColumnWidth(i, 256 * 30);
                }
                headerRow.setHeight((short) 900);

                // Process each object from AI response
                int rowNum = 1;
                for (JsonNode offerNode : rootNode) {
                    Row dataRow = sheet.createRow(rowNum++);
                    int colNum = 0;
                    
                    // Map JSON fields to Excel columns
                    createCell(dataRow, colNum++, offerNode.get("sku_code"));
                    createCell(dataRow, colNum++, offerNode.get("min_amount"));
                    createCell(dataRow, colNum++, offerNode.get("max_amount"));
                    createCell(dataRow, colNum++, offerNode.get("include_states"));
                    createCell(dataRow, colNum++, offerNode.get("exclude_states"));
                    createCell(dataRow, colNum++, offerNode.get("bank_name"));
                    createCell(dataRow, colNum++, offerNode.get("card_type"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_offer_amount_type"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_offer_value"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_offer_max_amount"));
                    createCell(dataRow, colNum++, offerNode.get("emi_offer_amount_type"));
                    createCell(dataRow, colNum++, offerNode.get("emi_offer_value"));
                    createCell(dataRow, colNum++, offerNode.get("emi_offer_max_amount"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_subvention_type"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_bank_subvention_value"));
                    createCell(dataRow, colNum++, offerNode.get("full_swipe_brand_subvention_value"));
                    createCell(dataRow, colNum++, offerNode.get("emi_subvention_type"));
                    createCell(dataRow, colNum++, offerNode.get("emi_bank_subvention_value"));
                    createCell(dataRow, colNum++, offerNode.get("emi_brand_subvention_value"));
                    createCell(dataRow, colNum++, offerNode.get("start_date"));
                    createCell(dataRow, colNum++, offerNode.get("end_date"));
                }

                // Auto-size columns
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }

                // Write to byte array
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                workbook.write(outputStream);
                byte[] result = outputStream.toByteArray();
                logger.info("Generated Excel file size: {} bytes", result.length);
                return result;
            }
        } catch (Exception e) {
            logger.error("Error processing file: {}", e.getMessage());
            throw new IOException("Error processing file: " + e.getMessage());
        }
    }

    private boolean isExcelFile(byte[] fileBytes) {
        // Check for Excel file signature
        if (fileBytes.length >= 4) {
            // Check for XLSX signature (PK header)
            if (fileBytes[0] == 0x50 && fileBytes[1] == 0x4B) {
                return true;
            }
            // Check for XLS signature
            if (fileBytes[0] == (byte)0xD0 && fileBytes[1] == (byte)0xCF) {
                return true;
            }
        }
        return false;
    }

    public byte[] processRawText(String rawText) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Offer Details");
            
            // Split text into lines and process
            String[] lines = rawText.split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                String[] values = lines[i].split("\\s+"); // Split by whitespace
                Row row = sheet.createRow(i);
                for (int j = 0; j < values.length; j++) {
                    row.createCell(j).setCellValue(values[j].trim());
                }
            }
            
            return processSheet(sheet);
        }
    }

    private byte[] processSheet(Sheet sheet) throws IOException {
        // Create header style
        CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
        Font headerFont = sheet.getWorkbook().createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Apply header style to first row
        Row headerRow = sheet.getRow(0);
        if (headerRow != null) {
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    cell.setCellStyle(headerStyle);
                }
            }
        }

        // Auto-size columns
        for (int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) {
            sheet.autoSizeColumn(i);
        }

        // Write workbook to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        sheet.getWorkbook().write(outputStream);
        return outputStream.toByteArray();
    }
} 