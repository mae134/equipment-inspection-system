package com.mae134.equipmentinspection.inspection;

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
    return inspectionService.findById(id);
  }

  @PostMapping
  public InspectionResponse save(@Valid @RequestBody InspectionRequest request) {
    return inspectionService.save(request);
  }

  @PutMapping("/{id}")
  public InspectionResponse update(
      @PathVariable Long id, @Valid @RequestBody InspectionRequest request) {
    return inspectionService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    inspectionService.delete(id);
  }
}
