package com.mae134.equipmentinspection.inspectionitem;

import com.mae134.equipmentinspection.equipment.Equipment;
import com.mae134.equipmentinspection.equipment.EquipmentRepository;
import com.mae134.equipmentinspection.exception.DuplicateResourceException;
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

    Equipment equipment = equipmentRepository
        .findById(request.equipmentId())
        .orElseThrow(
            () -> new ResourceNotFoundException("Equipment not found: " + request.equipmentId()));

    validateRequest(request);

    if (inspectionItemRepository.existsByEquipmentIdAndName(
        request.equipmentId(), request.name())) {
      throw new DuplicateResourceException("点検項目名は既に登録されています");
    }

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
    EquipmentInspectionItem inspectionItem = inspectionItemRepository
        .findById(id)
        .orElseThrow(
            () -> new ResourceNotFoundException("Equipment inspection item not found: " + id));

    return toResponse(inspectionItem);
  }

  public List<EquipmentInspectionItemResponse> findByEquipmentId(Long equipmentId) {
    if (!equipmentRepository.existsById(equipmentId)) {
      throw new ResourceNotFoundException("Equipment not found: " + equipmentId);
    }

    return inspectionItemRepository.findByEquipmentIdOrderByDisplayOrderAsc(equipmentId).stream()
        .map(this::toResponse)
        .toList();
  }

  public EquipmentInspectionItemResponse update(Long id, EquipmentInspectionItemRequest request) {

    EquipmentInspectionItem inspectionItem = inspectionItemRepository
        .findById(id)
        .orElseThrow(
            () -> new ResourceNotFoundException("Equipment inspection item not found: " + id));

    Equipment equipment = equipmentRepository
        .findById(request.equipmentId())
        .orElseThrow(
            () -> new ResourceNotFoundException("Equipment not found: " + request.equipmentId()));

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
    EquipmentInspectionItem inspectionItem = inspectionItemRepository
        .findById(id)
        .orElseThrow(
            () -> new ResourceNotFoundException("Equipment inspection item not found: " + id));

    inspectionItemRepository.delete(inspectionItem);
  }

  private void validateRequest(EquipmentInspectionItemRequest request) {

    // 数値型のバリデーション
    if (request.type() == InspectionItemType.NUMERIC) {
      if (request.normalBooleanValue() != null) {
        throw new IllegalArgumentException(
            "数値型の点検項目には真偽値の正常値を設定できません");
      }

      if (request.minValue() != null
          && request.maxValue() != null
          && request.minValue().compareTo(request.maxValue()) > 0) {

        throw new IllegalArgumentException(
            "正常範囲の最小値は最大値以下にしてください");
      }
    }

    // 真偽値型のバリデーション
    if (request.type() == InspectionItemType.BOOLEAN) {
      if (request.normalBooleanValue() == null) {
        throw new IllegalArgumentException(
            "真偽値型の点検項目は正常値を選択してください");
      }

      if (request.unit() != null
          || request.minValue() != null
          || request.maxValue() != null) {

        throw new IllegalArgumentException(
            "真偽値型の点検項目には数値型の設定を指定できません");
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
