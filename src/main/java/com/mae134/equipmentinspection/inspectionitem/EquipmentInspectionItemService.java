package com.mae134.equipmentinspection.inspectionitem;

import com.mae134.equipmentinspection.equipment.Equipment;
import com.mae134.equipmentinspection.equipment.EquipmentRepository;
import com.mae134.equipmentinspection.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EquipmentInspectionItemService {

  private final EquipmentInspectionItemRepository inspectionItemRepository;
  private final EquipmentRepository equipmentRepository;

  public EquipmentInspectionItemService(
      EquipmentInspectionItemRepository inspectionItemRepository,
      EquipmentRepository equipmentRepository) {

    this.inspectionItemRepository = inspectionItemRepository;
    this.equipmentRepository = equipmentRepository;
  }

  public EquipmentInspectionItemResponse save(EquipmentInspectionItemRequest request) {

    Equipment equipment =
        equipmentRepository
            .findById(request.equipmentId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("Equipment not found: " + request.equipmentId()));

    validateRequest(request);

    EquipmentInspectionItem inspectionItem = new EquipmentInspectionItem();

    inspectionItem.setEquipment(equipment);
    inspectionItem.setName(request.name());
    inspectionItem.setType(request.type());
    inspectionItem.setUnit(request.unit());
    inspectionItem.setMinValue(request.minValue());
    inspectionItem.setMaxValue(request.maxValue());
    inspectionItem.setNormalBooleanValue(request.normalBooleanValue());
    inspectionItem.setDescription(request.description());
    inspectionItem.setDisplayOrder(request.displayOrder());
    inspectionItem.setActive(request.active());

    EquipmentInspectionItem savedInspectionItem = inspectionItemRepository.save(inspectionItem);

    return toResponse(savedInspectionItem);
  }

  public List<EquipmentInspectionItemResponse> findAll() {
    return inspectionItemRepository.findAll().stream().map(this::toResponse).toList();
  }

  public EquipmentInspectionItemResponse findById(Long id) {
    EquipmentInspectionItem inspectionItem =
        inspectionItemRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("Equipment inspection item not found: " + id));

    return toResponse(inspectionItem);
  }

  public List<EquipmentInspectionItemResponse> findByEquipmentId(Long equipmentId) {
    if (!equipmentRepository.existsById(equipmentId)) {
      throw new ResourceNotFoundException("Equipment not found: " + equipmentId);
    }

    return inspectionItemRepository.findByEquipmentId(equipmentId).stream()
        .map(this::toResponse)
        .toList();
  }

  public EquipmentInspectionItemResponse update(Long id, EquipmentInspectionItemRequest request) {

    EquipmentInspectionItem inspectionItem =
        inspectionItemRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("Equipment inspection item not found: " + id));

    Equipment equipment =
        equipmentRepository
            .findById(request.equipmentId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("Equipment not found: " + request.equipmentId()));

    validateRequest(request);

    inspectionItem.setEquipment(equipment);
    inspectionItem.setName(request.name());
    inspectionItem.setType(request.type());
    inspectionItem.setUnit(request.unit());
    inspectionItem.setMinValue(request.minValue());
    inspectionItem.setMaxValue(request.maxValue());
    inspectionItem.setNormalBooleanValue(request.normalBooleanValue());
    inspectionItem.setDescription(request.description());
    inspectionItem.setDisplayOrder(request.displayOrder());
    inspectionItem.setActive(request.active());

    EquipmentInspectionItem updatedInspectionItem = inspectionItemRepository.save(inspectionItem);

    return toResponse(updatedInspectionItem);
  }

  public void delete(Long id) {
    EquipmentInspectionItem inspectionItem =
        inspectionItemRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("Equipment inspection item not found: " + id));

    inspectionItemRepository.delete(inspectionItem);
  }

  private void validateRequest(EquipmentInspectionItemRequest request) {

    // 閾値のバリデーション
    if (request.type() == InspectionItemType.NUMERIC) {
      if (request.normalBooleanValue() != null) {
        throw new IllegalArgumentException("NUMERIC type must not have normalBooleanValue");
      }

      if (request.minValue() != null
          && request.maxValue() != null
          && request.minValue().compareTo(request.maxValue()) > 0) {

        throw new IllegalArgumentException("minValue must be less than or equal to maxValue");
      }
    }

    // BOOLEAN型のバリデーション
    if (request.type() == InspectionItemType.BOOLEAN) {
      if (request.normalBooleanValue() == null) {
        throw new IllegalArgumentException("BOOLEAN type requires normalBooleanValue");
      }

      if (request.unit() != null || request.minValue() != null || request.maxValue() != null) {

        throw new IllegalArgumentException("BOOLEAN type must not have numeric settings");
      }
    }
  }

  private EquipmentInspectionItemResponse toResponse(EquipmentInspectionItem inspectionItem) {

    return new EquipmentInspectionItemResponse(
        inspectionItem.getId(),
        inspectionItem.getEquipment().getId(),
        inspectionItem.getName(),
        inspectionItem.getType(),
        inspectionItem.getUnit(),
        inspectionItem.getMinValue(),
        inspectionItem.getMaxValue(),
        inspectionItem.getNormalBooleanValue(),
        inspectionItem.getDescription(),
        inspectionItem.getDisplayOrder(),
        inspectionItem.getActive(),
        inspectionItem.getCreatedAt(),
        inspectionItem.getUpdatedAt());
  }
}
