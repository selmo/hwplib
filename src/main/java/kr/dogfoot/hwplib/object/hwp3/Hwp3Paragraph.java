package kr.dogfoot.hwplib.object.hwp3;

/**
 * 한글 3.x 문서의 문단 하나(추출된 텍스트 기준).
 *
 * <p>C3 단계에서는 문단의 평문 텍스트를 보존한다. 표 셀·각주·머리말 등 컨테이너
 * 컨트롤의 중첩 문단은 문서 순서대로 별도의 {@code Hwp3Paragraph}로 평탄화되어
 * 최상위 문단 리스트에 함께 담긴다. (구조 보존 객체 모델·HWPFile 변환은 이후 단계)</p>
 */
public class Hwp3Paragraph {
    /** 문단의 평문 텍스트. */
    private final String text;

    public Hwp3Paragraph(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
