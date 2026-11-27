package com.aiinterview.resume.parser;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * 简历文本抽取器：把上传的文件转换成纯文本，供异步解析与规则降级使用。
 *
 * <p>支持格式：</p>
 * <ul>
 *     <li>{@code txt} / {@code md}：按 UTF-8 读取；</li>
 *     <li>{@code docx}：使用 JDK 自带的 {@link java.util.zip.ZipFile}/{@link ZipInputStream}
 *         找到 {@code word/document.xml}，再用 DOM 解析，遍历 {@code w:p} 生成段落换行、
 *         拼接 {@code w:t} 文本，不引入任何第三方文档库；</li>
 *     <li>{@code pdf}：先由 {@link PdfToDocxConverter} 把 PDF 转成 docx 字节流，
 *         之后复用同一套 docx 抽取逻辑，保证两种格式的清洗规则完全一致；</li>
 *     <li>其他格式：直接抛出业务异常并给出明确提示。</li>
 * </ul>
 *
 * <p>安全要点：DOM 解析已禁用 DOCTYPE 与外部实体，防止 XXE 攻击。
 * 抽取结果统一做 20000 字长度保护。</p>
 */
@Component
public class ResumeTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(ResumeTextExtractor.class);

    /** 单份简历保留的最大字符数，避免超长文本拖垮 AI 调用与数据库 */
    private static final int MAX_CHARS = 20000;
    /** docx 正文在压缩包内的固定路径 */
    private static final String DOCUMENT_XML = "word/document.xml";
    private static final String UNSUPPORTED_MESSAGE = "仅支持 txt / md / docx / pdf 格式的简历";

    private final PdfToDocxConverter pdfToDocxConverter;

    /**
     * 构造器注入 PDF 转换器。
     *
     * @param pdfToDocxConverter PDF → docx 转换器
     */
    public ResumeTextExtractor(PdfToDocxConverter pdfToDocxConverter) {
        this.pdfToDocxConverter = pdfToDocxConverter;
    }

    /**
     * 从上传文件中抽取纯文本。
     *
     * @param file 上传文件
     * @return 抽取出的纯文本（已截断到 20000 字以内）
     * @throws BizException 文件为空、格式不支持、内容为空或读取失败时抛出
     */
    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("上传文件不能为空");
        }
        String extension = extensionOf(file.getOriginalFilename());
        try {
            return switch (extension) {
                case "txt", "md" -> finish(new String(file.getBytes(), StandardCharsets.UTF_8));
                case "docx" -> {
                    try (InputStream in = file.getInputStream()) {
                        yield finish(extractDocx(in));
                    }
                }
                case "pdf" -> finish(extractDocx(
                        new java.io.ByteArrayInputStream(pdfToDocxConverter.convert(file))));
                default -> throw new BizException(ErrorCode.BAD_REQUEST, UNSUPPORTED_MESSAGE);
            };
        } catch (IOException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "读取上传文件失败", e);
        }
    }

    /**
     * 从本地文件抽取纯文本，供异步解析线程使用。
     *
     * @param path     文件绝对路径
     * @param fileName 原始文件名，用于判断扩展名
     * @return 抽取出的纯文本（已截断到 20000 字以内）
     * @throws BizException 文件不存在、格式不支持、内容为空或读取失败时抛出
     */
    public String extract(Path path, String fileName) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "简历文件不存在或已被删除");
        }
        String extension = extensionOf(fileName);
        try {
            return switch (extension) {
                case "txt", "md" -> finish(Files.readString(path, StandardCharsets.UTF_8));
                case "docx" -> finish(extractDocx(path));
                case "pdf" -> finish(extractDocx(
                        new java.io.ByteArrayInputStream(pdfToDocxConverter.convert(path))));
                default -> throw new BizException(ErrorCode.BAD_REQUEST, UNSUPPORTED_MESSAGE);
            };
        } catch (IOException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "读取简历文件失败", e);
        }
    }

    /**
     * 纯文本直传：仅做空值与长度校验。
     *
     * @param text 简历原文
     * @return 规范化后的文本
     * @throws BizException 内容为空时抛出
     */
    public String extractFromText(String text) {
        return finish(text);
    }

    /**
     * 从 docx 压缩包的字节流中抽取文本。
     */
    private String extractDocx(InputStream inputStream) {
        try (ZipInputStream zip = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (DOCUMENT_XML.equals(entry.getName())) {
                    return normalizeDocxText(parseWordXml(zip));
                }
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "docx 文件解析失败", e);
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "docx 文件格式不正确：缺少 word/document.xml");
    }

    /**
     * 从本地 docx 文件中抽取文本（ZipFile 随机访问，效率更高）。
     */
    private String extractDocx(Path path) {
        try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zip.getEntry(DOCUMENT_XML);
            if (entry == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "docx 文件格式不正确：缺少 word/document.xml");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return normalizeDocxText(parseWordXml(in));
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "docx 文件解析失败", e);
        }
    }

    /**
     * 解析 word/document.xml，按段落拼接文本。
     */
    private String parseWordXml(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            // 禁用 DOCTYPE 与外部实体，防止 XXE
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            try {
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (IllegalArgumentException e) {
                log.debug("当前 XML 解析器不支持 ACCESS_EXTERNAL_DTD 属性，已依赖实体禁用特性", e);
            }
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                    log.debug("docx XML 解析警告：{}", exception.getMessage());
                }

                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });
            Document document = builder.parse(inputStream);
            StringBuilder text = new StringBuilder();
            collectParagraphs(document.getDocumentElement(), text);
            return text.toString();
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "docx 内容解析失败", e);
        }
    }

    /**
     * 递归遍历文档节点，遇到 w:p 输出一个段落并换行。
     */
    private void collectParagraphs(Node node, StringBuilder out) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (isWordElement(child, "p")) {
                StringBuilder paragraph = new StringBuilder();
                collectInlineText(child, paragraph);
                out.append(paragraph.toString().strip()).append('\n');
            } else {
                collectParagraphs(child, out);
            }
        }
    }

    /**
     * 收集段落内的文本：w:t 取文本，w:tab 补制表符，w:br 补换行。
     */
    private void collectInlineText(Node node, StringBuilder out) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.TEXT_NODE) {
                out.append(child.getNodeValue());
                continue;
            }
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            // 段落/字符属性中的制表位等格式定义不属于正文，直接跳过
            if (isWordElement(child, "pPr") || isWordElement(child, "rPr")) {
                continue;
            }
            if (isWordElement(child, "t")) {
                out.append(child.getTextContent());
            } else if (isWordElement(child, "tab")) {
                out.append('\t');
            } else if (isWordElement(child, "br") || isWordElement(child, "cr")) {
                out.append('\n');
            } else {
                collectInlineText(child, out);
            }
        }
    }

    /**
     * 判断节点是否为指定 localName 的 WordprocessingML 元素（忽略命名空间前缀）。
     */
    private boolean isWordElement(Node node, String localName) {
        String nodeName = node.getNodeName();
        int colon = nodeName.indexOf(':');
        String local = colon >= 0 ? nodeName.substring(colon + 1) : nodeName;
        return localName.equals(local);
    }

    /**
     * 合并 docx 段落带来的多余空行。
     */
    private String normalizeDocxText(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replaceAll(" *\n *", "\n");
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized.strip();
    }

    /**
     * 统一做空值校验与长度截断。
     */
    private String finish(String text) {
        if (TextUtils.isBlank(text)) {
            throw BizException.badRequest("简历内容为空");
        }
        String trimmed = text.strip();
        if (trimmed.length() > MAX_CHARS) {
            log.info("简历文本超过 {} 字，已截断，原长度={}", MAX_CHARS, trimmed.length());
            return trimmed.substring(0, MAX_CHARS);
        }
        return trimmed;
    }

    /**
     * 取小写扩展名，无扩展名时返回空串。
     */
    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
