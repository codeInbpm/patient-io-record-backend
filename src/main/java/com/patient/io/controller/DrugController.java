package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.common.TenantContext;
import com.patient.io.dto.DrugBatchStatusDTO;
import com.patient.io.dto.DrugSaveDTO;
import com.patient.io.service.DrugService;
import com.patient.io.vo.DrugMergePreviewVO;
import com.patient.io.vo.DrugPageVO;
import com.patient.io.vo.DrugVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 补液用药字典。纯本地自维护，不依赖 HIS 或任何外部接口。
 * <p>检索对已登录用户开放；增删改需要登录（拦截器只放行 GET）。</p>
 */
@RestController
@RequestMapping("/v1/dict/drugs")
@RequiredArgsConstructor
public class DrugController {

    private final DrugService drugService;

    /**
     * @param kw    中文名 / 拼音全拼 / 首字母，为空返回常用药
     * @param route 给药途径(可选)
     */
    @GetMapping("/search")
    public Result<List<DrugVO>> search(@RequestParam(value = "kw", required = false) String kw,
                                       @RequestParam(value = "route", required = false) String route,
                                       @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit,
                                       @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        return Result.success(drugService.search(resolveHospital(hospitalId), kw, route, limit == null ? 10 : limit));
    }

    /**
     * 我的药品库：含待确认(status=0)与已停用(status=2)项
     *
     * @param status 0-待确认 1-启用 2-停用，为空表示全部
     */
    @GetMapping("/library")
    public Result<DrugPageVO> library(@RequestParam(value = "kw", required = false) String kw,
                                      @RequestParam(value = "status", required = false) Integer status,
                                      @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                      @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
                                      @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        return Result.success(drugService.library(resolveHospital(hospitalId), kw, status,
                page == null ? 1 : page, size == null ? 20 : size));
    }

    /**
     * 药品库统计：总数 / 启用 / 待确认 / 停用 / 高警示
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        return Result.success(drugService.stats(resolveHospital(hospitalId)));
    }

    /**
     * 新增或编辑药品（个人录入，保存即启用）
     */
    @PostMapping
    public Result<DrugVO> save(@Valid @RequestBody DrugSaveDTO dto,
                               @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        return Result.success(drugService.save(resolveHospital(hospitalId), dto));
    }

    /**
     * 启用 / 停用：1-启用 2-停用
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id,
                                     @RequestParam("status") Integer status,
                                     @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        drugService.updateStatus(resolveHospital(hospitalId), id, status);
        return Result.success();
    }

    /**
     * 批量启用 / 停用，供管理端勾选多条后一次提交
     */
    @PutMapping("/batch-status")
    public Result<Integer> batchStatus(@RequestBody DrugBatchStatusDTO dto,
                                       @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        return Result.success(drugService.batchUpdateStatus(resolveHospital(hospitalId), dto.getIds(), dto.getStatus()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable("id") Long id,
                               @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        drugService.remove(resolveHospital(hospitalId), id);
        return Result.success();
    }

    /**
     * 重复药品归并预览：同通用名 + 同规格（归一后）算重复，返回保留项与待停用项，不落库
     */
    @GetMapping("/merge-preview")
    public Result<DrugMergePreviewVO> mergePreview(@RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        return Result.success(drugService.previewMerge(resolveHospital(hospitalId)));
    }

    /**
     * 执行归并：重复项停用（不删除），使用次数合并到保留项
     */
    @PostMapping("/merge-duplicates")
    public Result<Integer> mergeDuplicates(@RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        return Result.success(drugService.mergeDuplicates(resolveHospital(hospitalId)));
    }

    /**
     * 显式入参 > 当前登录租户 > 默认 1。
     * 小程序端不带租户头，仍落在默认医院；管理端切了医院则自动跟随，不用每个页面都传。
     */
    private Long resolveHospital(Long hospitalId) {
        if (hospitalId != null) {
            return hospitalId;
        }
        Long tenant = TenantContext.getHospitalId();
        return tenant == null ? 1L : tenant;
    }
}
