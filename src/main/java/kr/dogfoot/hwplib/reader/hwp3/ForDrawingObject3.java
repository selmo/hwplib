package kr.dogfoot.hwplib.reader.hwp3;

import kr.dogfoot.hwplib.object.hwp3.Hwp3Paragraph;
import kr.dogfoot.hwplib.util.hwp3.Hwp3StreamReader;

import java.util.List;

/**
 * 한글 3.x 그리기 개체(그리기 트리) 파서 — 글상자(텍스트 박스) 텍스트 복원(P4).
 *
 * <p>그림 컨트롤(특수문자 코드 11)의 확장 데이터에 담긴 그리기 개체 트리를 순회하여,
 * 글상자(개체 종류 6)의 문단 리스트 텍스트를 추출한다. 트리는 프레임 헤더 → (하이퍼텍스트
 * 정보) → 개체들(형제/자식 연결 정보로 연결)로 구성된다.</p>
 *
 * <p>이 파서는 그림 컨트롤의 확장 데이터 버퍼(서브 버퍼) 안에서만 동작하므로, 여기서
 * 정렬이 어긋나더라도 본문 글자 스트림에는 영향을 주지 않는다.</p>
 *
 * <p>레이아웃 출처: 공개 구현 rhwp(edwardkim/rhwp) {@code drawing.rs}.
 * 규격: 「한글 문서 파일 형식 3.0 / HWPML」 revision 1.2, Part I §11(그리기 개체).</p>
 */
public class ForDrawingObject3 {
    /** 프레임 헤더 크기: header_length·z_order·object_count(각 4) + bounds[4](16). */
    private static final int FRAME_HEADER_SIZE = 28;
    /** 하이퍼텍스트 정보 크기: length(4)+파일명(256)+책갈피(16)+매크로(325)+종류(1)+예약(3). */
    private static final int HYPERTEXT_INFO_SIZE = 605;
    /** 공통 헤더 고정부: header_length(4)+종류(2)+연결(2)+상대(8)+크기(8)+절대(8)+bounds(16). */
    private static final int COMMON_FIXED_SIZE = 48;
    /** 기본 속성 크기: 11 × dword. */
    private static final int BASIC_ATTR_SIZE = 44;

    /** 그리기 개체 최대 개수(손상 파일 방어). */
    private static final int MAX_OBJECTS = 4096;
    /** 트리 최대 깊이. */
    private static final int MAX_TREE_DEPTH = 32;

    /**
     * 그림 확장 데이터(그리기 트리)에서 글상자 텍스트 문단을 추출해 {@code out}에 추가한다.
     *
     * @param extBuf 그림 컨트롤의 확장 데이터 버퍼
     * @param out    추출된 글상자 문단을 담을 리스트
     * @param depth  본문 재귀 깊이(텍스트박스 내부 문단 파싱에 전달)
     */
    public static void extractTextBoxes(byte[] extBuf, List<Hwp3Paragraph> out, int depth) {
        try {
            Hwp3StreamReader sr = new Hwp3StreamReader(extBuf);
            long headerLength = sr.readUInt32(); // 프레임 헤더 길이
            sr.skip(FRAME_HEADER_SIZE - 4);      // 나머지 프레임 헤더(z_order/object_count/bounds)
            if (headerLength > 24) {
                sr.skip(HYPERTEXT_INFO_SIZE);    // 하이퍼텍스트 정보
            }
            parseShapeList(sr, out, depth, 0);
        } catch (ArrayIndexOutOfBoundsException truncated) {
            // 확장 데이터가 손상/절단된 경우 지금까지 추출한 텍스트로 종료(본문 영향 없음).
        }
    }

    private static void parseShapeList(Hwp3StreamReader sr, List<Hwp3Paragraph> out,
                                       int depth, int treeDepth) {
        if (treeDepth > MAX_TREE_DEPTH) {
            return;
        }
        int count = 0;
        while (!sr.isEndOfStream() && count++ < MAX_OBJECTS) {
            int connectionInfo = parseObject(sr, out, depth);
            if (connectionInfo < 0) {
                return; // 파싱 실패 → 이 레벨 종료
            }
            boolean hasChild = (connectionInfo & 0x02) != 0;
            boolean hasSibling = (connectionInfo & 0x01) != 0;
            if (hasChild) {
                parseShapeList(sr, out, depth, treeDepth + 1);
            }
            if (!hasSibling) {
                break;
            }
        }
    }

    /**
     * 개체 하나(공통 헤더 + 종류별 세부)를 읽는다. 글상자면 텍스트를 추출한다.
     *
     * @return 연결 정보(형제/자식 비트). 더 읽을 수 없으면 -1.
     */
    private static int parseObject(Hwp3StreamReader sr, List<Hwp3Paragraph> out, int depth) {
        if (sr.remaining() < COMMON_FIXED_SIZE + BASIC_ATTR_SIZE) {
            return -1;
        }
        sr.skip(4);                       // header_length
        int objectType = sr.readUInt16();
        int connectionInfo = sr.readUInt16();
        sr.skip(COMMON_FIXED_SIZE - 8);   // 상대/크기/절대 위치 + bounds

        // 기본 속성: options(11번째 dword)로 회전/그라데이션/비트맵 속성 유무 판단.
        sr.skip(BASIC_ATTR_SIZE - 4);
        long options = sr.readUInt32();
        if ((options & (1 << 17)) != 0) sr.skip(32);  // 회전 속성
        if ((options & (1 << 16)) != 0) sr.skip(28);  // 그라데이션 속성
        if ((options & (1 << 18)) != 0) sr.skip(278); // 비트맵 패턴 속성

        // 종류별 세부 정보.
        // 닫힌 도형(사각형/타원/글상자/호 등)은 [info1_len][중간][info2_len][문단 리스트]
        // 구조이며, info2의 문단 리스트가 도형 안 글상자 텍스트다. HWP3 정부문서의 글상자는
        // 글상자 전용 종류(6)가 아니라 사각형(2)에 텍스트를 담는 경우가 많다.
        switch (objectType) {
            case 0:  // 컨테이너(그룹): 세부 없음
                break;
            case 2:  // 사각형
            case 3:  // 타원
            case 6:  // 글상자
            case 9:  // 수정된 호
                extractClosedShapeText(sr, 0, out, depth);
                break;
            case 1:  // 선
            case 4:  // 호
                extractClosedShapeText(sr, 4, out, depth); // info1_len, shape_info, info2_len
                break;
            case 8:  // 수정된 타원
                extractClosedShapeText(sr, 16, out, depth); // info1_len, arc_bounds[4], info2_len
                break;
            case 5:  // 다각형
            case 7:  // 곡선
                skipPoints(sr, false);
                break;
            case 10: // 확장 곡선
            case 11: // 닫힌 다각형
                skipPoints(sr, true);
                break;
            default: { // 알 수 없음: info1(가변) + info2(가변)
                long info1Len = sr.readUInt32();
                if (info1Len < 0 || info1Len > sr.remaining()) return -1;
                sr.skip((int) info1Len);
                long info2Len = sr.readUInt32();
                if (info2Len < 0 || info2Len > sr.remaining()) return -1;
                sr.skip((int) info2Len);
                break;
            }
        }
        return connectionInfo;
    }

    /**
     * 닫힌 도형의 [info1_len][중간 바이트][info2_len][문단 리스트] 세부를 읽고,
     * info2의 문단 리스트(도형 안 글상자 텍스트)를 추출한다.
     *
     * @param middleBytes info1_len과 info2_len 사이의 바이트 수(예: 선/호=4, 수정된 타원=16)
     */
    private static void extractClosedShapeText(Hwp3StreamReader sr, int middleBytes,
                                               List<Hwp3Paragraph> out, int depth) {
        sr.readUInt32();          // info1_len (데이터 블록 없음)
        sr.skip(middleBytes);     // shape_info / arc_bounds 등
        long info2Len = sr.readUInt32();
        if (info2Len > 0 && info2Len <= sr.remaining()) {
            byte[] paraData = sr.readBytes((int) info2Len);
            Hwp3StreamReader psr = new Hwp3StreamReader(paraData);
            out.addAll(ForParagraphList3.parse(psr, depth));
        }
    }

    /** 다각형/곡선류 세부: info1_len + point_count + info2_len + points[pc][2] (+선속성[pc]). */
    private static void skipPoints(Hwp3StreamReader sr, boolean withLineAttrs) {
        sr.skip(4);                          // info1_len
        long pointCount = sr.readUInt32();
        sr.skip(4);                          // info2_len
        if (pointCount > 0 && pointCount < MAX_OBJECTS * 256L) {
            sr.skip((int) (pointCount * 8));  // points: 각 (i32 x, i32 y)
            if (withLineAttrs) {
                sr.skip((int) pointCount);    // line_attrs: 각 1바이트
            }
        }
    }
}
