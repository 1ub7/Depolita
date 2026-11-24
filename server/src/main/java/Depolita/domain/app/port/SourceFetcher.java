package Depolita.domain.app.port;

import java.nio.file.Path;

public interface SourceFetcher {
    FetchedSource fetch(String repositoryUrl);
    void cleanup(Path workDir);
}
