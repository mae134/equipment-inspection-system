package com.mae134.equipmentinspection.equipment;

import com.mae134.equipmentinspection.exception.DuplicateResourceException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EquipmentService {

  private final EquipmentRepository equipmentRepository;

  public EquipmentService(EquipmentRepository equipmentRepository) {
    this.equipmentRepository = equipmentRepository;
  }

  public List<EquipmentResponse> findAll() {
    return equipmentRepository.findAll().stream().map(this::toResponse).toList();
  }

  public EquipmentResponse save(EquipmentRequest request) {

    if (equipmentRepository.existsByEquipmentCode(request.equipmentCode())) {
      throw new DuplicateResourceException("設備コードは既に登録されています: " + request.equipmentCode());
    }

    Equipment equipment = new Equipment();

    equipment.setEquipmentCode(request.equipmentCode());
    equipment.setName(request.name());
    equipment.setManufacturer(request.manufacturer());
    equipment.setModel(request.model());
    equipment.setLocation(request.location());
    equipment.setInstalledDate(request.installedDate());
    equipment.setInspectionCycleDays(request.inspectionCycleDays());
    equipment.setDescription(request.description());
    equipment.setActive(request.active());

    Equipment savedEquipment = equipmentRepository.save(equipment);

    return toResponse(savedEquipment);
  }

  public Optional<EquipmentResponse> findById(Long id) {
    return equipmentRepository.findById(id).map(this::toResponse);
  }

  public EquipmentResponse update(Long id, EquipmentRequest request) {
    Equipment equipment =
        equipmentRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Equipment not found: " + id));

    if (equipmentRepository.existsByEquipmentCodeAndIdNot(request.equipmentCode(), id)) {

      throw new DuplicateResourceException("設備コードは既に登録されています: " + request.equipmentCode());
    }

    equipment.setEquipmentCode(request.equipmentCode());
    equipment.setName(request.name());
    equipment.setManufacturer(request.manufacturer());
    equipment.setModel(request.model());
    equipment.setLocation(request.location());
    equipment.setInstalledDate(request.installedDate());
    equipment.setInspectionCycleDays(request.inspectionCycleDays());
    equipment.setDescription(request.description());
    equipment.setActive(request.active());

    Equipment updatedEquipment = equipmentRepository.save(equipment);

    return toResponse(updatedEquipment);
  }

  public void delete(Long id) {
    Equipment equipment =
        equipmentRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Equipment not found: " + id));

    equipmentRepository.delete(equipment);
  }

  private EquipmentResponse toResponse(Equipment equipment) {
    return new EquipmentResponse(
        equipment.getId(),
        equipment.getEquipmentCode(),
        equipment.getName(),
        equipment.getManufacturer(),
        equipment.getModel(),
        equipment.getLocation(),
        equipment.getInstalledDate(),
        equipment.getInspectionCycleDays(),
        equipment.getDescription(),
        equipment.getActive(),
        equipment.getCreatedAt(),
        equipment.getUpdatedAt());
  }
}
