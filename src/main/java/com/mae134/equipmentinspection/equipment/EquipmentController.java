package com.mae134.equipmentinspection.equipment;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
  public List<Equipment> findAll() {
    return equipmentService.findAll();
  }

  @PostMapping
  public Equipment save(@RequestBody Equipment equipment) {
    return equipmentService.save(equipment);
  }

  @GetMapping("/{id}")
  public Equipment findById(@PathVariable Long id) {
    return equipmentService
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found: " + id));
  }
}
