package kr.dogfoot.hwplib.tool.textextractor.paraHead;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.control.Control;
import kr.dogfoot.hwplib.object.bodytext.control.ControlSectionDefine;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.docinfo.Bullet;
import kr.dogfoot.hwplib.object.docinfo.Numbering;
import kr.dogfoot.hwplib.object.docinfo.ParaShape;
import kr.dogfoot.hwplib.object.docinfo.Style;
import kr.dogfoot.hwplib.object.docinfo.numbering.LevelNumbering;
import kr.dogfoot.hwplib.object.docinfo.numbering.ParagraphNumberFormat;
import kr.dogfoot.hwplib.util.StringUtil;

public class ParaHeadMaker {
    private HWPFile hwpFile;
    private ControlSectionDefine sectionDefine;
    private ParaNumber paraNumberForNumbering;
    private ParaNumber paraNumberForOutline;
    private Numbering defaultNumbering;

    public ParaHeadMaker(HWPFile hwpFile) {
        this.hwpFile = hwpFile;
        
        makeDefaultNumbering();
        if (hwpFile.getBodyText().getSectionList().size() > 0) {
            setSectionDefine(hwpFile.getBodyText().getSectionList().get(0));
        }
        paraNumberForNumbering = new ParaNumber();
    }

    private void makeDefaultNumbering() {
        defaultNumbering = new Numbering();
        defaultNumbering.setStartNumber(0);

        {
            LevelNumbering lv = defaultNumbering.getLevelNumberingList().get(0);
            lv.setStartNumber(0);
            lv.getParagraphHeadInfo().getProperty().setParagraphNumberFormat(ParagraphNumberFormat.Number);
            lv.getNumberFormat().fromUTF16LEString(null);
        }
        {
            LevelNumbering lv = defaultNumbering.getLevelNumberingList().get(1);
            lv.setStartNumber(0);
            lv.getParagraphHeadInfo().getProperty().setParagraphNumberFormat(ParagraphNumberFormat.Number);
            lv.getNumberFormat().fromUTF16LEString("^2.");
        }
        {
            LevelNumbering lv = defaultNumbering.getLevelNumberingList().get(2);
            lv.setStartNumber(0);
            lv.getParagraphHeadInfo().getProperty().setParagraphNumberFormat(ParagraphNumberFormat.Number);
            lv.getNumberFormat().fromUTF16LEString("^2.^3.");
        }
        {
            LevelNumbering lv = defaultNumbering.getLevelNumberingList().get(3);
            lv.setStartNumber(0);
            lv.getParagraphHeadInfo().getProperty().setParagraphNumberFormat(ParagraphNumberFormat.Number);
            lv.getNumberFormat().fromUTF16LEString("^2.^3.^4.");
        }
        {
            LevelNumbering lv = defaultNumbering.getLevelNumberingList().get(4);
            lv.setStartNumber(0);
            lv.getParagraphHeadInfo().getProperty().setParagraphNumberFormat(ParagraphNumberFormat.Number);
            lv.getNumberFormat().fromUTF16LEString("^2.^3.^4.^5.");
        }
        {
            LevelNumbering lv = defaultNumbering.getLevelNumberingList().get(5);
            lv.setStartNumber(0);
            lv.getParagraphHeadInfo().getProperty().setParagraphNumberFormat(ParagraphNumberFormat.Number);
            lv.getNumberFormat().fromUTF16LEString("^2.^3.^4.^5.^6.");
        }
        {
            LevelNumbering lv = defaultNumbering.getLevelNumberingList().get(6);
            lv.setStartNumber(0);
            lv.getParagraphHeadInfo().getProperty().setParagraphNumberFormat(ParagraphNumberFormat.Number);
            lv.getNumberFormat().fromUTF16LEString("^2.^3.^4.^5.^6.^7.");
        }
        {
            LevelNumbering lv = defaultNumbering.getLevelNumberingList().get(7);
            lv.setStartNumber(0);
            lv.getParagraphHeadInfo().getProperty().setParagraphNumberFormat(ParagraphNumberFormat.Number);
            lv.getNumberFormat().fromUTF16LEString("^2.^3.^4.^5.^6.^7.^8.");
        }
        {
            LevelNumbering lv = defaultNumbering.getLevelNumberingList().get(8);
            lv.setStartNumber(0);
            lv.getParagraphHeadInfo().getProperty().setParagraphNumberFormat(ParagraphNumberFormat.Number);
            lv.getNumberFormat().fromUTF16LEString("^2.^3.^4.^5.^6.^7.^8.^9.");
        }
        {
            LevelNumbering lv = defaultNumbering.getLevelNumberingList().get(9);
            lv.setStartNumber(0);
            lv.getParagraphHeadInfo().getProperty().setParagraphNumberFormat(ParagraphNumberFormat.Number);
            lv.getNumberFormat().fromUTF16LEString("^2.^3.^4.^5.^6.^7.^8.^9.");
        }
    }

    public void startSection(Section section) {
        setSectionDefine(section);
        paraNumberForOutline = new ParaNumber();
    }

    public void endSection() {
        paraNumberForOutline = null;
    }


    private void setSectionDefine(Section section) {
        if (section.getParagraphCount() > 0 && section.getParagraph(0).getControlList() != null
                && section.getParagraph(0).getControlList().size() > 0) {
            Control firstControl = section.getParagraph(0).getControlList().get(0);
            if (firstControl.getType() == ControlType.SectionDefine) {
                sectionDefine = (ControlSectionDefine) firstControl;
            } else {
                if (section.getParagraph(0).getControlList().size() >= 2) {
                    Control secondControl = section.getParagraph(0).getControlList().get(1);
                    if (secondControl.getType() == ControlType.SectionDefine) {
                        sectionDefine = (ControlSectionDefine) secondControl;
                    }
                }
            }
        }
    }

    public String paraHeadString(Paragraph paragraph) {
        ParaShape paraShape = paraShape(paragraph.getHeader().getParaShapeId());
        if (paraShape == null) {
            return "";
        }
        switch (paraShape.getProperty1().getParaHeadShape()) {
            case None:
                return "";
            case Outline:
                return outline(paragraph.getHeader().getStyleId(),
                        paraShape.getProperty1().getParaLevel());
            case Numbering:
                return numbering(paraShape.getParaHeadId(),
                        paraShape.getProperty1().getParaLevel());
            case Bullet:
                return bullet(paraShape.getParaHeadId(),
                        paraShape.getProperty1().getParaLevel());
        }
        return null;
    }

    private String outline(int styleID, byte paraLevel) {
        if (styleID < 0 || styleID >= hwpFile.getDocInfo().getStyleList().size()) {
            return null;
        }
        Style style = hwpFile.getDocInfo().getStyleList().get(styleID);
        ParaShape outlineParaShape = paraShape(style.getParaShapeId());
        if (outlineParaShape == null) {
            return null;
        }

        Numbering numbering = getNumbering(outlineParaShape.getParaHeadId());
        if (numbering == null) {
            return null;
        }
        LevelNumbering lv;
        try {
            lv = numbering.getLevelNumbering(paraLevel + 1);
        } catch (Exception e) {
            e.printStackTrace();
            lv = null;
        }
        if (lv != null) {
            if (paraNumberForOutline.changedParaHead(outlineParaShape.getParaHeadId())) {
                paraNumberForOutline.reset(outlineParaShape.getParaHeadId(), paraLevel, (int) lv.getStartNumber());
            } else {
                paraNumberForOutline.increase(paraLevel);
            }

            return numberText(lv, paraNumberForOutline, paraLevel);
        } else {
            return null;
        }
    }

    /**
     * 문단 모양을 반환한다. 아이디가 문단 모양 목록 범위를 벗어나면 null을 반환한다.
     */
    private ParaShape paraShape(int paraShapeId) {
        if (paraShapeId < 0 || paraShapeId >= hwpFile.getDocInfo().getParaShapeList().size()) {
            return null;
        }
        return hwpFile.getDocInfo().getParaShapeList().get(paraShapeId);
    }

    private Numbering getNumbering(int paraHeadId) {
        if (paraHeadId == 0) return defaultNumbering;
        if (paraHeadId < 0 || paraHeadId > hwpFile.getDocInfo().getNumberingList().size()) return null;
        return hwpFile.getDocInfo().getNumberingList().get(paraHeadId - 1);
    }

    private String numbering(int paraHeadID, byte paraLevel) {
        Numbering numbering = getNumbering(paraHeadID);
        if (numbering == null) {
            return null;
        }

        LevelNumbering lv;
        try {
            lv = numbering.getLevelNumbering(paraLevel + 1);
        } catch (Exception e) {
            e.printStackTrace();
            lv = null;
        }

        if (lv != null) {
            if (paraNumberForNumbering.changedParaHead(paraHeadID)) {
                paraNumberForNumbering.reset(paraHeadID, paraLevel, (int) lv.getStartNumber());
            } else {
                paraNumberForNumbering.increase(paraLevel);
            }

            return numberText(lv, paraNumberForNumbering, paraLevel);
        } else {
            return null;
        }
    }

    private String numberText(LevelNumbering lv, ParaNumber paraNumber, int paraLevel) {
        String format = lv.getNumberFormat().toUTF16LEString();
        String[] tokens = new String[10];
        String[] values = new String[10];
        for (int level = 0; level <= paraLevel; level++) {
            tokens[level] = "^" + (level + 1);
            values[level] = ParaHeadNumber.toString(paraNumber.value(level), lv.getParagraphHeadInfo().getProperty().getParagraphNumberFormat());
        }
        return StringUtil.replaceEach(format, tokens, values);
    }

    private String bullet(int paraHeadId, byte paraLevel) {
        if (paraHeadId > hwpFile.getDocInfo().getBulletList().size()) {
            return null;
        } else if (paraHeadId > 0) {
            Bullet bullet = hwpFile.getDocInfo().getBulletList().get(paraHeadId - 1);
            return bullet.getBulletChar().toUTF16LEString();
        } else {
            return "●";
        }
    }
}
