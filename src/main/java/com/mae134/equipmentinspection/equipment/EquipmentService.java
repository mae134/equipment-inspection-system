package com.mae134.equipmentinspection.equipment;

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

  public List<Equipment> findAll() {
    return equipmentRepository.findAll();
  }

  public Equipment save(Equipment equipment) {
    return equipmentRepository.save(equipment);
  }

  public Optional<Equipment> findById(Long id) {
    return equipmentRepository.findById(id);
  }

  public Equipment update(Long id, Equipment request) {

    Equipment equipment = equipmentRepository.findById(id).orElseThrow();

    equipment.setEquipmentCode(request.getEquipmentCode());
    equipment.setName(request.getName());
    equipment.setManufacturer(request.getManufacturer());
    equipment.setModel(request.getModel());
    equipment.setLocation(request.getLocation());
    equipment.setInstalledDate(request.getInstalledDate());
    equipment.setInspectionCycleDays(request.getInspectionCycleDays());
    equipment.setDescription(request.getDescription());

    return equipmentRepository.save(equipment);
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
}
