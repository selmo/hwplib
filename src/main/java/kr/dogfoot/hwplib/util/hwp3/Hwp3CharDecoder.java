package kr.dogfoot.hwplib.util.hwp3;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * 한글 3.x의 문자 내부 코드(hchar, 2바이트)를 유니코드 코드 포인트로 변환한다.
 *
 * <p>한글 3.x는 한글을 KS C 5601 <b>조합형(johab)</b> 5-5-5 비트 구조로 저장한다.
 * 최상위 비트(0x8000)가 켜져 있으면 한글이며, 상위부터 초성(5비트)/중성(5비트)/
 * 종성(5비트)으로 구성된다. 영문/숫자 등 ASCII는 0x0000~0x007F에 그대로 저장된다.</p>
 *
 * <p>한자·기호 등 0x8000 이상의 비(非)한글 2바이트 코드는 조합형→유니코드 매핑
 * 테이블({@code johab_symbols.bin}, 5,893개)로 변환한다. 0x0080~0x7FFF의 한컴 사적
 * graphic 코드(사적 기호, KS C 5601 기호/한자 좌표 코드)는 {@link #decodeExtra(int)}로 변환한다.</p>
 *
 * <p>{@link #decodeExtra(int)}의 매핑 표와 KS C 5601 좌표 규칙은 rhwp
 * (https://github.com/edwardkim/rhwp, {@code src/parser/hwp3/johab.rs})에서 이식했다.</p>
 * <pre>
 * MIT License
 *
 * Copyright (c) 2025-2026 Edward Kim
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * </pre>
 */
public class Hwp3CharDecoder {
    /** 조합형 초성 5비트 값 → 현대 초성 인덱스(0=ㄱ … 18=ㅎ). 없으면 -1. */
    private static final int[] CHO = new int[32];
    /** 조합형 중성 5비트 값 → 현대 중성 인덱스(0=ㅏ … 20=ㅣ). 없으면 -1. */
    private static final int[] JUNG = new int[32];
    /** 조합형 종성 5비트 값 → 현대 종성 인덱스(0=없음, 1=ㄱ … 27=ㅎ). 없으면 -1. */
    private static final int[] JONG = new int[32];

    private static final Charset EUC_KR = Charset.forName("EUC-KR");

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
     * 0x0080~0x7FFF 영역의 한컴 사적 graphic 코드 → 유니코드 변환.
     * <p>실측된 하드코딩 매핑을 먼저 보고, 없으면 KS C 5601 완성형 좌표 규칙
     * (기호 {@link #decodeKscSymbol(int)} → 한자 {@link #decodeKscHanja(int)})을 적용한다.
     * 순서가 중요하다 — 예컨대 0x37C0~0x37C5(회사명 graphic)는 기호 규칙으로는 가타카나가 된다.
     * PUA(사용자 영역) 코드포인트는 한컴 HWP5 변환본과의 정합을 위해 보존한다.</p>
     * <p>출처: rhwp(edwardkim/rhwp, MIT) {@code src/parser/hwp3/johab.rs}
     * {@code decode_hwp3_extra} (commit 1a76570e83) — 한컴 HWP5 변환본/한글 오라클과의 실측 대조로
     * 도출된 매핑. 파일 상단의 라이선스 고지 참고.</p>
     * @return 유니코드 코드 포인트. 매핑 없으면 -1.
     */
    private static int decodeExtra(int ch) {
        // 라틴 확장(Latin-1 Supplement): 유니코드 값을 그대로 담는다. (ü, ä, ö, ß 등)
        if (ch >= 0x00A0 && ch <= 0x00FF) {
            return ch;
        }
        // 로마숫자 대문자 Ⅰ~Ⅹ: 0x3590~0x3599 → U+2160~U+2169
        if (ch >= 0x3590 && ch <= 0x3599) {
            return 0x2160 + (ch - 0x3590);
        }
        // 원문자 ①~⑩
        if (ch >= 0x36E7 && ch <= 0x36F0) {
            return 0x2460 + (ch - 0x36E7);
        }
        // 회사명 graphic("한글과컴퓨터") → 한컴 PUA
        if (ch >= 0x37C0 && ch <= 0x37C5) {
            return 0xF03EF + (ch - 0x37C0);
        }
        switch (ch) {
            case 0x0081: return 0x201C; // “
            case 0x0082: return 0x201D; // ”
            case 0x0083: return 0x2018; // ‘
            case 0x0084: return 0x2019; // ’
            case 0x301E: return 0xF0811; // 한컴 PUA - 관계도 가지 선문자
            case 0x301C: return 0xF080F; // 한컴 PUA - 굵은 가로선
            case 0x3024: return 0xF0817; // 한컴 PUA - 관계도 하단 가지 선문자
            case 0x3027: return 0xF081A; // 한컴 PUA - 관계도 가로 선문자
            case 0x3404: return 0x2024;  // ․ 한 점 리더
            case 0x3446: return 0x2192;  // →
            case 0x35E1: return 0x2500;  // ─
            case 0x303D: return 0xF0827; // 한컴 PUA
            case 0x3479: return 0x25B7;  // ▷
            case 0x347A: return 0x25B6;  // ▶
            case 0x3441: return 0x25A0;  // ■
            case 0x2F67: return 0x25B8;  // ▸ 표 셀 글머리표
            case 0x2F00: return 0x25A1;  // □ 빈 체크박스
            case 0x3366: return 0xF03C5; // 한컴 PUA - 글머리 prefix
            case 0x203B: return 0x203B;  // ※
            // 값별 실측으로 확인된 항등 매핑 (구간 항등이 아니다)
            case 0x2010: return 0x2010;  // ‐
            case 0x2013: return 0x2013;  // –
            case 0x2103: return 0x2103;  // ℃
            case 0x2113: return 0x2113;  // ℓ
            case 0x2190: return 0x2190;  // ←
            case 0x2192: return 0x2192;  // →
            case 0x2193: return 0x2193;  // ↓
            case 0x2219: return 0x2219;  // ∙
            case 0x22C5: return 0x22C5;  // ⋅
            // 항등이 아닌 값
            case 0x2024: return 0x30FB;  // ・
            case 0x2058: return 0x25B3;  // △
            case 0x205A: return 0x25CB;  // ○
            case 0x2F08: return 0x25AA;  // ▪
            case 0x2F11: return 0x25E6;  // ◦
            case 0x2F14: return 0x25E6;  // ◦
            case 0x3157: return 0x2027;  // ‧
            case 0x0480: return 0x02D0;  // ː
            // 일본어 글자·홑낫표
            case 0x1F2E: return 0x306E;  // の
            case 0x32B0: return 0xFF70;  // ｰ
            case 0x3067: return 0x2018;  // ‘
            case 0x3068: return 0x2019;  // ’
            case 0x309B: return 0xFF62;  // ｢
            case 0x309D: return 0xFF63;  // ｣
            // 텍스트 다이어그램 괘선 조각 → 한컴 PUA
            case 0x3013: return 0xF0806;
            case 0x3014: return 0xF0807;
            case 0x3015: return 0xF0808;
            case 0x3019: return 0xF080C;
            case 0x301B: return 0xF080E;
            case 0x301D: return 0xF0810;
            case 0x37ED: return 0x2550;  // ═ (기호 규칙으로는 가타카나가 된다)
            // 원문자·괄호문자 계열
            case 0x2E01: return 0x2460;  // ①
            case 0x2E02: return 0x2461;
            case 0x2E03: return 0x2462;
            case 0x2E04: return 0x2463;
            case 0x2E05: return 0x2464;
            case 0x2E06: return 0x2465;
            case 0x2E07: return 0x2466;  // ⑦
            case 0x2E00: return 0xF0288;
            case 0x2E0A: return 0xF0289;
            case 0x2E0B: return 0xF028A;
            case 0x2E0D: return 0xF028C;
            case 0x2E0E: return 0xF028D;
            case 0x2E0F: return 0xF028E;
            case 0x2E10: return 0xF028F;
            case 0x2E11: return 0xF0290;
            case 0x2E12: return 0xF0291;
            case 0x2C21: return 0x24D0;  // ⓐ
            case 0x2C22: return 0x24D1;
            case 0x2C23: return 0x24D2;
            case 0x2C24: return 0x24D3;
            case 0x2C25: return 0x24D4;
            case 0x2C26: return 0x24D5;  // ⓕ
            case 0x2C40: return 0x3260;  // ㉠
            case 0x2C41: return 0x3261;
            case 0x2C42: return 0x3262;
            // 글머리표·장식
            case 0x2022: return 0x2022;  // •
            case 0x2F17: return 0x2022;  // •
            case 0x2F06: return 0x25A0;  // ■
            case 0x25F5: return 0xF0099;
            case 0x2BCE: return 0xF012B; // 관인·서명란 도장 기호
            case 0x3048: return 0xF0832;
            default:
                break;
        }
        int cp = decodeKscSymbol(ch);
        if (cp < 0) {
            cp = decodeKscHanja(ch);
        }
        return cp;
    }

    /**
     * HWP3 기호 영역: KS C 5601 기호행(0xA1~0xAC)의 좌표를 행 간격 96으로 편 코드(기준 0x3401).
     *
     * @return 유니코드 코드 포인트. 해당 없으면 -1.
     */
    private static int decodeKscSymbol(int ch) {
        if (ch < 0x3401) {
            return -1;
        }
        int idx = ch - 0x3401;
        int row = 0xA1 + idx / 96;
        int cell = 0xA1 + idx % 96;
        if (row > 0xAC) {
            return -1;
        }
        return ksc5601Char(row, cell);
    }

    /**
     * HWP3 한자 영역: KS C 5601 한자행(0xCA~0xFD)의 좌표를 행 간격 94로 편 코드(기준 0x4000).
     *
     * @return 유니코드 코드 포인트. 해당 없으면 -1.
     */
    private static int decodeKscHanja(int ch) {
        if (ch < 0x4000) {
            return -1;
        }
        int idx = ch - 0x4000;
        int row = 0xCA + idx / 94;
        int cell = 0xA1 + idx % 94;
        if (row > 0xFD) {
            return -1;
        }
        return ksc5601Char(row, cell);
    }

    /**
     * KS C 5601 완성형 좌표(EUC-KR 바이트)를 유니코드 한 글자로 푼다. 배정되지 않은 자리는 -1.
     */
    private static int ksc5601Char(int row, int cell) {
        if (row < 0xA1 || row > 0xFE || cell < 0xA1 || cell > 0xFE) {
            return -1;
        }
        String s = new String(new byte[]{(byte) row, (byte) cell}, EUC_KR);
        if (s.length() != 1 || s.charAt(0) == '\uFFFD') {
            return -1;
        }
        return hancomVariant(s.charAt(0));
    }

    /**
     * KS C 5601 표준 매핑과 한컴이 실제로 쓰는 코드포인트가 갈리는 자리를 보정한다.
     */
    private static int hancomVariant(char c) {
        switch (c) {
            case '\u223C': return 0xFF5E; // ∼ → ～ (0xA1AD)
            case '\u2299': return 0x25C9; // ⊙ → ◉ (0xA2C1)
            default: return c;
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
