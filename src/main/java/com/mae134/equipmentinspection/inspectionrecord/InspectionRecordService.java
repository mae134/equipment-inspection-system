package com.mae134.equipmentinspection.inspectionrecord;

import com.mae134.equipmentinspection.exception.ResourceNotFoundException;
import com.mae134.equipmentinspection.inspection.Inspection;
import com.mae134.equipmentinspection.inspection.InspectionRepository;
import com.mae134.equipmentinspection.inspection.InspectionRequest;
import com.mae134.equipmentinspection.inspection.InspectionResponse;
import com.mae134.equipmentinspection.inspection.InspectionService;
import com.mae134.equipmentinspection.inspectionresult.InspectionResultRepository;
import com.mae134.equipmentinspection.inspectionresult.InspectionResultRequest;
import com.mae134.equipmentinspection.inspectionresult.InspectionResultResponse;
import com.mae134.equipmentinspection.inspectionresult.InspectionResultService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionRecordService {

  private final InspectionService inspectionService;
  private final InspectionResultService inspectionResultService;

  private final InspectionRepository inspectionRepository;
  private final InspectionResultRepository inspectionResultRepository;

  public InspectionRecordService(
      InspectionService inspectionService,
      InspectionResultService inspectionResultService,
      InspectionRepository inspectionRepository,
      InspectionResultRepository inspectionResultRepository) {

    this.inspectionService = inspectionService;
    this.inspectionResultService = inspectionResultService;
    this.inspectionRepository = inspectionRepository;
    this.inspectionResultRepository = inspectionResultRepository;
  }

  @Transactional
  public List<InspectionResultResponse> save(
      Long equipmentId, Long userId, InspectionRecordForm form) {

    InspectionRequest inspectionRequest =
        new InspectionRequest(equipmentId, userId, form.getInspectionAt(), form.getComment());

    InspectionResponse inspection = inspectionService.save(inspectionRequest);

    List<InspectionResultResponse> results = new ArrayList<>();

    for (InspectionRecordItemForm item : form.getItems()) {

      InspectionResultRequest resultRequest =
          new InspectionResultRequest(
              inspection.id(),
              item.getInspectionItemId(),
              item.getNumericValue(),
              item.getBooleanValue(),
              item.isNotApplicable(),
              item.getComment());

      results.add(inspectionResultService.save(resultRequest));
    }

    return results;
  }

  @Transactional(readOnly = true)
  public List<InspectionHistoryResponse> findHistoryByEquipmentId(Long equipmentId) {

    return inspectionRepository.findByEquipmentIdOrderByInspectionAtDesc(equipmentId).stream()
        .map(
            inspection ->
                new InspectionHistoryResponse(
                    inspection.getId(),
                    inspection.getInspectionAt(),
                    inspection.getUser().getName(),
                    inspection.getComment()))
        .toList();
  }

  @Transactional(readOnly = true)
  public InspectionHistoryDetailResponse findHistoryDetail(Long inspectionId) {

    Inspection inspection =
        inspectionRepository
            .findById(inspectionId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Inspection not found: " + inspectionId));

    List<InspectionHistoryResultResponse> results =
        inspectionResultRepository.findByInspectionId(inspectionId).stream()
            .map(
                inspectionResult ->
                    new InspectionHistoryResultResponse(
                        inspectionResult.getInspectionItem().getName(),
                        inspectionResult.getInspectionItem().getType(),
                        inspectionResult.getInspectionItem().getUnit(),
                        inspectionResult.getNumericValue(),
                        inspectionResult.getBooleanValue(),
                        inspectionResult.getResult(),
                        inspectionResult.getComment(),
                        inspectionResult.getInspectionItem().getDisplayOrder()))
            .sorted(Comparator.comparing(InspectionHistoryResultResponse::displayOrder))
            .toList();

    return new InspectionHistoryDetailResponse(
        inspection.getId(),
        inspection.getEquipment().getId(),
        inspection.getEquipment().getEquipmentCode(),
        inspection.getEquipment().getName(),
        inspection.getInspectionAt(),
        inspection.getUser().getName(),
        inspection.getComment(),
        inspection.getCreatedAt(),
        results);
  }
}
