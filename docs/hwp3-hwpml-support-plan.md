# hwplib HWP 3.x(레거시 바이너리) · HWPML 3.0 지원 계획서

- 작성일: 2026-06-02
- 대상 저장소: hwplib (v1.1.10)
- 참조 규격: 한컴 「한글 문서 파일 형식 3.0 / HWPML」 revision 1.2 (20141105)
  - 로컬 사본: `docs/spec/hwpml3.0_rev1.2.pdf`
  - Part I (p.3~46): 한글 3.x 문서 파일 구조 (Hwp Document File Format 3.x)
  - Part II (p.47~110): HWPML 구조

---

## 1. 목표 및 확정 범위

| 항목 | 결정 |
|------|------|
| 대상 포맷 | **둘 다** — ① 레거시 바이너리 HWP 3.x ② HWPML 3.0 (XML) |
| 지원 범위 | **전체 읽기(구조 보존)** — 텍스트 추출만이 아니라 서식·표·이미지·그리기 개체까지 객체 모델로 파싱 |
| 쓰기(저장) | **범위 외** (이번 계획에서 제외) |
| 호환 대상 | 한글 3.0 / 97 / 2002~2010이 생성한 3.x·HWPML 파일 |

설계 원칙(글로벌 지침 반영):
- **순수 추가 방식**: 기존 HWP5 읽기 경로(`HWPReader`, `CompoundFileReader`, 객체 모델)는 수정하지 않는다. 새 패키지만 추가하므로 기존 기능 회귀 위험이 없다(백업 불필요).
- **핵심 기능 / 보조 기능 구분**: 본문/문단/글자/서식 파싱은 핵심. 그리기 개체·OLE·미리보기 이미지 등은 보조이며, 보조 기능 착수 전 사용자 확인.

---

## 2. 현재 아키텍처 제약 (조사 결과)

- `kr.dogfoot.hwplib.object.HWPFile` = HWP 5.0 전용 모델
  - `FileHeader` / `DocInfo` / `BodyText` / `BinData` / `SummaryInformation` / `Scripts`
  - `BodyText` → `Section[]` → `Paragraph[]` → (글자/컨트롤/글자모양…)
- `kr.dogfoot.hwplib.reader.HWPReader`
  - 모든 진입점(`fromFile`, `fromInputStream`, `fromURL`, `fromBase64String`)이 `CompoundFileReader`(OLE/CFB)에 직접 결합.
  - record header + tagID 기반 스트림 파싱(HWP5 고유).
- 결론: HWP 3.x·HWPML은 이 경로를 **재사용할 수 없고**, 별도 리더가 동일한 `HWPFile`(또는 변환 가능한) 객체를 산출하도록 해야 한다.

---

## 3. 핵심 설계 결정 — 객체 모델 타깃팅 전략

다운스트림 도구(`tool/textextractor`, `objectfinder` 등)를 재사용하려면 최종 산출물이 기존 `HWPFile`이어야 한다. 그러나 포맷별 충실도가 달라 다음 **하이브리드 전략**을 권장한다.

### 3.1 HWPML 3.0 → `HWPFile`로 직접 매핑
- HWPML은 의미상 HWP 5.0과 사실상 동형(Header≈DocInfo, Body≈BodyText/Section). XML 엘리먼트를 기존 객체 모델 필드에 직접 채우는 것이 자연스럽다.
- 산출물: 기존 `HWPFile` 그대로 → 모든 다운스트림 도구 즉시 호환.

### 3.2 레거시 HWP 3.x → 전용 모델 + 변환기
- HWP 3.x는 구조가 HWP5와 크게 다르다(평면 record, `hchar` 내부코드, 단·페이지 힌트, 정보블록 #0/#1/#2 등). HWP5 record 모델에 직접 욱여넣으면 손실·왜곡이 크다.
- 권장: **충실한 전용 객체 모델** `object.hwp3.*` 를 먼저 만들고, **best-effort 변환기** `Hwp3ToHwpFileConverter`로 `HWPFile`을 부가 제공.
  - 1차 목표(전용 모델로 완전 파싱) → 2차 목표(HWPFile 변환으로 도구 재사용).

> ⚠️ 결정 필요 지점: HWP 3.x를 "전용 모델 + 변환기"(권장)로 갈지, "HWPFile 직접 매핑"으로 갈지. 권장안은 충실도·유지보수 측면에서 유리하나 코드량이 늘어난다. (§9 참조)

### 3.3 포맷 자동 판별 (Dispatch) — 신규 통합 진입점
새 통합 진입점 `kr.dogfoot.hwplib.HWPLibReader`(또는 기존 `HWPReader`에 정적 메서드 추가)를 두고 매직/내용으로 분기:

| 시그니처 / 내용 | 포맷 | 라우팅 |
|----------------|------|--------|
| OLE CFB (`D0 CF 11 E0 …`) + "HWP Document File" | HWP 5.x | 기존 `HWPReader` |
| `48 57 50 20 44 6F 63 75 6D 65 6E 74 20 46 69 6C 65 20 56 33 2E 30 30 …`(`HWP Document File V3.00 \x1a\1\2\3\4\5`) | HWP 3.x | 신규 `HWP3Reader` |
| `<?xml … <HWPML …>` (또는 BOM 후 `<HWPML`) | HWPML | 신규 `HWPMLReader` |

---

## 4. 레거시 바이너리 HWP 3.x 리더 계획

### 4.1 포맷 핵심 사실 (규격 Part I 기준)
- **파일 인식 정보**: 첫 30바이트, 시그니처 `"HWP Document File V3.00 \x1a\1\2\3\4\5"`.
- **자료형**: little-endian. `byte/sbyte/word/sword/dword/sdword`, 문자형 `hchar`(2B 한글 내부코드), `echar`(1B ASCII), `kchar`(1B 상용조합형), 크기단위 `hunit/shunit`(1/1800인치).
- **전체 구조**(표 2):
  1. 파일 인식 정보 (30B)
  2. 문서 정보 (128B) — 압축 플래그·암호 여부 포함
  3. 문서 요약 (1008B)
  4. 정보 블록 #0 (가변)
  5. 글꼴 이름 (가변, **압축**)
  6. 스타일 (가변, **압축**)
  7. 문단 리스트 (가변, **압축**)
  8. 추가 정보 블록 #1 (가변, **압축**)
  9. 추가 정보 블록 #2 (가변)
- **압축**: `글꼴 이름`~`추가 정보 블록 #1`이 하나의 gzip 스트림으로 저장. 문서 정보의 압축 플래그 확인 후 `GZIPInputStream`/`Inflater`로 해제하여 비압축 파일처럼 처리.
- **문자 인코딩**: `hchar`는 유니코드가 아니다 → **내부코드→유니코드 변환 테이블** 필요(조합형/확장완성형). 가장 큰 난제.
- **세부 구조**: 문단(4장), 문단모양(5장), 글자모양(6장), 특수문자/필드코드(10장), 그리기 개체(11장), OLE(12장).

### 4.2 패키지 설계
```
kr.dogfoot.hwplib.reader.hwp3            # 리더 (스트림/레코드 파서)
  HWP3Reader.java                        # 진입점: fromFile/fromInputStream
  ForFileRecognition.java                # 30B 인식정보 + 시그니처 검증
  ForDocInfo3.java                        # 128B 문서정보(압축/암호 플래그)
  ForDocSummary3.java                     # 1008B 문서요약
  ForInfoBlock3.java                      # 정보블록 #0/#1/#2
  ForFontNames3.java / ForStyles3.java
  ForParagraphList3.java / ForParagraph3.java
  ForParaShape3.java / ForCharShape3.java
  ForSpecialChar3.java                    # 필드코드/표/그림/선/각주 등 (보조)
  ForDrawingObject3.java / ForOle3.java   # (보조)
kr.dogfoot.hwplib.object.hwp3.*          # 전용 객체 모델
kr.dogfoot.hwplib.util.hwp3
  Hwp3StreamReader.java                   # LE 리더 + gzip 해제 + hchar 디코딩
  Hwp3CharCodeTable.java                  # 내부코드 ↔ 유니코드
kr.dogfoot.hwplib.tool.hwp3conv
  Hwp3ToHwpFileConverter.java             # HWP3 모델 → HWPFile (best-effort)
```

### 4.3 단계 (핵심 → 보조)
- **C1 (핵심)**: 시그니처 검증 + 30/128/1008B 헤더 + gzip 해제 파이프라인 + `Hwp3StreamReader`.
- **C2 (핵심)**: `hchar`→유니코드 변환 테이블, 글꼴 이름·스타일 파싱.
- **C3 (핵심)**: 문단 리스트/문단/줄/글자모양/글자들 → 텍스트·기본서식 완전 파싱.
- **C4 (보조, 사전확인)**: 특수문자/필드코드(표·각주·하이퍼링크·번호 등) 파싱.
- **C5 (보조, 사전확인)**: 그리기 개체·OLE·미리보기 이미지.
- **C6**: `Hwp3ToHwpFileConverter`로 HWPFile 변환 → 텍스트 추출기 재사용.

---

## 5. HWPML 3.0 리더 계획

### 5.1 포맷 핵심 사실 (규격 Part II 기준)
- 루트 `<HWPML>` → `<HEAD>`(문서요약/설정/글꼴·테두리·글자모양·탭·글머리표·문단모양·스타일/메모) + `<BODY>`(글자·구역정의·표·그림·그리기개체·양식객체·OLE·필드·책갈피·머리말꼬리말·각주미주 등).
- 좌표/크기 단위와 속성값 표기는 규격 §2(기본 속성값 형식)에 정의 — 파서가 공통 파싱 유틸로 처리.
- HWP5 객체 모델과 의미가 대응되므로 **직접 매핑** 가능.

### 5.2 패키지 설계
```
kr.dogfoot.hwplib.reader.hwpml
  HWPMLReader.java                 # 진입점: fromFile/fromInputStream
  ForHead.java                     # <HEAD> → DocInfo 매핑
  ForBody.java                     # <BODY> → BodyText/Section 매핑
  ForCharShapeML / ForParaShapeML / ForFaceNameML / ...
  ForTableML / ForPictureML / ForDrawingObjectML / ...   # (보조)
kr.dogfoot.hwplib.util.hwpml
  XmlReaderHelper.java             # StAX(XMLStreamReader) 기반 공통 파싱
  HWPMLAttrParser.java             # 기본 속성값 형식(§2) 파싱
```
- 파서 기술: **StAX(`javax.xml.stream`)** 권장(대용량·스트리밍, 추가 의존성 없음). JDK 내장.

### 5.3 단계 (핵심 → 보조)
- **M1 (핵심)**: 루트/HEAD/BODY 골격 + 글자 엘리먼트 → 텍스트.
- **M2 (핵심)**: 글꼴/글자모양/문단모양/스타일 → DocInfo, 구역/단 정의.
- **M3 (보조, 사전확인)**: 표·그림·그리기 개체·OLE·양식 객체.
- **M4 (보조, 사전확인)**: 필드/책갈피/머리말꼬리말/각주미주/찾아보기/덧말 등.

---

## 6. 단계별 로드맵 (마일스톤)

| 단계 | 산출물 | 검증 기준 |
|------|--------|-----------|
| **P0 ✅완료** | 포맷 판별 dispatch + 골격 패키지/인터페이스 | 세 포맷 라우팅 (합성 8 + 실제 19파일 통과) |
| **P1 🟢 M1·M2·M3완료** | HWPML 본문+HEAD+표+그리기/글상자 | 6종 전부 텍스트 **≥99.9%** (3종 100%), over-extraction 0 ✅ |
| **P2 🟡 C1+C2완료** | HWP3 헤더·해제(C1) + hchar 디코더(C2) | 조합형→유니코드 실문서 검증 ✅ (문단/표 C3 잔여) |
| P3 | HWP3→HWPFile 변환 + 텍스트추출기 통합 (C6) | 기존 `TextExtractor` 동작 |
| P4 | 보조 기능: 표/그림/그리기/OLE (양 포맷, **사전확인 후**) | 구조 보존 검증 |
| P5 | 문서화·README 갱신·회귀 테스트 | 전체 테스트 통과 |

---

## 7. 빌드/의존성 영향
- 빌드: Maven, **Java 17 타깃**(2026-06-02 7→17 상향), Apache POI 내장(`kr.dogfoot.hwplib.org.apache.poi`).
- HWP3 gzip: `java.util.zip` (JDK 내장, 추가 의존성 없음).
- HWPML: StAX `javax.xml.stream` (JDK 내장).
- → **신규 외부 의존성 없음**.

### 7.1 JDK 17 베이스 전환 (완료)
Java 7 타깃은 최신 JDK에서 빌드 불가(제거된 API 사용)하여 17로 상향했다.
- `pom.xml`: `source/target` 7→17, deprecated `<compilerVersion>1.7>` 제거.
- `reader/HWPReader.java`: `javax.xml.bind.DatatypeConverter`(Java 11에서 제거) → `java.util.Base64`(`getMimeDecoder`).
- `writer/autosetter/ForDocInfo.java`: 미사용 `com.sun.jmx.snmp.agent` 임포트 제거.
- 검증: `mvn clean compile` (JDK 17) 통과.

---

## 8. 테스트 전략
- `src/test/resources`에 포맷별 샘플(3.x, HWPML, 5.0 회귀용) 확보 — *사용자가 실제 샘플 파일 제공 필요*.
- 단위: 헤더/시그니처, gzip 해제, hchar 변환, XML 속성 파싱.
- 통합: 동일 내용의 5.0 / 3.x / HWPML 문서 → 추출 텍스트·문단 수 동등성 비교(가능 범위).
- 회귀: 기존 HWP5 테스트 전부 통과(기존 코드 무변경이므로 자동 보장).

---

## 9. 리스크 및 미해결 결정 사항
1. **HWP3 객체 모델 전략** (§3.2): 전용 모델+변환기(권장) vs HWPFile 직접 매핑 — *결정 필요*.
2. **hchar→유니코드 변환표**: 조합형/확장완성형 매핑 정확도가 텍스트 품질을 좌우. 검증된 테이블 확보 또는 이식 필요(리스크 高).
3. **샘플 파일 확보**: 실제 3.x·HWPML 파일이 있어야 검증 가능 — *사용자 제공 요청*.
4. **보조 기능 범위**: 그리기 개체·OLE·수식은 작업량이 크다. 핵심(텍스트·서식·표) 완료 후 필요성 재확인.
5. **암호/압축 변형**: 3.x 암호화 문서는 1차 범위에서 제외 권장.

---

## 11. P0 구현 결과 (2026-06-02 완료)

확정 사항: HWP3 = **전용 모델 + 변환기**, **핵심 우선**, JDK 17 베이스.

추가된 파일(모두 신규, 기존 코드 무수정):
| 파일 | 역할 |
|------|------|
| `reader/FileFormat.java` | 형식 열거형(HWP5/HWP3/HWPML/UNKNOWN) |
| `reader/FormatDetector.java` | 앞부분 바이트로 형식 판별(OLE/HWP3 sig/XML, BOM·UTF-16 고려) |
| `reader/HWPLibReader.java` | **통합 진입점** — 판별 후 라우팅, 공통 `HWPFile` 반환 |
| `reader/hwp3/HWP3Reader.java` | HWP3 리더 골격 — 시그니처 검증까지(본문 P2) |
| `reader/hwpml/HWPMLReader.java` | HWPML 리더 골격 — 루트 검증까지(본문 P1) |
| `object/hwp3/HWP3File.java` | HWP3 전용 모델 골격(구조 주석/ TODO) |
| `tool/hwp3conv/Hwp3ToHwpFileConverter.java` | HWP3→HWPFile 변환기 골격(P3) |
| `src/test/sample/Detecting_FileFormat.java` | 사용 예제 |

미구현 본문 파싱은 `UnsupportedOperationException("... not implemented yet (P0 skeleton)")`로 명시.

## 12. P1 / M1 구현 결과 (2026-06-03)

`reader/hwpml/HWPMLReader.java`에 StAX 기반 본문 파서 구현:
- `<BODY>` → `<SECTION>` → `<P>` → `<TEXT CharShape>` → `<CHAR>` 순회 → `HWPFile`의
  BodyText/Section/Paragraph/ParaText로 매핑.
- 한 `<P>` 안의 여러 `<TEXT>/<CHAR>` 런을 한 문단으로 연결, 글자 모양 경계는 `ParaCharShape`에 best-effort 기록.
- `<CHAR>` 끝의 pretty-print 줄바꿈 1개 제거(실제 텍스트와 정확히 일치 확인).
- 보안: StAX DTD/외부 엔티티 비활성화(XXE 방지), `IS_COALESCING` 사용.
- 컨트롤 컨테이너(TABLE/DRAWTEXT/HEADER/FOOTER/FOOTNOTE/ENDNOTE) 내부 중첩 문단은 M3까지 본문에서 제외.

### 검증 (테스트 코퍼스)
- `src/test/resources/sample-docs/` 에 동일 문서 6종 × {hwp5, hwp3, hwpml} + hwpx 1 + 배포용 hwp 1 (총 7.8MB).
- 6종 모두 HWPML 본문 문단 텍스트가 HWP5 추출 결과와 **바이트 단위 동일**(문단 수·문자 수 일치, 최대 607문단/11,855자).

## 13. P1 / M2 구현 결과 (2026-06-03)

`reader/hwpml/ForHead.java` 신규 + `HWPMLReader` 통합: `<HEAD>`의 MAPPINGTABLE을 `DocInfo`로 매핑.
- 글꼴(FONTFACE/FONT→7개 언어별 FaceName), 글자모양(CHARSHAPE: 크기·언어별 글꼴ID/비율/자간/장평/오프셋·굵게/기울임·색·테두리), 문단모양(PARASHAPE: 정렬·문단머리모양(HeadingType→ParaHeadShape)·머리id(Heading)·여백·탭), 번호정의(NUMBERING/PARAHEAD 레벨별 형식), 스타일(STYLE), 테두리·탭·글머리표(개수).
- IDMappings 카운트 설정. 각 구역 첫 문단에 컨트롤 리스트 생성(`TextExtractor`의 구역 정의 참조 NPE 방지).

### 검증
- 6종 모두 `TextExtractor.extract()` **무오류 동작**(M1 단계의 NPE 해소).
- 본문 문단 텍스트+문단머리 서식이 HWP5 추출과 일치. 3종은 HWPML 추출 라인이 HWP5의 부분집합으로 **완전 일치(CLEAN)**, 3종은 소수(1~9) 라인만 상이.
- 상이 원인: ①표 셀 텍스트(HWP5는 포함, HWPML은 M3 전까지 제외) ②일부 아웃라인 문단머리 공백·인라인 컨트롤(각주/하이퍼링크, M3/M4). 내용 손상 아님.

## 14. P1 / M3 (표) 구현 결과 (2026-06-03)

`HWPMLReader`를 **재귀 하강(recursive-descent)** 방식으로 재작성 + 표 매핑:
- `<TABLE>` → `ControlTable`(행/열/셀/셀 문단). 셀의 `<PARALIST>`→`<P>`를 재귀로 읽어 중첩 표·다단 문단 자연 처리.
- 불변식 준수: ParaText의 ControlExtend 문자(0x0b "tbl ")와 문단 컨트롤 리스트를 1:1 순서로 추가(추출기 순차 인덱스 매칭).
- 표 외 컨트롤(그림/그리기/글상자/각주)은 `skipSubtree`로 건너뜀(확장 문자/컨트롤 미생성 → 불변식 유지).

### 검증 (문자 단위 커버리지, HWP5 추출 대비)
| 문서 | 커버리지 |
|------|---------|
| 중소기업 융자계획(40,326자) | **100.00%** |
| 식품방사능 | **100.00%** |
| 재난예경보(21→1,556자) | **100.00%** |
| 재정동향 | 99.97% |
| 교육부 | 99.94% |
| 금융위 | 90.89% (글상자 371자 미처리) |

## 15. P1 / M3 (그리기 개체·글상자) 구현 결과 (2026-06-03)

`HWPMLReader`에 그리기 개체(GSO) 처리 추가:
- shape 엘리먼트(RECTANGLE/ELLIPSE/ARC/POLYGON/CURVE/LINE/CONTAINER/PICTURE/OLE 등) → `addNewGsoControl` + 확장 컨트롤 문자("gso ").
- 텍스트 박스(`<DRAWTEXT>`) → shape의 `TextBox` 문단 리스트로 매핑(`createTextBox`/`getTextBox`).
- 묶음(`<CONTAINER>`) → `ControlContainer`, 자식 shape는 `addNewChildControl`로 재귀(별도 확장 문자 없음 → 불변식 유지).

### 최종 검증 (문자 단위 커버리지, HWP5 추출 대비)
| 문서 | M3-표 후 | **그리기/글상자 후** |
|------|:---:|:---:|
| 중소기업(40,326자) | 100.00% | **100.00%** |
| 식품방사능 | 100.00% | **100.00%** |
| 재난예경보 | 100.00% | **100.00%** |
| 재정동향 | 99.97% | 99.97% |
| 교육부 | 99.94% | 99.94% |
| 금융위 | 90.89% | **99.90%** (글상자 복원) |

over-extraction(잉여 추출) 0 — GSO 구조/불변식 정확.

### 잔여 (영향 미미, 내용 손실 아님)
- 기호 글꼴 문자의 PUA(0xF0000) 오프셋 미적용(교육부 6자): HWP5는 `U+F02Bx`, HWPML은 원본 `U+02Bx`로 표현.
- 일부 하이픈(페이지번호/구분선 등 자동 컨트롤, 금융위 4·재정동향 1자).
- 각주/미주/숨은설명 등 일부 컨트롤 텍스트는 추후.

## 16. P2 / C1 구현 결과 (2026-06-03)

레거시 HWP 3.x 리더의 헤더·압축 해제·섹션 구조 파싱:
- `util/hwp3/Hwp3StreamReader`: 리틀 엔디언 리더(byte/word/dword/…) + `inflateRaw`(raw DEFLATE).
- `HWP3Reader.fromBytes`: 시그니처 검증 → 문서 정보(128B)에서 압축/암호/sub/정보블록길이 추출 → 압축 영역(오프셋 1166+정보블록) **raw DEFLATE 해제** → 글꼴 이름(7개 언어) + 스타일 섹션 구조 파싱.
- `object/hwp3/HWP3File`: 헤더 플래그·글꼴/스타일 개수 필드.

### 핵심 확인 사항
- **압축 방식 = raw DEFLATE**(`Inflater(nowrap=true)`). gzip/zlib 래퍼 아님.
- 레이아웃: 인식(30)+문서정보(128)+요약(1008)+정보블록(가변, 샘플은 0)+압축영역.
- 6종 검증: 압축=1·암호=0·sub=1 일치, 글꼴 개수 문서 간 일관(한글~123/영문~196/…), 스타일 2~60 — 해제 스트림 레이아웃 정확.

## 17. P2 / C2 구현 결과 (2026-06-04) — hchar 디코더(핵심 난제 해결)

`util/hwp3/Hwp3CharDecoder`: hchar(2바이트) → 유니코드.
- 한글 = KS C 5601 **조합형(johab) 5-5-5**(0x8000 비트 + 초성5/중성5/종성5). 비연속 5비트 값을 현대 자모 인덱스로 매핑 후 `0xAC00 + (초*21+중)*28 + 종`.
- ASCII(0x00~0x7F) 그대로.
- 한자/기호(0x80~0x7FFF) 변환은 보강 예정.

### 검증 (실제 레거시 파일 코드 디코딩)
- `B861`→자, `95B7`→동(초ㄷ·중ㅗ·종ㅇ) 등 손 검증 일치.
- 재난예경보 para#0 → **"자동음성경보장치 (70개소)"**
- 중소기업 para#0 → **"중소벤처기업부 공고 제2026-287"**
- 정확한 한국어 복원 확인.

### 잔여 (C3 — 문단/특수문자 순회)
- 문단 리스트: 문단정보(43/230B)→줄정보(14B×줄수)→글자모양정보→글자들(hchar) 순회.
- **특수문자(0~31) 인라인 처리**가 관건. 일반형식(표32: code+dwordLen+code+data)과 고정형(탭8/책갈피42/날짜84·96 등)은 명확하나, **표/그림/머리말/각주(코드 10/11/15/16/17)의 중첩 문단 리스트가 글자 스트림 내에서 차지하는 슬롯 수(`글자 수` 산입 방식)가 규격만으로 모호**(실측: 표 문단 nchar=5 = 식별정보 4슬롯+1).
  → 해당 in-stream 레이아웃은 공개 HWP3 구현(예: `ddoleye/java-hwp`의 `HwpTextExtractorV3`, `pyhwp` hwp3) 교차 참조 또는 코퍼스 기반 추가 리버스 엔지니어링으로 확정 필요.
- 한자/기호 코드 매핑.
- **C6**: `Hwp3ToHwpFileConverter`로 `HWPFile` 변환 → 텍스트 추출기 재사용.

## 18. P2 / C2 정정 + C3 구현 결과 (2026-06-08) — rhwp 교차검증

공개 구현 **rhwp**(edwardkim/rhwp)의 `src/parser/hwp3/` 를 줄 단위 대조하여 §17 잔여(C3)와 정확성 결함을 해소했다.

### 18.1 (정정) 중성(中聲) 매핑 버그
`Hwp3CharDecoder` 의 조합형 중성 5비트 빈칸(gap)이 `{17,18}` 로 잘못 설정되어 있었다(표준은 `{8,9},{16,17},{24,25}`). 그 결과 중성값 18=**ㅚ** 가 미매핑되어 **회·최·외·되·뇌·죄·왼** 등 ㅚ 음절이 디코딩 실패(-1)했다.
- 수정: `jungValues` 의 `16` → `18` (한 글자). 회귀 검증: `0xD241`→회, `0xC241`→최 정상.
- C2 검증이 통과한 이유: 당시 샘플 텍스트에 ㅚ 음절이 우연히 없었음.

### 18.2 (보강) 한자/기호 테이블
rhwp `johab_map.rs` 의 `JOHAB_SYMBOLS`(5,893쌍) + 사적 graphic 보정(`decode_hwp3_extra`)을 이식.
- 패키징: `src/main/resources/.../hwp3/johab_symbols.bin`(23.5KB, johab→유니코드 4B×N, 정렬). johab 코드가 UTF-16 서러게이트 구간을 포함하므로 String/배열 리터럴 대신 **바이너리 리소스 + 이진탐색**.
- 0x0080~0x7FFF 한컴 사적 코드(로마숫자 Ⅰ~Ⅹ, 일부 PUA)는 `decodeExtra()` 로 보정.
- 검증: `0x8441`→전각공백, `0x8442`→ㄱ, `0xF9FE`→詰 등.

### 18.3 (완료) C3 — 문단 리스트/특수문자 순회 (텍스트 추출)
`reader/hwp3/ForParagraphList3` 신규 + `HWP3File.getText()`. 문단정보(43/230B)→줄정보(14B×줄수)→인라인 글자모양(flag+31B)→글자들(hchar) 순회. 표/그림/각주/머리말/숨은설명은 **재귀**로 중첩 문단 텍스트 복원.
- **§17 모호점 확정**: 일반형 제어문자(코드 5~17,29 등)는 char_count 상 **8바이트 헤더=4 hchar 슬롯**만 차지하고, 본문(84B 표정보+27B×셀+중첩문단+캡션 / 348B 그림정보+확장 / 8·10·14B 정보+중첩)은 글자 스트림 **바깥**에서 별도로 읽힘. (rhwp round-trip 검증 기준)
- 제어코드별 바이트 소비 표는 `ForParagraphList3` 주석 참조.

### 18.4 검증 (HWP5 `TextExtractor` 대비 문자 단위 커버리지)
동일 문서 6종(hwp3 ↔ hwp5), 공백/제어문자 제외 멀티셋 비교:

| 문서 | 커버리지 | **한글 누락** | 비고 |
|------|:---:|:---:|------|
| 재난예경보 | 99.94% | 0 | — |
| 식품방사능 | 98.05% | 0 | — |
| 교육부 | 98.15% | 2 | — |
| 재정동향 | 97.29% | 0 | 괘선/한자 소수 |
| 중소기업(31K자) | 96.64% | **0** | 누락 전부 글머리표(•·□①→※) |
| 금융위 | 88.29% | 302 | 글상자(텍스트박스) 제목 — P4 |

**핵심: 한글 본문 누락 0(금융위 제외) → 스트림 정렬·johab 디코딩 정확(데스ync 없음).** 잔여 누락은 모두 보조 기능 영역:
- **글머리표/외곽번호 기호**(•, ·, □, ①②③, →, ※ 등): HWP3는 글자 스트림이 아닌 **문단 글머리표·외곽 레벨 속성**에서 생성 → rhwp 도 `fixup_hwp3_outline_bullets`/`heading_decoration` 사후 주입. (P4)
- **글상자 텍스트**(금융위): 그림 컨트롤(ch=11)의 확장 데이터(그리기 트리) 내 텍스트박스 — 현재 skip. (P4, HWPML M3에서 동일 이슈 90.89→99.90% 해결한 전례 있음)
- 일부 한자: 테이블 미수록 코드.

### 18.5 추가/수정 파일 (모두 신규 HWP3 경로, 기존 HWP5 무수정)
| 파일 | 변경 |
|------|------|
| `util/hwp3/Hwp3CharDecoder.java` | 중성 버그 수정 + 한자/기호 테이블 로더 + PUA 보정 |
| `resources/.../hwp3/johab_symbols.bin` | 신규(5,893쌍 바이너리) |
| `reader/hwp3/ForParagraphList3.java` | 신규(문단/특수문자 순회) |
| `object/hwp3/Hwp3Paragraph.java` | 신규(문단 텍스트) |
| `object/hwp3/HWP3File.java` | `paragraphs`/`getText()` 추가 |
| `reader/hwp3/HWP3Reader.java` | C3 파서 호출 연결 |

### 18.6 잔여 (다음 단계)
- **P4(보조)**: 글머리표/외곽번호 기호 주입, 글상자(그리기 트리) 텍스트, 표/그림 구조 보존 객체 모델.
- **C6**: `Hwp3ToHwpFileConverter` 로 `HWPFile` 변환 → 기존 `TextExtractor`/`objectfinder` 재사용.
- 기호 정규화(예: 글머리 ■/□)는 렌더러 없는 환경에서 HWP5 추출과의 표기 차이로 남음.

## 19. P4 구현 결과 (2026-06-10) — 글상자 텍스트 복원 + 글머리표 조사

### 19.1 (완료) 글상자(텍스트 박스) 텍스트 복원
`reader/hwp3/ForDrawingObject3` 신규. 그림 컨트롤(ch=11, pic_type=3)의 확장 데이터에 담긴
**그리기 개체 트리**를 순회하여 도형 안 글상자 문단 텍스트를 추출한다.
- 트리 구조: 프레임 헤더(28B) → (header_length>24 시 하이퍼텍스트 605B) → 개체들(연결 정보의 형제/자식 비트로 연결). 개체 = 공통 헤더(고정 48B + 기본속성 44B + options 비트별 회전32/그라데이션28/비트맵278) + 종류별 세부.
- **핵심 발견**: 정부문서의 글상자는 글상자 전용 종류(6)가 아니라 **사각형(종류 2)에 텍스트**를 담는다. 닫힌 도형(사각형/타원/글상자/호)은 `[info1_len][info2_len][문단 리스트(info2_len B)]` 구조이며 info2가 글상자 텍스트다. rhwp는 종류 6만 텍스트를 읽어 이 케이스를 놓친다(렌더링은 별도). → 닫힌 도형 전체로 일반화하여 info2 문단 리스트를 재귀 파싱.
- 격리: 그리기 트리 파싱은 확장 데이터 **서브 버퍼 안에서만** 동작 → 정렬이 어긋나도 본문 글자 스트림 무영향.

검증: **금융위 88.29% → 97.05%** (한글 누락 302 → **0**). 제목 "금융위, 핀테크 투자 생태계 활성화 나선다" 등 글상자 텍스트 전량 복원. 나머지 5종 회귀 없음.

| 문서 | C3(§18) | **P4 글상자 후** | 한글 누락 |
|------|:---:|:---:|:---:|
| 재난예경보 | 99.94% | 99.94% | 0 |
| 교육부 | 98.15% | 98.15% | 2 |
| 식품방사능 | 98.05% | 98.05% | 0 |
| 재정동향 | 97.29% | 97.29% | 0 |
| 중소기업 | 96.64% | 96.64% | 0 |
| 금융위 | 88.29% | **97.05%** | **0** |

### 19.2 (보류) 글머리표/외곽번호 기호 주입 — 일반화 불가로 미채택
잔여 누락은 전 문서 공통으로 **글머리표/문단번호 기호**(• · □ ① ② ③ → ※ 「」 따옴표 등). 조사 결과:
- **• (U+2022), · (U+00B7)**: johab 심볼 테이블에 **부재** → 본문 글자 스트림에 없음. HWP3에서 **문단 글머리표/문단번호 속성**으로 정의되어 글자가 아니라 렌더 시 생성됨.
- **① ② ③**: 외곽번호(자동 생성). HWP3 `ParaShape`에는 글머리/번호 필드가 없고(left/right/indent/line_spacing/tabs/…), 번호 정의는 문서 레벨 정의 + 레벨 참조로 해석해야 함.
- **■ ↔ □**: 동일 글머리 사적 코드의 글리프 정규화 차이(렌더러 의존).
- rhwp의 글머리 복원(`fixup_hwp3_outline_bullets`/`heading_decoration`)은 **sample16 전용 좁은 휴리스틱**(특정 margin 패턴 L=6500,R=1000,I=-2500 등)으로, rhwp 주석도 "다른 sample엔 0건 매치"라 명시. **정부문서엔 일반화되지 않으며**, 임의 휴리스틱 주입은 over-extraction 위험.
- **결론**: 코퍼스 전용 휴리스틱을 하드코딩하지 않음. 정확한 복원은 HWP3 번호/글머리 정의 해석(문서 레벨)을 요하며 별도 과제. **본문/표/글상자 텍스트는 전량 복원**되었고, 잔여는 내용 손실이 아닌 글머리 기호 표기 차이.

### 19.3 추가 파일
| 파일 | 변경 |
|------|------|
| `reader/hwp3/ForDrawingObject3.java` | 신규(그리기 트리 → 글상자 텍스트 추출) |
| `reader/hwp3/ForParagraphList3.java` | ch=11에서 pic_type=3 그리기 트리 텍스트 복원 연결 |

## 20. 표 추출 개선 A 구현 결과 (2026-06-11) — TextExtractor 표 렌더링

표 추출 개선 검토(A: 추출기 렌더링 / B: HWP3 구조 보존 / C: HWP3→HWPFile 변환) 중 **A 완료**.

### 구현
- `tool/textextractor/TableFormat`(신규): `None`(기존) / `Delimited`(셀=탭, 행=줄바꿈) / `Markdown`.
- `TextExtractOption.tableFormat` 추가(기본 `None` — 기존 출력 호환). 복사 생성자의 `insertParaHead` 누락도 수정.
- `ForControl.table()`: 셀 좌표(rowIndex/colIndex/rowSpan/colSpan)로 그리드 복원 → 병합 셀은 anchor에 텍스트, 덮인 칸은 빈 셀. 좌표 충돌/범위 밖이면 리스트 순서 폴백. 셀 내부 줄바꿈은 Delimited=공백, Markdown=`<br>`, 파이프 이스케이프.
- **캡션 추출 버그 수정**: 표 캡션 문단이 모든 모드에서 추출되도록 (기존엔 통째로 누락).

### 검증 (코퍼스 6종 × HWP5/HWPML)
- 예외 0. HWP5 ↔ HWPML 마크다운 표 행 수 6종 모두 일치(교차 검증).
- 식품방사능 표: "물고등어미검출…" 비가독 연결 → 열 정렬된 마크다운/탭 표로 복원(세로 병합 "수산물" 정확).
- None 모드 회귀(대조 실험, git stash): 6종 중 4종 **바이트 동일**. 재정동향 +44자·중소기업 +274자는 전부 기존에 누락되던 **캡션 텍스트**("(단위: 조원, %, %p)", "< … 기업 >" 등) — 손상 없음.
- 샘플: `src/test/sample/Extracting_Text_With_TableFormat.java`.

### 잔여 (B·C)
- B: HWP3 표 구조 보존(셀 줄/칸 주소·병합 비트 파싱 — 현재 skip 중) → C: ControlTable 변환으로 HWP3도 동일 렌더링 수혜.

## 10. 다음 액션 (착수 전 확인 요청)
- [x] §9-1 HWP3 객체 모델 전략 확정 — 전용 모델 + 변환기
- [x] §9-3 검증용 샘플 파일 제공 (6종 × 3포맷)
- [x] P0 dispatch → P1(HWPML) → P2(C1/C2) → C3 텍스트 추출 완료
- [x] P4 글상자 텍스트 복원 완료 / 글머리표는 일반화 불가로 보류
- [ ] **C6**: `Hwp3ToHwpFileConverter` 로 `HWPFile` 변환 → 기존 `TextExtractor`/`objectfinder` 재사용
- [ ] 표/그림 구조 보존 객체 모델(렌더링/편집용) 필요 시
