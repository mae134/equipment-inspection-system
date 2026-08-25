package com.mae134.equipmentinspection.inspectionrecord;

import com.mae134.equipmentinspection.inspection.InspectionRequest;
import com.mae134.equipmentinspection.inspection.InspectionResponse;
import com.mae134.equipmentinspection.inspection.InspectionService;
import com.mae134.equipmentinspection.inspectionresult.InspectionResultRequest;
import com.mae134.equipmentinspection.inspectionresult.InspectionResultResponse;
import com.mae134.equipmentinspection.inspectionresult.InspectionResultService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionRecordService {

  private final InspectionService inspectionService;
  private final InspectionResultService inspectionResultService;

  public InspectionRecordService(
      InspectionService inspectionService, InspectionResultService inspectionResultService) {
    this.inspectionService = inspectionService;
    this.inspectionResultService = inspectionResultService;
  }

  @Transactional
  public List<InspectionResultResponse> save(
      Long equipmentId, Long userId, InspectionRecordForm form) {

    InspectionRequest inspectionRequest =
        new InspectionRequest(equipmentId, userId, form.getInspectionAt(), form.getComment());

    InspectionResponse inspection = inspectionService.save(inspectionRequest);

    List<InspectionResultResponse> results = new ArrayList<>();

    for (InspectionRecordItemForm item : form.getItems()) {

      InspectionResultRequest resultRequest =
          new InspectionResultRequest(
              inspection.id(),
              item.getInspectionItemId(),
              item.getNumericValue(),
              item.getBooleanValue(),
              item.isNotApplicable(),
              item.getComment());

      results.add(inspectionResultService.save(resultRequest));
    }

    return results;
  }
}
