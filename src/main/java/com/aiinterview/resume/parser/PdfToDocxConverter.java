package com.aiinterview.resume.parser;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.TextUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 简历转换器：把 PDF 转成 docx 字节流，之后复用已有的 docx 文本抽取逻辑。
 *
 * <p>为什么先转 docx 再解析：简历解析链路上已经有一套经过验证的 docx 抽取
 * （段落合并、制表符、换行、空行归一化），PDF 只需把文本按行还原成段落，
 * 后续行为与上传 docx 完全一致，避免维护两套文本清洗逻辑。</p>
 *
 * <p>实现方式：PDFBox 负责抽取 PDF 文本（按页、按行），POI 负责把每一行写成一个
 * docx 段落。生成的 docx 只存在于内存，不落盘。</p>
 *
 * <p>已知限制：扫描件（纯图片 PDF）没有文本层，转换结果为空，此时抛业务异常提示
 * 用户改用粘贴文本；加密 PDF 无法在无密码情况下读取，同样给出明确提示。</p>
 */
@Component
public class PdfToDocxConverter {

    private static final Logger log = LoggerFactory.getLogger(PdfToDocxConverter.class);

    /** 单份 PDF 允许的最大页数，防止超大文件拖垮解析线程 */
    private static final int MAX_PAGES = 30;

    /**
     * 从上传文件转换。
     *
     * @param file 上传的 PDF 文件
     * @return docx 字节流
     * @throws BizException 文件为空、加密、无文本或解析失败时抛出
     */
    public byte[] convert(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("上传文件不能为空");
        }
        try (InputStream in = file.getInputStream()) {
            return convert(in);
        } catch (IOException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "读取 PDF 文件失败", e);
        }
    }

    /**
     * 从本地文件转换，供异步解析线程使用。
     *
     * @param path PDF 绝对路径
     * @return docx 字节流
     * @throws BizException 文件不存在、加密、无文本或解析失败时抛出
     */
    public byte[] convert(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "简历文件不存在或已被删除");
        }
        try (InputStream in = Files.newInputStream(path)) {
            return convert(in);
        } catch (IOException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "读取 PDF 文件失败", e);
        }
    }

    /**
     * 从输入流转换：抽取文本 → 按行写入 docx 段落。
     *
     * @param inputStream PDF 字节流
     * @return docx 字节流
     * @throws IOException 读取或写入失败
     */
    public byte[] convert(InputStream inputStream) throws IOException {
        List<String> lines = extractLines(inputStream);
        if (lines.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "PDF 未提取到文字（可能是扫描件或图片型 PDF），请改用 docx 或直接粘贴简历文本");
        }
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : lines) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(line);
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 抽取 PDF 文本并按行切分。
     *
     * @param inputStream PDF 字节流
     * @return 非空行列表
     * @throws IOException 读取失败
     */
    private List<String> extractLines(InputStream inputStream) throws IOException {
        try (PDDocument document = load(inputStream)) {
            int pages = document.getNumberOfPages();
            if (pages > MAX_PAGES) {
                log.info("PDF 页数 {} 超过上限 {}，仅解析前 {} 页", pages, MAX_PAGES, MAX_PAGES);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            stripper.setPageEnd("\n");
            if (pages > MAX_PAGES) {
                stripper.setStartPage(1);
                stripper.setEndPage(MAX_PAGES);
            }
            String text = stripper.getText(document);
            if (TextUtils.isBlank(text)) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (String raw : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
                String line = raw.strip();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            log.debug("PDF 文本抽取完成 pages={} lines={}", pages, lines.size());
            return lines;
        }
    }

    /**
     * 加载 PDF，把加密文件转成可读的业务异常。
     *
     * @param inputStream PDF 字节流
     * @return PDF 文档，调用方负责关闭
     * @throws IOException 读取失败
     */
    private PDDocument load(InputStream inputStream) throws IOException {
        try {
            return PDDocument.load(inputStream);
        } catch (InvalidPasswordException e) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "PDF 已加密，无法解析，请上传未加密的 PDF 或改用 docx", e);
        }
    }
}
