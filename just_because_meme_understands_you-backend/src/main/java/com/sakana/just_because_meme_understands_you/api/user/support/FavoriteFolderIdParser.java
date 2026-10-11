package com.sakana.just_because_meme_understands_you.api.user.support;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import org.springframework.util.StringUtils;

/** 收藏夹路径参数解析；0 表示虚拟默认收藏夹。 */
public final class FavoriteFolderIdParser {

    private FavoriteFolderIdParser() {
    }

    public static long parseFolderId(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不能为空");
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            if (parsed < 0) {
                throw new BizException(Result.CODE_BAD_REQUEST, "folderId 不合法");
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            throw new BizException(Result.CODE_BAD_REQUEST, "folderId 格式错误");
        }
    }
}
