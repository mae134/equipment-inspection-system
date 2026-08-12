package com.mae134.equipmentinspection.inspectionitem;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspection-items")
public class EquipmentInspectionItemController {

  private final EquipmentInspectionItemService inspectionItemService;

  public EquipmentInspectionItemController(EquipmentInspectionItemService inspectionItemService) {
    this.inspectionItemService = inspectionItemService;
  }

  @GetMapping
  public List<EquipmentInspectionItemResponse> findAll() {
    return inspectionItemService.findAll();
  }

  @GetMapping("/{id}")
  public EquipmentInspectionItemResponse findById(@PathVariable Long id) {
    return inspectionItemService.findById(id);
  }

  @PostMapping
  public EquipmentInspectionItemResponse save(
      @Valid @RequestBody EquipmentInspectionItemRequest request) {
    return inspectionItemService.save(request);
  }

  @PutMapping("/{id}")
  public EquipmentInspectionItemResponse update(
      @PathVariable Long id, @Valid @RequestBody EquipmentInspectionItemRequest request) {
    return inspectionItemService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    inspectionItemService.delete(id);
  }

  @GetMapping("/equipment/{equipmentId}")
  public List<EquipmentInspectionItemResponse> findByEquipmentId(@PathVariable Long equipmentId) {
    return inspectionItemService.findByEquipmentId(equipmentId);
  }
}
