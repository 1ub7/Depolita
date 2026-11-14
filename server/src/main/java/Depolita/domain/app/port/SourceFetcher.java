package Depolita.domain.app.port;

import java.nio.file.Path;

public interface SourceFetcher {
    Path fetch(String repositoryUrl);
    void cleanup(Path workDir);
}