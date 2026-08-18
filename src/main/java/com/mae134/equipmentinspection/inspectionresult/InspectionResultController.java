package com.mae134.equipmentinspection.inspectionresult;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspection-results")
public class InspectionResultController {

  private final InspectionResultService inspectionResultService;

  public InspectionResultController(InspectionResultService inspectionResultService) {
    this.inspectionResultService = inspectionResultService;
  }

  @PostMapping
  public InspectionResultResponse save(@Valid @RequestBody InspectionResultRequest request) {

    return inspectionResultService.save(request);
  }

  @GetMapping
  public List<InspectionResultResponse> findAll() {
    return inspectionResultService.findAll();
  }

  @GetMapping("/{id}")
  public InspectionResultResponse findById(@PathVariable Long id) {
    return inspectionResultService.findById(id);
  }

  @GetMapping("/inspection/{inspectionId}")
  public List<InspectionResultResponse> findByInspectionId(@PathVariable Long inspectionId) {
    return inspectionResultService.findByInspectionId(inspectionId);
  }

  @PutMapping("/{id}")
  public InspectionResultResponse update(
      @PathVariable Long id, @Valid @RequestBody InspectionResultRequest request) {
    return inspectionResultService.update(id, request);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id) {
    inspectionResultService.delete(id);
  }
}
