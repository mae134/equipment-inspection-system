package com.mae134.equipmentinspection.inspectionitem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mae134.equipmentinspection.equipment.Equipment;
import com.mae134.equipmentinspection.equipment.EquipmentRepository;
import com.mae134.equipmentinspection.equipment.EquipmentTestFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class EquipmentInspectionItemRepositoryTest {

  @Autowired private EquipmentInspectionItemRepository inspectionItemRepository;

  @Autowired private EquipmentRepository equipmentRepository;

  @Test
  void findByEquipmentIdShouldReturnItemsInDisplayOrder() {
    Equipment equipment = EquipmentTestFactory.create("TEST-EQ-153", "点検項目テスト設備");

    equipment = equipmentRepository.save(equipment);

    inspectionItemRepository.save(createItem(equipment, "油漏れ確認", 3));
    inspectionItemRepository.save(createItem(equipment, "モーター温度", 1));
    inspectionItemRepository.save(createItem(equipment, "振動確認", 2));

    List<EquipmentInspectionItem> results =
        inspectionItemRepository.findByEquipmentIdOrderByDisplayOrderAsc(equipment.getId());

    assertEquals(3, results.size());
    assertEquals(1, results.get(0).getDisplayOrder());
    assertEquals(2, results.get(1).getDisplayOrder());
    assertEquals(3, results.get(2).getDisplayOrder());
  }

  private EquipmentInspectionItem createItem(Equipment equipment, String name, int displayOrder) {

    EquipmentInspectionItem item = new EquipmentInspectionItem();
    item.setEquipment(equipment);
    item.setName(name);
    item.setType(InspectionItemType.NUMERIC);
    item.setDisplayOrder(displayOrder);
    item.setActive(true);

    return item;
  }
}
