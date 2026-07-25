package com.sakana.just_because_meme_understands_you.service.feedback.impl;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.constant.AuthConstants;
import com.sakana.just_because_meme_understands_you.dto.FeedbackSubmitRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.entity.UserAuth;
import com.sakana.just_because_meme_understands_you.mapper.UserAuthMapper;
import com.sakana.just_because_meme_understands_you.service.feedback.IFeedbackService;
import com.sakana.just_because_meme_understands_you.service.user.IUserService;
import com.sakana.just_because_meme_understands_you.util.EmailValidatorUtil;
import com.sakana.just_because_meme_understands_you.vo.FeedbackCategoryOptionVO;
import com.sakana.just_because_meme_understands_you.vo.FeedbackMetaVO;
import com.sakana.just_because_meme_understands_you.vo.FeedbackSubmitVO;
import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 用户反馈：校验后邮件通知运营邮箱。
 */
@Slf4j
@Service
public class FeedbackServiceImpl implements IFeedbackService {

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "bug", "suggestion", "report", "other"
    );

    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "bug", "问题反馈",
            "suggestion", "功能建议",
            "report", "内容举报",
            "other", "其他"
    );

    private static final Map<String, String> CATEGORY_ACCENTS = Map.of(
            "bug", "#E11D48",
            "suggestion", "#318AEF",
            "report", "#D97706",
            "other", "#64748B"
    );

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${app.feedback.to-email:827278063@qq.com}")
    private String feedbackToEmail;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Resource
    private JavaMailSender mailSender;

    @Resource
    private IUserService userService;

    @Resource
    private UserAuthMapper userAuthMapper;

    @Override
    public FeedbackMetaVO getMeta() {
        FeedbackMetaVO vo = new FeedbackMetaVO();
        vo.setCategories(List.of(
                new FeedbackCategoryOptionVO("bug", CATEGORY_LABELS.get("bug")),
                new FeedbackCategoryOptionVO("suggestion", CATEGORY_LABELS.get("suggestion")),
                new FeedbackCategoryOptionVO("report", CATEGORY_LABELS.get("report")),
                new FeedbackCategoryOptionVO("other", CATEGORY_LABELS.get("other"))
        ));
        vo.setNotice("提交后我们会尽快查阅；紧急问题请尽量留下可联系的邮箱。");
        return vo;
    }

    @Override
    public FeedbackSubmitVO submit(Long userId, FeedbackSubmitRequestDTO request, String clientIp) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "请先登录后再提交反馈");
        }
        if (request == null) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请求参数不能为空");
        }
        if (!StringUtils.hasText(feedbackToEmail)) {
            throw new BizException(Result.CODE_ERROR, "反馈通道暂不可用，请稍后重试");
        }
        if (!StringUtils.hasText(mailFrom)) {
            throw new BizException(Result.CODE_ERROR, "邮件服务未配置");
        }

        String category = normalizeCategory(request.getCategory());
        String content = request.getContent() != null ? request.getContent().trim() : "";
        if (content.length() < 5 || content.length() > 2000) {
            throw new BizException(Result.CODE_BAD_REQUEST, "反馈内容长度需在 5～2000 字");
        }

        User user = userService.getById(userId);
        if (user == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }

        String accountEmail = resolveAccountEmail(userId);
        String contactEmail = resolveContactEmail(request.getContactEmail(), accountEmail);
        String pageUrl = request.getPageUrl() != null ? request.getPageUrl().trim() : "";
        if (pageUrl.length() > 500) {
            throw new BizException(Result.CODE_BAD_REQUEST, "页面地址过长");
        }

        String categoryLabel = CATEGORY_LABELS.getOrDefault(category, category);
        String subject = "【只因梗懂你】" + categoryLabel + " · 用户反馈";
        String submittedAt = LocalDateTime.now().format(TIME_FMT);
        String plainBody = buildPlainMailBody(
                userId, user.getNickname(), accountEmail, contactEmail,
                categoryLabel, category, content, pageUrl, clientIp, submittedAt
        );
        String htmlBody = buildHtmlMailBody(
                userId, user.getNickname(), accountEmail, contactEmail,
                category, categoryLabel, content, pageUrl, clientIp, submittedAt
        );

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom);
            helper.setTo(feedbackToEmail.trim());
            helper.setSubject(subject);
            helper.setText(plainBody, htmlBody);
            if (StringUtils.hasText(contactEmail)) {
                helper.setReplyTo(contactEmail);
            }
            mailSender.send(mimeMessage);
        } catch (Exception mailEx) {
            log.warn("反馈邮件发送失败, userId={}", userId, mailEx);
            throw new BizException(Result.CODE_ERROR, "反馈提交失败，请稍后重试");
        }

        FeedbackSubmitVO vo = new FeedbackSubmitVO();
        vo.setAccepted(Boolean.TRUE);
        vo.setMessage("反馈已提交，感谢你的帮助");
        return vo;
    }

    private String normalizeCategory(String raw) {
        String category = raw != null ? raw.trim().toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_CATEGORIES.contains(category)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "反馈类型不合法");
        }
        return category;
    }

    private String resolveAccountEmail(Long userId) {
        UserAuth auth = userAuthMapper.selectByUserIdAndType(userId, AuthConstants.LOGIN_TYPE_EMAIL);
        if (auth == null || !StringUtils.hasText(auth.getIdentifier())) {
            return "";
        }
        return auth.getIdentifier().trim();
    }

    private String resolveContactEmail(String rawContact, String accountEmail) {
        String contact = rawContact != null ? rawContact.trim() : "";
        if (!StringUtils.hasText(contact)) {
            return accountEmail;
        }
        if (!EmailValidatorUtil.isValidEmail(contact)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "联系邮箱格式不正确");
        }
        return contact;
    }

    private String buildPlainMailBody(
            Long userId,
            String nickname,
            String accountEmail,
            String contactEmail,
            String categoryLabel,
            String category,
            String content,
            String pageUrl,
            String clientIp,
            String submittedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("══════════════════════════════════════").append('\n');
        sb.append("  只因梗懂你 · 用户反馈").append('\n');
        sb.append("══════════════════════════════════════").append('\n');
        sb.append('\n');
        sb.append("【基本信息】").append('\n');
        sb.append("  提交时间：").append(submittedAt).append('\n');
        sb.append("  反馈类型：").append(categoryLabel).append("（").append(category).append('）').append('\n');
        sb.append("  用户 ID ：").append(userId).append('\n');
        sb.append("  用户昵称：").append(displayOrDash(nickname)).append('\n');
        sb.append("  账号邮箱：").append(displayOrDash(accountEmail)).append('\n');
        sb.append("  联系邮箱：").append(displayOrDash(contactEmail)).append('\n');
        sb.append("  来源页面：").append(displayOrDash(pageUrl)).append('\n');
        sb.append("  客户端IP：").append(displayOrDash(clientIp)).append('\n');
        sb.append('\n');
        sb.append("【反馈正文】").append('\n');
        sb.append("--------------------------------------").append('\n');
        sb.append(content).append('\n');
        sb.append("--------------------------------------").append('\n');
        sb.append('\n');
        sb.append("— 本邮件由「只因梗懂你」站内反馈自动发送 —").append('\n');
        return sb.toString();
    }

    private String buildHtmlMailBody(
            Long userId,
            String nickname,
            String accountEmail,
            String contactEmail,
            String category,
            String categoryLabel,
            String content,
            String pageUrl,
            String clientIp,
            String submittedAt) {
        String accent = CATEGORY_ACCENTS.getOrDefault(category, "#318AEF");
        String safeNickname = escape(displayOrDash(nickname));
        String safeAccountEmail = escape(displayOrDash(accountEmail));
        String safeContactEmail = escape(displayOrDash(contactEmail));
        String safePageUrl = escape(displayOrDash(pageUrl));
        String safeClientIp = escape(displayOrDash(clientIp));
        String safeCategoryLabel = escape(categoryLabel);
        String safeContent = escape(content).replace("\n", "<br/>");

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>用户反馈</title>
                </head>
                <body style="margin:0;padding:0;background:#F4F7FB;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'PingFang SC','Microsoft YaHei',sans-serif;color:#1F2937;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#F4F7FB;padding:28px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="640" cellspacing="0" cellpadding="0" style="max-width:640px;width:100%%;background:#FFFFFF;border:1px solid #E5EAF1;border-radius:12px;overflow:hidden;">
                          <tr>
                            <td style="padding:18px 24px;background:linear-gradient(135deg,#318AEF 0%%,#52C7B8 100%%);color:#FFFFFF;">
                              <div style="font-size:12px;letter-spacing:0.08em;opacity:0.9;font-weight:600;">JUST BECAUSE MEME UNDERSTANDS YOU</div>
                              <div style="margin-top:6px;font-size:22px;font-weight:700;line-height:1.3;">只因梗懂你 · 用户反馈</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:22px 24px 8px 24px;">
                              <span style="display:inline-block;padding:4px 10px;border-radius:999px;background:%s1A;color:%s;font-size:12px;font-weight:700;">%s</span>
                              <div style="margin-top:10px;font-size:13px;color:#64748B;">提交时间 %s</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:8px 24px 4px 24px;">
                              <div style="font-size:13px;font-weight:700;color:#334155;margin-bottom:10px;">基本信息</div>
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border:1px solid #E8EEF5;border-radius:8px;overflow:hidden;">
                                %s
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 24px 24px 24px;">
                              <div style="font-size:13px;font-weight:700;color:#334155;margin-bottom:10px;">反馈正文</div>
                              <div style="padding:14px 16px;border-radius:8px;background:#F8FAFC;border:1px solid #E8EEF5;font-size:14px;line-height:1.75;color:#1E293B;white-space:normal;word-break:break-word;">
                                %s
                              </div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 24px 20px 24px;font-size:12px;color:#94A3B8;line-height:1.6;">
                              本邮件由「只因梗懂你」站内反馈系统自动发送。可直接回复本邮件联系用户（若已设置回复地址）。
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                accent,
                accent,
                safeCategoryLabel,
                escape(submittedAt),
                buildHtmlMetaRows(userId, safeNickname, safeAccountEmail, safeContactEmail, safePageUrl, safeClientIp),
                safeContent
        );
    }

    private String buildHtmlMetaRows(
            Long userId,
            String nickname,
            String accountEmail,
            String contactEmail,
            String pageUrl,
            String clientIp) {
        StringBuilder rows = new StringBuilder();
        rows.append(metaRow("用户 ID", String.valueOf(userId), false));
        rows.append(metaRow("用户昵称", nickname, true));
        rows.append(metaRow("账号邮箱", accountEmail, false));
        rows.append(metaRow("联系邮箱", contactEmail, true));
        rows.append(metaRow("来源页面", pageUrl, false));
        rows.append(metaRow("客户端 IP", clientIp, true));
        return rows.toString();
    }

    private String metaRow(String label, String value, boolean zebra) {
        String bg = zebra ? "#F8FAFC" : "#FFFFFF";
        return """
                <tr>
                  <td style="width:108px;padding:10px 12px;background:%s;border-bottom:1px solid #EEF2F7;font-size:12px;color:#64748B;font-weight:600;vertical-align:top;">%s</td>
                  <td style="padding:10px 12px;background:%s;border-bottom:1px solid #EEF2F7;font-size:13px;color:#0F172A;word-break:break-all;">%s</td>
                </tr>
                """.formatted(bg, escape(label), bg, value);
    }

    private static String displayOrDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "—";
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
