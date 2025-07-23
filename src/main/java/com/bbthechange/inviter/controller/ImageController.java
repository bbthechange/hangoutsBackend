package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.PredefinedImageResponse;
import com.bbthechange.inviter.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
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
}