package kr.dogfoot.hwplib.object.hwp3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * 한글 3.x의 표(컨트롤 코드 10) 구조.
 *
 * <p>규격: 「한글 문서 파일 형식 3.0 / HWPML」 revision 1.2, Part I §10.6.
 * 코드 10은 표뿐 아니라 텍스트박스/수식/버튼도 같은 자료 구조를 사용하므로
 * {@link #getBoxType()}으로 구분한다.</p>
 *
 * <p>셀 정보의 줄/칸 일련 번호(offset 0/1)는 내장 시트 기능용이라 채워지지 않는
 * 파일이 많다(한컴 변환기 생성 파일은 전부 0). 대신 셀의 위치/크기(hunit)가 그리드와
 * 병합을 정확히 인코딩하므로, {@link #computeGrid()}가 X/Y 경계 좌표를 수집해
 * 각 셀의 그리드 행/열 인덱스와 병합 범위(span)를 기하적으로 계산한다.</p>
 */
public class Hwp3Table {
    /** 박스 종류: 표. */
    public static final int BOX_TYPE_TABLE = 0;
    /** 박스 종류: 텍스트박스. */
    public static final int BOX_TYPE_TEXT_BOX = 1;
    /** 박스 종류: 수식. */
    public static final int BOX_TYPE_EQUATION = 2;
    /** 박스 종류: 버튼. */
    public static final int BOX_TYPE_BUTTON = 3;

    /** 박스 종류.(표 정보 offset 78) */
    private int boxType;
    /** 셀들(파일 저장 순서). */
    private final List<Hwp3Cell> cells = new ArrayList<Hwp3Cell>();
    /** 캡션 문단들. */
    private final List<Hwp3Paragraph> caption = new ArrayList<Hwp3Paragraph>();

    public int getBoxType() {
        return boxType;
    }

    public void setBoxType(int boxType) {
        this.boxType = boxType;
    }

    /**
     * 박스 종류가 표인지 여부.(텍스트박스/수식/버튼 제외)
     */
    public boolean isTable() {
        return boxType == BOX_TYPE_TABLE;
    }

    public List<Hwp3Cell> getCells() {
        return cells;
    }

    public List<Hwp3Paragraph> getCaption() {
        return caption;
    }

    /** {@link #computeGrid()}로 계산된 그리드 행 개수. */
    private int rowCount;
    /** {@link #computeGrid()}로 계산된 그리드 열 개수. */
    private int colCount;

    public int getRowCount() {
        return rowCount;
    }

    public int getColCount() {
        return colCount;
    }

    /**
     * 셀 위치/크기(hunit)로부터 그리드를 기하적으로 계산한다.
     * 각 셀의 {@code gridRow/gridCol/gridRowSpan/gridColSpan}과 표의 행/열 개수를 채운다.
     *
     * <p>행 경계 = 셀 시작 Y 좌표들의 집합, 열 경계 = 셀 시작 X 좌표들의 집합.
     * 셀의 span = 자신의 [시작, 시작+크기) 구간이 덮는 경계의 수.</p>
     */
    public void computeGrid() {
        if (cells.isEmpty()) {
            rowCount = 0;
            colCount = 0;
            return;
        }
        TreeSet<Integer> xsSet = new TreeSet<Integer>();
        TreeSet<Integer> ysSet = new TreeSet<Integer>();
        for (Hwp3Cell c : cells) {
            xsSet.add(c.getPosX());
            ysSet.add(c.getPosY());
        }
        List<Integer> xs = new ArrayList<Integer>(xsSet);
        List<Integer> ys = new ArrayList<Integer>(ysSet);

        for (Hwp3Cell c : cells) {
            int colIdx = Collections.binarySearch(xs, c.getPosX());
            int rowIdx = Collections.binarySearch(ys, c.getPosY());
            c.setGridCol(colIdx);
            c.setGridRow(rowIdx);
            c.setGridColSpan(countCovered(xs, colIdx, c.getPosX() + c.getWidth()));
            c.setGridRowSpan(countCovered(ys, rowIdx, c.getPosY() + c.getHeight()));
        }
        colCount = xs.size();
        rowCount = ys.size();
    }

    /**
     * 정렬된 경계 리스트에서 startIdx부터 end 좌표 전까지 덮이는 경계의 수.(최소 1)
     */
    private static int countCovered(List<Integer> boundaries, int startIdx, int end) {
        int n = 1;
        for (int i = startIdx + 1; i < boundaries.size() && boundaries.get(i) < end; i++) {
            n++;
        }
        return n;
    }
}
