package com.sakana.just_because_meme_understands_you.service.admin;

import com.sakana.just_because_meme_understands_you.dto.admin.AdminKnowledgeSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminKnowledgeVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IAdminKnowledgeService {

    PageVO<AdminKnowledgeVO> page(Integer page, Integer size, String keyword, String category);

    AdminKnowledgeVO detail(Long id);

    AdminKnowledgeVO create(Long adminUserId, AdminKnowledgeSaveRequestDTO request);

    AdminKnowledgeVO createFromFile(Long adminUserId,
                                    MultipartFile file,
                                    String title,
                                    String category,
                                    List<String> tags);

    AdminKnowledgeVO update(Long id, AdminKnowledgeSaveRequestDTO request);

    void delete(Long id);

    AdminKnowledgeVO reindex(Long id);
}
