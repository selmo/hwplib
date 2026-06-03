package kr.dogfoot.hwplib.reader;

/**
 * 한글 문서 파일의 종류를 나타내는 열거형.
 *
 * <ul>
 *     <li>{@link #HWP5} : 한글 5.0 형식 (OLE/Compound File 컨테이너)</li>
 *     <li>{@link #HWP3} : 한글 3.x 레거시 바이너리 형식</li>
 *     <li>{@link #HWPML} : HWPML(XML) 형식</li>
 *     <li>{@link #UNKNOWN} : 판별할 수 없는 형식</li>
 * </ul>
 */
public enum FileFormat {
    HWP5,
    HWP3,
    HWPML,
    UNKNOWN
}
