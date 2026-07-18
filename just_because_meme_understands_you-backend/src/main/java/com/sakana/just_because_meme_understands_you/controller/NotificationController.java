package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.dto.NotificationBatchDeleteRequestDTO;
import com.sakana.just_because_meme_understands_you.service.notification.IUserNotificationService;
import com.sakana.just_because_meme_understands_you.vo.NotificationBatchDeleteVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationDeleteVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationPageVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationReadAllVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationReadVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationUnreadCountVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内消息：分页列表、未读数、已读、删除。
 */
@RestController
public class NotificationController {

    @Resource
    private IUserNotificationService userNotificationService;

    /**
     * 消息分页列表
     * GET /notifications?page=&size=&type=&tab=
     */
    @GetMapping("/notifications")
    public Result<NotificationPageVO> pageNotifications(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "tab", required = false) String tab,
            HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(userNotificationService.pageNotifications(userId, page, size, type, tab));
    }

    /**
     * 未读数（顶栏）
     * GET /notifications/unread-count
     */
    @GetMapping("/notifications/unread-count")
    public Result<NotificationUnreadCountVO> unreadCount(HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(userNotificationService.getUnreadCount(userId));
    }

    /**
     * 单条已读
     * POST /notifications/{id}/read
     */
    @PostMapping("/notifications/{id:\\d+}/read")
    public Result<NotificationReadVO> markRead(@PathVariable("id") String id,
                                               HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        Long notificationId = AuthContext.parseLongId(id, "id");
        return Result.success(userNotificationService.markRead(userId, notificationId));
    }

    /**
     * 全部已读
     * POST /notifications/read-all?tab=all|interact|system
     */
    @PostMapping("/notifications/read-all")
    public Result<NotificationReadAllVO> markReadAll(
            @RequestParam(value = "tab", required = false, defaultValue = "all") String tab,
            HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(userNotificationService.markReadAll(userId, tab));
    }

    /**
     * 删除单条消息
     * DELETE /notifications/{id}
     */
    @DeleteMapping("/notifications/{id:\\d+}")
    public Result<NotificationDeleteVO> deleteOne(@PathVariable("id") String id,
                                                  HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        Long notificationId = AuthContext.parseLongId(id, "id");
        return Result.success(userNotificationService.deleteOne(userId, notificationId));
    }

    /**
     * 批量删除 / 清空
     * DELETE /notifications
     */
    @DeleteMapping("/notifications")
    public Result<NotificationBatchDeleteVO> batchDelete(@RequestBody(required = false) NotificationBatchDeleteRequestDTO body,
                                                         HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(userNotificationService.batchDelete(userId, body));
    }
}
