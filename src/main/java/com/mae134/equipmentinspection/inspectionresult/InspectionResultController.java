package com.mae134.equipmentinspection.inspectionresult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspection-results")
@Tag(name = "点検結果管理", description = "点検結果を管理するAPI")
public class InspectionResultController {

  private final InspectionResultService inspectionResultService;

  public InspectionResultController(InspectionResultService inspectionResultService) {
    this.inspectionResultService = inspectionResultService;
  }

  @PostMapping
  @Operation(summary = "点検結果を登録する")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "点検結果の登録に成功"),
    @ApiResponse(responseCode = "400", description = "入力値または点検結果の設定が不正"),
    @ApiResponse(responseCode = "404", description = "指定した点検または点検項目が存在しない")
  })
  public InspectionResultResponse save(@Valid @RequestBody InspectionResultRequest request) {

    return inspectionResultService.save(request);
  }

  @GetMapping
  @Operation(summary = "点検結果一覧を取得する")
  @ApiResponse(responseCode = "200", description = "点検結果一覧の取得に成功")
  public List<InspectionResultResponse> findAll() {
    return inspectionResultService.findAll();
  }

  @GetMapping("/{id}")
  @Operation(summary = "点検結果を取得する")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "点検結果の取得に成功"),
    @ApiResponse(responseCode = "404", description = "指定した点検結果が存在しない")
  })
  public InspectionResultResponse findById(@PathVariable Long id) {
    return inspectionResultService.findById(id);
  }

  @GetMapping("/inspection/{inspectionId}")
  @Operation(summary = "点検に紐づく点検結果一覧を取得する")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "点検に紐づく点検結果一覧の取得に成功"),
    @ApiResponse(responseCode = "404", description = "指定した点検が存在しない")
  })
  public List<InspectionResultResponse> findByInspectionId(@PathVariable Long inspectionId) {
    return inspectionResultService.findByInspectionId(inspectionId);
  }

  @PutMapping("/{id}")
  @Operation(summary = "点検結果を更新する")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "点検結果の更新に成功"),
    @ApiResponse(responseCode = "400", description = "入力値または点検結果の設定が不正"),
    @ApiResponse(responseCode = "404", description = "指定した点検結果、点検または点検項目が存在しない")
  })
  public InspectionResultResponse update(
      @PathVariable Long id, @Valid @RequestBody InspectionResultRequest request) {
    return inspectionResultService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "点検結果を削除する")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "点検結果の削除に成功"),
    @ApiResponse(responseCode = "404", description = "指定した点検結果が存在しない")
  })
  public void delete(@PathVariable Long id) {
    inspectionResultService.delete(id);
  }
}
