package com.mae134.equipmentinspection.inspectionresult;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionResultRepository extends JpaRepository<InspectionResult, Long> {

  List<InspectionResult> findByInspectionId(Long inspectionId);

  boolean existsByInspectionIdAndInspectionItemId(Long inspectionId, Long inspectionItemId);

  boolean existsByInspectionIdAndInspectionItemIdAndIdNot(
      Long inspectionId, Long inspectionItemId, Long id);
}
