package com.mae134.equipmentinspection.inspection;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

  List<Inspection> findByEquipmentIdOrderByInspectionAtDesc(Long equipmentId);
}
