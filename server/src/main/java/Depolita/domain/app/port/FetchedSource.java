package Depolita.domain.app.port;

import java.nio.file.Path;

/**
 * 복제한 소스.
 *
 * @param dir       작업 폴더 (배포가 끝나면 SourceFetcher.cleanup 으로 지운다)
 * @param commitSha 복제한 시점의 HEAD 커밋 SHA (알 수 없으면 null)
 */
public record FetchedSource(Path dir, String commitSha) {
}
