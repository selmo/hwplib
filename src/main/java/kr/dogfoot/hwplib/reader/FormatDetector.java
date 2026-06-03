package kr.dogfoot.hwplib.reader;

import java.nio.charset.StandardCharsets;

/**
 * 파일의 앞부분 바이트를 보고 한글 문서 파일의 종류({@link FileFormat})를 판별하는 객체.
 *
 * <ul>
 *     <li>HWP5 : OLE2/Compound File 시그니처(D0 CF 11 E0 A1 B1 1A E1)로 시작</li>
 *     <li>HWP3 : 텍스트 "HWP Document File V3.00"로 시작</li>
 *     <li>HWPML : (BOM 이후) "&lt;?xml" 또는 "&lt;HWPML"로 시작하는 XML</li>
 * </ul>
 */
public class FormatDetector {
    /**
     * OLE2/Compound File Binary 시그니처. 한글 5.0 파일의 컨테이너 형식.
     */
    private static final byte[] OLE_SIGNATURE = {
            (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1
    };

    /**
     * 한글 3.x 파일 인식 정보의 텍스트 시그니처.
     * 전체 인식 정보는 "HWP Document File V3.00 \x1a\1\2\3\4\5" (30바이트)이다.
     */
    public static final String HWP3_SIGNATURE_TEXT = "HWP Document File V3.00";

    private static final byte[] HWP3_SIGNATURE =
            HWP3_SIGNATURE_TEXT.getBytes(StandardCharsets.US_ASCII);

    /**
     * 판별을 위해 읽어야 하는 최소 바이트 수 권장값.
     */
    public static final int RECOMMENDED_HEAD_SIZE = 1024;

    /**
     * 파일 앞부분 바이트로 파일 형식을 판별한다.
     *
     * @param head 파일의 앞부분 바이트 (최소 {@link #RECOMMENDED_HEAD_SIZE}바이트 권장)
     * @return 판별된 파일 형식. 알 수 없으면 {@link FileFormat#UNKNOWN}
     */
    public static FileFormat detect(byte[] head) {
        if (head == null || head.length == 0) {
            return FileFormat.UNKNOWN;
        }
        if (startsWith(head, 0, OLE_SIGNATURE)) {
            return FileFormat.HWP5;
        }
        if (startsWith(head, 0, HWP3_SIGNATURE)) {
            return FileFormat.HWP3;
        }
        if (looksLikeHWPML(head)) {
            return FileFormat.HWPML;
        }
        return FileFormat.UNKNOWN;
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (data.length - offset < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * BOM과 인코딩(UTF-8 / UTF-16)을 고려하여 XML(HWPML)인지 느슨하게 판별한다.
     */
    private static boolean looksLikeHWPML(byte[] head) {
        int offset = skipBom(head);
        // UTF-16 형식은 ASCII 문자 사이에 0x00이 끼어 있으므로 0x00을 제거하고 검사한다.
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < head.length && sb.length() < 256; i++) {
            byte b = head[i];
            if (b == 0x00) {
                continue;
            }
            sb.append((char) (b & 0xFF));
        }
        String text = sb.toString().trim();
        String lower = text.toLowerCase();
        if (lower.startsWith("<?xml")) {
            // XML 선언 이후 어딘가에 <HWPML 루트가 있는지 확인한다.
            return lower.contains("<hwpml");
        }
        return lower.startsWith("<hwpml");
    }

    /**
     * UTF-8 / UTF-16 BOM 길이만큼 건너뛴 오프셋을 반환한다.
     */
    private static int skipBom(byte[] head) {
        if (head.length >= 3
                && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
            return 3; // UTF-8 BOM
        }
        if (head.length >= 2
                && ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xFE
                || (head[0] & 0xFF) == 0xFE && (head[1] & 0xFF) == 0xFF)) {
            return 2; // UTF-16 LE/BE BOM
        }
        return 0;
    }
}
