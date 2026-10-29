package com.mae134.equipmentinspection.inspectionitem;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentInspectionItemRepository
    extends JpaRepository<EquipmentInspectionItem, Long> {

  List<EquipmentInspectionItem> findByEquipmentId(Long equipmentId);
}
