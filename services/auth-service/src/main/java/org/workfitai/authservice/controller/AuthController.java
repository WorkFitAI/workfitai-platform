package org.workfitai.authservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.authservice.response.ResponseData;

@RestController
@RequestMapping()
@RequiredArgsConstructor
public class AuthController {

    @GetMapping()
    public ResponseData<String> healthCheck() {
        return ResponseData.success("Auth Service is running");
    }
}
