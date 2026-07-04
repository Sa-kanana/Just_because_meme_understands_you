package com.sakana.just_because_meme_understands_you.common.support;

/**
 * 分页参数归一化工具，统一处理 page/size 的默认值与上限。
 *
 * @author sakana
 */
public final class PageParamNormalizer {

    /** 默认页码 */
    public static final int DEFAULT_PAGE = 1;
    /** 默认每页条数 */
    public static final int DEFAULT_SIZE = 10;
    /** 每页最大条数，防止客户端传入过大值拖垮数据库 */
    public static final int MAX_SIZE = 50;

    private PageParamNormalizer() {
    }

    /**
     * 归一化页码：null 或非正数返回 1。
     */
    public static int normalizePage(Integer page) {
        return page == null || page <= 0 ? DEFAULT_PAGE : page;
    }

    /**
     * 归一化每页条数：null 或非正数返回 10，超过 50 截断为 50。
     */
    public static int normalizeSize(Integer size) {
        int value = size == null || size <= 0 ? DEFAULT_SIZE : size;
        return Math.min(value, MAX_SIZE);
    }
}
