package kr.dogfoot.hwplib.reader;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * 파일의 앞부분 바이트를 보고 한글 문서 파일의 종류({@link FileFormat})를 판별하는 객체.
 *
 * <ul>
 *     <li>HWP5 : OLE2/Compound File 시그니처(D0 CF 11 E0 A1 B1 1A E1)로 시작</li>
 *     <li>HWP3 : 텍스트 "HWP Document File V3.00"로 시작</li>
 *     <li>HWPML : (BOM 이후) "&lt;?xml" 또는 "&lt;HWPML"로 시작하는 XML</li>
 *     <li>HWPX : ZIP 첫 항목이 "mimetype"(내용 "application/hwp+zip") 또는 HWPX 패키지 고유 경로</li>
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

    /**
     * HWPX 등 ZIP 기반 문서를 열려고 할 때의 안내 메시지.
     */
    public static final String ZIP_NOT_SUPPORTED_MESSAGE =
            "This file is a ZIP-based document (HWPX or OOXML), not HWP 5.0. "
                    + "HWPX is not supported by hwplib; use hwpxlib instead.";

    /**
     * ZIP 로컬 파일 헤더 시그니처(PK\3\4).
     */
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};

    /**
     * HWPX 패키지에만 있는 항목 경로의 접두어.
     */
    private static final String[] HWPX_ENTRY_PREFIXES = {"Contents/", "BinData/", "Preview/", "version.xml"};

    private static final byte[] HWPX_MIMETYPE = "application/hwp+zip".getBytes(StandardCharsets.US_ASCII);

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
        if (isHWPX(head)) {
            return FileFormat.HWPX;
        }
        return FileFormat.UNKNOWN;
    }

    /**
     * HWPX(OWPML) 패키지인지 확인한다. ZIP 첫 번째 항목으로 판단한다.
     * <ul>
     *     <li>"mimetype" 항목의 내용이 "application/hwp+zip" (비압축 또는 deflate 압축)</li>
     *     <li>"mimetype"이 첫 항목이 아니어도, 첫 항목이 HWPX 패키지 고유 경로(Contents/, BinData/, Preview/, version.xml)</li>
     * </ul>
     */
    private static boolean isHWPX(byte[] head) {
        if (!startsWith(head, 0, ZIP_SIGNATURE) || head.length < 30) {
            return false;
        }
        int method = (head[8] & 0xFF) | (head[9] & 0xFF) << 8;
        int compressedSize = (head[18] & 0xFF) | (head[19] & 0xFF) << 8 | (head[20] & 0xFF) << 16 | (head[21] & 0xFF) << 24;
        int nameLength = (head[26] & 0xFF) | (head[27] & 0xFF) << 8;
        int extraLength = (head[28] & 0xFF) | (head[29] & 0xFF) << 8;
        if (head.length < 30 + nameLength) {
            return false;
        }
        String name = new String(head, 30, nameLength, StandardCharsets.US_ASCII);
        if (name.equals("mimetype")) {
            int dataOffset = 30 + nameLength + extraLength;
            if (method == 0) {
                return startsWith(head, dataOffset, HWPX_MIMETYPE);
            } else if (method == 8) {
                return startsWith(inflate(head, dataOffset, compressedSize), 0, HWPX_MIMETYPE);
            }
            return false;
        }
        for (String prefix : HWPX_ENTRY_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * deflate로 압축된 데이터를 푼다. 실패하면 빈 배열을 반환한다.
     */
    private static byte[] inflate(byte[] data, int offset, int length) {
        if (offset >= data.length) {
            return new byte[0];
        }
        int available = Math.min(length > 0 ? length : data.length - offset, data.length - offset);
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(data, offset, available);
            byte[] out = new byte[64];
            int n = inflater.inflate(out);
            return Arrays.copyOf(out, n);
        } catch (DataFormatException e) {
            return new byte[0];
        } finally {
            inflater.end();
        }
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
