package com.sakana.just_because_meme_understands_you.modules.notification.application.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.NotificationItemVO;
import com.sakana.just_because_meme_understands_you.common.mapping.MapStructConfig;
import com.sakana.just_because_meme_understands_you.modules.notification.domain.entity.UserNotification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 站内通知持久化字段到 API 视图的映射器。 */
@Mapper(config = MapStructConfig.class)
public interface NotificationViewMapper {

    /** 映射通知基础字段；参与人、扩展内容和跳转信息由通知组装器计算。 */
    @Mapping(target = "isRead", source = "isRead", qualifiedByName = "toReadFlag")
    @Mapping(target = "createTime", source = "createTime", qualifiedByName = "formatTime")
    @Mapping(target = "actor", ignore = true)
    @Mapping(target = "jump", ignore = true)
    @Mapping(target = "extra", ignore = true)
    NotificationItemVO toItemVO(UserNotification source);

    /** 将数据库读取状态转换为 API 布尔值。 */
    @Named("toReadFlag")
    default Boolean toReadFlag(Integer isRead) {
        return isRead != null && isRead == 1;
    }

    /** 按既有接口格式输出通知时间。 */
    @Named("formatTime")
    default String formatTime(LocalDateTime createTime) {
        return createTime == null ? "" : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(createTime);
    }
}
