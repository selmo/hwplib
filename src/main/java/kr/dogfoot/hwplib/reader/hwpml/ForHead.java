package kr.dogfoot.hwplib.reader.hwpml;

import kr.dogfoot.hwplib.object.docinfo.CharShape;
import kr.dogfoot.hwplib.object.docinfo.DocInfo;
import kr.dogfoot.hwplib.object.docinfo.FaceName;
import kr.dogfoot.hwplib.object.docinfo.IDMappings;
import kr.dogfoot.hwplib.object.docinfo.Numbering;
import kr.dogfoot.hwplib.object.docinfo.ParaShape;
import kr.dogfoot.hwplib.object.docinfo.Style;
import kr.dogfoot.hwplib.object.docinfo.numbering.LevelNumbering;
import kr.dogfoot.hwplib.object.docinfo.numbering.ParagraphNumberFormat;
import kr.dogfoot.hwplib.object.docinfo.parashape.Alignment;
import kr.dogfoot.hwplib.object.docinfo.parashape.ParaHeadShape;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * HWPML의 {@code <HEAD>} 영역(MAPPINGTABLE 등)을 {@link DocInfo}로 매핑하는 객체.
 *
 * <p>규격: 「한글 문서 파일 형식 3.0 / HWPML」 revision 1.2, Part II §4.</p>
 *
 * <p>현재 단계(M2)에서 매핑하는 항목: 글꼴(FONTFACE/FONT), 테두리/채우기(BORDERFILL,
 * 개수만), 글자 모양(CHARSHAPE), 탭(TABDEF, 개수만), 번호 정의(NUMBERING), 글머리표
 * (BULLET, 개수만), 문단 모양(PARASHAPE), 스타일(STYLE). 텍스트 추출에 필요한
 * 핵심 필드를 우선 채우고, 세부 속성은 이후 보강한다.</p>
 */
public class ForHead {
    /**
     * {@code <HEAD>} 시작 엘리먼트에 위치한 상태에서 호출한다. {@code </HEAD>}까지 읽어
     * {@link DocInfo}를 채운다.
     */
    public static void read(XMLStreamReader xr, DocInfo docInfo) throws Exception {
        CharShape curCharShape = null;
        ParaShape curParaShape = null;
        Numbering curNumbering = null;
        String curFaceLang = null;

        boolean capturingParaHead = false;
        int paraHeadLevel = 0;
        StringBuilder paraHeadText = new StringBuilder();

        while (xr.hasNext()) {
            int event = xr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = xr.getLocalName();
                if ("FONTFACE".equals(name)) {
                    curFaceLang = attr(xr, "Lang");
                } else if ("FONT".equals(name)) {
                    FaceName fn = addFaceName(docInfo, curFaceLang);
                    if (fn != null) {
                        fn.setName(attr(xr, "Name"));
                    }
                } else if ("BORDERFILL".equals(name)) {
                    docInfo.addNewBorderFill();
                } else if ("CHARSHAPE".equals(name)) {
                    curCharShape = docInfo.addNewCharShape();
                    curCharShape.setBaseSize(intAttr(xr, "Height", 1000));
                    curCharShape.getCharColor().setValue(longAttr(xr, "TextColor", 0));
                    curCharShape.setBorderFillId(intAttr(xr, "BorderFillId", 0));
                } else if ("FONTID".equals(name) && curCharShape != null) {
                    curCharShape.getFaceNameIds().setHangul(intAttr(xr, "Hangul", 0));
                    curCharShape.getFaceNameIds().setLatin(intAttr(xr, "Latin", 0));
                    curCharShape.getFaceNameIds().setHanja(intAttr(xr, "Hanja", 0));
                    curCharShape.getFaceNameIds().setJapanese(intAttr(xr, "Japanese", 0));
                    curCharShape.getFaceNameIds().setOther(intAttr(xr, "Other", 0));
                    curCharShape.getFaceNameIds().setSymbol(intAttr(xr, "Symbol", 0));
                    curCharShape.getFaceNameIds().setUser(intAttr(xr, "User", 0));
                } else if ("RATIO".equals(name) && curCharShape != null) {
                    curCharShape.getRatios().setHangul((short) intAttr(xr, "Hangul", 100));
                    curCharShape.getRatios().setLatin((short) intAttr(xr, "Latin", 100));
                    curCharShape.getRatios().setHanja((short) intAttr(xr, "Hanja", 100));
                    curCharShape.getRatios().setJapanese((short) intAttr(xr, "Japanese", 100));
                    curCharShape.getRatios().setOther((short) intAttr(xr, "Other", 100));
                    curCharShape.getRatios().setSymbol((short) intAttr(xr, "Symbol", 100));
                    curCharShape.getRatios().setUser((short) intAttr(xr, "User", 100));
                } else if ("CHARSPACING".equals(name) && curCharShape != null) {
                    curCharShape.getCharSpaces().setHangul((byte) intAttr(xr, "Hangul", 0));
                    curCharShape.getCharSpaces().setLatin((byte) intAttr(xr, "Latin", 0));
                    curCharShape.getCharSpaces().setHanja((byte) intAttr(xr, "Hanja", 0));
                    curCharShape.getCharSpaces().setJapanese((byte) intAttr(xr, "Japanese", 0));
                    curCharShape.getCharSpaces().setOther((byte) intAttr(xr, "Other", 0));
                    curCharShape.getCharSpaces().setSymbol((byte) intAttr(xr, "Symbol", 0));
                    curCharShape.getCharSpaces().setUser((byte) intAttr(xr, "User", 0));
                } else if ("RELSIZE".equals(name) && curCharShape != null) {
                    curCharShape.getRelativeSizes().setHangul((short) intAttr(xr, "Hangul", 100));
                    curCharShape.getRelativeSizes().setLatin((short) intAttr(xr, "Latin", 100));
                    curCharShape.getRelativeSizes().setHanja((short) intAttr(xr, "Hanja", 100));
                    curCharShape.getRelativeSizes().setJapanese((short) intAttr(xr, "Japanese", 100));
                    curCharShape.getRelativeSizes().setOther((short) intAttr(xr, "Other", 100));
                    curCharShape.getRelativeSizes().setSymbol((short) intAttr(xr, "Symbol", 100));
                    curCharShape.getRelativeSizes().setUser((short) intAttr(xr, "User", 100));
                } else if ("CHAROFFSET".equals(name) && curCharShape != null) {
                    curCharShape.getCharOffsets().setHangul((byte) intAttr(xr, "Hangul", 0));
                    curCharShape.getCharOffsets().setLatin((byte) intAttr(xr, "Latin", 0));
                    curCharShape.getCharOffsets().setHanja((byte) intAttr(xr, "Hanja", 0));
                    curCharShape.getCharOffsets().setJapanese((byte) intAttr(xr, "Japanese", 0));
                    curCharShape.getCharOffsets().setOther((byte) intAttr(xr, "Other", 0));
                    curCharShape.getCharOffsets().setSymbol((byte) intAttr(xr, "Symbol", 0));
                    curCharShape.getCharOffsets().setUser((byte) intAttr(xr, "User", 0));
                } else if ("BOLD".equals(name) && curCharShape != null) {
                    curCharShape.getProperty().setBold(true);
                } else if ("ITALIC".equals(name) && curCharShape != null) {
                    curCharShape.getProperty().setItalic(true);
                } else if ("TABDEF".equals(name)) {
                    docInfo.addNewTabDef();
                } else if ("NUMBERING".equals(name)) {
                    curNumbering = docInfo.addNewNumbering();
                    curNumbering.setStartNumber(intAttr(xr, "Start", 0));
                } else if ("PARAHEAD".equals(name) && curNumbering != null) {
                    paraHeadLevel = intAttr(xr, "Level", 1);
                    LevelNumbering lv = levelNumbering(curNumbering, paraHeadLevel);
                    if (lv != null) {
                        lv.setStartNumber(intAttr(xr, "Start", 1));
                        lv.getParagraphHeadInfo().getProperty()
                                .setParagraphNumberFormat(numberFormat(attr(xr, "NumFormat")));
                    }
                    capturingParaHead = true;
                    paraHeadText.setLength(0);
                } else if ("BULLET".equals(name)) {
                    docInfo.addNewBullet();
                } else if ("PARASHAPE".equals(name)) {
                    curParaShape = docInfo.addNewParaShape();
                    curParaShape.getProperty1().setAlignment(alignment(attr(xr, "Align")));
                    curParaShape.getProperty1().setParaHeadShape(paraHeadShape(attr(xr, "HeadingType")));
                    curParaShape.setParaHeadId(intAttr(xr, "Heading", 0));
                    curParaShape.setTabDefId(intAttr(xr, "TabDef", 0));
                    curParaShape.getProperty1().setParaLevel((byte) intAttr(xr, "Level", 0));
                } else if ("PARAMARGIN".equals(name) && curParaShape != null) {
                    curParaShape.setIndent(intAttr(xr, "Indent", 0));
                    curParaShape.setLeftMargin(intAttr(xr, "Left", 0));
                    curParaShape.setRightMargin(intAttr(xr, "Right", 0));
                    curParaShape.setTopParaSpace(intAttr(xr, "Prev", 0));
                    curParaShape.setBottomParaSpace(intAttr(xr, "Next", 0));
                    curParaShape.setLineSpace2(longAttr(xr, "LineSpacing", 0));
                } else if ("STYLE".equals(name)) {
                    Style st = docInfo.addNewStyle();
                    st.setHangulName(attr(xr, "Name"));
                    st.setEnglishName(attr(xr, "EngName"));
                    st.setParaShapeId(intAttr(xr, "ParaShape", 0));
                    st.setCharShapeId(intAttr(xr, "CharShape", 0));
                    st.setNextStyleId((short) intAttr(xr, "NextStyle", 0));
                    st.setLanguageId((short) intAttr(xr, "LangId", 0));
                }
            } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                if (capturingParaHead) {
                    paraHeadText.append(xr.getText());
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String name = xr.getLocalName();
                if ("PARAHEAD".equals(name) && curNumbering != null) {
                    LevelNumbering lv = levelNumbering(curNumbering, paraHeadLevel);
                    if (lv != null) {
                        lv.getNumberFormat().fromUTF16LEString(stripTrailingLineTerminator(paraHeadText.toString()));
                    }
                    capturingParaHead = false;
                } else if ("HEAD".equals(name)) {
                    break;
                }
            }
        }

        setCounts(docInfo);
    }

    private static FaceName addFaceName(DocInfo docInfo, String lang) {
        if (lang == null) {
            return docInfo.addNewHangulFaceName();
        }
        switch (lang) {
            case "Hangul":
                return docInfo.addNewHangulFaceName();
            case "Latin":
                return docInfo.addNewEnglishFaceName();
            case "Hanja":
                return docInfo.addNewHanjaFaceName();
            case "Japanese":
                return docInfo.addNewJapaneseFaceName();
            case "Other":
                return docInfo.addNewEtcFaceName();
            case "Symbol":
                return docInfo.addNewSymbolFaceName();
            case "User":
                return docInfo.addNewUserFaceName();
            default:
                return docInfo.addNewHangulFaceName();
        }
    }

    private static LevelNumbering levelNumbering(Numbering numbering, int level) throws Exception {
        if (level < 1 || level > 10) {
            return null;
        }
        return numbering.getLevelNumbering(level);
    }

    private static ParaHeadShape paraHeadShape(String headingType) {
        if (headingType == null) {
            return ParaHeadShape.None;
        }
        switch (headingType) {
            case "Outline":
                return ParaHeadShape.Outline;
            case "Number":
            case "Numbering":
                return ParaHeadShape.Numbering;
            case "Bullet":
                return ParaHeadShape.Bullet;
            default:
                return ParaHeadShape.None;
        }
    }

    private static Alignment alignment(String align) {
        if (align == null) {
            return Alignment.Justify;
        }
        switch (align) {
            case "Left":
                return Alignment.Left;
            case "Right":
                return Alignment.Right;
            case "Center":
                return Alignment.Center;
            case "Distribute":
                return Alignment.Distribute;
            case "Divide":
                return Alignment.Divide;
            case "Justify":
            default:
                return Alignment.Justify;
        }
    }

    private static ParagraphNumberFormat numberFormat(String numFormat) {
        if (numFormat == null) {
            return ParagraphNumberFormat.Number;
        }
        switch (numFormat) {
            case "Digit":
                return ParagraphNumberFormat.Number;
            case "CircledDigit":
                return ParagraphNumberFormat.CircledNumber;
            case "RomanCapital":
                return ParagraphNumberFormat.UppercaseRomanNumber;
            case "RomanSmall":
                return ParagraphNumberFormat.LowercaseRomanNumber;
            case "LatinCapital":
                return ParagraphNumberFormat.UppercaseAlphabet;
            case "LatinSmall":
                return ParagraphNumberFormat.LowercaseAlphabet;
            case "HangulSyllable":
                return ParagraphNumberFormat.Hangul;
            case "HangulJamo":
                return ParagraphNumberFormat.HangulJamo;
            case "Ideograph":
                return ParagraphNumberFormat.HanjaNumber;
            default:
                return ParagraphNumberFormat.Number;
        }
    }

    private static void setCounts(DocInfo docInfo) {
        IDMappings idm = docInfo.getIDMappings();
        idm.setHangulFaceNameCount(docInfo.getHangulFaceNameList().size());
        idm.setEnglishFaceNameCount(docInfo.getEnglishFaceNameList().size());
        idm.setHanjaFaceNameCount(docInfo.getHanjaFaceNameList().size());
        idm.setJapaneseFaceNameCount(docInfo.getJapaneseFaceNameList().size());
        idm.setEtcFaceNameCount(docInfo.getEtcFaceNameList().size());
        idm.setSymbolFaceNameCount(docInfo.getSymbolFaceNameList().size());
        idm.setUserFaceNameCount(docInfo.getUserFaceNameList().size());
        idm.setBorderFillCount(docInfo.getBorderFillList().size());
        idm.setCharShapeCount(docInfo.getCharShapeList().size());
        idm.setTabDefCount(docInfo.getTabDefList().size());
        idm.setNumberingCount(docInfo.getNumberingList().size());
        idm.setBulletCount(docInfo.getBulletList().size());
        idm.setParaShapeCount(docInfo.getParaShapeList().size());
        idm.setStyleCount(docInfo.getStyleList().size());
    }

    private static String stripTrailingLineTerminator(String s) {
        if (s.endsWith("\r\n")) {
            return s.substring(0, s.length() - 2);
        }
        if (s.endsWith("\n") || s.endsWith("\r")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String attr(XMLStreamReader xr, String name) {
        return xr.getAttributeValue(null, name);
    }

    private static int intAttr(XMLStreamReader xr, String name, int defaultValue) {
        String v = xr.getAttributeValue(null, name);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long longAttr(XMLStreamReader xr, String name, long defaultValue) {
        String v = xr.getAttributeValue(null, name);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
