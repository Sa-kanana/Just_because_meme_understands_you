package com.sakana.just_because_meme_understands_you.modules.admin.application.support;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 管理员知识文档解析：Markdown / PDF → 纯文本。
 */
public final class KnowledgeDocumentParseSupport {

    public static final long MAX_FILE_BYTES = 25L * 1024 * 1024;
    public static final int MAX_CONTENT_CHARS = 1_000_000;

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeDocumentParseSupport.class);
    private static final Set<String> ALLOWED_EXT = Set.of("md", "markdown", "pdf");

    private KnowledgeDocumentParseSupport() {
    }

    public static ParsedKnowledgeDocument parse(MultipartFile file) {
        KnowledgeUploadProperties defaults = new KnowledgeUploadProperties();
        return parse(file, defaults);
    }

    public static ParsedKnowledgeDocument parse(
            MultipartFile file,
            KnowledgeUploadProperties properties) {
        long startedAt = System.nanoTime();
        if (file == null || file.isEmpty()) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请上传文件");
        }
        long fileBytes = file.getSize();
        long maxFileBytes = properties.getMaxFileSize().toBytes();
        if (fileBytes > maxFileBytes) {
            throw new BizException(Result.CODE_BAD_REQUEST,
                    "文件超过允许大小 " + maxFileBytes + " 字节");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String ext = extensionOf(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "仅支持 .md / .markdown / .pdf");
        }

        try {
            String content;
            if ("pdf".equals(ext)) {
                content = extractPdf(file.getBytes());
            } else {
                content = extractMarkdown(file.getBytes());
            }
            if (!StringUtils.hasText(content)) {
                throw new BizException(Result.CODE_BAD_REQUEST, "未能从文件中提取到有效文本");
            }
            if (content.length() > properties.getMaxContentChars()) {
                throw new BizException(Result.CODE_BAD_REQUEST,
                        "提取正文超过 " + properties.getMaxContentChars()
                                + " 个字符，请拆分文件后重新上传");
            }
            String title = titleFromFilename(originalName);
            logger.info("Knowledge file parsed ext={} bytes={} chars={} elapsed_ms={}",
                    ext,
                    fileBytes,
                    content.length(),
                    (System.nanoTime() - startedAt) / 1_000_000L);
            return new ParsedKnowledgeDocument(title, content, originalName, ext);
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException(Result.CODE_BAD_REQUEST, "文件读取失败，请检查文件是否损坏");
        }
    }

    private static String extractMarkdown(byte[] bytes) {
        Charset charset = detectCharset(bytes);
        String text = new String(bytes, charset);
        // 去掉 UTF-8 BOM
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return text.trim();
    }

    private static String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new BizException(Result.CODE_BAD_REQUEST, "不支持加密 PDF");
            }
            StringBuilder pages = new StringBuilder();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                if (page > 1) {
                    pages.append('\f');
                }
                if (pageText != null) {
                    pages.append(pageText.trim());
                }
            }
            return pages.toString().trim();
        }
    }

    private static Charset detectCharset(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        return StandardCharsets.UTF_8;
    }

    private static String extensionOf(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "";
        }
        return filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private static String titleFromFilename(String filename) {
        String name = filename;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.trim();
        if (!StringUtils.hasText(name)) {
            return "未命名文档";
        }
        if (name.length() > 200) {
            return name.substring(0, 200);
        }
        return name;
    }

    public record ParsedKnowledgeDocument(String title, String content, String originalFilename, String extension) {
    }
}
