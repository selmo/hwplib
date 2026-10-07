package kr.dogfoot.hwplib.reader.bodytext.paragraph.control;

import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Row;
import kr.dogfoot.hwplib.object.etc.HWPTag;
import kr.dogfoot.hwplib.reader.bodytext.paragraph.control.bookmark.ForCtrlData;
import kr.dogfoot.hwplib.reader.bodytext.paragraph.control.gso.part.ForCaption;
import kr.dogfoot.hwplib.reader.bodytext.paragraph.control.gso.part.ForCtrlHeaderGso;
import kr.dogfoot.hwplib.reader.bodytext.paragraph.control.tbl.ForCell;
import kr.dogfoot.hwplib.reader.bodytext.paragraph.control.tbl.ForTable;
import kr.dogfoot.hwplib.util.compoundFile.reader.StreamReader;

import java.io.IOException;
import java.util.ArrayList;

/**
 * 표 컨트롤을 읽기 위한 객체
 *
 * @author 박성균
 */
public class ForControlTable {
    /**
     * 표 컨트롤
     */
    private ControlTable table;
    /**
     * 스트림 리더
     */
    private StreamReader sr;

    /**
     * 생성자
     */
    public ForControlTable() {
    }

    /**
     * 표 컨트롤을 읽는다.
     *
     * @param table 표 컨트롤 객체
     * @param sr    스트림 리더
     * @throws Exception
     */
    public void read(ControlTable table, StreamReader sr) throws Exception {
        this.table = table;
        this.sr = sr;

        ctrlHeader();
        ctrlData();
        caption();
        table();
        rows();
    }

    /**
     * 표 컨트롤의 컨트롤 헤더 레코드를 읽는다.
     *
     * @throws IOException
     */
    private void ctrlHeader() throws IOException {
        ForCtrlHeaderGso.read(table.getHeader(), sr);
    }


    /**
     * 컨트롤 데이터를 읽는다.
     *
     * @throws Exception
     */
    private void ctrlData() throws Exception {
        sr.readRecordHeader();
        if (sr.getCurrentRecordHeader().getTagID() == HWPTag.CTRL_DATA) {
            table.createCtrlData();
            ForCtrlData.read(table.getCtrlData(), sr);
        }
    }


    /**
     * 캡션 정보를 읽는다.
     *
     * @throws Exception
     */
    private void caption() throws Exception {
        if (sr.isImmediatelyAfterReadingHeader() == false) {
            sr.readRecordHeader();
        }
        if (sr.getCurrentRecordHeader().getTagID() == HWPTag.LIST_HEADER) {
            table.createCaption();
            ForCaption.read(table.getCaption(), sr);
        }
    }

    /**
     * 표 정보 레코드를 읽는다.
     *
     * @throws IOException
     */
    private void table() throws IOException {
        if (sr.isImmediatelyAfterReadingHeader() == false) {
            sr.readRecordHeader();
        }
        if (sr.getCurrentRecordHeader().getTagID() == HWPTag.TABLE) {
            // 표 레코드 크기가 스펙과 달라도(영역 속성 개수 누락 등) 다음 레코드 위치가 어긋나지 않도록
            // 레코드 범위로 제한해 읽는다.
            ForTable.read(table.getTable(), sr.recordBodyReader());
        }
    }

    /**
     * 행들을 읽는다.
     *
     * @throws Exception
     */
    private void rows() throws Exception {
        int rowCount = table.getTable().getRowCount();
        ArrayList<Integer> cellCountOfRow = table.getTable()
                .getCellCountOfRowList();
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            Row r = table.addNewRow();
            int readCount = row(r, cellCountOfRow.get(rowIndex));
            if (readCount < cellCountOfRow.get(rowIndex)) {
                // 표 레코드에 기록된 셀 개수보다 실제 셀이 적다. 실제 읽은 개수로 보정하고 표 읽기를 끝낸다.
                cellCountOfRow.set(rowIndex, readCount);
                for (int rest = rowIndex + 1; rest < rowCount; rest++) {
                    table.addNewRow();
                    cellCountOfRow.set(rest, 0);
                }
                break;
            }
        }
    }

    /**
     * 하나의 행 안에 셀들을 읽는다.
     *
     * @param r         행
     * @param cellCount 행에 포함된 셀 개수
     * @throws Exception
     */
    /**
     * 행의 셀들을 읽는다. 셀의 문단 리스트 헤더가 아닌 레코드가 나오면 멈추고,
     * 읽은 레코드 헤더는 다음 처리를 위해 남겨 둔다.
     *
     * @return 실제 읽은 셀 개수
     */
    private int row(Row r, int cellCount) throws Exception {
        for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
            if (sr.isImmediatelyAfterReadingHeader() == false) {
                sr.readRecordHeader();
            }
            if (sr.getCurrentRecordHeader().getTagID() != HWPTag.LIST_HEADER) {
                return cellIndex;
            }
            Cell c = r.addNewCell();
            ForCell.read(c, sr);
        }
        return cellCount;
    }
}
