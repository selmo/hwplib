package kr.dogfoot.hwplib.reader.hwp3;

import kr.dogfoot.hwplib.object.hwp3.Hwp3Paragraph;
import kr.dogfoot.hwplib.util.hwp3.Hwp3CharDecoder;
import kr.dogfoot.hwplib.util.hwp3.Hwp3StreamReader;

import java.util.ArrayList;
import java.util.List;

/**
 * 한글 3.x 문단 리스트 파서(C3, 텍스트 추출).
 *
 * <p>압축 해제된 본문 스트림에서 문단 정보 → 줄 정보 → (인라인 글자 모양) → 글자들
 * (hchar) 순서로 순회하며 텍스트를 복원한다. 표/그림/각주/머리말 등 컨테이너 컨트롤의
 * 중첩 문단은 재귀로 파싱하여 문서 순서대로 결과 리스트에 평탄화한다.</p>
 *
 * <p>제어문자별 바이트 소비량은 공개 구현 rhwp(edwardkim/rhwp)의
 * {@code parse_paragraph_list}를 참조해 확정했다. 일반형 제어문자(코드 5~17, 29 등)는
 * char_count 상 8바이트 헤더(=4 hchar 슬롯)만 차지하고, 본문(셀/중첩 문단/이미지 정보
 * 등)은 글자 스트림 바깥에서 별도로 읽힌다.</p>
 *
 * <p>규격: 「한글 문서 파일 형식 3.0 / HWPML」 revision 1.2, Part I §4(문단)·§10(특수문자).</p>
 */
public class ForParagraphList3 {
    /** Hwp3CharShape 직렬 크기(바이트). */
    private static final int CHAR_SHAPE_SIZE = 31;
    /** Hwp3ParaShape 직렬 크기(바이트). */
    private static final int PARA_SHAPE_SIZE = 187;
    /** Hwp3LineInfo 직렬 크기(바이트). */
    private static final int LINE_INFO_SIZE = 14;
    /** 중첩 재귀 최대 깊이(손상 파일 방어). */
    private static final int MAX_DEPTH = 64;

    /** 객체 대체 문자(표/그림 등 인라인 컨트롤 자리표시). */
    private static final char OBJECT_REPLACEMENT = '￼';

    /**
     * 문단 리스트를 끝(빈 문단)까지 읽어 텍스트 문단들을 반환한다.
     *
     * @param sr 본문 스트림 리더(현재 위치 = 문단 리스트 시작)
     * @return 문단 텍스트 리스트(중첩 컨테이너 문단 포함, 문서 순서)
     */
    public static List<Hwp3Paragraph> parse(Hwp3StreamReader sr) {
        return parse(sr, 0);
    }

    /**
     * 문단 리스트를 파싱한다(재귀 깊이 지정).
     *
     * @param sr    본문 스트림 리더(현재 위치 = 문단 리스트 시작)
     * @param depth 현재 재귀 깊이(그리기 트리 내 텍스트박스 등 중첩 호출용)
     * @return 문단 텍스트 리스트
     */
    public static List<Hwp3Paragraph> parse(Hwp3StreamReader sr, int depth) {
        List<Hwp3Paragraph> result = new ArrayList<Hwp3Paragraph>();
        parseInto(sr, result, depth);
        return result;
    }

    private static void parseInto(Hwp3StreamReader sr, List<Hwp3Paragraph> out, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        try {
            while (!sr.isEndOfStream()) {
                boolean terminator = parseParagraph(sr, out, depth);
                if (terminator) {
                    break; // char_count == 0 인 빈 문단 = 리스트 끝
                }
            }
        } catch (ArrayIndexOutOfBoundsException truncated) {
            // 스트림이 손상/절단된 경우 지금까지 읽은 내용으로 graceful 종료.
        }
    }

    /**
     * 문단 하나를 파싱한다.
     *
     * @return 빈 문단(리스트 종료 표식)이면 true
     */
    private static boolean parseParagraph(Hwp3StreamReader sr, List<Hwp3Paragraph> out, int depth) {
        int followPrev = sr.readUInt8();
        int charCount = sr.readUInt16();
        if (charCount == 0) {
            sr.skip(40); // 빈 문단은 총 43바이트(이미 3바이트 읽음)
            return true;
        }

        int lineCount = sr.readUInt16();
        int includeCharShape = sr.readUInt8();
        sr.readUInt8();        // flags
        sr.readUInt32();       // special_char_flags
        sr.readUInt8();        // style_index
        sr.skip(CHAR_SHAPE_SIZE); // rep_char_shape
        if (followPrev == 0) {
            sr.skip(PARA_SHAPE_SIZE); // para_shape
        }

        // 줄 정보
        sr.skip(lineCount * LINE_INFO_SIZE);

        // 인라인 글자 모양: char_count개의 flag, flag != 1 이면 31바이트 글자 모양
        if (includeCharShape != 0) {
            for (int c = 0; c < charCount; c++) {
                int flag = sr.readUInt8();
                if (flag != 1) {
                    sr.skip(CHAR_SHAPE_SIZE);
                }
            }
        }

        // 글자들(hchar) 순회
        StringBuilder text = new StringBuilder(charCount);
        List<Hwp3Paragraph> nested = new ArrayList<Hwp3Paragraph>();

        int i = 0;
        while (i < charCount) {
            int ch = sr.readUInt16();
            i++;

            if (ch > 0 && ch <= 31 && ch != 13) {
                i += consumeControl(sr, ch, text, nested, depth);
            } else if (ch != 0 && ch != 13) {
                int cp = Hwp3CharDecoder.toCodePoint(ch);
                if (cp >= 0) {
                    text.appendCodePoint(cp);
                }
                // cp < 0 (미지원 한자/기호 코드)는 건너뛴다.
            }
        }

        out.add(new Hwp3Paragraph(text.toString()));
        out.addAll(nested); // 컨테이너 중첩 문단을 문서 순서대로 평탄화
        return false;
    }

    /**
     * 제어문자(코드 1~31, 13 제외) 하나를 처리하고 추가로 소비한 hchar 슬롯 수를 반환한다.
     * (최초 1슬롯은 호출부에서 이미 i++ 처리)
     */
    private static int consumeControl(Hwp3StreamReader sr, int ch, StringBuilder text,
                                      List<Hwp3Paragraph> nested, int depth) {
        switch (ch) {
            case 30:   // 묶음 빈칸
            case 31: { // 고정 폭 빈칸
                sr.skip(2);
                text.append(ch == 30 ? ' ' : ' ');
                return 1; // 총 2 슬롯
            }
            case 24:   // 하이픈
            case 25: {
                sr.skip(4);
                text.append('-');
                return 2; // 총 3 슬롯
            }
            case 9: {  // 탭
                sr.skip(6);
                text.append('\t');
                return 3; // 총 4 슬롯(8바이트)
            }
            case 18:   // 자동 번호
            case 19:   // 새 번호
            case 20:   // 쪽 번호 위치
            case 21: { // 홀/짝 페이지
                sr.skip(6);
                text.append(ch == 18 ? ' ' : OBJECT_REPLACEMENT);
                return 3;
            }
            case 22: { // 메일 머지
                sr.skip(22);
                text.append(OBJECT_REPLACEMENT);
                return 11;
            }
            case 26: { // 찾아보기 표식
                sr.skip(244);
                text.append(OBJECT_REPLACEMENT);
                return 122;
            }
            case 28: { // 외곽 번호
                sr.skip(62);
                text.append(OBJECT_REPLACEMENT);
                return 31;
            }
            case 1: {  // 차례(TOC) 인라인 쪽번호
                long header = sr.readUInt32();
                int ch2 = sr.readUInt16();
                int d1 = (int) ((header >> 16) & 0xFFFF);
                boolean any = false;
                if (d1 >= 0x30 && d1 <= 0x39) { text.append((char) d1); any = true; }
                if (ch2 >= 0x30 && ch2 <= 0x39) { text.append((char) ch2); any = true; }
                if (!any) {
                    text.append(OBJECT_REPLACEMENT);
                }
                return 3;
            }
            default:
                // 일반형(8바이트 헤더 = ch + dword + ch). dword(=header_val1)와 닫기 ch 소비.
                return consumeGeneralControl(sr, ch, text, nested, depth);
        }
    }

    /**
     * 8바이트 헤더형 제어문자를 처리한다. 헤더 직후의 가변/고정 본문(셀·중첩 문단·이미지
     * 정보 등)을 코드별로 소비한다. 슬롯은 항상 헤더 4개(추가 3).
     */
    private static int consumeGeneralControl(Hwp3StreamReader sr, int ch, StringBuilder text,
                                             List<Hwp3Paragraph> nested, int depth) {
        long headerVal1 = sr.readUInt32(); // 자료구조 길이(코드에 따라 의미 다름)
        sr.readUInt16();                   // 닫기 코드(ch 반복)

        boolean inlineMarker = true;

        switch (ch) {
            case 10: { // 표 / 글상자 / 수식 / 버튼
                byte[] info = sr.readBytes(84);
                int cellCount = u16(info, 80);
                if (cellCount <= 0) {
                    cellCount = 1;
                }
                sr.skip(27 * cellCount);          // 셀 정보
                for (int c = 0; c < cellCount; c++) {
                    parseInto(sr, nested, depth + 1); // 셀 문단
                }
                parseInto(sr, nested, depth + 1);    // 캡션 문단
                break;
            }
            case 11: { // 그림 / 그리기 개체
                byte[] info = sr.readBytes(348);
                long nExt = u32(info, 0);
                int picType = info[74] & 0xFF;
                byte[] ext = (nExt > 0 && nExt < 64L * 1024 * 1024)
                        ? sr.readBytes((int) nExt) : new byte[0];
                // pic_type==3 = 그리기 개체. 확장 데이터(그리기 트리) 안 글상자 텍스트 복원.
                if (picType == 3 && ext.length > 0) {
                    ForDrawingObject3.extractTextBoxes(ext, nested, depth + 1);
                }
                parseInto(sr, nested, depth + 1); // 캡션 문단
                break;
            }
            case 14: { // 선
                sr.skip(84);
                break;
            }
            case 15: { // 숨은 설명
                sr.skip(8);
                parseInto(sr, nested, depth + 1);
                inlineMarker = false;
                break;
            }
            case 16: { // 머리말 / 꼬리말
                sr.skip(10);
                parseInto(sr, nested, depth + 1);
                inlineMarker = false;
                break;
            }
            case 17: { // 각주 / 미주
                sr.skip(14);
                parseInto(sr, nested, depth + 1);
                inlineMarker = false;
                break;
            }
            case 5: {  // 필드 코드(가변)
                if (headerVal1 > 0 && headerVal1 < 64L * 1024 * 1024) {
                    sr.skip((int) headerVal1);
                }
                break;
            }
            case 6: {  // 책갈피
                sr.skip(34);
                break;
            }
            case 7: {  // 날짜 형식
                sr.skip(76);
                break;
            }
            case 8: {  // 날짜 코드
                sr.skip(88);
                break;
            }
            case 29: { // 상호 참조
                if (headerVal1 > 0 && headerVal1 < 1000000) {
                    sr.skip((int) headerVal1);
                }
                break;
            }
            default:
                // 코드 0,2,3,4,12,23,27 등 예약/기타: 8바이트 헤더만 소비.
                break;
        }

        if (inlineMarker) {
            text.append(OBJECT_REPLACEMENT);
        }
        return 3; // 헤더 8바이트 = 4 슬롯(최초 1 + 추가 3)
    }

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static long u32(byte[] b, int off) {
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8)
                | ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }
}
