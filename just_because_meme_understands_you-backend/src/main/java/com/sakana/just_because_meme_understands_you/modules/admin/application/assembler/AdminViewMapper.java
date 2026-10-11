package com.sakana.just_because_meme_understands_you.modules.admin.application.assembler;

import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminSensitiveWordVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminHomeImageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminKnowledgeVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeActionVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeDetailVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeListItemVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminTagVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminUserListItemVO;
import com.sakana.just_because_meme_understands_you.common.mapping.MapStructConfig;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AdminKnowledge;
import com.sakana.just_because_meme_understands_you.modules.home.domain.entity.HomeImage;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.entity.SensitiveWord;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 管理端查询视图的字段映射器。
 */
@Mapper(config = MapStructConfig.class)
public interface AdminViewMapper {

    /**
     * 映射轮播图基础字段，公开图片地址由应用层转换。
     *
     * @param source 轮播图实体
     * @return 管理端轮播图视图
     */
    @Mapping(target = "imgUrl", ignore = true)
    AdminHomeImageVO toAdminHomeImageVO(HomeImage source);

    /**
     * 映射知识文档基础字段，标签解析由应用层完成。
     *
     * @param source 知识文档实体
     * @return 管理端知识文档视图
     */
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "duplicate", ignore = true)
    AdminKnowledgeVO toAdminKnowledgeVO(AdminKnowledge source);

    /**
     * 将敏感词实体转换为管理端视图。
     *
     * @param source 敏感词实体
     * @return 管理端敏感词视图
     */
    AdminSensitiveWordVO toSensitiveWordVO(SensitiveWord source);

    /**
     * 将梗标签实体转换为管理端视图。
     *
     * @param source 梗标签实体
     * @return 管理端标签视图
     */
    AdminTagVO toTagVO(MemeTag source);

    /**
     * 映射用户基础字段，头像公开地址由应用层转换。
     *
     * @param source 用户实体
     * @return 管理端用户列表项
     */
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "role", ignore = true)
    AdminUserListItemVO toAdminUserListItemVO(User source);

    /**
     * 映射梗列表项基础字段，派生展示信息由应用层补充。
     *
     * @param source 梗实体
     * @return 管理端梗列表项
     */
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "statusDesc", ignore = true)
    @Mapping(target = "authorNickname", ignore = true)
    AdminMemeListItemVO toAdminMemeListItemVO(Meme source);

    /**
     * 映射梗详情基础字段，派生展示信息由应用层补充。
     *
     * @param source 梗实体
     * @return 管理端梗详情
     */
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "statusDesc", ignore = true)
    @Mapping(target = "authorNickname", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "resources", ignore = true)
    AdminMemeDetailVO toAdminMemeDetailVO(Meme source);

    /**
     * 映射管理操作结果中的梗标识和状态。
     *
     * @param source 梗实体
     * @return 管理端操作结果
     */
    @Mapping(target = "memeId", source = "id")
    @Mapping(target = "statusDesc", ignore = true)
    AdminMemeActionVO toAdminMemeActionVO(Meme source);
}
