package service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public class DuplicateImageFinder {

    private DuplicateImageFinder() {
    }

    public static Optional<Path> findExistingDuplicate(Path targetDirectory, Path imageFile) throws IOException {
        if (targetDirectory == null || imageFile == null || !Files.isRegularFile(imageFile)) {
            return Optional.empty();
        }

        long sourceSize = Files.size(imageFile);
        Path normalizedSource = imageFile.toAbsolutePath().normalize();

        try (Stream<Path> files = Files.list(targetDirectory)) {
            for (Path candidate : files.toList()) {
                if (!Files.isRegularFile(candidate) || !Controller.isImageFile(candidate.toFile())) {
                    continue;
                }

                Path normalizedCandidate = candidate.toAbsolutePath().normalize();
                if (normalizedCandidate.equals(normalizedSource)) {
                    return Optional.of(candidate);
                }

                if (Files.size(candidate) == sourceSize && Files.mismatch(imageFile, candidate) == -1) {
                    return Optional.of(candidate);
                }
            }
        }

        return Optional.empty();
    }
}
