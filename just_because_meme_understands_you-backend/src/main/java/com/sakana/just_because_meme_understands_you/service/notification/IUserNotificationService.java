package com.sakana.just_because_meme_understands_you.service.notification;

import com.sakana.just_because_meme_understands_you.dto.NotificationBatchDeleteRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.NotificationBatchDeleteVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationDeleteVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationPageVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationReadAllVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationReadVO;
import com.sakana.just_because_meme_understands_you.vo.NotificationUnreadCountVO;

public interface IUserNotificationService {

    NotificationPageVO pageNotifications(Long userId, Integer page, Integer size, String type, String tab);

    NotificationUnreadCountVO getUnreadCount(Long userId);

    NotificationReadVO markRead(Long userId, Long notificationId);

    NotificationReadAllVO markReadAll(Long userId, String tab);

    NotificationDeleteVO deleteOne(Long userId, Long notificationId);

    NotificationBatchDeleteVO batchDelete(Long userId, NotificationBatchDeleteRequestDTO request);
}
