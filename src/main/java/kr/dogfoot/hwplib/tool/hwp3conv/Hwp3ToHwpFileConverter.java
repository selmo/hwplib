package kr.dogfoot.hwplib.tool.hwp3conv;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.ParagraphListInterface;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.object.bodytext.control.gso.ControlRectangle;
import kr.dogfoot.hwplib.object.bodytext.control.gso.GsoControlType;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.ListHeaderForCell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Row;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.header.ParaHeader;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.HWPCharControlExtend;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.ParaText;
import kr.dogfoot.hwplib.object.docinfo.DocInfo;
import kr.dogfoot.hwplib.object.docinfo.ParaShape;
import kr.dogfoot.hwplib.object.docinfo.Style;
import kr.dogfoot.hwplib.object.docinfo.parashape.Alignment;
import kr.dogfoot.hwplib.object.docinfo.parashape.ParaHeadShape;
import kr.dogfoot.hwplib.object.hwp3.HWP3File;
import kr.dogfoot.hwplib.object.hwp3.Hwp3Cell;
import kr.dogfoot.hwplib.object.hwp3.Hwp3Paragraph;
import kr.dogfoot.hwplib.object.hwp3.Hwp3Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 한글 3.x 전용 객체 모델({@link HWP3File})을 한글 5.0 객체 모델({@link HWPFile})로
 * 변환하는 객체.
 *
 * <p>변환 범위: 문단 텍스트와 표 구조(셀 그리드 좌표·병합 범위 → {@link ControlTable}),
 * 텍스트박스(코드 10의 boxType=1 → GSO 사각형 텍스트박스). 변환된 결과는
 * {@code TextExtractor}(표 렌더링 {@code TableFormat} 포함) 등 기존 도구를 그대로
 * 사용할 수 있다.</p>
 *
 * <p>제약(best-effort): 글자/문단 모양·스타일은 기본값 하나로 통일된다. 표의
 * 문단 내 정확한 위치(객체 대체 문자 자리)는 보존되지 않고 해당 문단 끝에 붙는다.
 * 각주/머리말/그리기 개체 글상자 등 구조화되지 않은 중첩 문단은 문서 순서대로
 * 일반 문단으로 변환된다.</p>
 */
public class Hwp3ToHwpFileConverter {
    /** HWP3 텍스트의 객체 대체 문자(표/그림 등 컨트롤 자리표시). 변환 시 제거된다. */
    private static final char OBJECT_REPLACEMENT = '￼';

    /**
     * 한글 3.x 객체를 한글 5.0 객체로 변환한다.
     *
     * @param hwp3 한글 3.x 문서 객체
     * @return 변환된 한글 5.0 문서 객체
     * @throws Exception 변환 중 오류가 발생한 경우
     */
    public static HWPFile convert(HWP3File hwp3) throws Exception {
        HWPFile hwpFile = new HWPFile();
        prepareDocInfo(hwpFile.getDocInfo());

        Section section = hwpFile.getBodyText().addNewSection();

        // 표/텍스트박스 구조에 속한 문단(셀·캡션)은 평탄화 리스트에서 제외하고
        // 표 변환 시 셀 안에서 변환한다.
        Set<Hwp3Paragraph> owned = collectBoxOwnedParagraphs(hwp3.getParagraphs());

        boolean first = true;
        for (Hwp3Paragraph p : hwp3.getParagraphs()) {
            if (owned.contains(p)) {
                continue;
            }
            Paragraph hp = convertParagraph(p, section);
            if (first) {
                // TextExtractor가 구역 첫 문단의 컨트롤 리스트를 참조하므로 비어 있어도 만들어 둔다.
                if (hp.getControlList() == null) {
                    hp.createControlList();
                }
                first = false;
            }
        }
        return hwpFile;
    }

    /**
     * 텍스트 추출에 필요한 최소 DocInfo(기본 글자/문단 모양, 스타일)를 만든다.
     */
    private static void prepareDocInfo(DocInfo docInfo) {
        docInfo.addNewCharShape();

        ParaShape paraShape = docInfo.addNewParaShape();
        paraShape.getProperty1().setAlignment(Alignment.Justify);
        paraShape.getProperty1().setParaHeadShape(ParaHeadShape.None);

        Style style = docInfo.addNewStyle();
        style.setHangulName("바탕글");
        style.setEnglishName("Normal");
        style.setParaShapeId(0);
        style.setCharShapeId(0);

        docInfo.getIDMappings().setCharShapeCount(1);
        docInfo.getIDMappings().setParaShapeCount(1);
        docInfo.getIDMappings().setStyleCount(1);
    }

    /**
     * 표/텍스트박스(코드 10) 구조가 소유한 모든 문단(셀·캡션, 중첩 포함)을 수집한다.
     */
    private static Set<Hwp3Paragraph> collectBoxOwnedParagraphs(List<Hwp3Paragraph> paragraphs) {
        Set<Hwp3Paragraph> owned =
                Collections.newSetFromMap(new IdentityHashMap<Hwp3Paragraph, Boolean>());
        for (Hwp3Paragraph p : paragraphs) {
            for (Hwp3Table t : p.getTables()) {
                collectFromTable(t, owned);
            }
        }
        return owned;
    }

    private static void collectFromTable(Hwp3Table table, Set<Hwp3Paragraph> owned) {
        for (Hwp3Cell cell : table.getCells()) {
            for (Hwp3Paragraph p : cell.getParagraphs()) {
                owned.add(p);
                for (Hwp3Table nested : p.getTables()) {
                    collectFromTable(nested, owned);
                }
            }
        }
        for (Hwp3Paragraph p : table.getCaption()) {
            owned.add(p);
            for (Hwp3Table nested : p.getTables()) {
                collectFromTable(nested, owned);
            }
        }
    }

    /**
     * HWP3 문단 하나를 HWP5 문단으로 변환하여 container에 추가한다.
     * 문단에 표/텍스트박스가 있으면 확장 컨트롤 문자와 컨트롤을 함께 만든다.
     */
    private static Paragraph convertParagraph(Hwp3Paragraph src,
                                              ParagraphListInterface container) throws Exception {
        Paragraph para = container.addNewParagraph();
        para.createText();
        para.createCharShape();
        ParaText text = para.getText();

        ParaHeader header = para.getHeader();
        header.setParaShapeId(0);
        header.setStyleId((short) 0);

        String s = src.getText();
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            if (cp != OBJECT_REPLACEMENT) {
                text.addNewNormalChar().setCode(cp);
            }
            i += Character.charCount(cp);
        }

        for (Hwp3Table t : src.getTables()) {
            if (t.isTable()) {
                addExtendChar(text, ControlType.Table.getCtrlId(), "tbl ");
                buildControlTable(t, para);
            } else {
                addExtendChar(text, GsoControlType.Rectangle.getId(), "gso ");
                buildTextBox(t, para);
            }
        }

        // 문단 끝 문자(0x0d)
        text.addNewCharControlChar().setCode(0x0d);

        header.setCharacterCount(text.getCharSize());
        para.getCharShape().addParaCharShape(0, 0);
        header.setCharShapeCount(1);
        header.setLineAlignCount(0);
        return para;
    }

    /**
     * Hwp3Table(기하 그리드 계산 완료 상태)을 ControlTable로 변환한다.
     */
    private static void buildControlTable(Hwp3Table src, Paragraph anchor) throws Exception {
        ControlTable ct = (ControlTable) anchor.addNewControl(ControlType.Table);
        int rowCount = src.getRowCount();
        ct.getTable().setRowCount(rowCount);
        ct.getTable().setColumnCount(src.getColCount());

        // 행별 anchor 셀 분류(gridRow 기준) 후 gridCol 순 정렬
        List<List<Hwp3Cell>> byRow = new ArrayList<List<Hwp3Cell>>(rowCount);
        for (int r = 0; r < rowCount; r++) {
            byRow.add(new ArrayList<Hwp3Cell>());
        }
        for (Hwp3Cell c : src.getCells()) {
            if (c.getGridRow() >= 0 && c.getGridRow() < rowCount) {
                byRow.get(c.getGridRow()).add(c);
            }
        }

        for (int r = 0; r < rowCount; r++) {
            List<Hwp3Cell> rowCells = byRow.get(r);
            rowCells.sort((a, b) -> Integer.compare(a.getGridCol(), b.getGridCol()));
            ct.getTable().addCellCountOfRow(rowCells.size());

            Row row = ct.addNewRow();
            for (Hwp3Cell sc : rowCells) {
                Cell cell = row.addNewCell();
                ListHeaderForCell lh = cell.getListHeader();
                lh.setRowIndex(sc.getGridRow());
                lh.setColIndex(sc.getGridCol());
                lh.setRowSpan(sc.getGridRowSpan());
                lh.setColSpan(sc.getGridColSpan());
                lh.setWidth(sc.getWidth());
                lh.setHeight(sc.getHeight());
                lh.setParaCount(sc.getParagraphs().size());
                for (Hwp3Paragraph p : sc.getParagraphs()) {
                    convertParagraph(p, cell.getParagraphList());
                }
            }
        }

        if (hasContent(src.getCaption())) {
            ct.createCaption();
            for (Hwp3Paragraph p : src.getCaption()) {
                convertParagraph(p, ct.getCaption().getParagraphList());
            }
        }
    }

    /**
     * 텍스트박스/수식/버튼(코드 10, boxType != 0)을 GSO 사각형 텍스트박스로 변환한다.
     * 모든 셀의 문단을 하나의 텍스트박스에 담는다.
     */
    private static void buildTextBox(Hwp3Table src, Paragraph anchor) throws Exception {
        ControlRectangle rect =
                (ControlRectangle) anchor.addNewGsoControl(GsoControlType.Rectangle);
        rect.createTextBox();
        for (Hwp3Cell cell : src.getCells()) {
            for (Hwp3Paragraph p : cell.getParagraphs()) {
                convertParagraph(p, rect.getTextBox().getParagraphList());
            }
        }
        for (Hwp3Paragraph p : src.getCaption()) {
            convertParagraph(p, rect.getTextBox().getParagraphList());
        }
    }

    /**
     * 문단 리스트에 실제 내용(텍스트 또는 표)이 있는지 여부.
     */
    private static boolean hasContent(List<Hwp3Paragraph> paragraphs) {
        for (Hwp3Paragraph p : paragraphs) {
            if (!p.getText().trim().isEmpty() || !p.getTables().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 확장 컨트롤 문자(code 0x0b)를 글자 스트림에 추가한다.
     * addition의 앞 4바이트는 컨트롤 ID 문자열을 역순으로 담는다.
     *
     * @param ctrlId 컨트롤 ID(미사용 — 문서화 목적)
     * @param idText 컨트롤 ID 문자열(예: "tbl ", "gso ")
     */
    private static void addExtendChar(ParaText text, long ctrlId, String idText) throws Exception {
        HWPCharControlExtend ch = text.addNewExtendControlChar();
        ch.setCode(0x000b);
        byte[] addition = new byte[12];
        for (int i = 0; i < 4; i++) {
            addition[i] = (byte) idText.charAt(3 - i);
        }
        ch.setAddition(addition);
    }
}
