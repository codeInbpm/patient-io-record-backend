package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.common.TenantContext;
import com.patient.io.entity.SysDictItem;
import com.patient.io.entity.SysDictType;
import com.patient.io.service.DictService;
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

import java.util.List;

/**
 * 数据字典维护（后台管理端）。
 * 读接口放开，写接口走登录拦截器。
 */
@RestController
@RequestMapping("/v1/admin/dict")
@RequiredArgsConstructor
public class AdminDictController {

    private final DictService dictService;

    @GetMapping("/types")
    public Result<List<SysDictType>> listTypes(@RequestParam(value = "keyword", required = false) String keyword,
                                              @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        return Result.success(dictService.listTypes(resolveHospital(hospitalId), keyword));
    }

    @PostMapping("/type")
    public Result<SysDictType> saveType(@RequestBody SysDictType type,
                                        @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        return Result.success(dictService.saveType(resolveHospital(hospitalId), type));
    }

    @DeleteMapping("/type/{id}")
    public Result<Void> deleteType(@PathVariable("id") Long id,
                                   @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        dictService.deleteType(resolveHospital(hospitalId), id);
        return Result.success();
    }

    @GetMapping("/items")
    public Result<List<SysDictItem>> listItems(@RequestParam("code") String code,
                                              @RequestParam(value = "includeDisabled", required = false, defaultValue = "true") Boolean includeDisabled,
                                              @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        return Result.success(dictService.listItems(resolveHospital(hospitalId), code, includeDisabled));
    }

    @PostMapping("/item")
    public Result<Void> saveItem(@RequestBody SysDictItem item,
                                 @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        dictService.saveItem(resolveHospital(hospitalId), item);
        return Result.success();
    }

    @DeleteMapping("/item/{id}")
    public Result<Void> deleteItem(@PathVariable("id") Long id,
                                   @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        dictService.deleteItem(resolveHospital(hospitalId), id);
        return Result.success();
    }

    @PutMapping("/item/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id,
                                     @RequestParam("status") Integer status,
                                     @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        dictService.updateItemStatus(resolveHospital(hospitalId), id, status);
        return Result.success();
    }

    /**
     * 一键补齐内置字典：只补缺失项，不覆盖已改过的配置
     */
    @PostMapping("/init-defaults")
    public Result<Integer> initDefaults(@RequestParam(value = "hospitalId", required = false) Long hospitalId) {
        return Result.success(dictService.initDefaults(resolveHospital(hospitalId)));
    }

    private Long resolveHospital(Long hospitalId) {
        if (hospitalId != null) {
            return hospitalId;
        }
        return TenantContext.getHospitalId();
    }
}
