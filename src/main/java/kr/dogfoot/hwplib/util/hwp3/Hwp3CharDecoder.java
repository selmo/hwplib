package kr.dogfoot.hwplib.util.hwp3;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 한글 3.x의 문자 내부 코드(hchar, 2바이트)를 유니코드 코드 포인트로 변환한다.
 *
 * <p>한글 3.x는 한글을 KS C 5601 <b>조합형(johab)</b> 5-5-5 비트 구조로 저장한다.
 * 최상위 비트(0x8000)가 켜져 있으면 한글이며, 상위부터 초성(5비트)/중성(5비트)/
 * 종성(5비트)으로 구성된다. 영문/숫자 등 ASCII는 0x0000~0x007F에 그대로 저장된다.</p>
 *
 * <p>한자·기호 등 0x8000 이상의 비(非)한글 2바이트 코드는 조합형→유니코드 매핑
 * 테이블({@code johab_symbols.bin}, 5,893개)로 변환한다. 0x0080~0x7FFF의 한컴 사적
 * graphic 코드 일부는 {@link #decodeExtra(int)}로 보정한다.</p>
 */
public class Hwp3CharDecoder {
    /** 조합형 초성 5비트 값 → 현대 초성 인덱스(0=ㄱ … 18=ㅎ). 없으면 -1. */
    private static final int[] CHO = new int[32];
    /** 조합형 중성 5비트 값 → 현대 중성 인덱스(0=ㅏ … 20=ㅣ). 없으면 -1. */
    private static final int[] JUNG = new int[32];
    /** 조합형 종성 5비트 값 → 현대 종성 인덱스(0=없음, 1=ㄱ … 27=ㅎ). 없으면 -1. */
    private static final int[] JONG = new int[32];

    /** 한자/기호 조합형 코드(오름차순 정렬). {@link #SYM_VALUES}와 인덱스 대응. */
    private static final int[] SYM_KEYS;
    /** {@link #SYM_KEYS}에 대응하는 유니코드 코드 포인트(BMP). */
    private static final char[] SYM_VALUES;

    static {
        for (int i = 0; i < 32; i++) {
            CHO[i] = -1;
            JUNG[i] = -1;
            JONG[i] = -1;
        }
        // 초성: 값 2~20 → 0~18
        for (int v = 2; v <= 20; v++) {
            CHO[v] = v - 2;
        }
        // 중성: 비연속 매핑. KS C 5601 조합형 중성 5비트 코드의 빈칸(gap)은
        // {8,9}, {16,17}, {24,25}에 있다. (값 18 = ㅚ 가 idx 11)
        int[] jungValues = {3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 18, 19, 20, 21, 22, 23, 26, 27, 28, 29};
        for (int idx = 0; idx < jungValues.length; idx++) {
            JUNG[jungValues[idx]] = idx;
        }
        // 종성: 값 1=없음, 이후 비연속(18 비어 있음)
        int[] jongValues = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29};
        for (int idx = 0; idx < jongValues.length; idx++) {
            JONG[jongValues[idx]] = idx;
        }

        int[] keys = new int[0];
        char[] values = new char[0];
        try (InputStream is = Hwp3CharDecoder.class.getResourceAsStream("johab_symbols.bin")) {
            if (is != null) {
                byte[] data = readAll(is);
                int n = data.length / 4;
                keys = new int[n];
                values = new char[n];
                DataInputStream dis = new DataInputStream(new java.io.ByteArrayInputStream(data));
                for (int i = 0; i < n; i++) {
                    keys[i] = dis.readUnsignedShort();
                    values[i] = (char) dis.readUnsignedShort();
                }
            }
        } catch (IOException ignore) {
            // 테이블 로드 실패 시 한자/기호는 변환되지 않는다(한글/ASCII는 계속 동작).
            keys = new int[0];
            values = new char[0];
        }
        SYM_KEYS = keys;
        SYM_VALUES = values;
    }

    /**
     * hchar 코드를 유니코드 코드 포인트로 변환한다.
     *
     * @param hchar 2바이트 문자 코드
     * @return 유니코드 코드 포인트. 변환할 수 없으면 -1.
     */
    public static int toCodePoint(int hchar) {
        hchar &= 0xFFFF;
        if (hchar < 0x80) {
            return hchar;
        }
        if (hchar >= 0x8000) {
            int cho = CHO[(hchar >> 10) & 0x1F];
            int jung = JUNG[(hchar >> 5) & 0x1F];
            int jong = JONG[hchar & 0x1F];
            if (cho >= 0 && jung >= 0 && jong >= 0) {
                return 0xAC00 + (cho * 21 + jung) * 28 + jong;
            }
            // 한글 조합 실패 → 한자/기호 테이블 조회
            int idx = binarySearch(SYM_KEYS, hchar);
            if (idx >= 0) {
                return SYM_VALUES[idx];
            }
            return -1;
        }
        // 0x0080 ~ 0x7FFF: 한컴 사적 graphic 코드 보정
        return decodeExtra(hchar);
    }

    /**
     * 한글(조합형) 코드인지 여부.
     */
    public static boolean isHangul(int hchar) {
        return (hchar & 0x8000) != 0 && CHO[(hchar >> 10) & 0x1F] >= 0
                && JUNG[(hchar >> 5) & 0x1F] >= 0 && JONG[hchar & 0x1F] >= 0;
    }

    /**
     * 0x0080~0x7FFF 영역의 한컴 사적 graphic 코드 → 유니코드 보정.
     *
     * <p>출처: rhwp(edwardkim/rhwp) {@code decode_hwp3_extra} — 한컴 HWP5 변환본과의
     * 교차 검증으로 도출된 매핑. PUA(사용자 영역) 코드포인트도 변환본 정합을 위해 보존한다.</p>
     *
     * @return 유니코드 코드 포인트. 매핑 없으면 -1.
     */
    private static int decodeExtra(int ch) {
        // 로마숫자 대문자 Ⅰ~Ⅹ: 0x3590~0x3599 → U+2160~U+2169
        if (ch >= 0x3590 && ch <= 0x3599) {
            return 0x2160 + (ch - 0x3590);
        }
        switch (ch) {
            case 0x301C: return 0xF080F; // 한컴 PUA — 굵은 가로선
            case 0x35E1: return 0x2500;  // ─ BOX DRAWINGS LIGHT HORIZONTAL
            case 0x303D: return 0xF0827; // 한컴 PUA
            case 0x3479: return 0x25B7;  // ▷ WHITE RIGHT-POINTING TRIANGLE
            case 0x347A: return 0x25B6;  // ▶ BLACK RIGHT-POINTING TRIANGLE
            case 0x3441: return 0x25A0;  // ■ BLACK SQUARE
            case 0x3366: return 0xF03C5; // 한컴 PUA — 글머리 prefix
            default: return -1;
        }
    }

    private static int binarySearch(int[] keys, int key) {
        int lo = 0, hi = keys.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int v = keys[mid];
            if (v < key) {
                lo = mid + 1;
            } else if (v > key) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return -1;
    }

    private static byte[] readAll(InputStream is) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(32768);
        byte[] buf = new byte[8192];
        int r;
        while ((r = is.read(buf)) != -1) {
            bos.write(buf, 0, r);
        }
        return bos.toByteArray();
    }
}
