package com.aiinterview.resume.parser;

import com.aiinterview.common.exception.BizException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 简历解析测试：PDF → docx → 复用 docx 抽取链路。
 *
 * <p>PDFBox 只内置拉丁字体，因此测试内容用英文；中文 PDF 由用户真实上传，
 * 走同一条转换与抽取路径。</p>
 */
class PdfResumeExtractionTest {

    private final PdfToDocxConverter converter = new PdfToDocxConverter();
    private final ResumeTextExtractor extractor = new ResumeTextExtractor(converter);

    /**
     * 正常 PDF：抽取到的文本应包含原文关键信息，且经过 docx 段落清洗。
     *
     * @throws Exception 生成 PDF 失败
     */
    @Test
    @DisplayName("PDF 简历能抽出文本并复用 docx 清洗逻辑")
    void shouldExtractPdfText() throws Exception {
        byte[] pdf = buildPdf(
                "Zhang Wei",
                "Java Backend Engineer, 5 years",
                "Skills: Java, Spring Boot, MySQL, Redis, Kafka",
                "Project: order platform QPS 3500, P99 120ms");
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdf);

        String text = extractor.extract(file);

        assertThat(text).contains("Zhang Wei");
        assertThat(text).contains("Spring Boot");
        assertThat(text).contains("P99 120ms");
        // docx 链路会按段落换行输出
        assertThat(text.lines().count()).isGreaterThanOrEqualTo(4);
    }

    /**
     * 转换器本身应产出合法的 docx（ZIP 头为 PK）。
     *
     * @throws Exception 生成 PDF 失败
     */
    @Test
    @DisplayName("PDF 转出的 docx 是合法 ZIP")
    void shouldProduceValidDocx() throws Exception {
        byte[] pdf = buildPdf("Hello PDF", "Second line");
        byte[] docx = converter.convert(new java.io.ByteArrayInputStream(pdf));
        assertThat(docx.length).isGreaterThan(100);
        assertThat(new String(docx, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
    }

    /**
     * 纯图片/空白 PDF 没有文本层，应给出可操作的提示。
     *
     * @throws Exception 生成 PDF 失败
     */
    @Test
    @DisplayName("无文本层 PDF 给出改用 docx 或粘贴文本的提示")
    void shouldRejectEmptyPdf() throws Exception {
        byte[] pdf = buildPdf();
        assertThatThrownBy(() -> converter.convert(new java.io.ByteArrayInputStream(pdf)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未提取到文字");
    }

    /**
     * 不支持的扩展名仍然报错，说明 PDF 放开没有放宽其他格式。
     */
    @Test
    @DisplayName("不支持的格式仍被拒绝")
    void shouldRejectUnsupportedFormat() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.exe", "application/octet-stream",
                new byte[]{1, 2, 3});
        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("txt / md / docx / pdf");
    }

    /**
     * 生成一份测试 PDF（每行一个段落）。
     *
     * @param lines 文本行
     * @return PDF 字节
     * @throws Exception 生成失败
     */
    private byte[] buildPdf(String... lines) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(50, 750);
                for (String line : lines) {
                    content.showText(line);
                    content.newLineAtOffset(0, -20);
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
