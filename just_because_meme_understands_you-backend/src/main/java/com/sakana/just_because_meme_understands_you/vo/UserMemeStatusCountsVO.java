package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * 本人发布列表各状态数量
 */
@Data
public class UserMemeStatusCountsVO {

    /** 全部（不含已彻底删除） */
    private long all;

    /** 已发布 status=1 */
    private long live;

    /** 审核中 status=2 或 6 */
    private long reviewing;

    /** 主动下架 status=3 */
    private long offline;

    /** 风控锁定 status=5 */
    private long locked;
}
