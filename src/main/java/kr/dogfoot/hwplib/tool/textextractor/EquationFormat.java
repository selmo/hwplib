package kr.dogfoot.hwplib.tool.textextractor;

/**
 * 텍스트 추출 시 수식 출력 형식
 */
public enum EquationFormat {
    /**
     * 한글 수식 스크립트를 그대로 출력한다. (기존 동작)
     */
    Script,
    /**
     * 한글 수식 스크립트를 LaTeX로 변환해 $...$로 감싸 출력한다. 변환에 실패하면 스크립트를 출력한다.
     */
    LaTeX
}
