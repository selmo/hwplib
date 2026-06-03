package sample;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.reader.hwpml.HWPMLReader;

/**
 * HWPML(.hml) 파일을 읽어 본문 문단 텍스트를 출력하는 예제.
 *
 * <p>현재 구현 범위(M1): 본문 구역의 문단/글자만 매핑한다. 표/그림/각주 등 컨트롤
 * 내부 텍스트와 글꼴·서식(DocInfo)은 이후 단계에서 추가된다.</p>
 */
public class Reading_HWPML {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("usage: Reading_HWPML <file.hml>");
            return;
        }

        HWPFile hwpFile = HWPMLReader.fromFile(args[0]);

        for (Section section : hwpFile.getBodyText().getSectionList()) {
            for (Paragraph paragraph : section.getParagraphs()) {
                String text = paragraph.getNormalString();
                if (text != null) {
                    System.out.println(text);
                }
            }
        }
    }
}
