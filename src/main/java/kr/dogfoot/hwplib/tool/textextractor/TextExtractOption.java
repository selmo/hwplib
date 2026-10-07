package kr.dogfoot.hwplib.tool.textextractor;

public class TextExtractOption {
    private TextExtractMethod method;

    private boolean withControlChar;
    private boolean appendEndingLF;
    private boolean insertParaHead;
    private TableFormat tableFormat;
    /**
     * 표 캡션을 표 뒤에 추출할지 여부 (기본 true). false이면 1.1.x와 같이 캡션을 추출하지 않는다.
     */
    private boolean insertTableCaption;
    /**
     * 그리기 개체(그림, 도형 등)와 수식의 캡션을 개체 뒤에 추출할지 여부 (기본 true).
     */
    private boolean insertObjectCaption;
    /**
     * 양식 개체(체크박스, 라디오 버튼, 명령 단추의 캡션, 입력 상자/콤보 상자의 값)의 텍스트를 추출할지 여부 (기본 true).
     */
    private boolean insertFormObjectText;
    /**
     * 자동 번호(그림/표/수식 번호, 각주/미주 번호)를 문단 안 위치에 추출할지 여부 (기본 true).
     * 쪽 번호는 쪽 나눔을 계산하지 않으므로 추출하지 않는다.
     */
    private boolean insertAutoNumber;

    public TextExtractOption() {
        method = TextExtractMethod.InsertControlTextBetweenParagraphText;
        withControlChar = false;
        appendEndingLF = true;
        insertParaHead = true;
        tableFormat = TableFormat.None;
        insertTableCaption = true;
        insertObjectCaption = true;
        insertFormObjectText = true;
        insertAutoNumber = true;
    }

    public TextExtractOption(TextExtractMethod method) {
        this.method = method;
        withControlChar = false;
        appendEndingLF = true;
        insertParaHead = true;
        tableFormat = TableFormat.None;
        insertTableCaption = true;
        insertObjectCaption = true;
        insertFormObjectText = true;
        insertAutoNumber = true;
    }

    public TextExtractOption(TextExtractMethod method, boolean appendEndingLF) {
        this.method = method;
        withControlChar = false;
        this.appendEndingLF = appendEndingLF;
        insertParaHead = true;
        tableFormat = TableFormat.None;
        insertTableCaption = true;
        insertObjectCaption = true;
        insertFormObjectText = true;
        insertAutoNumber = true;
    }


    public TextExtractOption(TextExtractOption that) {
        this.method = that.method;
        this.withControlChar = that.withControlChar;
        this.appendEndingLF = that.appendEndingLF;
        this.insertParaHead = that.insertParaHead;
        this.tableFormat = that.tableFormat;
        this.insertTableCaption = that.insertTableCaption;
        this.insertObjectCaption = that.insertObjectCaption;
        this.insertFormObjectText = that.insertFormObjectText;
        this.insertAutoNumber = that.insertAutoNumber;
    }

    public TextExtractMethod getMethod() {
        return method;
    }

    public void setMethod(TextExtractMethod method) {
        this.method = method;
    }

    public boolean isWithControlChar() {
        return withControlChar;
    }

    public void setWithControlChar(boolean withControlChar) {
        this.withControlChar = withControlChar;
    }

    public boolean isAppendEndingLF() {
        return appendEndingLF;
    }

    public void setAppendEndingLF(boolean appendEndingLF) {
        this.appendEndingLF = appendEndingLF;
    }

    public boolean isInsertParaHead() {
        return insertParaHead;
    }

    public void setInsertParaHead(boolean insertParaHead) {
        this.insertParaHead = insertParaHead;
    }

    public TableFormat getTableFormat() {
        return tableFormat;
    }

    public void setTableFormat(TableFormat tableFormat) {
        this.tableFormat = tableFormat;
    }

    public boolean isInsertTableCaption() {
        return insertTableCaption;
    }

    public void setInsertTableCaption(boolean insertTableCaption) {
        this.insertTableCaption = insertTableCaption;
    }

    public boolean isInsertObjectCaption() {
        return insertObjectCaption;
    }

    public void setInsertObjectCaption(boolean insertObjectCaption) {
        this.insertObjectCaption = insertObjectCaption;
    }

    public boolean isInsertFormObjectText() {
        return insertFormObjectText;
    }

    public void setInsertFormObjectText(boolean insertFormObjectText) {
        this.insertFormObjectText = insertFormObjectText;
    }

    public boolean isInsertAutoNumber() {
        return insertAutoNumber;
    }

    public void setInsertAutoNumber(boolean insertAutoNumber) {
        this.insertAutoNumber = insertAutoNumber;
    }
}
