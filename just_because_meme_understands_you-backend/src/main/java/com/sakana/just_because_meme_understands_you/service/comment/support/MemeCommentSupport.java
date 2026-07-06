package com.sakana.just_because_meme_understands_you.service.comment.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.dto.MemeCommentCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.MemeComment;
import com.sakana.just_because_meme_understands_you.entity.MemeCommentImage;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.mapper.MemeCommentImageMapper;
import com.sakana.just_because_meme_understands_you.mapper.MemeCommentMapper;
import com.sakana.just_because_meme_understands_you.service.user.IUserService;
import com.sakana.just_because_meme_understands_you.service.oss.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.vo.MemeCommentCreateResponseVO;
import com.sakana.just_because_meme_understands_you.vo.MemeReplyCommentVO;
import com.sakana.just_because_meme_understands_you.vo.MemeRootCommentVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class MemeCommentSupport {

    public static final int NOT_DELETED = 0;

    @Resource
    private MemeCommentMapper memeCommentMapper;

    @Resource
    private MemeCommentImageMapper memeCommentImageMapper;

    @Resource
    private IUserService userService;

    @Resource
    private OssUrlHelper ossUrlHelper;

    public void validateUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
    }

    public void validateCreateRequest(MemeCommentCreateRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "评论内容不能为空");
        }
        if (request.getContent().trim().length() > 2000) {
            throw new BizException(Result.CODE_BAD_REQUEST, "评论内容过长");
        }
    }

    public long parseCommentRefId(String raw, String fieldName) {
        if (!StringUtils.hasText(raw) || "0".equals(raw.trim())) {
            return 0L;
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            if (parsed < 0) {
                throw new BizException(Result.CODE_BAD_REQUEST, fieldName + " 不合法");
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            throw new BizException(Result.CODE_BAD_REQUEST, fieldName + " 格式错误");
        }
    }

    public boolean isValidReplyParent(MemeComment parent, long rootId) {
        if (parent == null || !Objects.equals(parent.getIsDeleted(), NOT_DELETED)) {
            return false;
        }
        if (Objects.equals(parent.getId(), rootId) && Objects.equals(parent.getRootId(), 0L)) {
            return true;
        }
        return Objects.equals(parent.getRootId(), rootId);
    }

    public MemeCommentCreateResponseVO toCreateResponseVO(MemeComment comment) {
        MemeCommentCreateResponseVO vo = new MemeCommentCreateResponseVO();
        vo.setCommentId(comment.getId());
        vo.setContent(comment.getContent());
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }

    public MemeRootCommentVO toRootVO(MemeComment comment, Map<Long, User> userMap, Map<Long, List<String>> imageMap) {
        MemeRootCommentVO vo = new MemeRootCommentVO();
        vo.setId(comment.getId());
        vo.setUserId(comment.getUserId());
        User user = userMap.get(comment.getUserId());
        vo.setUserName(user == null ? "匿名用户" : user.getNickname());
        vo.setUserAvatar(ossUrlHelper.toPublicUrl(user == null ? null : user.getAvatar()));
        vo.setContent(comment.getContent());
        vo.setImages(ossUrlHelper.toPublicUrls(imageMap.getOrDefault(comment.getId(), Collections.emptyList())));
        vo.setReplyCount(defaultInt(comment.getReplyCount()));
        vo.setLikes(defaultInt(comment.getLikes()));
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }

    public MemeReplyCommentVO toReplyVO(MemeComment comment,
                                        Map<Long, User> userMap,
                                        Map<Long, MemeComment> parentMap,
                                        Map<Long, List<String>> imageMap) {
        MemeReplyCommentVO vo = new MemeReplyCommentVO();
        vo.setId(comment.getId());
        vo.setParentId(comment.getParentId());
        vo.setUserId(comment.getUserId());
        User author = userMap.get(comment.getUserId());
        vo.setUserName(author == null ? "匿名用户" : author.getNickname());
        vo.setUserAvatar(ossUrlHelper.toPublicUrl(author == null ? null : author.getAvatar()));
        MemeComment parent = parentMap.get(comment.getParentId());
        if (parent != null) {
            vo.setReplyToUserId(parent.getUserId());
            User replyToUser = userMap.get(parent.getUserId());
            if (replyToUser == null && parent.getUserId() != null) {
                User loaded = userService.getById(parent.getUserId());
                if (loaded != null) {
                    replyToUser = loaded;
                }
            }
            vo.setReplyToUserName(replyToUser == null ? "匿名用户" : replyToUser.getNickname());
        }
        vo.setContent(comment.getContent());
        vo.setImages(ossUrlHelper.toPublicUrls(imageMap.getOrDefault(comment.getId(), Collections.emptyList())));
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }

    public Map<Long, User> loadUserMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userService.listByIds(ids);
        if (users == null || users.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, User> map = new HashMap<>();
        for (User user : users) {
            map.put(user.getId(), user);
        }
        return map;
    }

    public Map<Long, MemeComment> loadCommentMap(List<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = commentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MemeComment> comments = memeCommentMapper.selectList(
                new LambdaQueryWrapper<MemeComment>().in(MemeComment::getId, ids)
        );
        if (comments == null || comments.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, MemeComment> map = new HashMap<>();
        for (MemeComment comment : comments) {
            map.put(comment.getId(), comment);
        }
        return map;
    }

    public Map<Long, List<String>> loadImageMap(List<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = commentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MemeCommentImage> images = memeCommentImageMapper.selectList(
                new LambdaQueryWrapper<MemeCommentImage>()
                        .in(MemeCommentImage::getMemeCommentId, ids)
                        .orderByAsc(MemeCommentImage::getSortOrder)
                        .orderByAsc(MemeCommentImage::getId)
        );
        if (images == null || images.isEmpty()) {
            return Collections.emptyMap();
        }
        return images.stream().collect(Collectors.groupingBy(
                MemeCommentImage::getMemeCommentId,
                Collectors.mapping(MemeCommentImage::getUrl, Collectors.toList())
        ));
    }

    public void saveImages(Long commentId, List<String> images) {
        if (commentId == null || images == null || images.isEmpty()) {
            return;
        }
        int sort = 0;
        for (String url : images) {
            if (!StringUtils.hasText(url)) {
                continue;
            }
            String key = ossUrlHelper.normalizeForStorage(url);
            ossUrlHelper.assertOwnedImageKey(key, "comments/");
            MemeCommentImage image = new MemeCommentImage();
            image.setMemeCommentId(commentId);
            image.setUrl(key);
            image.setSortOrder(sort++);
            memeCommentImageMapper.insert(image);
        }
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
