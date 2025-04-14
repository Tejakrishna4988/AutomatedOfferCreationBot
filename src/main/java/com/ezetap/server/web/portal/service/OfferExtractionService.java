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
        String fileContent = readExcelFile(file);
        logger.info("Excel Content:\n{}", fileContent);
        
        String prompt = "You are an expert at analyzing Excel data and converting it into structured JSON format. " +
            "Your task is to analyze the following Excel data and convert it into a JSON array where each row becomes a separate JSON object.\n\n" +
            "CRITICAL INSTRUCTIONS:\n" +
            "1. Process EVERY row in the Excel file\n" +
            "2. Each row MUST become a separate JSON object\n" +
            "3. DO NOT combine or merge rows\n" +
            "4. If a row has data, it MUST be included in the output\n" +
            "5. The response MUST be an array of JSON objects, one for each row\n" +
            "6. The number of JSON objects in your response MUST EXACTLY match the number of rows shown in the sheet headers\n" +
            "7. For Sheet 2, you MUST create exactly 14 separate JSON objects, one for each row\n" +
            "8. DO NOT skip any rows - process all 14 rows\n" +
            "9. Each row should be a distinct offer with its own JSON object\n" +
            "10. The final response MUST contain exactly 14 JSON objects in the array\n" +
            "11. DO NOT combine any rows - each row must be its own object\n" +
            "12. If you see multiple rows with data, create a separate JSON object for each one\n" +
            "13. The response MUST be an array containing ALL rows as separate objects\n" +
            "14. DO NOT return a single object - it must be an array of objects\n" +
            "15. Each row in the Excel file must become its own JSON object in the array\n\n" +
            "Excel Data:\n" + fileContent + "\n\n" +
            "Required JSON Format (array of objects):\n" +
            "[\n" +
            "    {\n" +
            "        \"sku_code\": \"Product ID/SKU code (e.g., 47867, 47868, etc.)\",\n" +
            "        \"min_amount\": \"Minimum transaction amount (e.g., 19749)\",\n" +
            "        \"max_amount\": \"Maximum transaction amount (if specified)\",\n" +
            "        \"include_states\": \"States where offer is valid (comma-separated)\",\n" +
            "        \"exclude_states\": \"States where offer is not valid (comma-separated)\",\n" +
            "        \"bank_name\": \"Bank name (e.g., ICICI)\",\n" +
            "        \"card_type\": \"Card type (e.g., Credit)\",\n" +
            "        \"full_swipe_offer_amount_type\": \"Fixed or Percentage\",\n" +
            "        \"full_swipe_offer_value\": \"Offer value (e.g., 3000)\",\n" +
            "        \"full_swipe_offer_max_amount\": \"Maximum offer amount (if specified)\",\n" +
            "        \"emi_offer_amount_type\": \"Fixed or Percentage\",\n" +
            "        \"emi_offer_value\": \"Offer value (e.g., 3000)\",\n" +
            "        \"emi_offer_max_amount\": \"Maximum offer amount (if specified)\",\n" +
            "        \"full_swipe_subvention_type\": \"Fixed or Percentage\",\n" +
            "        \"full_swipe_bank_subvention_value\": \"Bank's share (e.g., 685)\",\n" +
            "        \"full_swipe_brand_subvention_value\": \"Brand's share (e.g., 2315)\",\n" +
            "        \"emi_subvention_type\": \"Fixed or Percentage\",\n" +
            "        \"emi_bank_subvention_value\": \"Bank's share (e.g., 685)\",\n" +
            "        \"emi_brand_subvention_value\": \"Brand's share (e.g., 2315)\",\n" +
            "        \"start_date\": \"Offer start date (YYYY-MM-DD HH:mm:ss)\",\n" +
            "        \"end_date\": \"Offer end date (YYYY-MM-DD HH:mm:ss)\"\n" +
            "    }\n" +
            "]\n\n" +
            "IMPORTANT:\n" +
            "1. Return ALL rows as separate JSON objects in an array\n" +
            "2. Each row MUST be processed independently\n" +
            "3. DO NOT skip any rows with data\n" +
            "4. If a field is empty in the Excel, use an empty string (\"\") in the JSON\n" +
            "5. Ensure dates are in the exact format: YYYY-MM-DD HH:mm:ss\n" +
            "6. The response MUST be a valid JSON array containing all rows\n" +
            "7. The number of JSON objects in your response MUST EXACTLY match the number of rows shown in the sheet headers\n" +
            "8. For Sheet 2, you MUST return exactly 14 JSON objects\n" +
            "9. DO NOT combine or merge any rows - each row must be a separate object\n" +
            "10. The final array MUST contain exactly 14 objects, one for each row\n" +
            "11. DO NOT return a single object - it must be an array\n" +
            "12. Each row must become its own JSON object\n" +
            "13. The response must be an array containing ALL rows\n" +
            "14. DO NOT combine any rows - each row must be separate\n" +
            "15. The response must be an array of objects, not a single object\n\n" +
            "Please analyze the Excel data and return a JSON array containing all 14 rows as separate objects.";

        String response = callAIService(prompt);
        logger.info("AI Response:\n{}", response);
        
        // Validate that the response is valid JSON
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(response);
            if (!node.isArray()) {
                // If the response is not an array, wrap it in an array
                ArrayNode arrayNode = mapper.createArrayNode();
                arrayNode.add(node);
                response = mapper.writeValueAsString(arrayNode);
            }
            
            // Log the number of JSON objects in the response
            JsonNode responseNode = mapper.readTree(response);
            int jsonObjectCount = responseNode.size();
            logger.info("AI Response contains {} JSON objects", jsonObjectCount);
            
            // Create Excel file regardless of the number of objects
            byte[] excelBytes = createExcelFromJson(response);
            
            // Return both the JSON response and the Excel bytes
            ObjectNode result = mapper.createObjectNode();
            result.put("json", response);
            result.put("excel", Base64.getEncoder().encodeToString(excelBytes));
            
            return mapper.writeValueAsString(result);
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
        createCell(row, columnIndex++, offerNode.get("sku_code").asText());
        createCell(row, columnIndex++, offerNode.get("min_amount").asText());
        createCell(row, columnIndex++, offerNode.get("max_amount").asText());
        createCell(row, columnIndex++, offerNode.get("include_states").asText());
        createCell(row, columnIndex++, offerNode.get("exclude_states").asText());
        createCell(row, columnIndex++, offerNode.get("bank_name").asText());
        createCell(row, columnIndex++, offerNode.get("card_type").asText());
        createCell(row, columnIndex++, offerNode.get("full_swipe_offer_amount_type").asText());
        createCell(row, columnIndex++, offerNode.get("full_swipe_offer_value").asText());
        createCell(row, columnIndex++, offerNode.get("full_swipe_offer_max_amount").asText());
        createCell(row, columnIndex++, offerNode.get("emi_offer_amount_type").asText());
        createCell(row, columnIndex++, offerNode.get("emi_offer_value").asText());
        createCell(row, columnIndex++, offerNode.get("emi_offer_max_amount").asText());
        createCell(row, columnIndex++, offerNode.get("full_swipe_subvention_type").asText());
        createCell(row, columnIndex++, offerNode.get("full_swipe_bank_subvention_value").asText());
        createCell(row, columnIndex++, offerNode.get("full_swipe_brand_subvention_value").asText());
        createCell(row, columnIndex++, offerNode.get("emi_subvention_type").asText());
        createCell(row, columnIndex++, offerNode.get("emi_bank_subvention_value").asText());
        createCell(row, columnIndex++, offerNode.get("emi_brand_subvention_value").asText());
        createCell(row, columnIndex++, offerNode.get("start_date").asText());
        createCell(row, columnIndex++, offerNode.get("end_date").asText());
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

    private String readExcelFile(MultipartFile file) throws IOException {
        StringBuilder excelText = new StringBuilder();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
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

    public String extractFromRawText(String rawText) throws IOException {
        try {
            // Create a prompt for the AI to extract offer information
            String prompt = String.format(
                "Extract offer information from the following text and format it as JSON. " +
                "The text contains multiple offers with details about models, prices, and bank offers. " +
                "Extract all offers and format them according to the following structure:\n\n" +
                "{\n" +
                "  \"offers\": [\n" +
                "    {\n" +
                "      \"sku_code\": \"model code\",\n" +
                "      \"min_amount\": \"minimum amount\",\n" +
                "      \"max_amount\": \"maximum amount\",\n" +
                "      \"include_states\": \"\",\n" +
                "      \"exclude_states\": \"\",\n" +
                "      \"bank_name\": \"bank name\",\n" +
                "      \"card_type\": \"Credit/Debit/Both\",\n" +
                "      \"full_swipe_offer_amount_type\": \"Fixed/Percentage\",\n" +
                "      \"full_swipe_offer_value\": \"offer value\",\n" +
                "      \"full_swipe_offer_max_amount\": \"maximum amount\",\n" +
                "      \"emi_offer_amount_type\": \"Fixed/Percentage\",\n" +
                "      \"emi_offer_value\": \"offer value\",\n" +
                "      \"emi_offer_max_amount\": \"maximum amount\",\n" +
                "      \"full_swipe_subvention_type\": \"Fixed/Percentage\",\n" +
                "      \"full_swipe_bank_subvention_value\": \"bank subvention value\",\n" +
                "      \"full_swipe_brand_subvention_value\": \"brand subvention value\",\n" +
                "      \"emi_subvention_type\": \"Fixed/Percentage\",\n" +
                "      \"emi_bank_subvention_value\": \"bank subvention value\",\n" +
                "      \"emi_brand_subvention_value\": \"brand subvention value\",\n" +
                "      \"start_date\": \"2025-04-01 00:00:00\",\n" +
                "      \"end_date\": \"2025-04-30 23:59:59\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" +
                "Text to analyze:\n%s", rawText);

            // Call the AI service to extract information
            String jsonResponse = callAIService(prompt);
            
            // Validate the JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            
            if (!rootNode.has("offers") || !rootNode.get("offers").isArray()) {
                throw new IOException("Invalid JSON response format");
            }
            
            return jsonResponse;
        } catch (Exception e) {
            logger.error("Error extracting from raw text: ", e);
            throw new IOException("Error processing text: " + e.getMessage());
        }
    }

    public byte[] generateExcelFromJson(String jsonResponse) throws IOException {
        try {
            // Parse the JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            
            if (!rootNode.has("offers") || !rootNode.get("offers").isArray()) {
                throw new IOException("Invalid JSON response format");
            }
            
            // Create Excel workbook
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

                // Define headers
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

                // Populate data rows
                int rowNum = 1;
                for (JsonNode offer : rootNode.get("offers")) {
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
        } catch (Exception e) {
            logger.error("Error generating Excel from JSON: ", e);
            throw new IOException("Error generating Excel: " + e.getMessage());
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

    private String cleanJsonResponse(String response) {
        // Remove any markdown formatting
        response = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        
        // Remove trailing commas
        response = response.replaceAll(",\\s*([}\\]])", "$1");
        
        // Remove any comments
        response = response.replaceAll("//.*$", "").replaceAll("/\\*.*?\\*/", "");
        
        // Find the first '{' and last '}'
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        
        if (start != -1 && end != -1 && end > start) {
            response = response.substring(start, end + 1);
        }
        
        // Validate JSON structure
        try {
            ObjectMapper mapper = new ObjectMapper();
            // Enable ALLOW_COMMENTS feature
            mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
            JsonNode node = mapper.readTree(response);
            return mapper.writeValueAsString(node); // Ensure proper JSON formatting
        } catch (Exception e) {
            logger.error("Error cleaning JSON response: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JSON structure in response: " + e.getMessage());
        }
    }

    public byte[] processExcelFile(byte[] fileBytes) throws IOException {
        try (Workbook inputWorkbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes));
             Workbook outputWorkbook = new XSSFWorkbook()) {
            
            Sheet inputSheet = inputWorkbook.getSheetAt(0);
            Sheet outputSheet = outputWorkbook.createSheet("Offer Details");

            // Create headers with exact field names
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

            // Create cell style for headers
            CellStyle headerStyle = outputWorkbook.createCellStyle();
            Font headerFont = outputWorkbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            // Set text rotation to 0 (horizontal)
            headerStyle.setRotation((short) 0);
            
            // Set text alignment
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            
            // Set border
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            
            // Set word wrap
            headerStyle.setWrapText(true);

            // Write headers
            Row headerRow = outputSheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                // Set column width to accommodate the text
                outputSheet.setColumnWidth(i, 256 * 30); // 30 characters width for better readability
            }

            // Set row height for better readability of wrapped text
            headerRow.setHeight((short) 900); // Approximately 45 points

            // Process each row from input sheet
            int outputRowNum = 1;
            for (int i = 1; i <= inputSheet.getLastRowNum(); i++) {
                Row inputRow = inputSheet.getRow(i);
                if (inputRow == null) continue;

                Row outputRow = outputSheet.createRow(outputRowNum++);
                
                // Extract and map data according to the structure
                outputRow.createCell(0).setCellValue(getCellValueAsString(inputRow.getCell(0))); // Sku Code
                outputRow.createCell(1).setCellValue(getCellValueAsString(inputRow.getCell(1))); // Min Amount
                outputRow.createCell(2).setCellValue(getCellValueAsString(inputRow.getCell(2))); // Max Amount
                outputRow.createCell(3).setCellValue(getCellValueAsString(inputRow.getCell(3))); // Include States
                outputRow.createCell(4).setCellValue(getCellValueAsString(inputRow.getCell(4))); // Exclude States
                outputRow.createCell(5).setCellValue(getCellValueAsString(inputRow.getCell(5))); // Bank Name
                outputRow.createCell(6).setCellValue(getCellValueAsString(inputRow.getCell(6))); // Card Type
                outputRow.createCell(7).setCellValue(getCellValueAsString(inputRow.getCell(7))); // Full Swipe Offer Amount Type
                outputRow.createCell(8).setCellValue(getCellValueAsString(inputRow.getCell(8))); // Full Swipe Offer Value
                outputRow.createCell(9).setCellValue(getCellValueAsString(inputRow.getCell(9))); // Full Swipe Offer Max Amount
                outputRow.createCell(10).setCellValue(getCellValueAsString(inputRow.getCell(10))); // EMI Offer Amount Type
                outputRow.createCell(11).setCellValue(getCellValueAsString(inputRow.getCell(11))); // EMI Offer Value
                outputRow.createCell(12).setCellValue(getCellValueAsString(inputRow.getCell(12))); // EMI Offer Max Amount
                outputRow.createCell(13).setCellValue(getCellValueAsString(inputRow.getCell(13))); // Full Swipe Subvention Type
                outputRow.createCell(14).setCellValue(getCellValueAsString(inputRow.getCell(14))); // Full Swipe Bank Subvention Value
                outputRow.createCell(15).setCellValue(getCellValueAsString(inputRow.getCell(15))); // Full Swipe Brand Subvention Value
                outputRow.createCell(16).setCellValue(getCellValueAsString(inputRow.getCell(16))); // EMI Subvention Type
                outputRow.createCell(17).setCellValue(getCellValueAsString(inputRow.getCell(17))); // EMI Bank Subvention Value
                outputRow.createCell(18).setCellValue(getCellValueAsString(inputRow.getCell(18))); // EMI Brand Subvention Value
                outputRow.createCell(19).setCellValue(getCellValueAsString(inputRow.getCell(19))); // Start Date
                outputRow.createCell(20).setCellValue(getCellValueAsString(inputRow.getCell(20))); // End Date
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                outputSheet.autoSizeColumn(i);
            }

            // Write workbook to byte array
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                outputWorkbook.write(outputStream);
                return outputStream.toByteArray();
            }
        }
    }

    public byte[] processCsvFile(byte[] fileBytes) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Offer Details");
            
            // Read CSV content
            String csvContent = new String(fileBytes);
            String[] lines = csvContent.split("\\r?\\n");
            
            // Process each line
            for (int i = 0; i < lines.length; i++) {
                String[] values = lines[i].split(",");
                Row row = sheet.createRow(i);
                for (int j = 0; j < values.length; j++) {
                    row.createCell(j).setCellValue(values[j].trim());
                }
            }
            
            return processSheet(sheet);
        }
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