package com.makusha.calc.infra.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CalculatorController {

    @PostMapping("/calculate")
    public ResponseEntity<String> calculate() {
        return ResponseEntity.ok("OK");
    }
}
