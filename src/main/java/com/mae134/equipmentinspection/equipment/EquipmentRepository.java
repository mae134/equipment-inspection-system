package com.mae134.equipmentinspection.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

  boolean existsByEquipmentCode(String equipmentCode);

  boolean existsByEquipmentCodeAndIdNot(String equipmentCode, Long id);
}
