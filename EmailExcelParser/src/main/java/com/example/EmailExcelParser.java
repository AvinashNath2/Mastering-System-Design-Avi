package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class fetches a JSON array of email addresses from a URL,
 * parses it, and creates an Excel file with each email in a separate cell.
 */
public class EmailExcelParser {

    private static final String JSON_URL = "https://gist.githubusercontent.com/immujahidkhan/6312618368ee7a84685616645548c9d0/raw/30118bb9317d674c33167a32417d79cf10e5264d/email.json";
    private static final String OUTPUT_FILE = "emails.xlsx";

    public static void main(String[] args) {
        EmailExcelParser parser = new EmailExcelParser();
        try {
            System.out.println("Fetching JSON from URL...");
            String jsonContent = parser.fetchJsonFromUrl(JSON_URL);
            
            System.out.println("Parsing JSON array...");
            List<String> emails = parser.parseJsonArray(jsonContent);
            
            System.out.println("Found " + emails.size() + " email addresses");
            
            System.out.println("Creating Excel file...");
            parser.createExcelFile(emails, OUTPUT_FILE);
            
            System.out.println("Excel file created successfully: " + OUTPUT_FILE);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Fetches JSON content from the given URL
     */
    public String fetchJsonFromUrl(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch JSON. HTTP Status: " + response.statusCode());
        }

        return response.body();
    }

    /**
     * Parses JSON array string into a List of email addresses
     */
    public List<String> parseJsonArray(String jsonContent) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(jsonContent, 
                mapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    /**
     * Validates if an email address is valid using regex pattern
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        // RFC 5322 compliant email regex pattern
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        Pattern pattern = Pattern.compile(emailRegex);
        return pattern.matcher(email.trim()).matches();
    }

    /**
     * Extracts company name from email domain
     */
    public String extractCompanyName(String email) {
        if (email == null || !email.contains("@")) {
            return "";
        }
        String domain = email.substring(email.indexOf("@") + 1);
        // Remove common TLDs and get the main domain name
        String[] parts = domain.split("\\.");
        if (parts.length > 0) {
            String companyPart = parts[0];
            // Capitalize first letter
            return companyPart.substring(0, 1).toUpperCase() + 
                   (companyPart.length() > 1 ? companyPart.substring(1) : "");
        }
        return domain;
    }

    /**
     * Extracts website URL from email domain
     */
    public String extractWebsite(String email) {
        if (email == null || !email.contains("@")) {
            return "";
        }
        String domain = email.substring(email.indexOf("@") + 1);
        return "https://" + domain;
    }

    /**
     * Detects and splits multiple email addresses from a concatenated string
     * Example: "email1@domain.comemail2@domain.com" -> ["email1@domain.com", "email2@domain.com"]
     */
    public List<String> splitMultipleEmails(String text) {
        List<String> emails = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return emails;
        }

        // Pattern to match email addresses: username@domain.tld
        // This will find all email patterns in the concatenated string
        String emailPattern = "[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}";
        Pattern pattern = Pattern.compile(emailPattern);
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String email = matcher.group();
            emails.add(email);
        }

        return emails;
    }

    /**
     * Creates an Excel file with each email address in a separate cell,
     * validates emails, color codes them, and adds company name and website
     */
    public void createExcelFile(List<String> emails, String outputFileName) throws IOException {
        // Create a new workbook
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Email Addresses");

        // Create styles
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        // Green style for valid emails
        CellStyle validEmailStyle = workbook.createCellStyle();
        validEmailStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        validEmailStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Red style for invalid emails
        CellStyle invalidEmailStyle = workbook.createCellStyle();
        invalidEmailStyle.setFillForegroundColor(IndexedColors.CORAL.getIndex());
        invalidEmailStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Create header row
        Row headerRow = sheet.createRow(0);
        Cell headerCell1 = headerRow.createCell(0);
        headerCell1.setCellValue("Email Address");
        headerCell1.setCellStyle(headerStyle);

        Cell headerCell2 = headerRow.createCell(1);
        headerCell2.setCellValue("Company Name");
        headerCell2.setCellStyle(headerStyle);

        Cell headerCell3 = headerRow.createCell(2);
        headerCell3.setCellValue("Website");
        headerCell3.setCellStyle(headerStyle);

        // Add email addresses to cells
        int rowNum = 1;
        int validCount = 0;
        int invalidCount = 0;

        for (String email : emails) {
            // Validate email first
            boolean isValid = isValidEmail(email);
            
            if (isValid) {
                // Valid email - create single row
                Row row = sheet.createRow(rowNum++);
                
                Cell emailCell = row.createCell(0);
                emailCell.setCellValue(email);
                emailCell.setCellStyle(validEmailStyle);
                validCount++;
                
                // Add company name for valid emails
                Cell companyCell = row.createCell(1);
                companyCell.setCellValue(extractCompanyName(email));
                
                // Add website for valid emails
                Cell websiteCell = row.createCell(2);
                websiteCell.setCellValue(extractWebsite(email));
            } else {
                // Invalid email - check if it contains multiple emails
                List<String> splitEmails = splitMultipleEmails(email);
                
                if (splitEmails.size() > 1) {
                    // Contains multiple emails - split them
                    // First row: original invalid email (marked red)
                    Row originalRow = sheet.createRow(rowNum++);
                    Cell originalEmailCell = originalRow.createCell(0);
                    originalEmailCell.setCellValue(email);
                    originalEmailCell.setCellStyle(invalidEmailStyle);
                    originalRow.createCell(1).setCellValue("");
                    originalRow.createCell(2).setCellValue("");
                    invalidCount++;
                    
                    // Create separate rows for each split email
                    for (String splitEmail : splitEmails) {
                        Row splitRow = sheet.createRow(rowNum++);
                        Cell splitEmailCell = splitRow.createCell(0);
                        splitEmailCell.setCellValue(splitEmail);
                        
                        boolean splitIsValid = isValidEmail(splitEmail);
                        if (splitIsValid) {
                            splitEmailCell.setCellStyle(validEmailStyle);
                            validCount++;
                            
                            // Add company name for valid split emails
                            Cell companyCell = splitRow.createCell(1);
                            companyCell.setCellValue(extractCompanyName(splitEmail));
                            
                            // Add website for valid split emails
                            Cell websiteCell = splitRow.createCell(2);
                            websiteCell.setCellValue(extractWebsite(splitEmail));
                        } else {
                            splitEmailCell.setCellStyle(invalidEmailStyle);
                            invalidCount++;
                            
                            // Leave company name and website empty for invalid split emails
                            splitRow.createCell(1).setCellValue("");
                            splitRow.createCell(2).setCellValue("");
                        }
                    }
                } else {
                    // Single invalid email (not multiple) - create single row
                    Row row = sheet.createRow(rowNum++);
                    Cell emailCell = row.createCell(0);
                    emailCell.setCellValue(email);
                    emailCell.setCellStyle(invalidEmailStyle);
                    invalidCount++;
                    
                    // Leave company name and website empty for invalid emails
                    row.createCell(1).setCellValue("");
                    row.createCell(2).setCellValue("");
                }
            }
        }

        // Auto-size columns
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);

        // Write the output to a file
        try (FileOutputStream outputStream = new FileOutputStream(outputFileName)) {
            workbook.write(outputStream);
        }

        // Close the workbook
        workbook.close();

        System.out.println("Validation Summary:");
        System.out.println("Valid emails: " + validCount);
        System.out.println("Invalid emails: " + invalidCount);
    }
}
