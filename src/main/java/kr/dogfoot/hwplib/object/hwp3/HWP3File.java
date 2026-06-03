package kr.dogfoot.hwplib.object.hwp3;

/**
 * 한글 3.x 레거시 바이너리 문서 파일을 나타내는 객체.
 *
 * <p>한글 5.0의 {@link kr.dogfoot.hwplib.object.HWPFile}과는 구조가 다르므로 별도의
 * 전용 객체 모델로 표현한다. 다운스트림 도구(텍스트 추출 등)에서 재사용하려면
 * {@code kr.dogfoot.hwplib.tool.hwp3conv.Hwp3ToHwpFileConverter}로 변환한다.</p>
 *
 * <p>규격: 「한글 문서 파일 형식 3.0 / HWPML」 revision 1.2, Part I (한글 3.x 문서 파일 구조).
 * 전체 구조(규격 표2)는 다음과 같다.</p>
 * <pre>
 *   파일 인식 정보   (30 바이트)
 *   문서 정보        (128 바이트, 압축/암호 플래그 포함)
 *   문서 요약        (1008 바이트)
 *   정보 블록 #0     (가변)
 *   글꼴 이름        (가변, 압축)
 *   스타일           (가변, 압축)
 *   문단 리스트      (가변, 압축)
 *   추가 정보 블록 #1 (가변, 압축)
 *   추가 정보 블록 #2 (가변)
 * </pre>
 *
 * <p>현재 구현 단계: C1(헤더 파싱 + 압축 해제 + 글꼴/스타일 섹션 구조 파악)까지.
 * 문단/글자 파싱(C2/C3)은 이후 단계에서 채운다.</p>
 */
public class HWP3File {
    /** 문서가 압축되어 저장되었는지 여부.(문서 정보 offset 124) */
    private boolean compressed;
    /** 암호가 걸린 문서인지 여부.(문서 정보 offset 96) */
    private boolean hasPassword;
    /** sub revision.(문서 정보 offset 125) */
    private int subRevision;
    /** 정보 블록(#0)의 길이(바이트).(문서 정보 offset 126) */
    private int infoBlockLength;

    /** 언어별 글꼴 개수.(한글/영문/한자/일어/기타/기호/사용자) */
    private int[] fontCountPerLanguage = new int[7];
    /** 스타일 개수. */
    private int styleCount;
    /** 파싱된 문단 개수.(본문 최상위 문단 리스트) */
    private int paragraphCount;

    public HWP3File() {
    }

    public boolean isCompressed() {
        return compressed;
    }

    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    public boolean hasPassword() {
        return hasPassword;
    }

    public void setHasPassword(boolean hasPassword) {
        this.hasPassword = hasPassword;
    }

    public int getSubRevision() {
        return subRevision;
    }

    public void setSubRevision(int subRevision) {
        this.subRevision = subRevision;
    }

    public int getInfoBlockLength() {
        return infoBlockLength;
    }

    public void setInfoBlockLength(int infoBlockLength) {
        this.infoBlockLength = infoBlockLength;
    }

    public int[] getFontCountPerLanguage() {
        return fontCountPerLanguage;
    }

    public int getStyleCount() {
        return styleCount;
    }

    public void setStyleCount(int styleCount) {
        this.styleCount = styleCount;
    }

    public int getParagraphCount() {
        return paragraphCount;
    }

    public void setParagraphCount(int paragraphCount) {
        this.paragraphCount = paragraphCount;
    }
}
