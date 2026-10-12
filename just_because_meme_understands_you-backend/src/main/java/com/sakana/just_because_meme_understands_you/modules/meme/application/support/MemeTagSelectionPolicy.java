package com.sakana.just_because_meme_understands_you.modules.meme.application.support;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 中文注释：统一验证一个主标签和三至七个普通标签，并生成持久化所需的标签列表。 */
public final class MemeTagSelectionPolicy {

    /** 中文注释：每条新梗至少需要的普通标签数量。 */
    public static final int MIN_SECONDARY_TAGS = 3;
    /** 中文注释：每条新梗最多允许的普通标签数量。 */
    public static final int MAX_SECONDARY_TAGS = 7;

    private MemeTagSelectionPolicy() {
    }

    /** 中文注释：验证主标签、普通标签数量、重复选择以及所有标签的存在性。 */
    public static Selection normalizeAndValidate(Integer primaryTagId,
                                                 List<Integer> secondaryTagIds,
                                                 Collection<Integer> existingTagIds) {
        if (primaryTagId == null || primaryTagId <= 0) {
            throw badRequest("请选择一个主分类标签");
        }
        if (secondaryTagIds == null
                || secondaryTagIds.size() < MIN_SECONDARY_TAGS
                || secondaryTagIds.size() > MAX_SECONDARY_TAGS) {
            throw badRequest("普通标签需选择 " + MIN_SECONDARY_TAGS + "～" + MAX_SECONDARY_TAGS + " 个");
        }

        Set<Integer> uniqueSecondaryIds = new HashSet<>();
        for (Integer tagId : secondaryTagIds) {
            if (tagId == null || tagId <= 0) {
                throw badRequest("标签 ID 不合法");
            }
            if (tagId.equals(primaryTagId)) {
                throw badRequest("主分类标签不能重复作为普通标签选择");
            }
            if (!uniqueSecondaryIds.add(tagId)) {
                throw badRequest("普通标签不能重复选择");
            }
        }

        Set<Integer> existing = existingTagIds == null ? Set.of() : new HashSet<>(existingTagIds);
        List<Integer> allTagIds = new ArrayList<>(secondaryTagIds.size() + 1);
        allTagIds.add(primaryTagId);
        allTagIds.addAll(secondaryTagIds);
        if (!existing.containsAll(allTagIds)) {
            throw badRequest("所选标签不存在或已失效");
        }
        return new Selection(primaryTagId, List.copyOf(secondaryTagIds), List.copyOf(allTagIds));
    }

    private static BizException badRequest(String message) {
        return new BizException(Result.CODE_BAD_REQUEST, message);
    }

    /** 中文注释：通过规则校验后的主标签、普通标签和完整标签 ID 集合。 */
    public record Selection(Integer primaryTagId, List<Integer> secondaryTagIds, List<Integer> allTagIds) {
    }
}
