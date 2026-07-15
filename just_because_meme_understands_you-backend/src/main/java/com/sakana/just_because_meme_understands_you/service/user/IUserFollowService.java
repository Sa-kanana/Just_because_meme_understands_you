package com.sakana.just_because_meme_understands_you.service.user;

import com.sakana.just_because_meme_understands_you.vo.FollowBatchStatusVO;
import com.sakana.just_because_meme_understands_you.vo.FollowListPageVO;
import com.sakana.just_because_meme_understands_you.vo.FollowedVO;

public interface IUserFollowService {

    FollowedVO follow(Long currentUserId, Long targetUserId);

    FollowedVO unfollow(Long currentUserId, Long targetUserId);

    FollowedVO getFollowStatus(Long currentUserId, Long targetUserId);

    FollowBatchStatusVO batchFollowStatus(Long currentUserId, String userIdsCsv);

    FollowListPageVO pageFollowing(Long targetUserId, Long currentUserId, Integer page, Integer size);

    /** 粉丝列表：谁关注了 targetUserId */
    FollowListPageVO pageFollowers(Long targetUserId, Long currentUserId, Integer page, Integer size);
}
