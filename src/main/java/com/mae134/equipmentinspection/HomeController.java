package com.mae134.equipmentinspection;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

  @GetMapping("/")
  public String home() {
    return "redirect:/equipment/1/inspections/new";
  }
}
