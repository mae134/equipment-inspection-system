package com.mae134.equipmentinspection.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class EquipmentRepositoryTest {

  @Autowired private EquipmentRepository equipmentRepository;

  @Test
  void searchShouldFindEquipmentByPartialEquipmentCode() {
    Equipment equipment = createEquipment("TEST-EQ-999", "Repository検索コードテスト設備");

    equipmentRepository.save(equipment);

    List<Equipment> result =
        equipmentRepository.findByEquipmentCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
            "EQ-999", "EQ-999");

    assertEquals(1, result.size());
    assertEquals("TEST-EQ-999", result.get(0).getEquipmentCode());
  }

  @Test
  void searchShouldFindEquipmentByPartialName() {
    Equipment equipment = createEquipment("TEST-EQ-998", "Repository検索名前テスト設備");

    equipmentRepository.save(equipment);

    List<Equipment> result =
        equipmentRepository.findByEquipmentCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
            "検索名前", "検索名前");

    assertEquals(1, result.size());
    assertEquals("Repository検索名前テスト設備", result.get(0).getName());
  }

  private Equipment createEquipment(String equipmentCode, String name) {
    Equipment equipment = new Equipment();
    equipment.setEquipmentCode(equipmentCode);
    equipment.setName(name);
    equipment.setManufacturer("ABC");
    equipment.setModel("P-100");
    equipment.setLocation("工場A");
    equipment.setInstalledDate(LocalDate.of(2026, 8, 6));
    equipment.setInspectionCycleDays(30);
    equipment.setDescription("定期点検対象設備");
    equipment.setActive(true);

    return equipment;
  }
}
