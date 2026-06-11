package sample;

import kr.dogfoot.hwplib.object.hwp3.HWP3File;
import kr.dogfoot.hwplib.object.hwp3.Hwp3Cell;
import kr.dogfoot.hwplib.object.hwp3.Hwp3Table;
import kr.dogfoot.hwplib.reader.hwp3.HWP3Reader;

/**
 * 한글 3.x 파일에서 표 구조(셀 그리드 좌표·병합 범위)를 읽어 마크다운 표로 출력하는 예제.
 *
 * <p>셀의 그리드 행/열({@code getGridRow()/getGridCol()})과 병합 범위
 * ({@code getGridRowSpan()/getGridColSpan()})는 셀 위치/크기로부터 기하적으로
 * 계산된 값이다. 병합 셀은 anchor 칸에 텍스트가 들어가고 덮인 칸은 빈 칸이 된다.</p>
 */
public class Reading_HWP3_Tables {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("usage: Reading_HWP3_Tables <file.hwp (3.x)>");
            return;
        }

        HWP3File file = HWP3Reader.fromFile(args[0]);

        int index = 0;
        for (Hwp3Table table : file.getTables()) {
            System.out.println("===== 표 #" + index++ + " ("
                    + table.getRowCount() + "행 × " + table.getColCount() + "열) =====");
            System.out.println(toMarkdown(table));
        }
    }

    private static String toMarkdown(Hwp3Table table) {
        String[][] grid = new String[table.getRowCount()][table.getColCount()];
        for (Hwp3Cell cell : table.getCells()) {
            String text = cell.getText().trim()
                    .replace("|", "\\|").replace("\n", "<br>");
            grid[cell.getGridRow()][cell.getGridCol()] = text;
            for (int r = cell.getGridRow(); r < cell.getGridRow() + cell.getGridRowSpan(); r++) {
                for (int c = cell.getGridCol(); c < cell.getGridCol() + cell.getGridColSpan(); c++) {
                    if (grid[r][c] == null) {
                        grid[r][c] = "";
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < grid.length; r++) {
            sb.append('|');
            for (String cell : grid[r]) {
                sb.append(' ').append(cell == null ? "" : cell).append(" |");
            }
            sb.append('\n');
            if (r == 0) {
                sb.append('|');
                for (int c = 0; c < grid[r].length; c++) {
                    sb.append(" --- |");
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
