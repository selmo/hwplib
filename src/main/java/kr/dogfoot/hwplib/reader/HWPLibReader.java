package kr.dogfoot.hwplib.reader;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.hwp3.HWP3File;
import kr.dogfoot.hwplib.reader.hwp3.HWP3Reader;
import kr.dogfoot.hwplib.reader.hwpml.HWPMLReader;
import kr.dogfoot.hwplib.tool.hwp3conv.Hwp3ToHwpFileConverter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * 파일 형식(한글 5.0 / 한글 3.x / HWPML)을 자동으로 판별하여 알맞은 리더로 위임하는
 * 통합 진입점.
 *
 * <p>모든 형식을 공통 객체 모델인 {@link HWPFile}로 반환한다. 한글 3.x의 경우
 * {@link HWP3Reader}로 전용 모델을 읽은 뒤 {@link Hwp3ToHwpFileConverter}로 변환한다.
 * 한글 3.x의 충실한 전용 모델({@link HWP3File})이 필요하면 {@link HWP3Reader}를 직접
 * 사용한다.</p>
 */
public class HWPLibReader {
    /**
     * 파일을 읽어 형식에 맞게 파싱한다.
     *
     * @param filepath 파일 경로
     * @return 공통 객체 모델 {@link HWPFile}
     * @throws Exception 형식을 판별할 수 없거나 읽기 오류가 발생한 경우
     */
    public static HWPFile fromFile(String filepath) throws Exception {
        return fromInputStream(new FileInputStream(filepath));
    }

    /**
     * 파일을 읽어 형식에 맞게 파싱한다.
     *
     * @param file 파일
     * @return 공통 객체 모델 {@link HWPFile}
     * @throws Exception 형식을 판별할 수 없거나 읽기 오류가 발생한 경우
     */
    public static HWPFile fromFile(File file) throws Exception {
        return fromInputStream(new FileInputStream(file));
    }

    /**
     * 입력 스트림을 읽어 형식에 맞게 파싱한다.
     *
     * @param is 입력 스트림
     * @return 공통 객체 모델 {@link HWPFile}
     * @throws Exception 형식을 판별할 수 없거나 읽기 오류가 발생한 경우
     */
    public static HWPFile fromInputStream(InputStream is) throws Exception {
        return fromBytes(readAll(is));
    }

    /**
     * 파일 전체 바이트를 읽어 형식에 맞게 파싱한다.
     *
     * @param data 파일 전체 바이트
     * @return 공통 객체 모델 {@link HWPFile}
     * @throws Exception 형식을 판별할 수 없거나 읽기 오류가 발생한 경우
     */
    public static HWPFile fromBytes(byte[] data) throws Exception {
        FileFormat format = detectFormat(data);
        switch (format) {
            case HWP5:
                return HWPReader.fromInputStream(new ByteArrayInputStream(data));
            case HWPML:
                return HWPMLReader.fromBytes(data);
            case HWP3:
                HWP3File hwp3 = HWP3Reader.fromBytes(data);
                return Hwp3ToHwpFileConverter.convert(hwp3);
            default:
                throw new Exception("Unknown or unsupported file format.");
        }
    }

    /**
     * 파일 전체 바이트로 형식을 판별한다.
     *
     * @param data 파일 바이트
     * @return 판별된 {@link FileFormat}
     */
    public static FileFormat detectFormat(byte[] data) {
        return FormatDetector.detect(data);
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
