package com.sakana.just_because_meme_understands_you.modules.meme.contract;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 梗图模块对其他业务模块开放的应用契约，不暴露实体或持久层类型。 */
public interface MemeCatalogPort {

    /** 按关键词搜索已发布梗图，结果数量由调用方限制。 */
    List<MemeCard> searchPublishedByKeyword(String keyword, int limit);

    /** 查询单个梗图；不存在时返回空。 */
    MemeCard findById(long memeId);

    /** 查询单个公开梗图；不存在或当前不可见时返回空。 */
    MemeCard findPublishedById(long memeId);

    /** 按 ID 批量查询梗图，不应用公开状态过滤。 */
    List<MemeCard> findByIds(Collection<Long> memeIds);

    /** 批量查询已发布梗图。 */
    List<MemeCard> findPublishedByIds(Collection<Long> memeIds);

    /** 按梗 ID 游标分页读取已发布梗 ID；仅返回 ID，单页数量最多为 50。 */
    List<Long> pagePublishedIdsAfterId(long afterMemeId, int limit);

    /** 查询用户主页梗图；关键字按作品名称模糊匹配，并由梗图模块执行状态可见规则。 */
    MemePage pageByAuthor(long userId, boolean owner, Integer status, int page, int size, MemeSort sort, String keyword);

    /** 汇总用户主页的梗图状态数量。 */
    MemeStatusCounts countByAuthor(long userId);

    /** 按 meme.user_id 与 status=1 统计作者当前已发布梗图数量。 */
    long countPublishedByAuthor(long userId);

    /** 查询用户最近发布的梗图。 */
    List<MemeCard> findLatestPublishedByAuthor(long userId, int limit);

    /** 批量查询梗图标签，并按梗图 ID 分组。 */
    Map<Long, List<MemeTagSummary>> findTagsByMemeIds(Collection<Long> memeIds);

    /** 查询梗图的全部关联资源，包含下架和归档资源供管理员核查。 */
    List<MemeResourceSummary> findResourcesByMemeId(long memeId);

    /** 批量查询梗图最近一次内容修改审核摘要。 */
    Map<Long, MemeEditRevisionSummary> findLatestEditRevisionsByMemeIds(Collection<Long> memeIds);

    /** 批量查询标签信息。 */
    List<MemeTagSummary> findTagsByIds(Collection<Long> tagIds);

    /** 查询梗图模块维护的热门标签摘要。 */
    List<MemeTagSummary> findHotTags(int limit);

    /** 查询标签；不存在时返回空。 */
    MemeTagSummary findTagById(long tagId);

    /** 首页实时热梗展示参数。 */
    CrawlSettings crawlSettings();

    /** 判断梗图是否处于可公开互动状态。 */
    boolean isPublished(long memeId);

    /** 判断 OSS 图片是否仍被梗图封面、资源或待审核修改引用。 */
    boolean isImageAssetReferenced(String objectKey);

    /** 返回生命周期状态的中文描述。 */
    String statusDescription(Integer status);

    /** 调整梗图点赞数，delta 仅支持 +1 或 -1。 */
    void adjustLikeCount(long memeId, int delta);

    /** 在当前事务中原子递增累计评论数；找不到梗时返回 false。 */
    boolean incrementCommentCount(long memeId);

    /** 梗图模块跨模块返回的安全摘要。 */
    record MemeCard(
            Long id,
            Long authorId,
            String name,
            String introduction,
            String image,
            Integer pageViews,
            Integer likes,
            Integer comments,
            Integer status,
            LocalDateTime releaseTime,
            String offlineReason,
            Integer appealRejectCount) {
    }

    /** 梗图模块跨模块返回的标签摘要。 */
    record MemeTagSummary(Long id, String name, Integer relatedQuantity, String category, Boolean isPrimary) {
        public MemeTagSummary(Long id, String name, Integer relatedQuantity) {
            this(id, name, relatedQuantity, null, false);
        }
    }

    /** 梗图关联资源摘要，不向调用方暴露梗图模块的持久化实体。 */
    record MemeResourceSummary(Long id, Integer resourceType, String title, String resourceUrl,
                               Integer sortOrder, Integer status) {
    }

    /** 作者在个人主页看到的最近一次修改审核摘要。 */
    record MemeEditRevisionSummary(String status, LocalDateTime createdAt, String reviewNote) {
    }

    /** 用户主页的分页查询结果。 */
    record MemePage(List<MemeCard> records, long total) {
    }

    /** 用户主页发布列表可用的安全排序选项。 */
    enum MemeSort {
        LATEST,
        VIEWS,
        LIKES,
        COMMENTS;

        /** 将请求参数转换为白名单排序值；空值或未知值默认按最新发布排序。 */
        public static MemeSort fromValue(String value) {
            if (value == null || value.isBlank()) {
                return LATEST;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return LATEST;
            }
        }
    }

    /** 用户主页的梗图状态计数。 */
    record MemeStatusCounts(long live, long reviewing, long offline, long locked) {
        public long all() {
            return live + reviewing + offline + locked;
        }
    }

    /** 首页实时热梗展示所需配置。 */
    record CrawlSettings(
            boolean enabled,
            boolean scheduleEnabled,
            int limit,
            long publisherUserId,
            int liveLimit,
            int liveHours) {
    }
}
