package com.hezitu.heaicodemother.controller;

import com.hezitu.heaicodemother.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;

@RestController
@RequestMapping("/deploy")
@Slf4j
public class DeployResourceController {

    private static final String DEPLOY_ROOT_DIR = AppConstant.CODE_DEPLOY_ROOT_DIR;

    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveDeployedResource(@PathVariable String deployKey,
                                                          HttpServletRequest request) {
        try {
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/deploy/" + deployKey).length());
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }

            File targetFile = resolveRequestedFile(deployKey, resourcePath);
            if (!targetFile.exists() || !targetFile.isFile()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(targetFile);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, getContentTypeWithCharset(targetFile.getName()))
                    .body(resource);
        } catch (Exception e) {
            log.error("Serve deployed resource failed, deployKey: {}, uri: {}, error: {}",
                    deployKey, request.getRequestURI(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private File resolveRequestedFile(String deployKey, String resourcePath) {
        String normalizedPath = resourcePath;
        if ("/".equals(normalizedPath) || normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath + "index.html";
        }
        File file = new File(DEPLOY_ROOT_DIR + "/" + deployKey + normalizedPath);
        if (file.isDirectory()) {
            return new File(file, "index.html");
        }
        return file;
    }

    private String getContentTypeWithCharset(String fileName) {
        if (fileName.endsWith(".html")) return "text/html; charset=UTF-8";
        if (fileName.endsWith(".css")) return "text/css; charset=UTF-8";
        if (fileName.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (fileName.endsWith(".json")) return "application/json; charset=UTF-8";
        if (fileName.endsWith(".svg")) return "image/svg+xml";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".ico")) return "image/x-icon";
        if (fileName.endsWith(".woff")) return "font/woff";
        if (fileName.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }
}
