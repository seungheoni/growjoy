package com.example.growjoy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/analysis")
public class AnalysisController {
    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${openai.api-key}")
    private String apiKey;

    @GetMapping
    public String index() {
        return "index";
    }

    @PostMapping("/image")
    public String uploadImage(@RequestParam("file") MultipartFile file, Model model) {
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());

        try {
            // 파일 저장 경로 설정
            Path path = Paths.get(uploadDir + File.separator + fileName);
            Files.createDirectories(path.getParent());
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

            // MultipartFile을 File 객체로 변환
            File convertedFile = convertMultipartFileToFile(file);

            // 변환된 파일을 OpenAI API로 보내는 코드 호출
            String analysisResponse = analyzeImage(convertedFile);

            model.addAttribute("analysisResponse", analysisResponse);
            model.addAttribute("fileName", fileName);

            return "result";
        } catch (IOException ex) {
            model.addAttribute("message", "파일 업로드 실패");
            return "error";
        }
    }

    @GetMapping("/image/{fileName:.+}")
    public ResponseEntity<Resource> downloadImage(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists()) {
                // Content type을 설정하여 이미지 파일임을 명시
                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private File convertMultipartFileToFile(MultipartFile file) throws IOException {
        File convFile = new File(System.getProperty("java.io.tmpdir") + "/" + file.getOriginalFilename());
        file.transferTo(convFile);
        return convFile;
    }

    public String analyzeImage(File imageFile) {
        try {
            String base64Image = encodeImage(imageFile);

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", "gpt-4o-mini");

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");

            Map<String, String> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", "식물학자처럼 대답해주세요. 이 식물의 상태가 어떤가요?");

            Map<String, Object> imageUrl = new HashMap<>();
            imageUrl.put("url", "data:image/jpeg;base64," + base64Image);

            Map<String, Object> imageContent = new HashMap<>();
            imageContent.put("type", "image_url");
            imageContent.put("image_url", imageUrl);

            message.put("content", new Object[]{textContent, imageContent});
            payload.put("messages", new Object[]{message});
            payload.put("max_tokens", 300);

            return postRequest("https://api.openai.com/v1/chat/completions", payload);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String encodeImage(File file) throws IOException {
        FileInputStream imageInFile = new FileInputStream(file);
        byte[] imageData = new byte[(int) file.length()];
        imageInFile.read(imageData);
        imageInFile.close();
        return Base64.getEncoder().encodeToString(imageData);
    }

    private String postRequest(String requestUrl, Map<String, Object> payload) throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Content-Type", "application/json");

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonPayload = objectMapper.writeValueAsString(payload);

        HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
        ResponseEntity<String> response = restTemplate.exchange(requestUrl, HttpMethod.POST, request, String.class);

        return response.getBody();
    }
}