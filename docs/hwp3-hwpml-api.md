# hwplib 신규 API — 포맷 자동 판별 · HWPML · HWP 3.x 읽기 (v1.2.0)

1.2.0에서 추가된 공개 API 사용 안내. 기존 한글 5.0(`HWPReader`) API는 변경되지 않았다.

- 대상 버전: hwplib 1.2.0 (JDK 17, 추가 외부 의존성 없음)
- 관련 설계/진행 문서: [hwp3-hwpml-support-plan.md](hwp3-hwpml-support-plan.md)
- 사용 예제: `src/test/sample/Detecting_FileFormat.java`, `Reading_HWPML.java`, `Extracting_Text_From_HWP3.java`, `Verifying_HWP3_HWPML.java`

---

## 1. 개요

| 기능 | 진입점 | 반환 | 비고 |
|------|--------|------|------|
| 포맷 자동 판별 | `FormatDetector.detect` / `HWPLibReader.detectFormat` | `FileFormat` | HWP5/HWP3/HWPML/UNKNOWN |
| 통합 읽기 | `HWPLibReader` | `HWPFile` | **HWP5·HWPML·HWP3 모두 지원**(HWP3는 변환기 경유) |
| HWPML 읽기 | `HWPMLReader` | `HWPFile` | 기존 도구(TextExtractor 등) 그대로 재사용 |
| HWP 3.x 읽기 | `HWP3Reader` | `HWP3File` | 전용 모델. 텍스트(`getText()`)·표 구조(`getTables()`) |
| HWP3 → HWP5 변환 | `Hwp3ToHwpFileConverter` | `HWPFile` | 문단·표(`ControlTable`)·텍스트박스 변환 |

패키지:
```
kr.dogfoot.hwplib.reader            FileFormat, FormatDetector, HWPLibReader
kr.dogfoot.hwplib.reader.hwpml      HWPMLReader
kr.dogfoot.hwplib.reader.hwp3       HWP3Reader, ForParagraphList3, ForDrawingObject3
kr.dogfoot.hwplib.object.hwp3       HWP3File, Hwp3Paragraph, Hwp3Table, Hwp3Cell
kr.dogfoot.hwplib.util.hwp3         Hwp3CharDecoder, Hwp3StreamReader
kr.dogfoot.hwplib.tool.hwp3conv     Hwp3ToHwpFileConverter
```

---

## 2. 포맷 자동 판별

### `enum FileFormat`
`HWP5` · `HWP3` · `HWPML` · `UNKNOWN`

### `FormatDetector.detect(byte[] head) → FileFormat`
파일 앞부분 바이트(매직/내용)로 형식을 판별한다. 전체 바이트를 넘겨도 되고, 앞부분만 넘겨도 된다.

| 형식 | 판별 기준 |
|------|-----------|
| `HWP5` | OLE/CFB 시그니처(`D0 CF 11 E0 …`) |
| `HWP3` | `"HWP Document File V3.00 …"` (30바이트 인식 정보) |
| `HWPML` | XML 선언 후 `<HWPML …>` 루트 (BOM/UTF-16 고려) |
| `UNKNOWN` | 위 어디에도 해당하지 않음(예: .hwpx ZIP) |

```java
byte[] data = Files.readAllBytes(Path.of("sample.hwp"));
FileFormat format = FormatDetector.detect(data);   // 또는 HWPLibReader.detectFormat(data)
```

---

## 3. 통합 진입점 — `HWPLibReader`

형식을 자동 판별해 알맞은 리더로 위임하고 공통 모델 `HWPFile`을 반환한다.

```java
public static HWPFile fromFile(String filepath)        throws Exception
public static HWPFile fromFile(File file)              throws Exception
public static HWPFile fromInputStream(InputStream is)  throws Exception
public static HWPFile fromBytes(byte[] data)           throws Exception
public static FileFormat detectFormat(byte[] data)
```

```java
HWPFile hwp = HWPLibReader.fromFile("sample.hwp");     // HWP5 / HWPML / HWP3(자동 변환)
String text = TextExtractor.extract(hwp, TextExtractMethod.InsertControlTextBetweenParagraphText);
```

> HWP3 입력은 `HWP3Reader`로 읽은 뒤 `Hwp3ToHwpFileConverter`로 `HWPFile`로 변환해
> 반환한다. 문단 텍스트와 표 구조(`ControlTable`, 셀 그리드·병합)가 변환되므로
> `TextExtractor`(표 렌더링 `TableFormat` 포함)를 그대로 쓸 수 있다. 글자/문단 모양은
> 기본값으로 통일되며, HWP3 전용 정보가 필요하면 `HWP3Reader`를 직접 사용한다.

---

## 4. HWPML 읽기 — `HWPMLReader`

HWPML(.hml, XML)을 읽어 기존 `HWPFile` 객체 모델로 매핑한다. 본문/글자, 글꼴·글자모양·문단모양·스타일, 표·그리기 개체·글상자를 채우므로 `TextExtractor`·`ObjectFinder` 등 기존 도구를 그대로 쓸 수 있다.

```java
public static HWPFile fromFile(String filepath)        throws Exception
public static HWPFile fromFile(File file)              throws Exception
public static HWPFile fromInputStream(InputStream is)  throws Exception
public static HWPFile fromBytes(byte[] data)           throws Exception
public static boolean isHWPML(byte[] data)
```

```java
HWPFile hwp = HWPMLReader.fromFile("sample.hml");
String text = TextExtractor.extract(hwp, TextExtractMethod.InsertControlTextBetweenParagraphText);
```

- 보안: StAX 파싱 시 DTD/외부 엔티티 비활성화(XXE 방지).
- 검증: 동일 내용의 HWP5 추출과 비교 시 텍스트 99.9% 이상 일치.

---

## 5. HWP 3.x 읽기 — `HWP3Reader`

한글 3.0/97 레거시 바이너리(`"HWP Document File V3.00"`)를 읽어 **전용 모델** `HWP3File`을 반환한다. HWP5와 구조가 크게 달라 별도 모델을 사용한다.

```java
public static HWP3File fromFile(String filepath)       throws Exception
public static HWP3File fromFile(File file)             throws Exception
public static HWP3File fromInputStream(InputStream is) throws Exception
public static HWP3File fromBytes(byte[] data)          throws Exception
public static boolean isHWP3(byte[] data)
```

```java
HWP3File hwp3 = HWP3Reader.fromFile("legacy.hwp");
String text = hwp3.getText();                 // 전체 평문 텍스트
for (Hwp3Paragraph p : hwp3.getParagraphs()) {
    System.out.println(p.getText());
}
```

### `class HWP3File`
| 메서드 | 반환 | 설명 |
|--------|------|------|
| `getText()` | `String` | 모든 문단 텍스트를 줄바꿈으로 연결. 표 셀·각주·머리말·글상자의 중첩 문단도 문서 순서로 포함 |
| `getParagraphs()` | `List<Hwp3Paragraph>` | 문단 목록(중첩 평탄화) |
| `getTables()` | `List<Hwp3Table>` | 문서 전체 표 구조(중첩 포함, 문서 순서) |
| `getParagraphCount()` | `int` | 문단 수 |
| `isCompressed()` | `boolean` | 본문 압축(raw DEFLATE) 여부 |
| `hasPassword()` | `boolean` | 암호 설정 여부 |
| `getSubRevision()` | `int` | 하위 리비전 |
| `getInfoBlockLength()` | `int` | 정보 블록 #0 길이 |
| `getFontCountPerLanguage()` | `int[]` | 7개 언어별 글꼴 개수 |
| `getStyleCount()` | `int` | 스타일 개수 |

### `class Hwp3Paragraph`
| 메서드 | 반환 | 설명 |
|--------|------|------|
| `getText()` | `String` | 문단 평문 텍스트 |
| `getTables()` | `List<Hwp3Table>` | 이 문단에 포함된 표 구조(등장 순서) |

### `class Hwp3Table` / `class Hwp3Cell` (표 구조)
| 메서드 | 반환 | 설명 |
|--------|------|------|
| `Hwp3Table.isTable()` | `boolean` | 박스 종류가 표인지(코드 10은 텍스트박스/수식/버튼 공용) |
| `Hwp3Table.getRowCount()` / `getColCount()` | `int` | 기하 복원된 그리드 행/열 수 |
| `Hwp3Table.getCells()` / `getCaption()` | `List` | 셀 / 캡션 문단 |
| `Hwp3Cell.getGridRow()` / `getGridCol()` | `int` | 그리드 행/열 인덱스(셀 위치·크기로 기하 계산) |
| `Hwp3Cell.getGridRowSpan()` / `getGridColSpan()` | `int` | 병합 범위 |
| `Hwp3Cell.getText()` / `getParagraphs()` | | 셀 내용 |

> 셀 정보의 줄/칸 일련 번호 필드는 "내장 시트 기능용"이라 실제 파일에서 채워지지
> 않는다(코퍼스 전부 0). 그리드는 셀 위치/크기(hunit)로부터 기하적으로 복원된다.

### 지원 범위 (텍스트 추출 기준)
- ✅ 조합형 한글 + 한자/기호(5,893쌍 매핑) → 유니코드 디코딩
- ✅ 문단/특수문자 순회: 표 셀, 각주/미주, 머리말/꼬리말, 숨은 설명, **그리기 개체 안 글상자** 텍스트 재귀 추출
- ✅ 표 구조 보존(`Hwp3Table`/`Hwp3Cell` 그리드·병합) + `HWPFile` 변환(`Hwp3ToHwpFileConverter`) → `TableFormat` 렌더링 지원
- ✅ 실문서 6종 HWP5 추출 대비 본문 96~99.9% 일치(본문/표/글상자 한글 누락 0), 변환 후 마크다운 표 행 수 6종 전부 HWP5와 일치
- ⚠️ 글머리표/외곽번호 기호(• · □ ① 등)는 HWP3에서 문단 속성으로 생성되어 글자 스트림에 없어 미복원
- ⛔ 암호화된 HWP3 문서는 미지원(`hasPassword()==true`면 예외)

---

## 6. 유틸리티 (`kr.dogfoot.hwplib.util.hwp3`)

### `Hwp3CharDecoder`
한글 3.x 문자 내부코드(hchar, 2바이트) → 유니코드 코드 포인트.
```java
public static int toCodePoint(int hchar)   // 변환 불가 시 -1
public static boolean isHangul(int hchar)
```

### `Hwp3StreamReader`
리틀 엔디언 바이너리 리더 + raw DEFLATE 해제.
```java
int readUInt8(); int readInt8(); int readUInt16(); short readInt16(); long readUInt32();
byte[] readBytes(int n); void skip(int n);
static byte[] inflateRaw(byte[] input, int offset) throws Exception
```

---

## 7. 의존성 / 빌드
- Maven, **Java 17** 타깃. HWP3 압축 해제는 `java.util.zip`, HWPML은 StAX(`javax.xml.stream`) — 모두 JDK 내장.
- 한자/기호 매핑 테이블은 리소스(`util/hwp3/johab_symbols.bin`)로 패키징되어 jar에 포함된다.
