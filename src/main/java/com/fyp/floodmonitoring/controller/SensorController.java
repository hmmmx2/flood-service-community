package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.response.SensorNodeDto;
import com.fyp.floodmonitoring.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /sensors — returns all 111 IoT sensor nodes for the map view. */
@RestController
@RequestMapping("/sensors")
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;

    @GetMapping
    public ResponseEntity<List<SensorNodeDto>> getSensors() {
        return ResponseEntity.ok(sensorService.getAllSensors());
    }
}
