package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.PredefinedImageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class S3Service {

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    private static final String PREDEFINED_PREFIX = "predefined/";
    
    // Map filenames to display names
    private static final Map<String, String> IMAGE_DISPLAY_NAMES = createImageDisplayNames();

    public List<PredefinedImageResponse> getPredefinedImages() {
        try {
            log.info("Fetching predefined images from S3 bucket: {} with prefix: {}", bucketName, PREDEFINED_PREFIX);
            
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(PREDEFINED_PREFIX)
                    .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            
            log.info("Found {} objects in S3 bucket", response.contents().size());
            response.contents().forEach(obj -> log.debug("S3 Object key: {}", obj.key()));

            List<PredefinedImageResponse> results = response.contents().stream()
                    .filter(s3Object -> !s3Object.key().equals(PREDEFINED_PREFIX)) // Filter out the prefix folder itself
                    .map(this::mapToImageResponse)
                    .collect(Collectors.toList());
                    
            log.info("Returning {} predefined images", results.size());
            return results;

        } catch (Exception e) {
            log.error("Error fetching predefined images from S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch predefined images", e);
        }
    }

    private PredefinedImageResponse mapToImageResponse(S3Object s3Object) {
        String key = s3Object.key();
        String filename = key.substring(PREDEFINED_PREFIX.length());
        String displayName = IMAGE_DISPLAY_NAMES.getOrDefault(filename, formatDisplayName(filename));
        
        log.debug("Mapping S3 object - key: {}, filename: {}, displayName: {}", key, filename, displayName);
        
        return new PredefinedImageResponse(filename.replace(".jpg", ""), key, displayName);
    }

    private static Map<String, String> createImageDisplayNames() {
        Map<String, String> map = new HashMap<>();
        map.put("art-gallery.jpg", "Art Gallery");
        map.put("birthday.jpg", "Birthday Party");
        map.put("business-meeting.jpg", "Business Meeting");
        map.put("celebration-cake.jpg", "Celebration");
        map.put("charity-event.jpg", "Charity Event");
        map.put("christmas-party.jpg", "Christmas Party");
        map.put("cocktail-party.jpg", "Cocktail Party");
        map.put("conference.jpg", "Conference");
        map.put("corporate-event.jpg", "Corporate Event");
        map.put("dinner-party.jpg", "Dinner Party");
        map.put("family-gathering.jpg", "Family Gathering");
        map.put("graduation-cap.jpg", "Graduation");
        map.put("live-music.jpg", "Live Music");
        map.put("networking.jpg", "Networking");
        map.put("new-year.jpg", "New Year");
        map.put("outdoor-festival.jpg", "Outdoor Festival");
        map.put("party-balloons.jpg", "Party");
        map.put("sports-event.jpg", "Sports Event");
        map.put("wedding.jpg", "Wedding");
        map.put("workshop.jpg", "Workshop");
        return map;
    }

    private String formatDisplayName(String filename) {
        // Remove extension and format as display name
        String nameWithoutExtension = filename.replaceAll("\\.[^.]+$", "");
        String formatted = nameWithoutExtension.replace("-", " ").replace("_", " ").toLowerCase();
        
        // Capitalize first letter of each word
        String[] words = formatted.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
                result.append(" ");
            }
        }
        return result.toString().trim();
    }

    public String generatePresignedUploadUrl(String key, String contentType) {
        try {
            log.info("Generating presigned upload URL for key: {} with content type: {}", key, contentType);
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15)) // URL expires in 15 minutes
                    .putObjectRequest(putObjectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
            String presignedUrl = presignedRequest.url().toString();
            
            log.info("Generated presigned URL for key: {}", key);
            return presignedUrl;
            
        } catch (Exception e) {
            log.error("Error generating presigned upload URL for key {}: {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned upload URL", e);
        }
    }
}