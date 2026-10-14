package com.sakana.just_because_meme_understands_you.modules.meme.application.support;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 中文注释：验证可选主标签和普通标签，并生成持久化所需的标签列表。 */
public final class MemeTagSelectionPolicy {

    /** 中文注释：每条新梗最多允许的普通标签数量。 */
    public static final int MAX_SECONDARY_TAGS = 7;

    private MemeTagSelectionPolicy() {
    }

    /** 中文注释：验证可选标签数量、重复选择以及所有已选标签的存在性。 */
    public static Selection normalizeAndValidate(Integer primaryTagId,
                                                 List<Integer> secondaryTagIds,
                                                 Collection<Integer> existingTagIds) {
        if (primaryTagId != null && primaryTagId <= 0) {
            throw badRequest("主标签 ID 不合法");
        }
        List<Integer> selectedSecondaryTagIds = secondaryTagIds == null ? List.of() : secondaryTagIds;
        if (selectedSecondaryTagIds.size() > MAX_SECONDARY_TAGS) {
            throw badRequest("普通标签最多选择 " + MAX_SECONDARY_TAGS + " 个");
        }

        Set<Integer> uniqueSecondaryIds = new HashSet<>();
        for (Integer tagId : selectedSecondaryTagIds) {
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
        List<Integer> allTagIds = new ArrayList<>(selectedSecondaryTagIds.size() + (primaryTagId == null ? 0 : 1));
        if (primaryTagId != null) {
            allTagIds.add(primaryTagId);
        }
        allTagIds.addAll(selectedSecondaryTagIds);
        if (!existing.containsAll(allTagIds)) {
            throw badRequest("所选标签不存在或已失效");
        }
        return new Selection(primaryTagId, List.copyOf(selectedSecondaryTagIds), List.copyOf(allTagIds));
    }

    private static BizException badRequest(String message) {
        return new BizException(Result.CODE_BAD_REQUEST, message);
    }

    /** 中文注释：通过规则校验后的可选主标签、普通标签和完整标签 ID 集合。 */
    public record Selection(Integer primaryTagId, List<Integer> secondaryTagIds, List<Integer> allTagIds) {
    }
}
