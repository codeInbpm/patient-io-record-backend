package com.patient.io.controller;

import com.patient.io.common.BusinessException;
import com.patient.io.common.Result;
import com.patient.io.common.UserContext;
import com.patient.io.dto.FeedbackHandleDTO;
import com.patient.io.service.FeedbackCenterService;
import com.patient.io.vo.FeedbackItemVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 反馈处理中心（后台）
 *
 * 权限：护士长是唯一处理人；医院管理员、平台超管只读。
 * 临床判定不能由非临床角色代做，这条边界写在接口里，而不是只靠前端隐藏按钮。
 */
@RestController
@RequestMapping("/v1/admin/feedback")
public class AdminFeedbackController {

    @Autowired
    private FeedbackCenterService feedbackCenterService;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(required = false) Long wardId,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false, defaultValue = "PENDING") String status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(feedbackCenterService.list(wardId, category, status, keyword, page, size));
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Long wardId) {
        return Result.success(feedbackCenterService.summary(wardId));
    }

    @GetMapping("/{id}")
    public Result<FeedbackItemVO> detail(@PathVariable Long id) {
        return Result.success(feedbackCenterService.detail(id));
    }

    @PostMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody @Validated FeedbackHandleDTO dto) {
        if (!"HEAD_NURSE".equals(UserContext.getRole())) {
            throw new BusinessException(403, "仅护士长可处置反馈，管理员仅可查看");
        }
        feedbackCenterService.handle(id, dto);
        return Result.success();
    }
}
