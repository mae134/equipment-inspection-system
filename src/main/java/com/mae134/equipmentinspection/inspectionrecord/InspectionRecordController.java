package com.mae134.equipmentinspection.inspectionrecord;

import com.mae134.equipmentinspection.equipment.EquipmentResponse;
import com.mae134.equipmentinspection.equipment.EquipmentService;
import com.mae134.equipmentinspection.exception.ResourceNotFoundException;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItemResponse;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItemService;
import com.mae134.equipmentinspection.inspectionitem.InspectionItemType;
import com.mae134.equipmentinspection.inspectionresult.InspectionResultResponse;
import com.mae134.equipmentinspection.user.User;
import com.mae134.equipmentinspection.user.UserRepository;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class InspectionRecordController {

  private final EquipmentService equipmentService;

  private final EquipmentInspectionItemService inspectionItemService;
  private final InspectionRecordService inspectionRecordService;
  private final UserRepository userRepository;

  public InspectionRecordController(
      EquipmentService equipmentService,
      EquipmentInspectionItemService inspectionItemService,
      InspectionRecordService inspectionRecordService,
      UserRepository userRepository) {

    this.equipmentService = equipmentService;
    this.inspectionItemService = inspectionItemService;
    this.inspectionRecordService = inspectionRecordService;
    this.userRepository = userRepository;
  }

  @GetMapping("/equipment/{equipmentId}/inspections/new")
  public String showInspectionRecord(
      @PathVariable Long equipmentId, Model model, Principal principal) {

    User currentUser =
        userRepository
            .findByEmail(principal.getName())
            .orElseThrow(
                () -> new ResourceNotFoundException("User not found: " + principal.getName()));

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
    model.addAttribute("currentUser", currentUser);

    return "inspection-record";
  }

  @PostMapping("/equipment/{equipmentId}/inspections")
  public String saveInspectionRecord(
      @PathVariable Long equipmentId,
      @Valid @ModelAttribute InspectionRecordForm form,
      BindingResult bindingResult,
      Model model,
      Principal principal) {

    User currentUser =
        userRepository
            .findByEmail(principal.getName())
            .orElseThrow(
                () -> new ResourceNotFoundException("User not found: " + principal.getName()));

    List<EquipmentInspectionItemResponse> inspectionItems =
        inspectionItemService.findByEquipmentId(equipmentId).stream()
            .filter(EquipmentInspectionItemResponse::active)
            .toList();

    if (form.getItems().size() != inspectionItems.size()) {
      bindingResult.reject("invalidItems", "点検項目の入力内容が不正です");
    }

    if (form.getItems().size() == inspectionItems.size()) {
      for (int i = 0; i < form.getItems().size(); i++) {
        InspectionRecordItemForm formItem = form.getItems().get(i);
        EquipmentInspectionItemResponse inspectionItem = inspectionItems.get(i);

        if (formItem.isNotApplicable()) {
          if (formItem.getNumericValue() != null || formItem.getBooleanValue() != null) {
            bindingResult.rejectValue(
                "items[" + i + "].notApplicable", "invalid", "対象外の場合は値を入力しないでください");
          }

          continue;
        }

        if (inspectionItem.type() == InspectionItemType.NUMERIC
            && formItem.getNumericValue() == null) {
          bindingResult.rejectValue("items[" + i + "].numericValue", "required", "数値を入力してください");
        }

        if (inspectionItem.type() == InspectionItemType.BOOLEAN
            && formItem.getBooleanValue() == null) {
          bindingResult.rejectValue("items[" + i + "].booleanValue", "required", "はい・いいえを選択してください");
        }
      }
    }

    if (bindingResult.hasErrors()) {
      addViewModel(equipmentId, inspectionItems, model, currentUser);
      return "inspection-record";
    }

    try {
      List<InspectionResultResponse> results =
          inspectionRecordService.save(equipmentId, currentUser.getId(), form);

      addViewModel(equipmentId, inspectionItems, model, currentUser);
      model.addAttribute("registrationSuccess", true);
      model.addAttribute("inspectionResults", results);

      return "inspection-record";

    } catch (IllegalArgumentException e) {
      bindingResult.reject("registrationError", e.getMessage());

      addViewModel(equipmentId, inspectionItems, model, currentUser);
      return "inspection-record";
    }
  }

  private void addViewModel(
      Long equipmentId,
      List<EquipmentInspectionItemResponse> inspectionItems,
      Model model,
      User currentUser) {

    EquipmentResponse equipment =
        equipmentService
            .findById(equipmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Equipment not found: " + equipmentId));

    model.addAttribute("equipment", equipment);
    model.addAttribute("inspectionItems", inspectionItems);
    model.addAttribute("currentUser", currentUser);
  }

  @GetMapping("/equipment/{equipmentId}/inspections")
  public String showInspectionHistory(@PathVariable Long equipmentId, Model model) {

    EquipmentResponse equipment =
        equipmentService
            .findById(equipmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Equipment not found: " + equipmentId));

    List<InspectionHistoryResponse> inspectionHistory =
        inspectionRecordService.findHistoryByEquipmentId(equipmentId);

    model.addAttribute("equipment", equipment);
    model.addAttribute("inspectionHistory", inspectionHistory);

    return "inspection-history";
  }

  @GetMapping("/inspections/{inspectionId}")
  public String showInspectionHistoryDetail(@PathVariable Long inspectionId, Model model) {

    InspectionHistoryDetailResponse detail =
        inspectionRecordService.findHistoryDetail(inspectionId);

    model.addAttribute("detail", detail);

    return "inspection-history-detail";
  }
}
