package kr.dogfoot.hwplib.object.hwp3;

import java.util.ArrayList;
import java.util.List;

/**
 * 한글 3.x 표의 셀 하나.
 *
 * <p>규격: 「한글 문서 파일 형식 3.0 / HWPML」 revision 1.2, Part I §10.6 표 42(셀 정보, 27바이트).</p>
 */
public class Hwp3Cell {
    /** 줄(행) 일련 번호. 0부터 시작.(셀 정보 offset 0) */
    private int row;
    /** 칸(열) 일련 번호. 0부터 시작.(셀 정보 offset 1) */
    private int col;
    /** 표 안에서 셀 위치 가로(hunit).(offset 4) */
    private int posX;
    /** 표 안에서 셀 위치 세로(hunit).(offset 6) */
    private int posY;
    /** 셀 크기 가로(hunit).(offset 8) */
    private int width;
    /** 셀 크기 세로(hunit).(offset 10) */
    private int height;
    /** 대각선/병합 플래그 바이트 원본.(offset 25) */
    private int mergeFlags;
    /** 기하 기반으로 계산된 그리드 행 인덱스.({@link Hwp3Table#computeGrid()} 이후 유효) */
    private int gridRow;
    /** 기하 기반으로 계산된 그리드 열 인덱스. */
    private int gridCol;
    /** 기하 기반으로 계산된 행 병합 범위.(1 이상) */
    private int gridRowSpan = 1;
    /** 기하 기반으로 계산된 열 병합 범위.(1 이상) */
    private int gridColSpan = 1;
    /** 셀의 문단들. */
    private final List<Hwp3Paragraph> paragraphs = new ArrayList<Hwp3Paragraph>();

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getCol() {
        return col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public int getPosX() {
        return posX;
    }

    public void setPosX(int posX) {
        this.posX = posX;
    }

    public int getPosY() {
        return posY;
    }

    public void setPosY(int posY) {
        this.posY = posY;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getMergeFlags() {
        return mergeFlags;
    }

    public void setMergeFlags(int mergeFlags) {
        this.mergeFlags = mergeFlags;
    }

    /**
     * 병합된 셀(다른 셀에 덮인 칸)인지 여부.(플래그 bit 4)
     */
    public boolean isMerged() {
        return (mergeFlags & 0x10) != 0;
    }

    /**
     * 병합 방향.(플래그 bit 5: false=가로, true=세로. {@link #isMerged()}일 때만 유효)
     */
    public boolean isMergeVertical() {
        return (mergeFlags & 0x20) != 0;
    }

    public int getGridRow() {
        return gridRow;
    }

    public void setGridRow(int gridRow) {
        this.gridRow = gridRow;
    }

    public int getGridCol() {
        return gridCol;
    }

    public void setGridCol(int gridCol) {
        this.gridCol = gridCol;
    }

    public int getGridRowSpan() {
        return gridRowSpan;
    }

    public void setGridRowSpan(int gridRowSpan) {
        this.gridRowSpan = gridRowSpan;
    }

    public int getGridColSpan() {
        return gridColSpan;
    }

    public void setGridColSpan(int gridColSpan) {
        this.gridColSpan = gridColSpan;
    }

    public List<Hwp3Paragraph> getParagraphs() {
        return paragraphs;
    }

    /**
     * 셀 문단 텍스트를 줄바꿈으로 이어 반환한다.
     */
    public String getText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(paragraphs.get(i).getText());
        }
        return sb.toString();
    }
}
