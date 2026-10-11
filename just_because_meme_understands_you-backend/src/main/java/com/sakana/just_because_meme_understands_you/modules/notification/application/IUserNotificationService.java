package com.sakana.just_because_meme_understands_you.modules.notification.application;

import com.sakana.just_because_meme_understands_you.api.user.dto.NotificationBatchDeleteRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.NotificationBatchDeleteVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.NotificationDeleteVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.NotificationPageVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.NotificationReadAllVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.NotificationReadVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.NotificationUnreadCountVO;

public interface IUserNotificationService {

    NotificationPageVO pageNotifications(Long userId, Integer page, Integer size, String type, String tab);

    NotificationUnreadCountVO getUnreadCount(Long userId);

    NotificationReadVO markRead(Long userId, Long notificationId);

    NotificationReadAllVO markReadAll(Long userId, String tab);

    NotificationDeleteVO deleteOne(Long userId, Long notificationId);

    NotificationBatchDeleteVO batchDelete(Long userId, NotificationBatchDeleteRequestDTO request);
}
