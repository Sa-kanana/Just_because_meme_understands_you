package com.sakana.just_because_meme_understands_you.service.admin.support;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
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

    public static final long MAX_FILE_BYTES = 15L * 1024 * 1024;
    public static final int MAX_CONTENT_CHARS = 200_000;

    private static final Set<String> ALLOWED_EXT = Set.of("md", "markdown", "pdf");

    private KnowledgeDocumentParseSupport() {
    }

    public static ParsedKnowledgeDocument parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请上传文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException(Result.CODE_BAD_REQUEST, "文件不能超过 15MB");
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
            if (content.length() > MAX_CONTENT_CHARS) {
                content = content.substring(0, MAX_CONTENT_CHARS);
            }
            String title = titleFromFilename(originalName);
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
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return text == null ? "" : text.trim();
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
