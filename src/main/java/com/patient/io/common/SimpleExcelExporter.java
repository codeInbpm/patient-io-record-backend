package com.patient.io.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 纯 Java 实现的轻量级 Office OpenXML (.xlsx) 导出工具
 * 无任何外部依赖，安全高效，生成的文件完全符合微软 OpenXML 标准，
 * 支持单元格背景染色预警（红底、黄底、浅蓝表头等），
 * 微信小程序 openDocument、Microsoft Excel、WPS 均可原生直接打开。
 */
public class SimpleExcelExporter {

    public static final int STYLE_DATA = 0;         // 普通数据行
    public static final int STYLE_HEADER = 1;       // 表头行 (浅蓝底+蓝粗体字)
    public static final int STYLE_TITLE = 2;        // 大标题 (14号粗体)
    public static final int STYLE_ALERT_RED = 3;    // 红色警示 (浅粉红底 #FFC7CE + 深红粗体字 #9C0006) -> 尿量<400ml / 差值>1000ml
    public static final int STYLE_ALERT_YELLOW = 4; // 黄色警示 (浅米黄底 #FFEB9C + 深黄粗体字 #9C6500) -> 静脉输液>3000ml
    public static final int STYLE_MUTED = 5;        // 浅灰底隔行/图例
    public static final int STYLE_CENTER = 6;       // 普通居中对齐

    private final String sheetName;
    private final List<List<String>> rows = new ArrayList<>();
    private final List<List<Integer>> rowStyles = new ArrayList<>();
    private final List<Integer> colWidths = new ArrayList<>();

    public SimpleExcelExporter(String sheetName) {
        this.sheetName = (sheetName == null || sheetName.trim().isEmpty()) ? "Sheet1" : sheetName.trim();
    }

    public void addRow(List<String> row) {
        int defaultStyle = rows.isEmpty() ? STYLE_HEADER : STYLE_DATA;
        addRow(row, defaultStyle);
    }

    public void addRow(List<String> row, int defaultStyleId) {
        if (row != null) {
            List<Integer> styles = new ArrayList<>();
            for (int i = 0; i < row.size(); i++) {
                styles.add(defaultStyleId);
            }
            addRowWithStyles(row, styles);
        }
    }

    public void addRowWithStyles(List<String> row, List<Integer> customStyleIds) {
        if (row != null) {
            rows.add(new ArrayList<>(row));
            List<Integer> styles = new ArrayList<>();
            for (int i = 0; i < row.size(); i++) {
                String val = row.get(i);
                int len = val == null ? 0 : val.getBytes(StandardCharsets.UTF_8).length;
                if (i >= colWidths.size()) {
                    colWidths.add(Math.max(12, len + 4));
                } else {
                    colWidths.set(i, Math.max(colWidths.get(i), Math.min(len + 4, 50)));
                }

                if (customStyleIds != null && i < customStyleIds.size() && customStyleIds.get(i) != null) {
                    styles.add(customStyleIds.get(i));
                } else {
                    styles.add(STYLE_DATA);
                }
            }
            rowStyles.add(styles);
        }
    }

    public byte[] exportToBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeTo(baos);
        return baos.toByteArray();
    }

    public void writeTo(OutputStream out) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            // 1. [Content_Types].xml
            addZipEntry(zos, "[Content_Types].xml", buildContentTypesXml());

            // 2. _rels/.rels
            addZipEntry(zos, "_rels/.rels", buildGlobalRelsXml());

            // 3. xl/_rels/workbook.xml.rels
            addZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml());

            // 4. xl/workbook.xml
            addZipEntry(zos, "xl/workbook.xml", buildWorkbookXml());

            // 5. xl/styles.xml
            addZipEntry(zos, "xl/styles.xml", buildStylesXml());

            // 6. xl/worksheets/sheet1.xml
            addZipEntry(zos, "xl/worksheets/sheet1.xml", buildSheetXml());
        }
    }

    private void addZipEntry(ZipOutputStream zos, String path, String content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        zos.putNextEntry(entry);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        zos.write(bytes);
        zos.closeEntry();
    }

    private String buildContentTypesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                "  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n" +
                "  <Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n" +
                "  <Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>\n" +
                "</Types>";
    }

    private String buildGlobalRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n" +
                "</Relationships>";
    }

    private String buildWorkbookRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>\n" +
                "  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>\n" +
                "</Relationships>";
    }

    private String buildWorkbookXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n" +
                "  <sheets>\n" +
                "    <sheet name=\"" + escapeXml(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/>\n" +
                "  </sheets>\n" +
                "</workbook>";
    }

    private String buildStylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n" +
                "  <fonts count=\"5\">\n" +
                "    <font><sz val=\"11\"/><name val=\"微软雅黑\"/></font>\n" + // 0: 普通
                "    <font><b/><sz val=\"11\"/><color rgb=\"FF1A56DB\"/><name val=\"微软雅黑\"/></font>\n" + // 1: 表头 (蓝色加粗)
                "    <font><b/><sz val=\"14\"/><name val=\"微软雅黑\"/></font>\n" + // 2: 大标题
                "    <font><b/><sz val=\"11\"/><color rgb=\"FF9C0006\"/><name val=\"微软雅黑\"/></font>\n" + // 3: 红色警示文字 (深暗红)
                "    <font><b/><sz val=\"11\"/><color rgb=\"FF9C6500\"/><name val=\"微软雅黑\"/></font>\n" + // 4: 黄色警示文字 (深暗黄)
                "  </fonts>\n" +
                "  <fills count=\"6\">\n" +
                "    <fill><patternFill patternType=\"none\"/></fill>\n" + // 0
                "    <fill><patternFill patternType=\"gray125\"/></fill>\n" + // 1
                "    <fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF0F5FF\"/></patternFill></fill>\n" + // 2: 浅蓝表头
                "    <fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFC7CE\"/></patternFill></fill>\n" + // 3: 浅粉红填充 (Red Alert: 尿量<400 或 差值>1000)
                "    <fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFEB9C\"/></patternFill></fill>\n" + // 4: 浅米黄填充 (Yellow Alert: 静脉输液>3000)
                "    <fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF8F9FA\"/></patternFill></fill>\n" + // 5: 浅灰隔行
                "  </fills>\n" +
                "  <borders count=\"2\">\n" +
                "    <border><left/><right/><top/><bottom/><diagonal/></border>\n" +
                "    <border>\n" +
                "      <left style=\"thin\"><color rgb=\"FFD9D9D9\"/></left>\n" +
                "      <right style=\"thin\"><color rgb=\"FFD9D9D9\"/></right>\n" +
                "      <top style=\"thin\"><color rgb=\"FFD9D9D9\"/></top>\n" +
                "      <bottom style=\"thin\"><color rgb=\"FFD9D9D9\"/></bottom>\n" +
                "    </border>\n" +
                "  </borders>\n" +
                "  <cellStyleXfs count=\"1\">\n" +
                "    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/>\n" +
                "  </cellStyleXfs>\n" +
                "  <cellXfs count=\"7\">\n" +
                "    <!-- 0: 普通数据行 -->\n" +
                "    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\">\n" +
                "      <alignment vertical=\"center\"/>\n" +
                "    </xf>\n" +
                "    <!-- 1: 表头行 (浅蓝底+加粗) -->\n" +
                "    <xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">\n" +
                "      <alignment horizontal=\"center\" vertical=\"center\"/>\n" +
                "    </xf>\n" +
                "    <!-- 2: 大标题 -->\n" +
                "    <xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\">\n" +
                "      <alignment horizontal=\"left\" vertical=\"center\"/>\n" +
                "    </xf>\n" +
                "    <!-- 3: 红色警示 (少尿 <400ml / 出入差值 >1000ml) -->\n" +
                "    <xf numFmtId=\"0\" fontId=\"3\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">\n" +
                "      <alignment horizontal=\"center\" vertical=\"center\"/>\n" +
                "    </xf>\n" +
                "    <!-- 4: 黄色警示 (静脉输液 >3000ml) -->\n" +
                "    <xf numFmtId=\"0\" fontId=\"4\" fillId=\"4\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">\n" +
                "      <alignment horizontal=\"center\" vertical=\"center\"/>\n" +
                "    </xf>\n" +
                "    <!-- 5: 浅灰隔行/图例说明 -->\n" +
                "    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"5\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">\n" +
                "      <alignment horizontal=\"left\" vertical=\"center\"/>\n" +
                "    </xf>\n" +
                "    <!-- 6: 普通居中对齐 -->\n" +
                "    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\">\n" +
                "      <alignment horizontal=\"center\" vertical=\"center\"/>\n" +
                "    </xf>\n" +
                "  </cellXfs>\n" +
                "</styleSheet>";
    }

    private String buildSheetXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");

        // 列宽配置
        if (!colWidths.isEmpty()) {
            sb.append("  <cols>\n");
            for (int i = 0; i < colWidths.size(); i++) {
                int colIdx = i + 1;
                int w = colWidths.get(i);
                sb.append("    <col min=\"").append(colIdx).append("\" max=\"").append(colIdx)
                        .append("\" width=\"").append(w).append("\" customWidth=\"1\"/>\n");
            }
            sb.append("  </cols>\n");
        }

        sb.append("  <sheetData>\n");
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            List<Integer> styles = (r < rowStyles.size()) ? rowStyles.get(r) : null;
            int rowIdx = r + 1;
            sb.append("    <row r=\"").append(rowIdx).append("\" ht=\"24\" customHeight=\"1\">\n");
            for (int c = 0; c < row.size(); c++) {
                String val = row.get(c);
                String cellRef = getColumnLetter(c + 1) + rowIdx;
                int styleId = (styles != null && c < styles.size()) ? styles.get(c) : ((r == 0) ? STYLE_HEADER : STYLE_DATA);
                if (val == null) {
                    val = "";
                }
                sb.append("      <c r=\"").append(cellRef).append("\" s=\"").append(styleId).append("\" t=\"inlineStr\">\n");
                sb.append("        <is><t>").append(escapeXml(val)).append("</t></is>\n");
                sb.append("      </c>\n");
            }
            sb.append("    </row>\n");
        }
        sb.append("  </sheetData>\n");
        sb.append("</worksheet>");
        return sb.toString();
    }

    private String getColumnLetter(int colIndex) {
        StringBuilder sb = new StringBuilder();
        while (colIndex > 0) {
            int rem = (colIndex - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            colIndex = (colIndex - 1) / 26;
        }
        return sb.toString();
    }

    private String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
