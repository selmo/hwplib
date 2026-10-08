package kr.dogfoot.hwplib.tool.textextractor;

import kr.dogfoot.hwplib.object.bodytext.control.Control;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.rangetag.RangeTagItem;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.HWPChar;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.HWPCharNormal;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.ParaText;
import kr.dogfoot.hwplib.tool.textextractor.paraHead.ParaHeadMaker;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class ForParagraph {
    public static final int ParaStart = -1;
    public static final int ParaEnd = 0xffff;

    /**
     * startIndex 순번 부터 끝 순번 까지의 문단의 텍스트를 추출한다.
     *
     * @param p             문단
     * @param startIndex    시작 순번
     * @param tem           텍스트 추출 방법
     * @param paraHeadMaker 문단 번호/글머리표 생성기
     * @param sb            추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    public static void extract(Paragraph p,
                               int startIndex,
                               TextExtractMethod tem,
                               ParaHeadMaker paraHeadMaker,
                               StringBuilder sb) throws UnsupportedEncodingException {
        extract(p,
                startIndex, ParaEnd,
                false,
                new TextExtractOption(tem),
                paraHeadMaker,
                sb);
    }

    /**
     * startIndex 순번 부터 끝 순번 까지의 문단의 텍스트를 추출한다.
     *
     * @param p             문단
     * @param startIndex    시작 순번
     * @param option        추출 옵션
     * @param paraHeadMaker 문단 번호/글머리표 생성기
     * @param sb            추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    public static void extract(Paragraph p,
                               int startIndex,
                               TextExtractOption option,
                               ParaHeadMaker paraHeadMaker,
                               StringBuilder sb) throws UnsupportedEncodingException {
        extract(p,
                startIndex, ParaEnd,
                false,
                option,
                paraHeadMaker,
                sb);
    }

    /**
     * startIndex 순번 부터 endIndex 순번 까지의 문단의 텍스트를 추출한다.
     *
     * @param p             문단
     * @param startIndex    시작 순번
     * @param endIndex      끝 순번
     * @param tem           텍스트 추출 방법
     * @param paraHeadMaker 문단 번호/글머리표 생성기
     * @param sb            추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    public static void extract(Paragraph p,
                               int startIndex,
                               int endIndex,
                               TextExtractMethod tem,
                               ParaHeadMaker paraHeadMaker,
                               StringBuilder sb) throws UnsupportedEncodingException {
        extract(p,
                startIndex, endIndex,
                false,
                new TextExtractOption(tem),
                paraHeadMaker,
                sb);
    }

    /**
     * 문단의 텍스트를 추출한다.
     *
     * @param p             문단
     * @param tem           텍스트 추출 방법
     * @param paraHeadMaker 문단 번호/글머리표 생성기
     * @param sb            추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    public static void extract(Paragraph p,
                               TextExtractMethod tem,
                               ParaHeadMaker paraHeadMaker,
                               StringBuilder sb) throws UnsupportedEncodingException {
        extract(p,
                ParaStart, ParaEnd,
                false,
                new TextExtractOption(tem),
                paraHeadMaker,
                sb);
    }

    /**
     * startIndex 순번 부터 endIndex 순번 까지의 문단의 텍스트를 추출한다.
     *
     * @param p             문단
     * @param startIndex    시작 순번
     * @param endIndex      끝 순번
     * @param option        추출 옵션
     * @param paraHeadMaker 문단 번호/글머리표 생성기
     * @param sb            추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    public static void extract(Paragraph p,
                               int startIndex,
                               int endIndex,
                               boolean appendLF,
                               TextExtractOption option,
                               ParaHeadMaker paraHeadMaker,
                               StringBuilder sb) throws UnsupportedEncodingException {
        if (option.isInsertParaHead() && startIndex <= 0 && paraHeadMaker != null) {
            String head = paraHeadMaker.paraHeadString(p);
            if (head != null && head.length() > 0) {
                sb.append(head).append(" ");
            }
        }

        ArrayList<Control> controlList = new ArrayList<Control>();
        ParaText pt = p.getText();
        if (pt != null) {
            int controlIndex = 0;
            List<RangeTagItem> deletedRanges = option.isInsertTrackChangeDeletedText() ? null : deletedRanges(p);
            long position = 0;

            int charCount = pt.getCharList().size();
            for (int charIndex = 0; charIndex < charCount; charIndex++) {
                HWPChar ch = pt.getCharList().get(charIndex);
                boolean inRange = startIndex <= charIndex && charIndex <= endIndex
                        && !isDeleted(deletedRanges, position);
                position += ch.getCharSize();
                switch (ch.getType()) {
                    case Normal:
                        if (inRange) {
                            normalText(ch, sb);
                        }
                        break;
                    case ControlChar:
                    case ControlInline:
                        if (inRange) {
                            if (option.isWithControlChar()) {
                                controlText(ch, sb);
                            }
                        }
                        break;
                    case ControlExtend:
                        // 손상된 파일에서 확장 컨트롤 문자에 대응하는 컨트롤이 없으면 그 컨트롤만 건너뛴다.
                        if (inRange && p.getControlList() != null && controlIndex < p.getControlList().size()) {
                            Control control = p.getControlList().get(controlIndex);
                            if (option.isInsertAutoNumber() && control.getType() == ControlType.AutoNumber
                                    || option.getEquationFormat() == EquationFormat.LaTeX
                                    && control.getType() == ControlType.Equation) {
                                // 자동 번호("그림 1")와 LaTeX 수식("함수 $f(x)$에 대하여")은 문단 안 위치에 그대로 넣는다.
                                ForControl.extract(control, option, paraHeadMaker, sb);
                            } else if (option.getMethod() == TextExtractMethod.InsertControlTextBetweenParagraphText) {
                                sb.append("\n");
                                ForControl.extract(p.getControlList().get(controlIndex),
                                        option,
                                        paraHeadMaker,
                                        sb);
                            } else {
                                controlList.add(p.getControlList()
                                        .get(controlIndex));
                            }
                        }
                        controlIndex++;
                        break;
                    default:
                        break;
                }
            }
        }

        if (appendLF && option.isAppendEndingLF()) {
            sb.append("\n");
        }

        if (option.getMethod() == TextExtractMethod.AppendControlTextAfterParagraphText) {
            controls(controlList, option, paraHeadMaker, sb);
        }
    }


    /**
     * 변경 추적(교정)에서 삭제된 구간(범위 태그 종류 0x11)을 반환한다. 없으면 null.
     * 범위 태그 종류는 DocInfo 변경 추적 레코드의 종류와 같다. (0x10 삽입, 0x11 삭제, 0x12/0x13 서식 변경)
     */
    private static List<RangeTagItem> deletedRanges(Paragraph p) {
        if (p.getRangeTag() == null) {
            return null;
        }
        List<RangeTagItem> list = null;
        for (RangeTagItem item : p.getRangeTag().getRangeTagItemList()) {
            if (item.getSort() == TRACK_CHANGE_DELETE) {
                if (list == null) {
                    list = new ArrayList<RangeTagItem>();
                }
                list.add(item);
            }
        }
        return list;
    }

    private static final short TRACK_CHANGE_DELETE = 0x11;

    /**
     * 글자 위치(코드 단위)가 삭제된 구간에 속하는지 여부.
     */
    private static boolean isDeleted(List<RangeTagItem> deletedRanges, long position) {
        if (deletedRanges == null) {
            return false;
        }
        for (RangeTagItem item : deletedRanges) {
            if (item.getRangeStart() <= position && position < item.getRangeEnd()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 일반 문자에서 문자를 추출한다.
     *
     * @param ch 한글 문자
     * @param sb 추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    private static void normalText(HWPChar ch, StringBuilder sb) throws UnsupportedEncodingException {
        sb.append(((HWPCharNormal) ch).getCh());
    }

    private static void controlText(HWPChar ch, StringBuilder sb) {
        switch (ch.getCode()) {
            case 9:
                sb.append("\t");
                break;
            case 10:
                sb.append("\n");
                break;
            case 24:
                sb.append("_");
                break;
        }
    }

    /**
     * 컨트롤 리스트에 포함된 컨트롤에서 텍스트를 추출한다.
     *
     * @param controlList   컨트롤 리스트
     * @param option        추출 옵션
     * @param paraHeadMaker 문단 번호 생성기
     * @param sb            추출된 텍스트를 저정할 StringBuilder 객체
     * @throws UnsupportedEncodingException
     */
    private static void controls(ArrayList<Control> controlList,
                                 TextExtractOption option,
                                 ParaHeadMaker paraHeadMaker,
                                 StringBuilder sb) throws UnsupportedEncodingException {

        if (controlList != null) {
            for (Control c : controlList) {
                ForControl.extract(c, option, paraHeadMaker, sb);
            }
        }
    }
}

