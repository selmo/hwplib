package kr.dogfoot.hwplib.object.hwp3;

import java.util.Collections;
import java.util.List;

/**
 * 한글 3.x 문서의 문단 하나(추출된 텍스트 기준).
 *
 * <p>문단의 평문 텍스트를 보존한다. 표 셀·각주·머리말 등 컨테이너 컨트롤의 중첩
 * 문단은 문서 순서대로 별도의 {@code Hwp3Paragraph}로 평탄화되어 최상위 문단
 * 리스트에 함께 담긴다(하위 호환). 이 문단에 들어 있는 표(코드 10) 컨트롤의
 * <b>구조</b>(셀 줄/칸 주소, 병합, 셀별 문단)는 {@link #getTables()}로 별도 보존된다 —
 * 셀 문단 객체는 평탄화 리스트와 공유된다.</p>
 */
public class Hwp3Paragraph {
    /** 문단의 평문 텍스트. */
    private final String text;
    /** 이 문단에 포함된 표 구조들(코드 10, 등장 순서). 없으면 null. */
    private List<Hwp3Table> tables;

    public Hwp3Paragraph(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    /**
     * 이 문단에 포함된 표(텍스트박스/수식/버튼 포함) 구조 리스트.
     *
     * @return 표 리스트(등장 순서). 없으면 빈 리스트.
     */
    public List<Hwp3Table> getTables() {
        if (tables == null) {
            return Collections.emptyList();
        }
        return tables;
    }

    public void setTables(List<Hwp3Table> tables) {
        this.tables = (tables == null || tables.isEmpty()) ? null : tables;
    }
}
