package kr.dogfoot.hwplib.reader.hwp3;

import kr.dogfoot.hwplib.object.hwp3.HWP3File;
import kr.dogfoot.hwplib.reader.FileFormat;
import kr.dogfoot.hwplib.reader.FormatDetector;
import kr.dogfoot.hwplib.util.hwp3.Hwp3StreamReader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;

/**
 * 한글 3.x 레거시 바이너리 파일을 읽기 위한 객체.
 *
 * <p>규격: 「한글 문서 파일 형식 3.0 / HWPML」 revision 1.2, Part I.</p>
 *
 * <p>현재 P0 단계의 골격이다. 시그니처 검증까지만 구현되어 있고, 본문 파싱은
 * 이후 단계(P2: 헤더/gzip 해제/문단 텍스트)에서 구현한다.</p>
 */
public class HWP3Reader {
    /**
     * 파일 인식 정보의 길이(바이트).
     */
    public static final int FILE_RECOGNITION_SIZE = 30;
    /** 문서 정보의 길이(바이트). */
    public static final int DOC_INFO_SIZE = 128;
    /** 문서 요약의 길이(바이트). */
    public static final int DOC_SUMMARY_SIZE = 1008;
    /** 글꼴 이름 하나의 길이(kchar array[40]). */
    public static final int FONT_NAME_LENGTH = 40;
    /** 스타일 정보 하나의 길이(바이트). */
    public static final int STYLE_INFO_SIZE = 238;

    /**
     * hwp 3.x 파일을 읽는다.
     *
     * @param filepath 파일 경로
     * @return HWP3File 객체
     * @throws Exception 파일을 읽는 도중 오류가 발생한 경우
     */
    public static HWP3File fromFile(String filepath) throws Exception {
        return fromInputStream(new FileInputStream(filepath));
    }

    /**
     * hwp 3.x 파일을 읽는다.
     *
     * @param file 파일
     * @return HWP3File 객체
     * @throws Exception 파일을 읽는 도중 오류가 발생한 경우
     */
    public static HWP3File fromFile(File file) throws Exception {
        return fromInputStream(new FileInputStream(file));
    }

    /**
     * hwp 3.x 파일을 읽는다.
     *
     * @param is 입력 스트림
     * @return HWP3File 객체
     * @throws Exception 파일을 읽는 도중 오류가 발생한 경우
     */
    public static HWP3File fromInputStream(InputStream is) throws Exception {
        byte[] data = readAll(is);
        return fromBytes(data);
    }

    /**
     * hwp 3.x 파일의 전체 바이트를 읽는다.
     *
     * @param data 파일 전체 바이트
     * @return HWP3File 객체
     * @throws Exception 시그니처가 올바르지 않거나 아직 미구현인 경우
     */
    public static HWP3File fromBytes(byte[] data) throws Exception {
        if (!isHWP3(data)) {
            throw new Exception("Not a HWP 3.x file. (signature mismatch)");
        }

        HWP3File file = new HWP3File();

        // 문서 정보(128바이트, 파일 오프셋 30~157)에서 필요한 플래그를 읽는다.(규격 표3)
        final int docInfoOffset = FILE_RECOGNITION_SIZE; // 30
        int password = uint16(data, docInfoOffset + 96);
        boolean compressed = uint8(data, docInfoOffset + 124) != 0;
        int subRevision = uint8(data, docInfoOffset + 125);
        int infoBlockLength = uint16(data, docInfoOffset + 126);

        file.setHasPassword(password != 0);
        file.setCompressed(compressed);
        file.setSubRevision(subRevision);
        file.setInfoBlockLength(infoBlockLength);

        if (password != 0) {
            throw new Exception("Password-protected HWP 3.x files are not supported.");
        }

        // 압축 영역 시작: 인식(30) + 문서정보(128) + 문서요약(1008) + 정보블록(infoBlockLength)
        int bodyOffset = FILE_RECOGNITION_SIZE + DOC_INFO_SIZE + DOC_SUMMARY_SIZE + infoBlockLength;

        // 글꼴 이름 ~ 추가 정보 블록 #1은 raw DEFLATE로 압축되어 있다.
        byte[] body = compressed
                ? Hwp3StreamReader.inflateRaw(data, bodyOffset)
                : Arrays.copyOfRange(data, bodyOffset, data.length);

        Hwp3StreamReader sr = new Hwp3StreamReader(body);

        // 글꼴 이름: 7개 언어별로 (word nfonts, kchar fontnames[nfonts][40])
        for (int lang = 0; lang < 7; lang++) {
            int nfonts = sr.readUInt16();
            file.getFontCountPerLanguage()[lang] = nfonts;
            sr.skip(nfonts * FONT_NAME_LENGTH);
        }

        // 스타일: word nstyles, { 스타일 정보(238바이트) } x nstyles
        int nstyles = sr.readUInt16();
        file.setStyleCount(nstyles);
        sr.skip(nstyles * STYLE_INFO_SIZE);

        // TODO(C3): 문단 리스트 파싱(문단 정보/줄 정보/글자 모양/글자들) + hchar 디코딩
        return file;
    }

    /**
     * 주어진 바이트가 한글 3.x 파일 인식 정보 시그니처로 시작하는지 확인한다.
     *
     * @param data 파일 앞부분 바이트
     * @return 한글 3.x 시그니처이면 true
     */
    public static boolean isHWP3(byte[] data) {
        return FormatDetector.detect(data) == FileFormat.HWP3;
    }

    private static int uint8(byte[] b, int offset) {
        return b[offset] & 0xFF;
    }

    private static int uint16(byte[] b, int offset) {
        return (b[offset] & 0xFF) | ((b[offset + 1] & 0xFF) << 8);
    }

    private static byte[] readAll(InputStream is) throws Exception {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        } finally {
            is.close();
        }
    }
}
