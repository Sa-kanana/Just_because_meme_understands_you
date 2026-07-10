package com.sakana.just_because_meme_understands_you.service.meme;

import com.sakana.just_because_meme_understands_you.dto.MemeViewReportRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.MemePageViewBatchVO;
import com.sakana.just_because_meme_understands_you.vo.MemePageViewVO;
import jakarta.servlet.http.HttpServletRequest;

public interface IMemeViewService {

    MemePageViewVO reportView(Long userId, String viewSessionId, MemeViewReportRequestDTO request,
                              HttpServletRequest httpServletRequest);

    MemePageViewVO getViewCount(long memeId);

    MemePageViewBatchVO batchViewCounts(String commaSeparatedMemeIds);
}
