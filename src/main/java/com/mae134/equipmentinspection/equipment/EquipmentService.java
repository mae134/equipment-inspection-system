package com.mae134.equipmentinspection.equipment;

import java.util.List;
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
}
