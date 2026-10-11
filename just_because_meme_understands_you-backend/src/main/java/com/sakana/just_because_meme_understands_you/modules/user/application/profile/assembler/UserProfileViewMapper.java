package com.sakana.just_because_meme_understands_you.modules.user.application.profile.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.AccountProfileVO;
import com.sakana.just_because_meme_understands_you.common.mapping.MapStructConfig;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDate;

/** 用户资料实体到 API 视图的纯字段映射。 */
@Mapper(config = MapStructConfig.class)
public interface UserProfileViewMapper {

    /** 映射可复用的账号资料字段，头像 URL 由 OSS 应用服务补全。 */
    @Mapping(target = "userId", source = "id", qualifiedByName = "toUserId")
    @Mapping(target = "nickname", source = "nickname", qualifiedByName = "trimToEmpty")
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "signature", source = "signature", qualifiedByName = "trimToEmpty")
    @Mapping(target = "gender", source = "gender", qualifiedByName = "genderOrUnknown")
    @Mapping(target = "birthday", source = "birthday", qualifiedByName = "dateToText")
    AccountProfileVO toAccountProfileVO(User source);

    /** 将用户编号保持为原有的字符串表示。 */
    @Named("toUserId")
    default String toUserId(Long id) {
        return String.valueOf(id);
    }

    /** 将空文本规范化为空字符串，并去掉首尾空白。 */
    @Named("trimToEmpty")
    default String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** 将未设置的性别映射为未知。 */
    @Named("genderOrUnknown")
    default Integer genderOrUnknown(Integer gender) {
        return gender == null ? 0 : gender;
    }

    /** 将生日转换为 API 使用的日期文本。 */
    @Named("dateToText")
    default String dateToText(LocalDate birthday) {
        return birthday == null ? "" : birthday.toString();
    }
}
