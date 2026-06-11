package kr.dogfoot.hwplib.tool.textextractor;

import kr.dogfoot.hwplib.object.bodytext.control.*;
import kr.dogfoot.hwplib.object.bodytext.control.gso.GsoControl;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Row;
import kr.dogfoot.hwplib.tool.textextractor.paraHead.ParaHeadMaker;

import java.io.UnsupportedEncodingException;

/**
 * 컨트롤을 위한 텍스트 추출기 객체
 *
 * @author neolord
 */
public class ForControl {
    /**
     * 컨트롤에서 텍스트를 추출한다.
     *
     * @param c             컨트롤
     * @param tem           텍스트 추출 방법
     * @param paraHeadMaker 문단 번호/글머리표 생성기
     * @param sb            추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    public static void extract(Control c,
                               TextExtractMethod tem,
                               ParaHeadMaker paraHeadMaker,
                               StringBuilder sb) throws UnsupportedEncodingException {
        extract(c, new TextExtractOption(tem), paraHeadMaker, sb);
    }

    /**
     * 컨트롤에서 텍스트를 추출한다.
     *
     * @param c             컨트롤
     * @param option        추출 옵션
     * @param paraHeadMaker 문단 번호/글머리표 생성기
     * @param sb            추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    public static void extract(Control c,
                               TextExtractOption option,
                               ParaHeadMaker paraHeadMaker,
                               StringBuilder sb) throws UnsupportedEncodingException {
        if (c.isField()) {
        } else {
            switch (c.getType()) {
                case Table:
                    table((ControlTable) c, option, paraHeadMaker, sb);
                    break;
                case Gso:
                    ForGso.extract((GsoControl) c, option, paraHeadMaker, sb);
                    break;
                case Equation:
                    equation((ControlEquation) c, sb);
                    break;
                case SectionDefine:
                    break;
                case ColumnDefine:
                    break;
                case Header:
                    header((ControlHeader) c, option, paraHeadMaker, sb);
                    break;
                case Footer:
                    footer((ControlFooter) c, option, paraHeadMaker, sb);
                    break;
                case Footnote:
                    footnote((ControlFootnote) c, option, paraHeadMaker, sb);
                    break;
                case Endnote:
                    endnote((ControlEndnote) c, option, paraHeadMaker, sb);
                    break;
                case AutoNumber:
                    break;
                case NewNumber:
                    break;
                case PageHide:
                    break;
                case PageOddEvenAdjust:
                    break;
                case PageNumberPosition:
                    break;
                case IndexMark:
                    break;
                case Bookmark:
                    break;
                case OverlappingLetter:
                    break;
                case AdditionalText:
                    additionalText((ControlAdditionalText) c, sb);
                    break;
                case HiddenComment:
                    hiddenComment((ControlHiddenComment) c, option, paraHeadMaker, sb);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 표 컨트롤에서 텍스트를 추출한다. {@link TextExtractOption#getTableFormat()}에 따라
     * 셀/행 구분자 또는 마크다운 표로 렌더링하며, 캡션 문단도 표 뒤에 추출한다.
     *
     * @param table         표 컨트롤
     * @param option        추출 옵션
     * @param paraHeadMaker 문단 번호/글머리표 생성기
     * @param sb            추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    private static void table(ControlTable table,
                              TextExtractOption option,
                              ParaHeadMaker paraHeadMaker,
                              StringBuilder sb) throws UnsupportedEncodingException {
        switch (option.getTableFormat()) {
            case Delimited:
            case Markdown:
                structuredTable(table, option, paraHeadMaker, sb);
                break;
            case None:
            default:
                for (Row r : table.getRowList()) {
                    for (Cell c : r.getCellList()) {
                        ForParagraphList.extract(c.getParagraphList(), option, paraHeadMaker, sb);
                    }
                }
                break;
        }

        if (table.getCaption() != null) {
            ForParagraphList.extract(table.getCaption().getParagraphList(), option, paraHeadMaker, sb);
        }
    }

    /**
     * 그리드를 복원할 수 있는 최대 행/열 수.(손상된 좌표 값 방어)
     */
    private static final int MAX_GRID_SIZE = 4096;

    /**
     * 셀 좌표(행/열 주소와 병합 범위)로 그리드를 복원하여 표를 렌더링한다.
     * 좌표가 유효하지 않으면(중복/범위 밖) 리스트 순서 기반으로 폴백한다.
     */
    private static void structuredTable(ControlTable table,
                                        TextExtractOption option,
                                        ParaHeadMaker paraHeadMaker,
                                        StringBuilder sb) throws UnsupportedEncodingException {
        boolean markdown = option.getTableFormat() == TableFormat.Markdown;

        String[][] grid = gridByCoordinates(table, option, paraHeadMaker, markdown);
        if (grid == null) {
            grid = gridByListOrder(table, option, paraHeadMaker, markdown);
        }
        if (grid.length == 0) {
            return;
        }

        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        for (int r = 0; r < grid.length; r++) {
            String[] row = grid[r];
            if (markdown) {
                sb.append('|');
                for (String cell : row) {
                    sb.append(' ').append(cell == null ? "" : cell).append(" |");
                }
                sb.append('\n');
                if (r == 0) {
                    sb.append('|');
                    for (int c = 0; c < row.length; c++) {
                        sb.append(" --- |");
                    }
                    sb.append('\n');
                }
            } else {
                for (int c = 0; c < row.length; c++) {
                    if (c > 0) {
                        sb.append('\t');
                    }
                    sb.append(row[c] == null ? "" : row[c]);
                }
                sb.append('\n');
            }
        }
    }

    /**
     * 셀의 행/열 주소와 병합 범위로 그리드를 만든다.
     *
     * @return 복원된 그리드. 좌표가 유효하지 않으면 null.
     */
    private static String[][] gridByCoordinates(ControlTable table,
                                                TextExtractOption option,
                                                ParaHeadMaker paraHeadMaker,
                                                boolean markdown) throws UnsupportedEncodingException {
        int rowCount = 0;
        int colCount = 0;
        for (Row r : table.getRowList()) {
            for (Cell c : r.getCellList()) {
                int row = c.getListHeader().getRowIndex();
                int col = c.getListHeader().getColIndex();
                int rowSpan = Math.max(1, c.getListHeader().getRowSpan());
                int colSpan = Math.max(1, c.getListHeader().getColSpan());
                if (row < 0 || col < 0 || row + rowSpan > MAX_GRID_SIZE || col + colSpan > MAX_GRID_SIZE) {
                    return null;
                }
                rowCount = Math.max(rowCount, row + rowSpan);
                colCount = Math.max(colCount, col + colSpan);
            }
        }
        if (rowCount == 0 || colCount == 0) {
            return null;
        }

        String[][] grid = new String[rowCount][colCount];
        for (Row r : table.getRowList()) {
            for (Cell c : r.getCellList()) {
                int row = c.getListHeader().getRowIndex();
                int col = c.getListHeader().getColIndex();
                if (grid[row][col] != null) {
                    return null; // 좌표 충돌 → 리스트 순서 폴백
                }
                grid[row][col] = cellText(c, option, paraHeadMaker, markdown);
                // 병합으로 덮인 칸은 빈 셀로 표시
                int rowSpan = Math.max(1, c.getListHeader().getRowSpan());
                int colSpan = Math.max(1, c.getListHeader().getColSpan());
                for (int rr = row; rr < row + rowSpan; rr++) {
                    for (int cc = col; cc < col + colSpan; cc++) {
                        if (rr == row && cc == col) {
                            continue;
                        }
                        if (grid[rr][cc] != null) {
                            return null;
                        }
                        grid[rr][cc] = "";
                    }
                }
            }
        }
        return grid;
    }

    /**
     * 좌표를 신뢰할 수 없을 때, 행/셀 리스트 순서대로 그리드를 만든다.(병합 미반영)
     */
    private static String[][] gridByListOrder(ControlTable table,
                                              TextExtractOption option,
                                              ParaHeadMaker paraHeadMaker,
                                              boolean markdown) throws UnsupportedEncodingException {
        int rowCount = table.getRowList().size();
        String[][] grid = new String[rowCount][];
        for (int r = 0; r < rowCount; r++) {
            Row row = table.getRowList().get(r);
            grid[r] = new String[row.getCellList().size()];
            for (int c = 0; c < grid[r].length; c++) {
                grid[r][c] = cellText(row.getCellList().get(c), option, paraHeadMaker, markdown);
            }
        }
        return grid;
    }

    /**
     * 셀의 문단 텍스트를 한 칸짜리 문자열로 정규화한다.
     * (구분자 충돌 방지: 줄바꿈은 마크다운이면 &lt;br&gt;, 아니면 공백으로, 탭은 공백으로,
     * 마크다운이면 파이프를 이스케이프)
     */
    private static String cellText(Cell cell,
                                   TextExtractOption option,
                                   ParaHeadMaker paraHeadMaker,
                                   boolean markdown) throws UnsupportedEncodingException {
        StringBuilder cellSb = new StringBuilder();
        ForParagraphList.extract(cell.getParagraphList(), option, paraHeadMaker, cellSb);
        String text = cellSb.toString().replace("\r", "").trim();
        text = text.replace("\t", " ");
        if (markdown) {
            text = text.replace("|", "\\|").replace("\n", "<br>");
        } else {
            text = text.replace('\n', ' ');
        }
        return text;
    }

    /**
     * 수식 컨트롤에서 텍스트를 추출한다
     *
     * @param equation 수식 컨트롤 객체
     * @param sb       추출된 텍스트를 저정할 StringBuilder 객체
     */
    private static void equation(ControlEquation equation, StringBuilder sb) {
        sb.append(equation.getEQEdit().getScript().toUTF16LEString()).append("\n");
    }

    /**
     * 머리말 컨트롤에서 텍스트를 추출한다.
     *
     * @param header        머리말 컨트롤
     * @param option        추출 옵션
     * @param paraHeadMaker 문단 번호/글머리표 생성기
     * @param sb            추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    private static void header(ControlHeader header,
                               TextExtractOption option,
                               ParaHeadMaker paraHeadMaker,
                               StringBuilder sb) throws UnsupportedEncodingException {
        ForParagraphList.extract(header.getParagraphList(), option, paraHeadMaker, sb);
    }

    /**
     * 꼬리말 컨트롤에서 텍스트를 추출한다.
     *
     * @param footer        꼬리말 컨트롤
     * @param option        추출 옵션
     * @param paraHeadMaker 문단 번호/글머리표 생성기
     * @param sb            추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    private static void footer(ControlFooter footer,
                               TextExtractOption option,
                               ParaHeadMaker paraHeadMaker,
                               StringBuilder sb) throws UnsupportedEncodingException {
        ForParagraphList.extract(footer.getParagraphList(), option, paraHeadMaker, sb);
    }

    /**
     * 각주 컨트롤에서 텍스트를 추출한다.
     *
     * @param footnote      각주 컨트롤
     * @param option        추출 옵션
     * @param paraHeadMaker 문단 번호/글머리표 생성기
     * @param sb            추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    private static void footnote(ControlFootnote footnote,
                                 TextExtractOption option,
                                 ParaHeadMaker paraHeadMaker,
                                 StringBuilder sb) throws UnsupportedEncodingException {
        ForParagraphList.extract(footnote.getParagraphList(), option, paraHeadMaker, sb);
    }

    /**
     * 미주 컨트롤에서 텍스트를 추출한다.
     *
     * @param endnote       미주 컨트롤
     * @param option        추출 옵션
     * @param paraHeadMaker 문단 번호/글머리표 생성기
     * @param sb            추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    private static void endnote(ControlEndnote endnote,
                                TextExtractOption option,
                                ParaHeadMaker paraHeadMaker,
                                StringBuilder sb) throws UnsupportedEncodingException {
        ForParagraphList.extract(endnote.getParagraphList(), option, paraHeadMaker, sb);
    }

    /**
     * 덧말 컨트롤에서 텍스트를 추출한다.
     *
     * @param additionalText 덧말 컨트롤
     * @param sb             추출된 텍스트를 저정할 StringBuilder 객체
     */
    private static void additionalText(ControlAdditionalText additionalText,
                                       StringBuilder sb) {
        sb.append(additionalText.getHeader().getMainText()).append("\n");
        sb.append(additionalText.getHeader().getSubText()).append("\n");
    }

    /**
     * 숨은 설명 컨트롤에서 텍스트를 추출한다.
     *
     * @param hiddenComment 숨은 설명 컨트롤
     * @param option        추출 옵션
     * @param paraHeadMaker 문단 번호/글머리표 생성기
     * @param sb            추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    private static void hiddenComment(ControlHiddenComment hiddenComment,
                                      TextExtractOption option,
                                      ParaHeadMaker paraHeadMaker,
                                      StringBuilder sb) throws UnsupportedEncodingException {
        ForParagraphList.extract(hiddenComment.getParagraphList(), option, paraHeadMaker, sb);
    }
}
