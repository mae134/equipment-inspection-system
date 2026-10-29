package com.mae134.equipmentinspection.inspectionresult;

import com.mae134.equipmentinspection.exception.ResourceNotFoundException;
import com.mae134.equipmentinspection.inspection.Inspection;
import com.mae134.equipmentinspection.inspection.InspectionRepository;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItem;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItemRepository;
import com.mae134.equipmentinspection.inspectionitem.InspectionItemType;
import java.util.List;
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

    InspectionResultStatus result = determineResult(request, inspectionItem);

    InspectionResult inspectionResult = new InspectionResult();

    inspectionResult.setInspection(inspection);
    inspectionResult.setInspectionItem(inspectionItem);
    inspectionResult.setNumericValue(request.numericValue());
    inspectionResult.setBooleanValue(request.booleanValue());
    inspectionResult.setResult(result);
    inspectionResult.setComment(request.comment());

    InspectionResult savedInspectionResult = inspectionResultRepository.save(inspectionResult);

    return toResponse(savedInspectionResult);
  }

  public List<InspectionResultResponse> findAll() {
    return inspectionResultRepository.findAll().stream().map(this::toResponse).toList();
  }

  public InspectionResultResponse findById(Long id) {
    InspectionResult inspectionResult =
        inspectionResultRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Inspection result not found: " + id));

    return toResponse(inspectionResult);
  }

  public List<InspectionResultResponse> findByInspectionId(Long inspectionId) {

    if (!inspectionRepository.existsById(inspectionId)) {
      throw new ResourceNotFoundException("Inspection not found: " + inspectionId);
    }

    return inspectionResultRepository.findByInspectionId(inspectionId).stream()
        .map(this::toResponse)
        .toList();
  }

  private InspectionResultStatus determineResult(
      InspectionResultRequest request, EquipmentInspectionItem inspectionItem) {

    if (request.notApplicable()) {

      if (request.numericValue() != null || request.booleanValue() != null) {
        throw new IllegalArgumentException(
            "NOT_APPLICABLE must not have numericValue or booleanValue");
      }

      return InspectionResultStatus.NOT_APPLICABLE;
    }

    if (inspectionItem.getType() == InspectionItemType.NUMERIC) {

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

      return belowMin || aboveMax ? InspectionResultStatus.NG : InspectionResultStatus.OK;
    }

    if (inspectionItem.getType() == InspectionItemType.BOOLEAN) {

      if (request.booleanValue() == null) {
        throw new IllegalArgumentException("BOOLEAN type requires booleanValue");
      }

      if (request.numericValue() != null) {
        throw new IllegalArgumentException("BOOLEAN type must not have numericValue");
      }

      return request.booleanValue().equals(inspectionItem.getNormalBooleanValue())
          ? InspectionResultStatus.OK
          : InspectionResultStatus.NG;
    }

    throw new IllegalArgumentException(
        "Unsupported inspection item type: " + inspectionItem.getType());
  }

  public InspectionResultResponse update(Long id, InspectionResultRequest request) {

    InspectionResult inspectionResult =
        inspectionResultRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Inspection result not found: " + id));

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

    if (inspectionResultRepository.existsByInspectionIdAndInspectionItemIdAndIdNot(
        request.inspectionId(), request.inspectionItemId(), id)) {

      throw new IllegalArgumentException(
          "Inspection result already exists for this inspection item");
    }

    InspectionResultStatus result = determineResult(request, inspectionItem);

    inspectionResult.setInspection(inspection);
    inspectionResult.setInspectionItem(inspectionItem);
    inspectionResult.setNumericValue(request.numericValue());
    inspectionResult.setBooleanValue(request.booleanValue());
    inspectionResult.setResult(result);
    inspectionResult.setComment(request.comment());

    InspectionResult updatedInspectionResult = inspectionResultRepository.save(inspectionResult);

    return toResponse(updatedInspectionResult);
  }

  public void delete(Long id) {
    InspectionResult inspectionResult =
        inspectionResultRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Inspection result not found: " + id));

    inspectionResultRepository.delete(inspectionResult);
  }
}
