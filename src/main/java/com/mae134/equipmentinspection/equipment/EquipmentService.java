package com.mae134.equipmentinspection.equipment;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

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
}
