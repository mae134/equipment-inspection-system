package com.mae134.equipmentinspection.inspectionrecord;

import com.mae134.equipmentinspection.equipment.EquipmentResponse;
import com.mae134.equipmentinspection.equipment.EquipmentService;
import com.mae134.equipmentinspection.exception.ResourceNotFoundException;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItemResponse;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItemService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class InspectionRecordController {

  private final EquipmentService equipmentService;

  private final EquipmentInspectionItemService inspectionItemService;

  public InspectionRecordController(
      EquipmentService equipmentService, EquipmentInspectionItemService inspectionItemService) {
    this.equipmentService = equipmentService;
    this.inspectionItemService = inspectionItemService;
  }

  @GetMapping("/equipment/{equipmentId}/inspections/new")
  public String showInspectionRecord(@PathVariable Long equipmentId, Model model) {
    EquipmentResponse equipment =
        equipmentService
            .findById(equipmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Equipment not found: " + equipmentId));

    List<EquipmentInspectionItemResponse> inspectionItems =
        inspectionItemService.findByEquipmentId(equipmentId);

    model.addAttribute("equipment", equipment);
    model.addAttribute("inspectionItems", inspectionItems);

    return "inspection-record";
  }
}
