package com.mae134.equipmentinspection.inspectionrecord;

import com.mae134.equipmentinspection.equipment.EquipmentResponse;
import com.mae134.equipmentinspection.equipment.EquipmentService;
import com.mae134.equipmentinspection.exception.ResourceNotFoundException;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItemResponse;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItemService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class InspectionRecordController {

  private final EquipmentService equipmentService;

  private final EquipmentInspectionItemService inspectionItemService;
  private final InspectionRecordService inspectionRecordService;

  public InspectionRecordController(
      EquipmentService equipmentService,
      EquipmentInspectionItemService inspectionItemService,
      InspectionRecordService inspectionRecordService) {

    this.equipmentService = equipmentService;
    this.inspectionItemService = inspectionItemService;
    this.inspectionRecordService = inspectionRecordService;
  }

  @GetMapping("/equipment/{equipmentId}/inspections/new")
  public String showInspectionRecord(@PathVariable Long equipmentId, Model model) {
    EquipmentResponse equipment =
        equipmentService
            .findById(equipmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Equipment not found: " + equipmentId));

    List<EquipmentInspectionItemResponse> inspectionItems =
        inspectionItemService.findByEquipmentId(equipmentId).stream()
            .filter(EquipmentInspectionItemResponse::active)
            .toList();

    InspectionRecordForm form = new InspectionRecordForm();

    for (EquipmentInspectionItemResponse item : inspectionItems) {
      InspectionRecordItemForm itemForm = new InspectionRecordItemForm();
      itemForm.setInspectionItemId(item.id());
      form.getItems().add(itemForm);
    }

    form.setInspectionAt(LocalDateTime.now());

    model.addAttribute("equipment", equipment);
    model.addAttribute("inspectionItems", inspectionItems);
    model.addAttribute("inspectionRecordForm", form);

    return "inspection-record";
  }

  @PostMapping("/equipment/{equipmentId}/inspections")
  public String saveInspectionRecord(
      @PathVariable Long equipmentId, @ModelAttribute InspectionRecordForm form) {

    inspectionRecordService.save(equipmentId, form);

    return "redirect:/equipment/" + equipmentId + "/inspections/new";
  }
}
