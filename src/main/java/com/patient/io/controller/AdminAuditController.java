package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 审计日志查询。只读接口，应用层不提供删除/修改。
 */
@RestController
@RequestMapping("/v1/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final RbacService rbacService;

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "action", required = false) String action,
                                            @RequestParam(value = "result", required = false) String result,
                                            @RequestParam(value = "from", required = false) String from,
                                            @RequestParam(value = "to", required = false) String to,
                                            @RequestParam(value = "page", defaultValue = "1") int page,
                                            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(rbacService.listAuditLogs(keyword, action, result, from, to, page, size));
    }
}
