package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.sakana.just_because_meme_understands_you.api.user.dto.MemeViewReportRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemePageViewBatchVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemePageViewVO;
import jakarta.servlet.http.HttpServletRequest;

public interface IMemeViewService {

    MemePageViewVO reportView(Long userId, String viewSessionId, MemeViewReportRequestDTO request,
                              HttpServletRequest httpServletRequest);

    MemePageViewVO getViewCount(long memeId);

    MemePageViewBatchVO batchViewCounts(String commaSeparatedMemeIds);
}
