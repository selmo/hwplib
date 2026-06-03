package kr.dogfoot.hwplib.util.hwp3;

/**
 * 한글 3.x의 문자 내부 코드(hchar, 2바이트)를 유니코드 코드 포인트로 변환한다.
 *
 * <p>한글 3.x는 한글을 KS C 5601 <b>조합형(johab)</b> 5-5-5 비트 구조로 저장한다.
 * 최상위 비트(0x8000)가 켜져 있으면 한글이며, 상위부터 초성(5비트)/중성(5비트)/
 * 종성(5비트)으로 구성된다. 영문/숫자 등 ASCII는 0x0000~0x007F에 그대로 저장된다.</p>
 *
 * <p>한자·기호 등 그 외 2바이트 코드의 변환은 이후 단계에서 보강한다.</p>
 */
public class Hwp3CharDecoder {
    /** 조합형 초성 5비트 값 → 현대 초성 인덱스(0=ㄱ … 18=ㅎ). 없으면 -1. */
    private static final int[] CHO = new int[32];
    /** 조합형 중성 5비트 값 → 현대 중성 인덱스(0=ㅏ … 20=ㅣ). 없으면 -1. */
    private static final int[] JUNG = new int[32];
    /** 조합형 종성 5비트 값 → 현대 종성 인덱스(0=없음, 1=ㄱ … 27=ㅎ). 없으면 -1. */
    private static final int[] JONG = new int[32];

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
        // 중성: 비연속 매핑
        int[] jungValues = {3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 26, 27, 28, 29};
        for (int idx = 0; idx < jungValues.length; idx++) {
            JUNG[jungValues[idx]] = idx;
        }
        // 종성: 값 1=없음, 이후 비연속(18 비어 있음)
        int[] jongValues = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29};
        for (int idx = 0; idx < jongValues.length; idx++) {
            JONG[jongValues[idx]] = idx;
        }
    }

    /**
     * hchar 코드를 유니코드 코드 포인트로 변환한다.
     *
     * @param hchar 2바이트 문자 코드
     * @return 유니코드 코드 포인트. 변환할 수 없으면 -1.
     */
    public static int toCodePoint(int hchar) {
        hchar &= 0xFFFF;
        if ((hchar & 0x8000) != 0) {
            int cho = CHO[(hchar >> 10) & 0x1F];
            int jung = JUNG[(hchar >> 5) & 0x1F];
            int jong = JONG[hchar & 0x1F];
            if (cho >= 0 && jung >= 0 && jong >= 0) {
                return 0xAC00 + (cho * 21 + jung) * 28 + jong;
            }
            return -1;
        }
        if (hchar < 0x80) {
            return hchar;
        }
        // TODO: 한자/기호(완성형 등) 변환
        return -1;
    }

    /**
     * 한글(조합형) 코드인지 여부.
     */
    public static boolean isHangul(int hchar) {
        return (hchar & 0x8000) != 0;
    }
}
