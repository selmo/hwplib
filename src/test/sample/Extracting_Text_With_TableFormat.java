package sample;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TableFormat;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractOption;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;

/**
 * 표를 구조화된 형식으로 렌더링하여 텍스트를 추출하는 예제.
 *
 * <p>{@link TextExtractOption#setTableFormat(TableFormat)}으로 표 렌더링 형식을 선택한다.</p>
 * <ul>
 *     <li>{@link TableFormat#None} : 기존 동작(셀 텍스트를 구분 없이 연결)</li>
 *     <li>{@link TableFormat#Delimited} : 셀 = 탭, 행 = 줄바꿈</li>
 *     <li>{@link TableFormat#Markdown} : 마크다운 표(병합 셀은 빈 칸). LLM/RAG 입력에 적합</li>
 * </ul>
 */
public class Extracting_Text_With_TableFormat {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("usage: Extracting_Text_With_TableFormat <file.hwp> [none|delimited|markdown]");
            return;
        }

        TableFormat format = TableFormat.Markdown;
        if (args.length >= 2) {
            if ("none".equalsIgnoreCase(args[1])) {
                format = TableFormat.None;
            } else if ("delimited".equalsIgnoreCase(args[1])) {
                format = TableFormat.Delimited;
            }
        }

        HWPFile hwpFile = HWPReader.fromFile(args[0]);

        TextExtractOption option = new TextExtractOption(TextExtractMethod.InsertControlTextBetweenParagraphText);
        option.setTableFormat(format);

        System.out.println(TextExtractor.extract(hwpFile, option));
    }
}
