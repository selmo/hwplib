package kr.dogfoot.hwplib.reader.hwpml;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.BodyText;
import kr.dogfoot.hwplib.object.bodytext.ParagraphListInterface;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.object.bodytext.control.gso.ControlArc;
import kr.dogfoot.hwplib.object.bodytext.control.gso.ControlContainer;
import kr.dogfoot.hwplib.object.bodytext.control.gso.ControlCurve;
import kr.dogfoot.hwplib.object.bodytext.control.gso.ControlEllipse;
import kr.dogfoot.hwplib.object.bodytext.control.gso.ControlPolygon;
import kr.dogfoot.hwplib.object.bodytext.control.gso.ControlRectangle;
import kr.dogfoot.hwplib.object.bodytext.control.gso.GsoControl;
import kr.dogfoot.hwplib.object.bodytext.control.gso.GsoControlType;
import kr.dogfoot.hwplib.object.bodytext.control.gso.textbox.TextBox;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Row;
import kr.dogfoot.hwplib.object.bodytext.control.table.ListHeaderForCell;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.charshape.ParaCharShape;
import kr.dogfoot.hwplib.object.bodytext.paragraph.header.ParaHeader;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.HWPCharControlExtend;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.ParaText;
import kr.dogfoot.hwplib.reader.FileFormat;
import kr.dogfoot.hwplib.reader.FormatDetector;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * HWPML(XML) 파일을 읽기 위한 객체.
 *
 * <p>규격: 「한글 문서 파일 형식 3.0 / HWPML」 revision 1.2, Part II.</p>
 *
 * <p>HWPML은 한글 5.0과 의미상 동형이므로 기존 {@link HWPFile} 객체 모델에 직접
 * 매핑한다. 재귀 하강 방식으로 파싱하여 표 셀 안의 중첩 문단(및 중첩 표)을 자연스럽게
 * 처리한다.</p>
 *
 * <p>구현 범위:
 * <ul>
 *     <li>HEAD → DocInfo (글꼴/글자모양/문단모양/스타일/번호 등, {@link ForHead})</li>
 *     <li>BODY → Section/Paragraph/글자(TEXT/CHAR)</li>
 *     <li>표(TABLE) → {@link ControlTable} (행/열/셀/셀 문단)</li>
 * </ul>
 * 그림·그리기 개체·글상자·각주 등 그 외 컨트롤은 아직 본문에서 건너뛴다.(이후 단계)</p>
 */
public class HWPMLReader {
    /**
     * HWPML 그리기 개체 엘리먼트 이름 → GSO 컨트롤 종류 매핑.
     */
    private static final Map<String, GsoControlType> SHAPE_TYPE = new HashMap<String, GsoControlType>();

    static {
        SHAPE_TYPE.put("LINE", GsoControlType.Line);
        SHAPE_TYPE.put("RECTANGLE", GsoControlType.Rectangle);
        SHAPE_TYPE.put("ELLIPSE", GsoControlType.Ellipse);
        SHAPE_TYPE.put("ARC", GsoControlType.Arc);
        SHAPE_TYPE.put("POLYGON", GsoControlType.Polygon);
        SHAPE_TYPE.put("CURVE", GsoControlType.Curve);
        SHAPE_TYPE.put("CONNECTLINE", GsoControlType.ObjectLinkLine);
        SHAPE_TYPE.put("CONTAINER", GsoControlType.Container);
        SHAPE_TYPE.put("PICTURE", GsoControlType.Picture);
        SHAPE_TYPE.put("OLE", GsoControlType.OLE);
        SHAPE_TYPE.put("TEXTART", GsoControlType.TextArt);
    }

    /**
     * HWPML 파일을 읽는다.
     *
     * @param filepath 파일 경로
     * @return HWPFile 객체
     * @throws Exception 파일을 읽는 도중 오류가 발생한 경우
     */
    public static HWPFile fromFile(String filepath) throws Exception {
        return fromInputStream(new FileInputStream(filepath));
    }

    /**
     * HWPML 파일을 읽는다.
     *
     * @param file 파일
     * @return HWPFile 객체
     * @throws Exception 파일을 읽는 도중 오류가 발생한 경우
     */
    public static HWPFile fromFile(File file) throws Exception {
        return fromInputStream(new FileInputStream(file));
    }

    /**
     * HWPML 파일을 읽는다.
     *
     * @param is 입력 스트림
     * @return HWPFile 객체
     * @throws Exception 파일을 읽는 도중 오류가 발생한 경우
     */
    public static HWPFile fromInputStream(InputStream is) throws Exception {
        byte[] data = readAll(is);
        return fromBytes(data);
    }

    /**
     * HWPML 파일의 전체 바이트를 읽는다.
     *
     * @param data 파일 전체 바이트
     * @return HWPFile 객체
     * @throws Exception XML이 아니거나 파싱 오류가 발생한 경우
     */
    public static HWPFile fromBytes(byte[] data) throws Exception {
        if (!isHWPML(data)) {
            throw new Exception("Not a HWPML file. (root element mismatch)");
        }
        HWPFile hwpFile = new HWPFile();
        XMLStreamReader xr = createStreamReader(new ByteArrayInputStream(data));
        try {
            new HWPMLReader().parse(xr, hwpFile);
        } finally {
            xr.close();
        }
        return hwpFile;
    }

    /**
     * 주어진 바이트가 HWPML 형식인지 확인한다.
     *
     * @param data 파일 앞부분 바이트
     * @return HWPML이면 true
     */
    public static boolean isHWPML(byte[] data) {
        return FormatDetector.detect(data) == FileFormat.HWPML;
    }

    /**
     * 문서 루트를 순회하며 HEAD/BODY를 처리한다.
     */
    private void parse(XMLStreamReader xr, HWPFile hwpFile) throws Exception {
        BodyText bodyText = hwpFile.getBodyText();
        while (xr.hasNext()) {
            int event = xr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = xr.getLocalName();
                if ("HEAD".equals(name)) {
                    ForHead.read(xr, hwpFile.getDocInfo());
                } else if ("SECTION".equals(name)) {
                    readSection(bodyText.addNewSection(), xr);
                }
            }
        }
    }

    /**
     * {@code <SECTION>} 시작에 위치한 상태에서 호출. {@code </SECTION>}까지 문단을 읽는다.
     */
    private void readSection(Section section, XMLStreamReader xr) throws Exception {
        boolean first = true;
        while (xr.hasNext()) {
            int event = xr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("P".equals(xr.getLocalName())) {
                    Paragraph p = readParagraph(section, xr);
                    if (first) {
                        // TextExtractor가 구역 첫 문단의 컨트롤 리스트(구역 정의)를 참조하므로 비어 있어도 만들어 둔다.
                        if (p.getControlList() == null) {
                            p.createControlList();
                        }
                        first = false;
                    }
                } else {
                    skipSubtree(xr);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "SECTION".equals(xr.getLocalName())) {
                break;
            }
        }
    }

    /**
     * {@code <P>} 시작에 위치한 상태에서 호출. {@code </P>}까지 글자/표를 읽어 문단을 만든다.
     *
     * @return 생성된 문단
     */
    private Paragraph readParagraph(ParagraphListInterface container, XMLStreamReader xr) throws Exception {
        Paragraph para = container.addNewParagraph();
        para.createText();
        para.createCharShape();
        ParaText text = para.getText();
        ParaCharShape charShape = para.getCharShape();

        ParaHeader header = para.getHeader();
        header.setParaShapeId(intAttr(xr, "ParaShape", 0));
        header.setStyleId((short) intAttr(xr, "Style", 0));

        int charPos = 0;
        int lastCharShapeId = -1;
        int curCharShapeId = 0;

        while (xr.hasNext()) {
            int event = xr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = xr.getLocalName();
                if ("TEXT".equals(name)) {
                    curCharShapeId = intAttr(xr, "CharShape", lastCharShapeId < 0 ? 0 : lastCharShapeId);
                } else if ("CHAR".equals(name)) {
                    String t = stripTrailingLineTerminator(readElementText(xr));
                    if (!t.isEmpty()) {
                        if (curCharShapeId != lastCharShapeId) {
                            charShape.addParaCharShape(charPos, curCharShapeId);
                            lastCharShapeId = curCharShapeId;
                        }
                        int i = 0;
                        while (i < t.length()) {
                            int cp = t.codePointAt(i);
                            text.addNewNormalChar().setCode(cp);
                            charPos++;
                            i += Character.charCount(cp);
                        }
                    }
                } else if ("TABLE".equals(name)) {
                    if (curCharShapeId != lastCharShapeId) {
                        charShape.addParaCharShape(charPos, curCharShapeId);
                        lastCharShapeId = curCharShapeId;
                    }
                    addTableExtendChar(text);
                    charPos++;
                    readTable(para, xr);
                } else if (SHAPE_TYPE.containsKey(name)) {
                    if (curCharShapeId != lastCharShapeId) {
                        charShape.addParaCharShape(charPos, curCharShapeId);
                        lastCharShapeId = curCharShapeId;
                    }
                    addGsoExtendChar(text);
                    charPos++;
                    GsoControl gc = para.addNewGsoControl(SHAPE_TYPE.get(name));
                    readShape(gc, name, xr);
                } else {
                    // 각주/숨은설명 등 그 외 컨트롤은 아직 건너뛴다.
                    skipSubtree(xr);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "P".equals(xr.getLocalName())) {
                break;
            }
        }

        // 문단 끝에 문단 구분 문자(0x0d)를 추가한다.(중복 방지)
        text.addNewCharControlChar().setCode(0x0d);

        header.setCharacterCount(text.getCharSize());
        header.setCharShapeCount(charShape.getPositonShapeIdPairList().size());
        header.setLineAlignCount(0);
        return para;
    }

    /**
     * {@code <TABLE>} 시작에 위치한 상태에서 호출. 표 컨트롤을 만들고 {@code </TABLE>}까지 읽는다.
     * (글자 스트림의 확장 컨트롤 문자는 호출 전에 추가되어 있어야 한다 — 컨트롤과 1:1 순서 매칭)
     */
    private void readTable(Paragraph para, XMLStreamReader xr) throws Exception {
        ControlTable ct = (ControlTable) para.addNewControl(ControlType.Table);
        ct.getTable().setRowCount(intAttr(xr, "RowCount", 0));
        ct.getTable().setColumnCount(intAttr(xr, "ColCount", 0));
        ct.getTable().setBorderFillId(intAttr(xr, "BorderFill", 0));
        ct.getTable().setCellSpacing(intAttr(xr, "CellSpacing", 0));

        Row curRow = null;
        Cell curCell = null;
        while (xr.hasNext()) {
            int event = xr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = xr.getLocalName();
                if ("ROW".equals(name)) {
                    curRow = ct.addNewRow();
                } else if ("CELL".equals(name) && curRow != null) {
                    curCell = curRow.addNewCell();
                    setCellHeader(curCell, xr);
                } else if ("PARALIST".equals(name) && curCell != null) {
                    readParaList(curCell.getParagraphList(), xr);
                } else {
                    // SHAPEOBJECT, INSIDEMARGIN, CELLMARGIN 등은 건너뛴다.
                    skipSubtree(xr);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "TABLE".equals(xr.getLocalName())) {
                break;
            }
        }
    }

    /**
     * {@code <PARALIST>} 시작에 위치한 상태에서 호출. {@code </PARALIST>}까지 문단을 읽어 container에 추가한다.
     */
    private void readParaList(ParagraphListInterface container, XMLStreamReader xr) throws Exception {
        while (xr.hasNext()) {
            int event = xr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("P".equals(xr.getLocalName())) {
                    readParagraph(container, xr);
                } else {
                    skipSubtree(xr);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "PARALIST".equals(xr.getLocalName())) {
                break;
            }
        }
    }

    /**
     * 그리기 개체 shape 엘리먼트 시작에 위치한 상태에서 호출. 매칭되는 종료 태그까지 읽으며
     * 글상자 텍스트(DRAWTEXT)와 묶음(CONTAINER)의 자식 개체를 처리한다.
     */
    private void readShape(GsoControl gc, String shapeTag, XMLStreamReader xr) throws Exception {
        boolean isContainer = gc instanceof ControlContainer;
        while (xr.hasNext()) {
            int event = xr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = xr.getLocalName();
                if ("DRAWTEXT".equals(name)) {
                    readDrawText(gc, xr);
                } else if (SHAPE_TYPE.containsKey(name)) {
                    if (isContainer) {
                        GsoControl child = ((ControlContainer) gc).addNewChildControl(SHAPE_TYPE.get(name));
                        readShape(child, name, xr);
                    } else {
                        skipSubtree(xr);
                    }
                }
                // 그 외(DRAWINGOBJECT, SHAPECOMPONENT 등)는 건너뛰지 않고 그대로 진입하여
                // 하위의 DRAWTEXT를 찾는다.
            } else if (event == XMLStreamConstants.END_ELEMENT && shapeTag.equals(xr.getLocalName())) {
                break;
            }
        }
    }

    /**
     * {@code <DRAWTEXT>} 시작에 위치한 상태에서 호출. 글상자 문단(PARALIST→P)을 shape의
     * 텍스트 박스에 채운다.
     */
    private void readDrawText(GsoControl gc, XMLStreamReader xr) throws Exception {
        TextBox textBox = createTextBox(gc);
        while (xr.hasNext()) {
            int event = xr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("PARALIST".equals(xr.getLocalName()) && textBox != null) {
                    readParaList(textBox.getParagraphList(), xr);
                } else {
                    skipSubtree(xr);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "DRAWTEXT".equals(xr.getLocalName())) {
                break;
            }
        }
    }

    /**
     * 텍스트 박스를 가질 수 있는 shape이면 텍스트 박스를 생성하여 반환한다.(아니면 null)
     */
    private static TextBox createTextBox(GsoControl gc) {
        if (gc instanceof ControlRectangle) {
            ((ControlRectangle) gc).createTextBox();
            return ((ControlRectangle) gc).getTextBox();
        } else if (gc instanceof ControlEllipse) {
            ((ControlEllipse) gc).createTextBox();
            return ((ControlEllipse) gc).getTextBox();
        } else if (gc instanceof ControlArc) {
            ((ControlArc) gc).createTextBox();
            return ((ControlArc) gc).getTextBox();
        } else if (gc instanceof ControlPolygon) {
            ((ControlPolygon) gc).createTextBox();
            return ((ControlPolygon) gc).getTextBox();
        } else if (gc instanceof ControlCurve) {
            ((ControlCurve) gc).createTextBox();
            return ((ControlCurve) gc).getTextBox();
        }
        return null;
    }

    /**
     * 그리기 개체를 나타내는 확장 컨트롤 문자(code 0x0b, "gso ")를 글자 스트림에 추가한다.
     */
    private static void addGsoExtendChar(ParaText text) throws Exception {
        HWPCharControlExtend ch = text.addNewExtendControlChar();
        ch.setCode(0x000b);
        byte[] addition = new byte[12];
        addition[0] = ' ';
        addition[1] = 'o';
        addition[2] = 's';
        addition[3] = 'g';
        ch.setAddition(addition);
    }

    private static void setCellHeader(Cell cell, XMLStreamReader xr) {
        ListHeaderForCell lh = cell.getListHeader();
        lh.setColIndex(intAttr(xr, "ColAddr", 0));
        lh.setRowIndex(intAttr(xr, "RowAddr", 0));
        lh.setColSpan(intAttr(xr, "ColSpan", 1));
        lh.setRowSpan(intAttr(xr, "RowSpan", 1));
        lh.setWidth(longAttr(xr, "Width", 0));
        lh.setHeight(longAttr(xr, "Height", 0));
        lh.setBorderFillId(intAttr(xr, "BorderFill", 0));
    }

    /**
     * 표를 나타내는 확장 컨트롤 문자(code 0x0b, "tbl ")를 글자 스트림에 추가한다.
     * ({@code ParaText.addExtendCharForTable}과 달리 문단 구분 문자를 건드리지 않는다.)
     */
    private static void addTableExtendChar(ParaText text) throws Exception {
        HWPCharControlExtend ch = text.addNewExtendControlChar();
        ch.setCode(0x000b);
        byte[] addition = new byte[12];
        addition[0] = ' ';
        addition[1] = 'l';
        addition[2] = 'b';
        addition[3] = 't';
        ch.setAddition(addition);
    }

    /**
     * 현재 START 엘리먼트의 텍스트 내용을 읽고 해당 END 엘리먼트까지 소비한다.
     */
    private static String readElementText(XMLStreamReader xr) throws Exception {
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        while (xr.hasNext() && depth > 0) {
            int event = xr.next();
            switch (event) {
                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                    sb.append(xr.getText());
                    break;
                case XMLStreamConstants.START_ELEMENT:
                    depth++;
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    depth--;
                    break;
                default:
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * 현재 START 엘리먼트의 하위 트리 전체를 읽어 버린다(매칭되는 END까지).
     */
    private static void skipSubtree(XMLStreamReader xr) throws Exception {
        int depth = 1;
        while (xr.hasNext() && depth > 0) {
            int event = xr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private static String stripTrailingLineTerminator(String s) {
        if (s.endsWith("\r\n")) {
            return s.substring(0, s.length() - 2);
        }
        if (s.endsWith("\n") || s.endsWith("\r")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static int intAttr(XMLStreamReader xr, String name, int defaultValue) {
        String v = xr.getAttributeValue(null, name);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long longAttr(XMLStreamReader xr, String name, long defaultValue) {
        String v = xr.getAttributeValue(null, name);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static XMLStreamReader createStreamReader(InputStream is) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        // 보안: DTD / 외부 엔티티 비활성화 (XXE 방지)
        setPropertyQuietly(factory, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        setPropertyQuietly(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        return factory.createXMLStreamReader(is);
    }

    private static void setPropertyQuietly(XMLInputFactory factory, String name, Object value) {
        try {
            factory.setProperty(name, value);
        } catch (IllegalArgumentException ignore) {
            // 일부 구현은 해당 속성을 지원하지 않을 수 있다.
        }
    }

    private static byte[] readAll(InputStream is) throws Exception {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        } finally {
            is.close();
        }
    }
}
