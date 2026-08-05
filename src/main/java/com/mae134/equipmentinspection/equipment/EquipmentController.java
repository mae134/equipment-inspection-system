package com.mae134.equipmentinspection.equipment;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

  private final EquipmentService equipmentService;

  public EquipmentController(EquipmentService equipmentService) {
    this.equipmentService = equipmentService;
  }

  @GetMapping
  public List<EquipmentResponse> findAll() {
    return equipmentService.findAll();
  }

  @PostMapping
  public EquipmentResponse save(@RequestBody EquipmentRequest request) {
    return equipmentService.save(request);
  }

  @GetMapping("/{id}")
  public EquipmentResponse findById(@PathVariable Long id) {
    return equipmentService
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found: " + id));
  }

  @PutMapping("/{id}")
  public EquipmentResponse update(@PathVariable Long id, @RequestBody EquipmentRequest request) {
    return equipmentService.update(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    equipmentService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
