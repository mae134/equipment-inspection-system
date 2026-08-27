package com.mae134.equipmentinspection.dashboard;

import com.mae134.equipmentinspection.equipment.EquipmentResponse;
import com.mae134.equipmentinspection.equipment.EquipmentService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

  private final EquipmentService equipmentService;

  public DashboardController(EquipmentService equipmentService) {
    this.equipmentService = equipmentService;
  }

  @GetMapping("/dashboard")
  public String showDashboard(@RequestParam(required = false) String keyword, Model model) {

    List<EquipmentResponse> equipmentList = equipmentService.search(keyword);

    model.addAttribute("equipmentList", equipmentList);
    model.addAttribute("keyword", keyword);

    return "dashboard";
  }
}
