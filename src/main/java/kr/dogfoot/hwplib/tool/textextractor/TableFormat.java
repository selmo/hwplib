package kr.dogfoot.hwplib.tool.textextractor;

/**
 * 텍스트 추출 시 표 컨트롤의 렌더링 형식.
 */
public enum TableFormat {
    /**
     * 기존 동작. 셀 텍스트를 구분자 없이 순서대로 이어붙인다.
     */
    None,
    /**
     * 셀은 탭(\t), 행은 줄바꿈(\n)으로 구분한다. 셀 내부 줄바꿈은 공백으로 치환된다.
     */
    Delimited,
    /**
     * 마크다운 표 형식으로 렌더링한다. 셀 좌표(행/열 주소, 병합 범위)를 이용해
     * 그리드를 복원하며, 병합으로 덮인 칸은 빈 셀로 출력한다.
     */
    Markdown
}
