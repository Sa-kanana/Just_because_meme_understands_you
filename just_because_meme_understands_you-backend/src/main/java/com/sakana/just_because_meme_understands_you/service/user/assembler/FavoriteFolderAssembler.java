package com.sakana.just_because_meme_understands_you.service.user.assembler;

import com.sakana.just_because_meme_understands_you.entity.UserFavoriteFolder;
import com.sakana.just_because_meme_understands_you.service.user.IUserFavoriteFolderService;
import com.sakana.just_because_meme_understands_you.service.user.support.FavoriteFolderCacheSupport.DefaultFolderMeta;
import com.sakana.just_because_meme_understands_you.vo.FavoriteFolderListVO;
import com.sakana.just_because_meme_understands_you.vo.FavoriteFolderVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Favorite folder VO assembly helpers.
 */
@Component
public class FavoriteFolderAssembler {

    private static final String DEFAULT_FOLDER_NAME = "默认收藏夹";

    public FavoriteFolderVO toVO(UserFavoriteFolder folder) {
        FavoriteFolderVO vo = new FavoriteFolderVO();
        vo.setId(folder.getId());
        vo.setName(folder.getName());
        vo.setDescription(folder.getDescription());
        vo.setIsPublic(folder.getIsPublic());
        vo.setSortOrder(folder.getSortOrder());
        vo.setMemeCount(folder.getMemeCount());
        vo.setIsDefault(false);
        vo.setCreateTime(folder.getCreateTime());
        vo.setUpdateTime(folder.getUpdateTime());
        return vo;
    }

    public FavoriteFolderVO buildDefaultFolderVo(int memeCount, DefaultFolderMeta meta) {
        FavoriteFolderVO vo = new FavoriteFolderVO();
        vo.setId(IUserFavoriteFolderService.DEFAULT_FOLDER_ID);
        vo.setName(resolveDefaultFolderName(meta));
        vo.setDescription(meta != null ? meta.description : null);
        vo.setIsPublic(meta != null && meta.isPublic != null ? meta.isPublic : 1);
        vo.setSortOrder(-1);
        vo.setMemeCount(memeCount);
        vo.setIsDefault(true);
        return vo;
    }

    public String resolveDefaultFolderName(DefaultFolderMeta meta) {
        if (meta != null && StringUtils.hasText(meta.name)) {
            return meta.name.trim();
        }
        return DEFAULT_FOLDER_NAME;
    }

    public void filterPrivateAndDefault(FavoriteFolderListVO listVO) {
        if (listVO == null || listVO.getFolders() == null) {
            return;
        }
        List<FavoriteFolderVO> filtered = new ArrayList<>();
        for (FavoriteFolderVO vo : listVO.getFolders()) {
            if (vo.getId() != null && vo.getId() == IUserFavoriteFolderService.DEFAULT_FOLDER_ID) {
                if (vo.getIsPublic() != null && vo.getIsPublic() == 1) {
                    filtered.add(vo);
                }
                continue;
            }
            if (vo.getIsPublic() != null && vo.getIsPublic() == 1) {
                filtered.add(vo);
            }
        }
        listVO.setFolders(filtered);
        listVO.setTotal(filtered.size());
    }
}
