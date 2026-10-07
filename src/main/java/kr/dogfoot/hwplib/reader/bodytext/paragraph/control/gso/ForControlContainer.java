package kr.dogfoot.hwplib.reader.bodytext.paragraph.control.gso;

import kr.dogfoot.hwplib.object.bodytext.control.gso.ControlContainer;
import kr.dogfoot.hwplib.object.bodytext.control.gso.GsoControl;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.ShapeComponentContainer;
import kr.dogfoot.hwplib.object.etc.HWPTag;
import kr.dogfoot.hwplib.reader.bodytext.paragraph.control.bookmark.ForCtrlData;
import kr.dogfoot.hwplib.util.compoundFile.reader.StreamReader;

/**
 * 묶은 컨트롤의 나머지 부분을 읽기 위한 객체
 *
 * @author neolord
 */
public class ForControlContainer {
    /**
     * 묶은 컨트롤의 나머지 부분을 읽는다.
     *
     * @param container 묶은 컨트롤
     * @param sr        스트림 리더
     * @throws Exception
     */
    public static void readRest(ControlContainer container, StreamReader sr)
            throws Exception {
        ShapeComponentContainer scc = (ShapeComponentContainer) container
                .getShapeComponent();
        int childCount = scc.getChildControlIdList().size();
        if (childCount > 0) {
            // 묶음 개체 요소 레코드 뒤에 컨트롤 임의 데이터가 올 수 있다.
            // 아니면 이미 읽은 헤더는 첫 번째 자식 컨트롤의 개체 요소 레코드이다.
            sr.readRecordHeader();
            if (sr.getCurrentRecordHeader().getTagID() == HWPTag.CTRL_DATA) {
                container.createCtrlData();
                ForCtrlData.read(container.getCtrlData(), sr);
            }
        }
        for (int index = 0; index < childCount; index++) {
            ForGsoControl fgc = new ForGsoControl();
            GsoControl gc = fgc.readInContainer(sr);
            container.addChildControl(gc);
        }
    }
}
