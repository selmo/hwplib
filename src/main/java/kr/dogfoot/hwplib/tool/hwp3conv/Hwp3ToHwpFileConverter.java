package kr.dogfoot.hwplib.tool.hwp3conv;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.hwp3.HWP3File;

/**
 * 한글 3.x 전용 객체 모델({@link HWP3File})을 한글 5.0 객체 모델({@link HWPFile})로
 * 변환하는 객체.
 *
 * <p>변환은 best-effort이다. 3.x에만 존재하거나 5.0과 구조가 크게 다른 요소는 손실될 수
 * 있다. 변환을 통해 기존 다운스트림 도구(텍스트 추출기 등)를 재사용할 수 있다.</p>
 *
 * <p>현재 P0 단계의 골격이며, 실제 변환 로직은 이후 단계(P3)에서 구현한다.</p>
 */
public class Hwp3ToHwpFileConverter {
    /**
     * 한글 3.x 객체를 한글 5.0 객체로 변환한다.
     *
     * @param hwp3 한글 3.x 문서 객체
     * @return 변환된 한글 5.0 문서 객체
     */
    public static HWPFile convert(HWP3File hwp3) {
        // TODO(P3): HWP3File → HWPFile (DocInfo/BodyText/Section/Paragraph) 매핑
        throw new UnsupportedOperationException(
                "HWP 3.x to HWPFile conversion is not implemented yet (P0 skeleton).");
    }
}
