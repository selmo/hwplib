package sample;

import kr.dogfoot.hwplib.object.hwp3.HWP3File;
import kr.dogfoot.hwplib.reader.hwp3.HWP3Reader;

import java.io.File;

/**
 * 한글 3.x(레거시 바이너리) 파일에서 텍스트를 추출하는 샘플.
 *
 * <p>한글 5.0(.hwp/CFB)·HWPML과 달리 한글 3.x는 "HWP Document File V3.00" 시그니처로
 * 시작하는 단일 바이너리다. {@link HWP3Reader}로 읽어 {@link HWP3File#getText()}로
 * 전체 평문 텍스트를 얻는다. (표 셀·각주·머리말 등 중첩 문단은 문서 순서로 포함된다.)</p>
 */
public class Extracting_Text_From_HWP3 {
    public static void main(String[] args) throws Exception {
        File dir = new File("src/test/resources/sample-docs/hwp3");
        File[] files = dir.listFiles((d, name) -> name.endsWith(".hwp"));
        if (files == null) {
            System.out.println("샘플 디렉터리가 없습니다: " + dir.getPath());
            return;
        }
        for (File f : files) {
            HWP3File hwp3 = HWP3Reader.fromFile(f);
            String text = hwp3.getText();
            System.out.println("==== " + f.getName() + " ====");
            System.out.println("문단 수: " + hwp3.getParagraphCount()
                    + ", 압축: " + hwp3.isCompressed()
                    + ", 글자 수: " + text.length());
            // 앞부분 일부만 미리보기
            System.out.println(text.substring(0, Math.min(200, text.length())));
            System.out.println();
        }
    }
}
