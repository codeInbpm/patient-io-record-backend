package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.service.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/file")
@RequiredArgsConstructor
public class FileUploadController {

    private final MinioService minioService;

    @PostMapping("/upload")
    public Result<MinioService.UploadResult> upload(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(value = "folder", defaultValue = "infusion") String folder) {
        MinioService.UploadResult result = minioService.uploadImage(file, folder);
        return Result.success(result);
    }
}
