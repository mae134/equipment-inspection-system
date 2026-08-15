package com.mae134.equipmentinspection.inspectionresult;

import com.mae134.equipmentinspection.exception.ResourceNotFoundException;
import com.mae134.equipmentinspection.inspection.Inspection;
import com.mae134.equipmentinspection.inspection.InspectionRepository;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItem;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItemRepository;
import com.mae134.equipmentinspection.inspectionitem.InspectionItemType;
import org.springframework.stereotype.Service;

@Service
public class InspectionResultService {

  private final InspectionResultRepository inspectionResultRepository;
  private final InspectionRepository inspectionRepository;
  private final EquipmentInspectionItemRepository inspectionItemRepository;

  public InspectionResultService(
      InspectionResultRepository inspectionResultRepository,
      InspectionRepository inspectionRepository,
      EquipmentInspectionItemRepository inspectionItemRepository) {

    this.inspectionResultRepository = inspectionResultRepository;
    this.inspectionRepository = inspectionRepository;
    this.inspectionItemRepository = inspectionItemRepository;
  }

  private InspectionResultResponse toResponse(InspectionResult inspectionResult) {
    return new InspectionResultResponse(
        inspectionResult.getId(),
        inspectionResult.getInspection().getId(),
        inspectionResult.getInspectionItem().getId(),
        inspectionResult.getNumericValue(),
        inspectionResult.getBooleanValue(),
        inspectionResult.getResult(),
        inspectionResult.getComment(),
        inspectionResult.getCreatedAt(),
        inspectionResult.getUpdatedAt());
  }

  public InspectionResultResponse save(InspectionResultRequest request) {

    Inspection inspection =
        inspectionRepository
            .findById(request.inspectionId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Inspection not found: " + request.inspectionId()));

    EquipmentInspectionItem inspectionItem =
        inspectionItemRepository
            .findById(request.inspectionItemId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Equipment inspection item not found: " + request.inspectionItemId()));

    if (!inspection.getEquipment().getId().equals(inspectionItem.getEquipment().getId())) {

      throw new IllegalArgumentException("Inspection item must belong to the inspected equipment");
    }

    if (inspectionResultRepository.existsByInspectionIdAndInspectionItemId(
        request.inspectionId(), request.inspectionItemId())) {

      throw new IllegalArgumentException(
          "Inspection result already exists for this inspection item");
    }

    InspectionResultStatus result;

    if (request.notApplicable()) {

      if (request.numericValue() != null || request.booleanValue() != null) {
        throw new IllegalArgumentException(
            "NOT_APPLICABLE must not have numericValue or booleanValue");
      }

      result = InspectionResultStatus.NOT_APPLICABLE;

    } else if (inspectionItem.getType() == InspectionItemType.NUMERIC) {

      if (request.numericValue() == null) {
        throw new IllegalArgumentException("NUMERIC type requires numericValue");
      }

      if (request.booleanValue() != null) {
        throw new IllegalArgumentException("NUMERIC type must not have booleanValue");
      }

      boolean belowMin =
          inspectionItem.getMinValue() != null
              && request.numericValue().compareTo(inspectionItem.getMinValue()) < 0;

      boolean aboveMax =
          inspectionItem.getMaxValue() != null
              && request.numericValue().compareTo(inspectionItem.getMaxValue()) > 0;

      result = belowMin || aboveMax ? InspectionResultStatus.NG : InspectionResultStatus.OK;
    }

    // このあと入力値の検証・判定・保存を追加する
    return null;
  }
}
