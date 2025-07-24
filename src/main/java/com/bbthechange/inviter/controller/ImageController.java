package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.PredefinedImageResponse;
import com.bbthechange.inviter.dto.UploadUrlRequest;
import com.bbthechange.inviter.dto.UploadUrlResponse;
import com.bbthechange.inviter.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/images")
@Tag(name = "Images", description = "Image management and predefined image options")
public class ImageController {
    
    @Autowired
    private S3Service s3Service;
    
    @GetMapping("/predefined")
    @Operation(summary = "Get all predefined images", 
               description = "Returns a list of all available predefined images from S3 bucket")
    public ResponseEntity<List<PredefinedImageResponse>> getPredefinedImages() {
        List<PredefinedImageResponse> predefinedImages = s3Service.getPredefinedImages();
        return new ResponseEntity<>(predefinedImages, HttpStatus.OK);
    }
    
    @PostMapping("/upload-url")
    @Operation(summary = "Get presigned S3 upload URL", 
               description = "Generate a presigned URL for direct S3 upload of custom images",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<UploadUrlResponse> getUploadUrl(@RequestBody UploadUrlRequest request) {
        String uploadUrl = s3Service.generatePresignedUploadUrl(request.getKey(), request.getContentType());
        UploadUrlResponse response = new UploadUrlResponse(uploadUrl, request.getKey());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}