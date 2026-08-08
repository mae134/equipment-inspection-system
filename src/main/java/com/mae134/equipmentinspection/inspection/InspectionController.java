package com.mae134.equipmentinspection.inspection;

import java.util.List;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/inspections")
public class InspectionController {

  private final InspectionService inspectionService;

  public InspectionController(InspectionService inspectionService) {
    this.inspectionService = inspectionService;
  }

  @GetMapping
  public List<InspectionResponse> findAll() {
    return inspectionService.findAll();
  }

  @GetMapping("/{id}")
  public InspectionResponse findById(@PathVariable Long id) {
    return inspectionService
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inspection not found: " + id));
  }

  @PostMapping
  public InspectionResponse save(@RequestBody InspectionRequest request) {
    return inspectionService.save(request);
  }

  @PutMapping("/{id}")
  public InspectionResponse update(@PathVariable Long id, @RequestBody InspectionRequest request) {
    return inspectionService.update(id, request);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id) {
    inspectionService.delete(id);
  }
}
