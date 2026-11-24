package com.mae134.equipmentinspection.web;

import com.mae134.equipmentinspection.dashboard.DashboardController;
import com.mae134.equipmentinspection.equipment.EquipmentPageController;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItemPageController;
import com.mae134.equipmentinspection.inspectionrecord.InspectionRecordController;
import com.mae134.equipmentinspection.user.UserRepository;
import java.security.Principal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(
    assignableTypes = {
      DashboardController.class,
      EquipmentPageController.class,
      EquipmentInspectionItemPageController.class,
      InspectionRecordController.class
    })
public class GlobalViewModelAdvice {

  private final UserRepository userRepository;

  public GlobalViewModelAdvice(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @ModelAttribute("headerUserName")
  public String headerUserName(Principal principal) {
    if (principal == null) {
      return null;
    }

    return userRepository.findByEmail(principal.getName()).map(user -> user.getName()).orElse(null);
  }
}
