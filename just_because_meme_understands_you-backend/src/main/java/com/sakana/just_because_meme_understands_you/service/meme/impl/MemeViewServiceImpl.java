package com.sakana.just_because_meme_understands_you.service.meme.impl;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.dto.MemeViewReportRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.service.meme.IMemeService;
import com.sakana.just_because_meme_understands_you.service.meme.IMemeViewService;
import com.sakana.just_because_meme_understands_you.service.meme.MemeBloomFilterService;
import com.sakana.just_because_meme_understands_you.service.meme.MemeViewDedupService;
import com.sakana.just_because_meme_understands_you.service.meme.MemeViewRateLimiter;
import com.sakana.just_because_meme_understands_you.service.meme.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.util.ClientIpResolver;
import com.sakana.just_because_meme_understands_you.vo.MemePageViewBatchVO;
import com.sakana.just_because_meme_understands_you.vo.MemePageViewVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MemeViewServiceImpl implements IMemeViewService {

    private static final int MAX_BATCH_SIZE = 50;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> ALLOWED_SOURCES = Set.of("detail", "list", "search", "share");

    @Resource
    private MemeMapper memeMapper;

    @Resource
    private IMemeService memeService;

    @Resource
    private MemeBloomFilterService memeBloomFilterService;

    @Resource
    private MemeViewDedupService memeViewDedupService;

    @Resource
    private MemeViewRateLimiter memeViewRateLimiter;

    @Resource
    private ClientIpResolver clientIpResolver;

    @Override
    public MemePageViewVO reportView(Long userId, String viewSessionId, MemeViewReportRequestDTO request,
                                     HttpServletRequest httpServletRequest) {
        long memeId = parseMemeId(request);
        normalizeSource(request != null ? request.getSource() : null);

        Meme meme = loadPublicMeme(memeId, userId);
        int currentCount = resolvePageViews(meme);

        String viewerKey = resolveViewerKey(userId, viewSessionId, clientIpResolver.resolve(httpServletRequest));
        memeViewRateLimiter.check(viewerKey);

        MemePageViewVO vo = new MemePageViewVO();
        vo.setMemeId(memeId);
        vo.setCounted(false);
        vo.setPageViews(currentCount);

        if (!memeViewDedupService.tryAcquire(viewerKey, memeId)) {
            return vo;
        }

        int updated = memeMapper.incrementPageViews(memeId);
        if (updated <= 0) {
            vo.setPageViews(resolvePageViews(reloadMeme(memeId)));
            return vo;
        }

        vo.setCounted(true);
        vo.setPageViews(currentCount + 1);
        return vo;
    }

    @Override
    public MemePageViewVO getViewCount(long memeId) {
        if (memeId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不能为空");
        }
        Meme meme = memeService.getById(memeId);
        if (meme == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在");
        }
        MemePageViewVO vo = new MemePageViewVO();
        vo.setMemeId(memeId);
        vo.setPageViews(resolvePageViews(meme));
        return vo;
    }

    @Override
    public MemePageViewBatchVO batchViewCounts(String commaSeparatedMemeIds) {
        List<Long> memeIds = normalizeMemeIds(parseMemeIdsParam(commaSeparatedMemeIds));
        MemePageViewBatchVO result = new MemePageViewBatchVO();
        if (memeIds.isEmpty()) {
            return result;
        }

        List<Meme> memes = memeService.listByIds(memeIds.stream().map(Long::intValue).toList());
        Map<Long, Meme> memeMap = new HashMap<>();
        for (Meme meme : memes) {
            if (meme != null && meme.getId() != null) {
                memeMap.put(meme.getId().longValue(), meme);
            }
        }

        List<MemePageViewVO> items = new ArrayList<>(memeIds.size());
        for (Long memeId : memeIds) {
            Meme meme = memeMap.get(memeId);
            MemePageViewVO item = new MemePageViewVO();
            item.setMemeId(memeId);
            item.setPageViews(meme != null ? resolvePageViews(meme) : 0);
            items.add(item);
        }
        result.setItems(items);
        return result;
    }

    private Meme loadPublicMeme(long memeId, Long userId) {
        if (!memeBloomFilterService.mightContain((int) memeId)) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在或不可统计浏览");
        }
        Meme meme = memeService.getById(memeId);
        if (meme == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在或不可统计浏览");
        }
        MemeVisibilitySupport.ViewAccess access = MemeVisibilitySupport.resolveViewAccess(meme, userId);
        if (access != MemeVisibilitySupport.ViewAccess.PUBLIC) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在或不可统计浏览");
        }
        return meme;
    }

    private long parseMemeId(MemeViewReportRequestDTO request) {
        if (request == null || request.getMemeId() == null || request.getMemeId() <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不能为空");
        }
        return request.getMemeId();
    }

    private String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return "detail";
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SOURCES.contains(normalized)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "source 不合法");
        }
        return normalized;
    }

    private String resolveViewerKey(Long userId, String viewSessionId, String clientIp) {
        if (userId != null && userId > 0) {
            return "u:" + userId;
        }
        if (StringUtils.hasText(viewSessionId)) {
            String session = viewSessionId.trim().toLowerCase(Locale.ROOT);
            if (isValidViewSessionId(session)) {
                return "s:" + session;
            }
        }
        return "ip:" + (StringUtils.hasText(clientIp) ? clientIp.trim() : "unknown");
    }

    private boolean isValidViewSessionId(String sessionId) {
        return sessionId.length() <= 64 && UUID_PATTERN.matcher(sessionId).matches();
    }

    static List<Long> parseMemeIdsParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split(",");
        List<Long> ids = new ArrayList<>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                long id = Long.parseLong(trimmed);
                if (id > 0) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // skip invalid token
            }
        }
        return ids;
    }

    private List<Long> normalizeMemeIds(List<Long> memeIds) {
        if (memeIds == null || memeIds.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new HashSet<>();
        List<Long> ordered = new ArrayList<>();
        for (Long memeId : memeIds) {
            if (memeId == null || memeId <= 0 || !unique.add(memeId)) {
                continue;
            }
            ordered.add(memeId);
            if (ordered.size() >= MAX_BATCH_SIZE) {
                break;
            }
        }
        return ordered;
    }

    private Meme reloadMeme(long memeId) {
        return memeMapper.selectById(memeId);
    }

    private int resolvePageViews(Meme meme) {
        if (meme == null || meme.getPageViews() == null) {
            return 0;
        }
        return Math.max(0, meme.getPageViews());
    }
}
